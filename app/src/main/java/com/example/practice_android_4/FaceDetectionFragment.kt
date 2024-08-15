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
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.practice_android_4.camera.CameraService
import com.example.practice_android_4.common.CommonHelper
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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class FaceDetectionFragment : BaseFragment() {

    private var _binding: FragmentFaceDetectionBinding? = null
    private val binding get() = _binding!!
    private lateinit var cameraService: CameraService
    private lateinit var faceDetectionService: Helper
    private lateinit var commonHelper: CommonHelper
    private var lensFacing = CameraSelector.LENS_FACING_FRONT
    private lateinit var faceDetector: FaceDetector

    private val appPackage by lazy { RetrofitInstance.appPackage }

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
        commonHelper = CommonHelper(requireContext())

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

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(imageProxy: ImageProxy, bitmap: Bitmap) {
     val inputImage = InputImage.fromMediaImage(imageProxy.image!!, imageProxy.imageInfo.rotationDegrees)
        viewLifecycleOwner.lifecycleScope.launch {
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
            .addOnSuccessListener { faces -> continuation.resume(faces) }
            .addOnFailureListener { e -> continuation.resumeWithException(e) }
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
        updateOverlayBasedOnFaces(faces, imageProxy)
    }

    private var alertShown = false
    private fun showBlockingAlert(message: String) {
        if (alertShown) return
        val dialog = AlertDialog.Builder(requireContext())
            .setMessage(message)
            .setCancelable(false)
            .show()
        // Auto-close the dialog after 5 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            if (dialog.isShowing) {
                dialog.dismiss()
                alertShown = false
            }
        }, 5000)
        alertShown = true
    }


    private var isProcessing = false



    private suspend fun updateOverlayBasedOnFaces(faces: List<Face>, imageProxy: ImageProxy) {

        if(isProcessing) return

        if(alertShown) return
        // Perform bitmap operations off the main thread
        val bitmap = withContext(Dispatchers.Default) {
            imageProxy.toBitmap().rotate(270f)
        }

        val mutableBitmap = withContext(Dispatchers.Default) {
            bitmap.copy(Bitmap.Config.ARGB_8888, true)
        }

        if(faces.isNotEmpty()) {

            var face = faces.last()

            val boundingBox = withContext(Dispatchers.Default) {
                faceDetectionService.getBoxRect(
                    binding.overlayView,
                    imageProxy.width.toFloat(),
                    imageProxy.height.toFloat(),
                    face.boundingBox
                )
            }

            val faceBounds = Rect(face.boundingBox.left, face.boundingBox.top, face.boundingBox.right, face.boundingBox.bottom)
            val faceCroppedBitmap = withContext(Dispatchers.Default) {
            faceDetectionService.cropBitmap(
                mutableBitmap,
                faceBounds.left,
                faceBounds.top,
                faceBounds.width(),
                faceBounds.height()
            )
        }
            val faceSize = faceDetectionService.getFaceSize(face.boundingBox, imageProxy.width, imageProxy.height)

            when {
                faces.size > 1 -> {
                    return withContext(Dispatchers.Main) {
                        handleDetectedFace(MULTIPLE_FACES_DETECTED)
                    }
                }

                !binding.overlayView.isBoundingBoxInsideOval(boundingBox) -> {
                    return withContext(Dispatchers.Main) {
                        handleDetectedFace(FACE_IS_OUTSIDE_OVAL)
                    }
                }

                faceDetectionService.isFaceTooFar(faceSize) -> {
                    return withContext(Dispatchers.Main) {
                        handleDetectedFace(FACE_IS_TOO_FAR)
                    }
                }

                faceDetectionService.isFaceTooClose(faceSize)-> {
                    return withContext(Dispatchers.Main) {
                        handleDetectedFace(FACE_IS_TOO_CLOSE)
                    }
                }

                !faceDetectionService.isFaceStraight(face) -> {
                    return withContext(Dispatchers.Main) {
                        handleDetectedFace(FACE_IS_NOT_STRAIGHT)
                    }
                }

                !faceDetectionService.areEyesOpen(face) -> {
                    return withContext(Dispatchers.Main) {
                        handleDetectedFace(BOTH_EYES_CLOSED)
                    }
                }

                faceDetectionService.isSmiling(face) -> {
                    return withContext(Dispatchers.Main) {
                        handleDetectedFace(SMILEY_FACE)
                    }
                }

                faceDetectionService.isLowLight(faceCroppedBitmap) -> {
                    return withContext(Dispatchers.Main) {
                        handleDetectedFace(LOW_LIGHT_DETECTED)
                    }
                }

                faceDetectionService.isImageBlurry(faceCroppedBitmap) -> {
                    return withContext(Dispatchers.Main) {
                        handleDetectedFace(FACE_BLURRY)
                    }
                }
                faceDetectionService.isImageMotionBlurry(bitmap) -> {
                    return withContext(Dispatchers.Main) {
                        handleDetectedFace(FACE_BLURRY)
                    }
                }

                else -> {
                    return withContext(Dispatchers.Main) {
                        handleDetectedFace("", bitmap)
                    }
                }
            }

        } else {
            return withContext(Dispatchers.Main) {
                handleDetectedFace(FACE_NOT_DETECTED)
            }
        }

    }

    private fun handleDetectedFace(type: String, bitmap: Bitmap? = null) {
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
                if (bitmap != null) {
                    faceDetectSuccessfully(bitmap)
                }
            }
        }
    }

    private fun faceDetectSuccessfully(bitmap: Bitmap) {
        playShotSound()
        val byteArray = faceDetectionService.convertBitmapToByteArrayUncompressed(bitmap)
        showLoading()
        cameraService.stopCamera()
        compareFaces(byteArray)
    }


    private fun compareFaces(selfieByteArray: ByteArray) {

        val token = arguments?.getString("token") ?: ""

        // Prepare file parts
        val selfiePart = selfieByteArray.let {
            val selfieRequestBody = it.toRequestBody("image/jpeg".toMediaTypeOrNull())
            Log.d("SELFIE_REQUEST_BODY", selfieRequestBody.toString())
            MultipartBody.Part.createFormData("selfie", "selfie.jpeg", selfieRequestBody)
        }

        val call = appPackage.compareFaces(
            authorization = "Bearer $token",
            selfie = selfiePart
        )

        call.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    handleSuccessfulResponse(response)
                } else {
                    logAndReportError("Unsuccessful response", response)
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                logAndReportError("Network request failed", throwable = t)
            }
        })
    }
    private fun handleSuccessfulResponse(response: Response<ResponseBody>) {
        response.body()?.string()?.let { jsonData ->
            try {
                parseAndHandleResponse(jsonData)
            } catch (e: JSONException) {
                logAndReportError("Error parsing response JSON", exception = e)
            }
        } ?: run {
            logAndReportError("Response body is null")
        }
    }
    private fun parseAndHandleResponse(jsonData: String) {
        val resObject = JSONObject(jsonData).apply {
            Log.d("resObject", toString())
        }
        if (resObject.getBoolean("error")) {
            val errMessage = resObject.getString("message")
            logAndReportError("Response error: $errMessage", jsonObject = resObject)
        } else {
            val token = resObject.getJSONObject("data").getString("token")
            Log.d("processIdCard RESPONSE_SUCCESS", "$resObject $token")
            // navigate(bundle) // Uncomment and implement if needed
            onSuccess(token)
        }
    }
    private fun logAndReportError(
        message: String,
        response: Response<ResponseBody>? = null,
        jsonObject: JSONObject? = null,
        exception: JSONException? = null,
        throwable: Throwable? = null
    ) {
        Log.e("${TAG} ERROR", "$message ${response?.code() ?: ""}")
        response?.let { Log.e("${TAG} ERROR_RESPONSE", "$response") }
        jsonObject?.let { Log.e("${TAG} RESPONSE_ERROR", it.toString()) }
        exception?.let { Log.e("${TAG} JSON_PARSE_ERROR", it.message, it) }
        throwable?.let { Log.e("${TAG} NETWORK_ERROR", it.message, it) }
        onFailure(message)
    }

    private fun onSuccess(token : String) {
        val scope: Bundle? = arguments

//        if(scope?.getString("scope") === commonHelper.DOCUMENT_OR_FACE_TYPE){
            // Create another Bundle with data
            val currentBundle = Bundle().apply {
                putString("token", token)
            }
            findNavController().navigate(R.id.action_FaceDetectionFragment_to_HomeFragment, currentBundle)
//        } else {

//        }

        // Merge bundles
//        val mergedBundle = Bundle().apply {
//            putAll(previousBundle)
//            putAll(currentBundle)
//        }


    }
    private fun onFailure(message: String) {
        commonHelper.showMessage(message)
        findNavController().navigate(R.id.action_FaceDetectionFragment_to_HomeFragment)
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