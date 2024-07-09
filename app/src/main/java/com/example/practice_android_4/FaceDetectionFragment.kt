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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.practice_android_4.camera.CameraService
import com.example.practice_android_4.databinding.FragmentFaceDetectionBinding
import com.example.practice_android_4.face_detection.FaceBox
import com.example.practice_android_4.face_detection.Helper
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetector
import java.io.ByteArrayOutputStream

import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import com.example.practice_android_4.network.RetrofitInstance

class FaceDetectionFragment : BaseFragment() {

    private var _binding: FragmentFaceDetectionBinding? = null
    private val binding get() = _binding!!
    private lateinit var cameraService: CameraService
    private lateinit var faceDetectionService: Helper
    private var lensFacing = CameraSelector.LENS_FACING_FRONT
    private lateinit var faceDetector: FaceDetector

    private val apiService by lazy { RetrofitInstance.apiService }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentFaceDetectionBinding.inflate(inflater, container, false)

        val rootView = super.onCreateView(inflater, container, savedInstanceState) as ViewGroup
        // Combine base layout and fragment layout
        rootView.findViewById<FrameLayout>(R.id.content_frame).addView(binding.root)

        setupFullscreen()
        setupOtherServices()

        faceDetector = faceDetectionService.setupFaceDetector()

        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
        faceDetectionService = Helper()
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
            findNavController().navigateUp()
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

    fun Bitmap.flip(): Bitmap {
        val m = Matrix()
        m.preScale(-1f, 1f)
        return Bitmap.createBitmap(this, 0, 0, width, height, m, false)
    }


    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(imageProxy: ImageProxy, bitmap: Bitmap) {
        val inputImage = InputImage.fromMediaImage(imageProxy.image!!, imageProxy.imageInfo.rotationDegrees)
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val faces = detectFaces(inputImage)
                handleDetectedFaces(faces, imageProxy)
            } catch (e: Exception) {
                Log.e(TAG, "Face detection failed: ${e.message}")
            } finally {
                imageProxy.close()
            }
        }
    }

    private suspend fun detectFaces(inputImage: InputImage): List<Face> = suspendCoroutine { continuation ->
        faceDetector.process(inputImage)
            .addOnSuccessListener { faces ->
                continuation.resume(faces)
            }
            .addOnFailureListener { e ->
                continuation.resumeWithException(e)
            }
    }

    private suspend fun handleDetectedFaces(faces: List<Face>, imageProxy: ImageProxy) {
//        withContext(Dispatchers.Main) {
//            binding.previewViewOverlay.clear()
//            faces.forEach { face ->
//                val faceBox = FaceBox(
//                    binding.previewViewOverlay,
//                    face,
//                    imageProxy.cropRect
//                )
//                binding.previewViewOverlay.add(faceBox)
//            }
//        }

//        Log.d("byteArray handleDetectedFaces", byteArray.toString())
        updateOverlayBasedOnFaces(faces, imageProxy)
    }

    private suspend fun updateOverlayBasedOnFaces(faces: List<Face>, imageProxy: ImageProxy) {
        var type = FACE_NOT_DETECTED

        // Perform bitmap operations off the main thread
        val bitmap = withContext(Dispatchers.Default) {
            imageProxy.toBitmap().rotate(270f)
        }
//        val bitmap = imageProxy.toBitmap().rotate(270f)

        val mutableBitmap = withContext(Dispatchers.Default) {
            bitmap.copy(Bitmap.Config.ARGB_8888, true)
        }

        if (faces.isEmpty()) {
            type = FACE_NOT_DETECTED
        } else {
            for (face in faces) {
                val boundingBox = withContext(Dispatchers.Default) {
                    faceDetectionService.getBoxRect(
                        binding.overlayView,
                        imageProxy.width.toFloat(),
                        imageProxy.height.toFloat(),
                        face.boundingBox
                    )
                }
                val faceBounds = Rect(face.boundingBox.left, face.boundingBox.top, face.boundingBox.right, face.boundingBox.bottom)
                val croppedBitmap = withContext(Dispatchers.Default) {
                    faceDetectionService.cropBitmap(
                        mutableBitmap,
                        faceBounds.left,
                        faceBounds.top,
                        faceBounds.width(),
                        faceBounds.height()
                    )
                }

                val faceSize = faceDetectionService.getFaceSize(face.boundingBox, imageProxy.width, imageProxy.height)
                Log.d("FACE_SIZE", faceSize.toString())

                when {
                    faces.size > 1 -> {
                        type = MULTIPLE_FACES_DETECTED
                        handleHoldOnLoading("HIDE")
                        break
                    }

                    !binding.overlayView.isBoundingBoxInsideOval(boundingBox) -> {
                        type = FACE_IS_OUTSIDE_OVAL
                        break
                    }

                    faceSize < faceDetectionService.faceToFarThreshold -> {
                        type = FACE_IS_TOO_FAR
                        break
                    }

                    faceSize > faceDetectionService.faceToCloseThreshold -> {
                        type = FACE_IS_TOO_CLOSE
                        break
                    }

                    !faceDetectionService.isFaceStraight(
                        face,
                        faceDetectionService.angleThreshold
                    ) -> {
                        type = FACE_IS_NOT_STRAIGHT
                        break
                    }

                    !faceDetectionService.areEyesOpen(
                        face,
                        faceDetectionService.eyeOpenProbabilityThreshold
                    ) -> {
                        type = BOTH_EYES_CLOSED
                        break
                    }

                    faceDetectionService.isSmiling(
                        face,
                        faceDetectionService.smileProbabilityThreshold
                    ) -> {
                        type = SMILEY_FACE
                        break
                    }

                    else -> {

                        // Show HOLD ON loading
                        type = HOLD_ON
                        handleHoldOnLoading("SHOW")
                        val blurry = withContext(Dispatchers.Default) {
                            faceDetectionService.isImageBlurry(
                                croppedBitmap,
                                faceDetectionService.imageBlurryThreshold
                            )
                        }
                        val isLowLight = withContext(Dispatchers.Default) {
                            faceDetectionService.calculateBitmapBrightness(
                                croppedBitmap,
                                faceDetectionService.imageBrightnessThreshold
                            )
                        }

                        type = when {
                            !isLowLight && !blurry -> ""
                            isLowLight -> LOW_LIGHT_DETECTED
                            blurry -> FACE_BLURRY
                            else -> type // default to previous type
                        }

                    }
                }
            }
        }

        if (type != HOLD_ON) {
            handleHoldOnLoading("HIDE")
        }
        withContext(Dispatchers.Main) {
            handleDetectedFace(type, imageProxy, bitmap)
        }
    }

    private fun viewBitmapOnScreen(bitmap:Bitmap) {
        binding.processedImageView.setImageBitmap(bitmap)
    }

    private fun handleHoldOnLoading(type: String) {
        if(type === "HIDE"){
            hideHoldOnLoading()
        } else {
            showHoldOnLoading()
        }
    }

    private fun handleDetectedFace(type: String, imageProxy: ImageProxy, bitmap: Bitmap) {
        Log.d("TYPE", type)
        when (type) {
            MULTIPLE_FACES_DETECTED -> {
                binding.infoText.visibility = View.VISIBLE
                binding.infoText.text = getString(R.string.facial_recognition_label_3)
                binding.overlayView.changeBorderColor(Color.RED)
            }

            FACE_NOT_DETECTED -> {
                binding.infoText.visibility = View.VISIBLE
                binding.infoText.text = getString(R.string.facial_recognition_label_2)
                binding.overlayView.changeBorderColor(Color.RED)
            }

            LOW_LIGHT_DETECTED -> {
                binding.infoText.visibility = View.VISIBLE
                binding.infoText.text = getString(R.string.facial_recognition_label_11)
                binding.overlayView.changeBorderColor(Color.RED)
            }

            FACE_IS_OUTSIDE_OVAL -> {
                binding.infoText.visibility = View.VISIBLE
                binding.infoText.text = getString(R.string.facial_recognition_label_4)
                binding.overlayView.changeBorderColor(Color.RED)
            }

            FACE_BLURRY -> {
                binding.infoText.visibility = View.VISIBLE
                binding.infoText.text = getString(R.string.facial_recognition_label_12)
                binding.overlayView.changeBorderColor(Color.RED)
            }

            FACE_IS_TOO_FAR -> {
                binding.infoText.visibility = View.VISIBLE
                binding.infoText.text = getString(R.string.facial_recognition_label_9)
                binding.overlayView.changeBorderColor(Color.RED)
            }

            FACE_IS_TOO_CLOSE -> {
                binding.infoText.visibility = View.VISIBLE
                binding.infoText.text = getString(R.string.facial_recognition_label_10)
                binding.overlayView.changeBorderColor(Color.RED)
            }

            FACE_IS_NOT_STRAIGHT -> {
                binding.infoText.visibility = View.VISIBLE
                binding.infoText.text = getString(R.string.facial_recognition_label_5)
                binding.overlayView.changeBorderColor(Color.RED)
            }

            BOTH_EYES_CLOSED -> {
                binding.infoText.visibility = View.VISIBLE
                binding.infoText.text = getString(R.string.facial_recognition_label_6)
                binding.overlayView.changeBorderColor(Color.RED)
            }

            SMILEY_FACE -> {
                binding.infoText.visibility = View.VISIBLE
                binding.infoText.text = getString(R.string.facial_recognition_label_7)
                binding.overlayView.changeBorderColor(Color.RED)
            }

            HOLD_ON -> {
                binding.infoText.visibility = View.VISIBLE
                binding.infoText.text = getString(R.string.facial_recognition_label_8)
                binding.overlayView.changeBorderColor(Color.GREEN)
            }

            else -> {
                binding.infoText.visibility = View.VISIBLE
                binding.infoText.text = getString(R.string.facial_recognition_label_8)
                binding.overlayView.changeBorderColor(Color.GREEN)
                faceDetectSuccessfully(bitmap)
            }
        }
    }

    private fun faceDetectSuccessfully(bitmap: Bitmap) {

        // Check if the fragment is still attached to its context
        if (isAdded && !isDetached) {

            playShotSound()
//            val actualBitmap = faceDetectionService.cropBitmapToScreen(bitmap)
            val frontId = arguments?.getByteArray("front_id")
            val backId = arguments?.getByteArray("back_id")

            // Safely navigate up
            val byteArray = faceDetectionService.convertBitmapToByteArrayUncompressed(bitmap)

            val bundle: Bundle = Bundle().apply {
                if(frontId !== null){
                    putByteArray("front_id", frontId)
                }
                if(backId !== null){
                    putByteArray("back_id", backId)
                }
                putByteArray("selfie", byteArray)
            }

            findNavController().navigate(R.id.action_FaceDetectionFragment_to_processFragment, bundle)

        } else {
            // Handle the case where the fragment is not attached (optional)
            Log.w(TAG, "Fragment is not attached to context. Cannot proceed with faceDetectSuccessfully.")
        }
    }


    override fun onResume() {
        super.onResume()
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        faceDetector.close()
        cameraService.stopCamera()
        viewLifecycleOwner.lifecycleScope.coroutineContext.cancelChildren()
        // _binding = null
    }

    companion object {
        private const val CAMERA_REQUEST_CODE = 100
        private val TAG = FaceDetectionFragment::class.simpleName

        private const val FACE_NOT_DETECTED = "FACE_NOT_DETECTED"
        private const val MULTIPLE_FACES_DETECTED = "MULTIPLE_FACES_DETECTED"
        private const val FACE_IS_OUTSIDE_OVAL = "FACE_IS_OUTSIDE_OVAL"
        private const val FACE_IS_TOO_FAR = "FACE_IS_TOO_FAR"
        private const val FACE_IS_TOO_CLOSE = "FACE_IS_TOO_CLOSE"
        private const val FACE_IS_NOT_STRAIGHT = "FACE_IS_NOT_STRAIGHT"
        private const val BOTH_EYES_CLOSED = "BOTH_EYES_CLOSED"
        private const val SMILEY_FACE = "SMILEY_FACE"
        private const val FACE_BLURRY = "FACE_BLURRY"
        private const val LOW_LIGHT_DETECTED = "LOW_LIGHT_DETECTED"
        private const val HOLD_ON = "HOLD_ON"

    }
}