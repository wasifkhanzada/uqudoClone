package com.example.practice_android_4

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.YuvImage
import android.media.Image
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.appcompat.widget.Toolbar
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.practice_android_4.camera.CameraService
import com.example.practice_android_4.databinding.FragmentFrontEidDetectionBinding
import com.example.practice_android_4.eid_detection.Helper
import com.example.practice_android_4.ml.Eid
import kotlinx.coroutines.cancelChildren
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.io.ByteArrayOutputStream

class FrontEidDetectionFragment : BaseFragment() {

    private var _binding: FragmentFrontEidDetectionBinding? = null
    private val binding get() = _binding!!
    private lateinit var cameraService: CameraService
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private lateinit var eidDetectionService: Helper

    private lateinit var imageProcessor: ImageProcessor
    private lateinit var labels:List<String>
    private lateinit var model: Eid

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Adding the base layout to the fragment's root view
        _binding = FragmentFrontEidDetectionBinding.inflate(inflater, container, false)

        val rootView = super.onCreateView(inflater, container, savedInstanceState) as ViewGroup
        // Combine base layout and fragment layout
        rootView.findViewById<FrameLayout>(R.id.content_frame).addView(binding.root)

//        showLoading()

        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupFullscreen()
        setupOtherServices()

        // Check camera permission
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST_CODE)
        } else {
            initializeCameraService()
        }

        goBack()

        labels = FileUtil.loadLabels(requireContext(), "labels.txt")
        imageProcessor = ImageProcessor.Builder().add(ResizeOp(eidDetectionService.imageProcessorTargetSize, eidDetectionService.imageProcessorTargetSize, ResizeOp.ResizeMethod.BILINEAR)).build()
        model = Eid.newInstance(requireContext())

    }

    private fun setupFullscreen() {}

    private fun setupOtherServices() {
        // Initialize Helper service
        eidDetectionService = Helper()
        // Example usage of loading layer
    }

    private fun initializeCameraService() {
        cameraService = CameraService(
            context = requireContext(),
            lifecycleOwner = viewLifecycleOwner,
            previewView = binding.previewView,
            onImageCaptured = ::processImageProxy
        )
        cameraService.startCamera(lensFacing)
    }

    private fun playShotSound() {
        activity?.startService(Intent(requireContext(), SoundPoolService::class.java))
    }

    private fun goBack() {
        val toolbar: Toolbar = binding.goBack
        toolbar.setNavigationOnClickListener {
//            findNavController().navigateUp()
            findNavController().navigate(R.id.action_frontEidDetection_to_HomeFragment)
//            playShotSound()
        }
    }

    private fun Image.toBitmap(): Bitmap {
        val yBuffer = planes[0].buffer // Y
        val vuBuffer = planes[2].buffer // VU

        val ySize = yBuffer.remaining()
        val vuSize = vuBuffer.remaining()

        val nv21 = ByteArray(ySize + vuSize)

        yBuffer.get(nv21, 0, ySize)
        vuBuffer.get(nv21, ySize, vuSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 50, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }
    private fun Bitmap.rotate(degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(imageProxy: ImageProxy) {
        val image = imageProxy.image
        if (image != null) {
            val byteArray = eidDetectionService.imageProxyToByteArray(imageProxy)
            updateOverlayBasedOnFrontEid(imageProxy, byteArray)
        }
        imageProxy.close()
    }

    private fun updateOverlayBasedOnFrontEid(imageProxy: ImageProxy, byteArray: ByteArray){
        //get Bitmap into the imageProxy
        var bitmap = imageProxy.toBitmap().rotate(90f)
        //crop bitmap
        val cropBitmap = eidDetectionService.cropToSquare(bitmap)

        bitmap = cropBitmap.bitmap!!

        var image = TensorImage.fromBitmap(bitmap)

        image = imageProcessor.process(image)

        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val outputs = model.process(image)

        val locations = outputs.locationAsTensorBuffer.floatArray
        val classes = outputs.categoryAsTensorBuffer.floatArray
        val scores = outputs.scoreAsTensorBuffer.floatArray

        val h = bitmap.height
        val w = bitmap.width

        var x = 0

        var left = 0.toFloat()
        var right = 0.toFloat()
        var bottom = 0.toFloat()
        var top = 0.toFloat()
        var label = ""
        var type = "NOT_DETECTED"

        val frameDimension = binding.cardOverlayView.getFrameDimension()

        scores.forEachIndexed { index, score ->
            if (score > eidDetectionService.eidScore) {

                label = labels[classes[index].toInt()]

                if (!eidDetectionService.toFrontDetect.contains(label)) {
                    type = "NOT_DETECTED_FRONT_EID"
                    return@forEachIndexed
                }

                x = index * 4
                top = locations[x] * h
                left = locations[x + 1] * w
                bottom = locations[x + 2] * h
                right = locations[x + 3] * w

                if (cropBitmap.cropSide!! == "height") {
                    top += cropBitmap.dimension!!
                    bottom += cropBitmap.dimension
                }
                if (cropBitmap.cropSide == "width") {
                    left += cropBitmap.dimension!!
                    right += cropBitmap.dimension
                }

                Log.d("FOUND_EID_LABEL", label)

                val rectF = RectF(locations[x + 1] * w, locations[x] * h, locations[x + 3] * w, locations[x + 2] * h)
                var croppedBitmap: Bitmap? = eidDetectionService.cropBitmap(mutableBitmap, rectF.left.toInt(), rectF.top.toInt(), rectF.width().toInt(), rectF.height().toInt())

                if(croppedBitmap != null) {

//                    requireActivity().runOnUiThread {
//                        binding.processedFrontEidImageView.setImageBitmap(croppedBitmap)
//                    }

                    if (eidDetectionService.isImageBlurry(croppedBitmap, eidDetectionService.imageBlurryThreshold)) {
                        type = "EID_IS_BLURRY"
                        return@forEachIndexed
                    }

                    if (eidDetectionService.calculateBitmapBrightness(croppedBitmap) < eidDetectionService.imageBrightnessThreshold) {
                        type = "LOW_LIGHT_DETECTED"
                        return@forEachIndexed
                    }

                    if (eidDetectionService.detectGlare(croppedBitmap)) {
                        type = "DETECTED_WITH_GLARE"
                        return@forEachIndexed
                    }
                }

                val card = Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())

                val boundingBox = eidDetectionService.getBoxRect(binding.cardBoxOverlay, imageProxy.width.toFloat(), imageProxy.height.toFloat(), card)

                 if(eidDetectionService.isRectFMatchWithinTolerance(boundingBox, frameDimension, eidDetectionService.insideTolerance)){
                     type = "DETECTED_TO_FAR"
                     return@forEachIndexed
                 }

                // Check if the bounding box is inside the frame
                if (!binding.cardOverlayView.isBoundingBoxInsideFrame(boundingBox)) {
                    Log.d("boundingBox", boundingBox.toString())
                    Log.d("frameDimension", frameDimension.toString())
                    type = "EID_NOT_DETECTED_IN_FRAME"
                    return@forEachIndexed
                }

//                val cardBox = CardBox(binding.cardBoxOverlay, card, imageProxy.cropRect)
//                binding.cardBoxOverlay.clear()
//                binding.cardBoxOverlay.add(cardBox)
//                binding.cardBoxOverlay.invalidate()

                type = "DETECTED"
            }
        }

        handleDetectedFrontEid(type, imageProxy, byteArray)
    }

    private fun handleDetectedFrontEid(type: String, imageProxy: ImageProxy, byteArray: ByteArray) {
        Log.d("TYPE", type)
        // Code to run on the UI thread
        requireActivity().runOnUiThread {
            when (type) {
                "NOT_DETECTED_FRONT_EID" -> {
                        binding.infoContainer.visibility = View.VISIBLE
                        binding.infoText.text = getString(R.string.front_eid_detection_label_1)
                        binding.cardOverlayView.setFrameBorderColor(Color.RED)
                }
                "EID_NOT_DETECTED_IN_FRAME" -> {
                        binding.infoContainer.visibility = View.VISIBLE
                        binding.infoText.text = getString(R.string.eid_detection_label_3)
                        binding.cardOverlayView.setFrameBorderColor(Color.RED)
                }
                "DETECTED_TO_FAR" -> {
                        binding.infoContainer.visibility = View.VISIBLE
                        binding.infoText.text = getString(R.string.eid_detection_label_1)
                        binding.cardOverlayView.setFrameBorderColor(Color.RED)
                }
                "EID_IS_BLURRY" -> {
                    binding.infoContainer.visibility = View.VISIBLE
                    binding.infoText.text = getString(R.string.eid_detection_label_6)
                    binding.cardOverlayView.setFrameBorderColor(Color.RED)
                }
                "LOW_LIGHT_DETECTED" -> {
                        binding.infoContainer.visibility = View.VISIBLE
                        binding.infoText.text = getString(R.string.eid_detection_label_5)
                        binding.cardOverlayView.setFrameBorderColor(Color.RED)
                }
                "DETECTED_WITH_GLARE" -> {
                        binding.infoContainer.visibility = View.VISIBLE
                        binding.infoText.text = getString(R.string.eid_detection_label_2)
                        binding.cardOverlayView.setFrameBorderColor(Color.RED)
                }
                "DETECTED" -> {
                    binding.infoContainer.visibility = View.VISIBLE
                    binding.infoText.text = getString(R.string.eid_detection_label_4)
                    binding.cardOverlayView.setFrameBorderColor(Color.GREEN)
                    frontEidDetectSuccessfully(byteArray)
                }
                "NOT_DETECTED" -> {
                    binding.infoContainer.visibility = View.GONE
                    binding.infoText.text = ""
                    binding.cardOverlayView.setFrameBorderColor(Color.WHITE)
                }
            }
        }
    }

    private fun frontEidDetectSuccessfully(byteArray: ByteArray) {
        // Check if the fragment is still attached to its context
        if (isAdded && !isDetached) {

            playShotSound()

            if (byteArray != null) {
                val bundle = Bundle().apply {
                    putByteArray("front_id", byteArray)
                }
                // Use the bundle as needed (e.g., pass to a fragment)
                 findNavController().navigate(R.id.action_frontEidDetection_to_backEidDetectionFragment, bundle)
            }

        } else {
            // Handle the case where the fragment is not attached (optional)
            Log.w(TAG, "Fragment is not attached to context. Cannot proceed with frontEidDetectSuccessfully.")
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraService.stopCamera()
        viewLifecycleOwner.lifecycleScope.coroutineContext.cancelChildren()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("RUN onDestroy", "SUCCESSFULLY")
    }

    companion object {
        private const val CAMERA_REQUEST_CODE = 100
        private val TAG = FrontEidDetectionFragment::class.simpleName
    }
}

