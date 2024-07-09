package com.example.practice_android_4

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.practice_android_4.camera.CameraService
import com.example.practice_android_4.databinding.FragmentBackEidDetectionBinding
import com.example.practice_android_4.eid_detection.CardBox
import com.example.practice_android_4.eid_detection.Helper
import com.example.practice_android_4.ml.Eid
import kotlinx.coroutines.cancelChildren
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp


class BackEidDetectionFragment : BaseFragment() {

    private var _binding: FragmentBackEidDetectionBinding? = null
    private val binding get() = _binding!!
    private lateinit var cameraService: CameraService
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private lateinit var eidDetectionService: Helper
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var labels:List<String>
    private lateinit var model: Eid


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentBackEidDetectionBinding.inflate(inflater, container, false)

        val rootView = super.onCreateView(inflater, container, savedInstanceState) as ViewGroup
        // Combine base layout and fragment layout
        rootView.findViewById<FrameLayout>(R.id.content_frame).addView(binding.root)

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
    }

    private fun setupFullscreen() {}

    private fun setupOtherServices() {
        // Initialize Helper service
        eidDetectionService = Helper()
        labels = FileUtil.loadLabels(requireContext(), "labels.txt")
        imageProcessor = ImageProcessor.Builder().add(ResizeOp(eidDetectionService.imageProcessorTargetSize, eidDetectionService.imageProcessorTargetSize, ResizeOp.ResizeMethod.BILINEAR)).build()
        model = Eid.newInstance(requireContext())
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
            findNavController().navigate(R.id.action_backEidDetectionFragment_to_frontEidDetection)
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(imageProxy: ImageProxy, bitmap: Bitmap) {
        Log.d(TAG, "processImageProxy started")
        val image = imageProxy.image
        if (image != null) {
            try {
                updateOverlayBasedOnFrontEid(imageProxy, bitmap)
            } catch (e: Exception) {
                Log.e(TAG, "Error in processImageProxy: ${e.message}")
                e.printStackTrace()
            }
        }
        imageProxy.close()
        Log.d(TAG, "processImageProxy finished")
    }

    private fun Bitmap.rotate(degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private fun updateOverlayBasedOnFrontEid(imageProxy: ImageProxy, bitmap: Bitmap) {

        val originalBitmap = bitmap.rotate(90f)

        val downscaledBitmap = eidDetectionService.downscaleBitmapWithWidth(originalBitmap, 640)

        val bitmapCropRect = Rect(0, 0, downscaledBitmap.height, downscaledBitmap.width)

        val cropBitmap = eidDetectionService.cropToSquare(downscaledBitmap)

        val bitmap = cropBitmap.bitmap ?: run {
            Log.e(TAG, "Cropped bitmap is null")
            return
        }

        var image = TensorImage.fromBitmap(bitmap)
        image = imageProcessor.process(image)

        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val outputs = model.process(image)

        val locations = outputs.locationAsTensorBuffer.floatArray
        val classes = outputs.categoryAsTensorBuffer.floatArray
        val scores = outputs.scoreAsTensorBuffer.floatArray

        val h = bitmap.height
        val w = bitmap.width

        var x: Int
        var left: Float
        var right: Float
        var bottom: Float
        var top: Float
        var label: String
        var type = NOT_DETECTED

        // GET EID FRAME DIMENSION
        val frameDimension = binding.cardOverlayView.getFrameDimension()

        scores.forEachIndexed { index, score ->
            Log.d("BACK_EID SCORE", score.toString())
            if (score > eidDetectionService.eidScore) {

                label = labels[classes[index].toInt()]

                Log.d("BACK_EID LABEL", label.toString())

                x = index * 4
                top = locations[x] * h
                left = locations[x + 1] * w
                bottom = locations[x + 2] * h
                right = locations[x + 3] * w

                if (cropBitmap.cropSide == "height") {
                    top += cropBitmap.dimension!!
                    bottom += cropBitmap.dimension
                }
                if (cropBitmap.cropSide == "width") {
                    left += cropBitmap.dimension!!
                    right += cropBitmap.dimension
                }

                //CROP DETECTED EID
                val rectF = RectF(locations[x + 1] * w, locations[x] * h, locations[x + 3] * w, locations[x + 2] * h)
                val croppedBitmap: Bitmap = eidDetectionService.cropBitmap(mutableBitmap, rectF.left.toInt(), rectF.top.toInt(), rectF.width().toInt(), rectF.height().toInt())
                val card = Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
                val boundingBox = eidDetectionService.getBoxRect(binding.cardBoxOverlay, bitmapCropRect.width().toFloat(), bitmapCropRect.height().toFloat(), card)

//                viewBitmapOnScreen(cropBitmap.bitmap)

                //GET COORDINATES OF EID IN FRAME

                //DRAW BOX ON EID START HERE
//                val cardBox = CardBox(binding.cardBoxOverlay, card, bitmapCropRect)
//                binding.cardBoxOverlay.clear()
//                binding.cardBoxOverlay.add(cardBox)
//                binding.cardBoxOverlay.invalidate()
                //DRAW BOX ON EID END HERE

                if (!binding.cardOverlayView.isBoundingBoxInsideFrame(boundingBox)) {
                    type = EID_NOT_DETECTED_IN_FRAME
                    return@forEachIndexed
                }

                if (!eidDetectionService.toBackDetect.contains(label)) {
                    type = NOT_DETECTED_BACK_EID
                    handleHoldOnLoading("HIDE")
                    return@forEachIndexed
                }

                if (eidDetectionService.isRectFMatchWithinTolerance(boundingBox, frameDimension, eidDetectionService.insideTolerance)) {
                    type = DETECTED_TO_FAR
                    handleHoldOnLoading("HIDE")
                    return@forEachIndexed
                }

                // Show HOLD ON loading
                type = HOLD_ON
                handleHoldOnLoading("SHOW")
//
                // Check for blurry, low light, and glare after setting to "HOLD_ON"
                val isLowLight = eidDetectionService.calculateBitmapBrightness(croppedBitmap, eidDetectionService.imageBrightnessThreshold)
                val blurry = eidDetectionService.isImageBlurry(croppedBitmap, eidDetectionService.imageBlurryThreshold)
                val glare = eidDetectionService.detectGlare(croppedBitmap)
                type = when {
                    !isLowLight && !blurry && !glare -> DETECTED
                    isLowLight -> LOW_LIGHT_DETECTED
                    blurry -> EID_IS_BLURRY
                    glare -> DETECTED_WITH_GLARE
                    else -> type // default to previous type
                }
            }
        }

        // Hide HOLD ON loading if the type is no longer HOLD ON
        if (type != HOLD_ON) {
            handleHoldOnLoading("HIDE")
        }
        handleDetectedBackEid(type, originalBitmap)

    }

    private fun viewBitmapOnScreen(bitmap:Bitmap) {
        requireActivity().runOnUiThread {
            binding.processedBackEidImageView.setImageBitmap(bitmap)
        }
    }

    private fun handleHoldOnLoading(type: String) {
        requireActivity().runOnUiThread {
            if(type === "HIDE"){
                hideHoldOnLoading()
            } else {
                showHoldOnLoading()
            }
        }
    }

    private fun handleDetectedBackEid(type: String, bitmap: Bitmap) {
        requireActivity().runOnUiThread {
            when (type) {
                NOT_DETECTED_BACK_EID -> {
                    binding.infoContainer.visibility = View.VISIBLE
                    binding.infoText.text = getString(R.string.back_eid_detection_label_1)
                    binding.cardOverlayView.setFrameBorderColor(Color.RED)
                }
                EID_NOT_DETECTED_IN_FRAME -> {
                    binding.infoContainer.visibility = View.VISIBLE
                    binding.infoText.text = getString(R.string.eid_detection_label_3)
                    binding.cardOverlayView.setFrameBorderColor(Color.RED)
                }
                DETECTED_TO_FAR -> {
                    binding.infoContainer.visibility = View.VISIBLE
                    binding.infoText.text = getString(R.string.eid_detection_label_1)
                    binding.cardOverlayView.setFrameBorderColor(Color.RED)
                }
                LOW_LIGHT_DETECTED -> {
                    binding.infoContainer.visibility = View.VISIBLE
                    binding.infoText.text = getString(R.string.eid_detection_label_5)
                    binding.cardOverlayView.setFrameBorderColor(Color.RED)
                }
                DETECTED_WITH_GLARE -> {
                    binding.infoContainer.visibility = View.VISIBLE
                    binding.infoText.text = getString(R.string.eid_detection_label_2)
                    binding.cardOverlayView.setFrameBorderColor(Color.RED)
                }
                HOLD_ON -> {
                    binding.infoContainer.visibility = View.VISIBLE
                    binding.infoText.text = getString(R.string.eid_detection_label_4)
                    binding.cardOverlayView.setFrameBorderColor(Color.GREEN)
                }
                DETECTED -> {
                    binding.infoContainer.visibility = View.VISIBLE
                    binding.infoText.text = getString(R.string.eid_detection_label_4)
                    binding.cardOverlayView.setFrameBorderColor(Color.GREEN)
                    backEidDetectSuccessfully(bitmap)
                }
                NOT_DETECTED -> {
                    binding.infoContainer.visibility = View.GONE
                    binding.infoText.text = ""
                    binding.cardOverlayView.setFrameBorderColor(Color.WHITE)
                }
            }
        }
    }

    private fun backEidDetectSuccessfully(bitmap: Bitmap) {
        // Check if the fragment is still attached to its context
        if (isAdded && !isDetached) {
            playShotSound()

            val frontId = arguments?.getByteArray("front_id")

            val actualBitmap = eidDetectionService.cropBitmapToScreen(bitmap)
            val byteArray = eidDetectionService.convertBitmapToByteArrayUncompressed(actualBitmap)

            val bundle: Bundle = Bundle().apply {

                if(frontId !== null){
                    putByteArray("front_id", frontId)
                }

                putByteArray("back_id", byteArray)

            }

            // Safely navigate up
            findNavController().navigate(R.id.action_backEidDetectionFragment_to_FaceDetectionFragment, bundle)
            // findNavController().navigate(R.id.action_backEidDetectionFragment_to_FaceDetectionFragment)

        } else {
            // Handle the case where the fragment is not attached (optional)
            Log.w(TAG, "Fragment is not attached to context. Cannot proceed with backEidDetectSuccessfully.")
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
        // _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        model.close() // If the model needs to be closed or released
        Log.d("RUN onDestroy", "SUCCESSFULLY")
    }

    companion object {
        private const val CAMERA_REQUEST_CODE = 100
        private val TAG = BackEidDetectionFragment::class.simpleName

        private const val NOT_DETECTED_BACK_EID = "NOT_DETECTED_BACK_EID"
        private const val EID_NOT_DETECTED_IN_FRAME = "EID_NOT_DETECTED_IN_FRAME"
        private const val DETECTED_TO_FAR = "DETECTED_TO_FAR"
        private const val EID_IS_BLURRY = "EID_IS_BLURRY"
        private const val LOW_LIGHT_DETECTED = "LOW_LIGHT_DETECTED"
        private const val DETECTED_WITH_GLARE = "DETECTED_WITH_GLARE"
        private const val HOLD_ON = "HOLD_ON"
        private const val DETECTED = "DETECTED"
        private const val NOT_DETECTED = "NOT_DETECTED"
    }
}