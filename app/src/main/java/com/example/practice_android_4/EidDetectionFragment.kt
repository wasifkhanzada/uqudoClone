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
import android.hardware.camera2.CameraCharacteristics
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
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.practice_android_4.camera.CameraService
import com.example.practice_android_4.databinding.FragmentEidDetectionBinding
import com.example.practice_android_4.eid_detection.Helper
import com.example.practice_android_4.common.CommonHelper
import com.example.practice_android_4.ml.Eid
import com.example.practice_android_4.network.RetrofitInstance
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp

import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class EidDetectionFragment : BaseFragment() {

    private val appPackage by lazy { RetrofitInstance.appPackage }

    private var _binding: FragmentEidDetectionBinding? = null
    private val binding get() = _binding!!
    private lateinit var cameraService: CameraService
    private var lensFacing = CameraCharacteristics.LENS_FACING_BACK
    private lateinit var eidDetectionService: Helper
    private lateinit var commonHelper: CommonHelper

    private lateinit var imageProcessor: ImageProcessor
    private lateinit var labels:List<String>
    private lateinit var model: Eid

    private var processSide: String = "FRONT"

    private lateinit var frontId: ByteArray
    private lateinit var backId: ByteArray

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Adding the base layout to the fragment's root view
        _binding = FragmentEidDetectionBinding.inflate(inflater, container, false)

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

    private fun initializeCameraService() {
        cameraService = CameraService(
            context = requireContext(),
            lifecycleOwner = viewLifecycleOwner,
            previewView = binding.previewView,
            onImageCaptured = ::processImageProxy
        )
        cameraService.startCamera(lensFacing)
    }

    private fun setupOtherServices() {
        // Initialize Helper service
        eidDetectionService = Helper()
        commonHelper = CommonHelper(requireContext())

        labels = FileUtil.loadLabels(requireContext(), "labels.txt")
        imageProcessor = ImageProcessor.Builder().add(
            ResizeOp(
                eidDetectionService.IMAGE_PROCESSOR_TARGET_SIZE,
                eidDetectionService.IMAGE_PROCESSOR_TARGET_SIZE,
                ResizeOp.ResizeMethod.BILINEAR
            )
        ).build()
        model = Eid.newInstance(requireContext())
    }

    private fun playShotSound() {
        activity?.startService(Intent(requireContext(), SoundPoolService::class.java))
    }

    private fun goBack() {
        val toolbar: Toolbar = binding.goBack
        toolbar.setNavigationOnClickListener {
            findNavController().navigate(R.id.action_eidDetection_to_HomeFragment)
        }
    }

    private fun Bitmap.rotate(degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private fun changeSide(type: String?) {
        if(type === "SHOW"){

            binding.cardOverlayView.visibility = View.VISIBLE
            binding.topContainer.visibility = View.VISIBLE
            binding.bottomContainer.visibility = View.VISIBLE

            binding.topContainerText.text = if (processSide == "FRONT")
                getString(R.string.front_eid_detection_label_1)
            else
                getString(R.string.back_eid_detection_label_1)

            hideScanSuccessfully()

        } else {

            binding.cardOverlayView.visibility = View.GONE
            binding.topContainer.visibility = View.GONE
            binding.bottomContainer.visibility = View.GONE

            showScanSuccessfully()
        }

    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(imageProxy: ImageProxy, bitmap: Bitmap) {
        Log.d(TAG, "processImageProxy started")
        val image = imageProxy.image
        if (image != null) {
            try {
                detectionEid(bitmap)
            } catch (e: Exception) {
                Log.e(TAG, "Error in processImageProxy: ${e.message}")
                e.printStackTrace()
            }
        }
        imageProxy.close()
        Log.d(TAG, "processImageProxy finished")
    }

    private var alertShown = false
    private fun showBlockingAlert(message: String) {
        if (alertShown) return
        Log.d("run alertShown", "run alert")
        val dialog = AlertDialog.Builder(requireContext())
            .setMessage(message)
            .setCancelable(false)
//            .setOnDismissListener {
//                alertShown = false // Set the variable to false when the dialog is dismissed
//                Log.e("CLOSE ALERT", "$alertShown")
////                onDismiss()
//            }
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

    private fun detectionEid(bitmap: Bitmap) {

        if(isProcessing) return

        // Rotate and downscale the bitmap
//        val downscaledBitmap = bitmap.rotate(90f).run {
//            eidDetectionService.downscaleBitmapWithWidth(this, 640).also { recycle() }
//        }

        //        Rotate the bitmap for proper orientation
        val originalBitmap = bitmap.rotate(90f)

//        Downscale the bitmap to a fixed width
        val downscaledBitmap = eidDetectionService.downscaleBitmapWithWidth(originalBitmap, 640)

        // Define the cropping rectangle
        val bitmapCropRect = Rect(0, 0, downscaledBitmap.height, downscaledBitmap.width)

        // Crop the bitmap to a square
        val cropBitmap = eidDetectionService.cropToSquare(downscaledBitmap)
        val squareBitmap = cropBitmap.bitmap ?: run {
            Log.e(TAG, "Cropped bitmap is null")
            return
        }

        // Prepare TensorImage for the model
        val image = TensorImage.fromBitmap(squareBitmap).let { imageProcessor.process(it) }

        // Run the model on the image
        val outputs = model.process(image)
        // Extract the outputs from the model
        val locations = outputs.locationAsTensorBuffer.floatArray
        val classes = outputs.categoryAsTensorBuffer.floatArray
        val scores = outputs.scoreAsTensorBuffer.floatArray

        val (h, w) = squareBitmap.height to squareBitmap.width

        // Get the frame dimension for the overlay
        val frameDimension = binding.cardOverlayView.getFrameDimension()

        // Initialize variables
        var eidCroppedBitmap: Bitmap? = null
        var card: Rect? = null
        var label = ""
        val latestScore = scores.last()

        // Iterate through scores to find the best match
        for (index in scores.indices) {
            if (scores[index] > eidDetectionService.EID_SCORE) {
                val x = index * 4
                val top = locations[x] * h
                val left = locations[x + 1] * w
                val bottom = locations[x + 2] * h
                val right = locations[x + 3] * w
                label = labels[classes[index].toInt()]

                val adjustedTop = if (cropBitmap.cropSide == "height") top + cropBitmap.dimension!! else top
                val adjustedBottom = if (cropBitmap.cropSide == "height") bottom + cropBitmap.dimension!! else bottom
                val adjustedLeft = if (cropBitmap.cropSide == "width") left + cropBitmap.dimension!! else left
                val adjustedRight = if (cropBitmap.cropSide == "width") right + cropBitmap.dimension!! else right

                // Crop the detected EID
                val rectF = RectF(left, top, right, bottom)
                eidCroppedBitmap = eidDetectionService.cropBitmap(squareBitmap, rectF.left.toInt(), rectF.top.toInt(), rectF.width().toInt(), rectF.height().toInt())
                card = Rect(adjustedLeft.toInt(), adjustedTop.toInt(), adjustedRight.toInt(), adjustedBottom.toInt())

//                val cardBox = CardBox(binding.cardBoxOverlay, card, bitmapCropRect)
//                binding.cardBoxOverlay.clear()
//                binding.cardBoxOverlay.add(cardBox)
//                binding.cardBoxOverlay.invalidate()
            }
        }

        if((latestScore > eidDetectionService.EID_SCORE) && eidCroppedBitmap != null && card != null){
            if(alertShown) return
            // GET COORDINATES OF EID IN FRAME
            val boundingBox = eidDetectionService.getBoxRect(binding.cardOverlayView, bitmapCropRect.width().toFloat(), bitmapCropRect.height().toFloat(), card)

            val notInSideFrame = !binding.cardOverlayView.isBoundingBoxInsideFrame(boundingBox)
            val tooFar = eidDetectionService.isRectFMatchWithinTolerance(boundingBox, frameDimension)
            val notFront = !eidDetectionService.toFrontDetect.contains(label)
            val notBack = !eidDetectionService.toBackDetect.contains(label)

            if(notInSideFrame){
                Log.w("EID DETECTION CONDITION", "EID_NOT_DETECTED_IN_FRAME")
//                updateOverlay(EID_NOT_DETECTED_IN_FRAME)
                resetProcessingState()
                return
            }

            if(tooFar) {
                Log.w("EID DETECTION CONDITION", "DETECTED_TO_FAR")
//                updateOverlay(DETECTED_TO_FAR)
                requireActivity().runOnUiThread {
                    showBlockingAlert(getString(R.string.eid_detection_label_1))
                }
                resetProcessingState()
                return
            }

            if(processSide === "FRONT" && notFront) {
                requireActivity().runOnUiThread {
                    showBlockingAlert(getString(R.string.front_eid_detection_label_1))
                }
                resetProcessingState()
                return
            }

            if(processSide === "BACK" && notBack) {
                requireActivity().runOnUiThread {
                    showBlockingAlert(getString(R.string.back_eid_detection_label_1))
                }
                resetProcessingState()
                return
            }

            Log.d("!(notFront || notBack)", "${!(notFront || notBack)}")

            if(!notInSideFrame && !tooFar && (processSide === "FRONT" && !notFront) || (processSide === "BACK" && !notBack)) {
                Log.d("EID DETECTION CONDITION", "HOLD_ON")
                startProcessingState()
                updateOverlay(HOLD_ON)
                processOnDetectedEid(eidCroppedBitmap, originalBitmap)
            }
        } else {
            Log.e("EID DETECTION CONDITION NOT_DETECTED", "Detection failed")
            updateOverlay(NOT_DETECTED)
            resetProcessingState()
        }

    }
    private fun processOnDetectedEid(eidCroppedBitmap: Bitmap, originalBitmap: Bitmap) {
        Log.d("processOnDetectedEid", "how many times run this functions")
        if (eidDetectionService.isLowLight(eidCroppedBitmap)) {
            Log.e("EID DETECTION CONDITION", "Image is in low light")
            updateOverlay(NOT_DETECTED)
            requireActivity().runOnUiThread {
                showBlockingAlert(getString(R.string.eid_detection_label_5))
            }
            resetProcessingState()
            return
        } else if (eidDetectionService.isImageBlurry(eidCroppedBitmap)) {
            Log.e("EID DETECTION CONDITION", "Image is blurry")
            updateOverlay(NOT_DETECTED)
            requireActivity().runOnUiThread {
                showBlockingAlert(getString(R.string.eid_detection_label_6))
            }
            resetProcessingState()
            return
        } else if (eidDetectionService.isGlareDetect(eidCroppedBitmap)) {
            Log.e("EID DETECTION CONDITION", "Glare detected in image")
            updateOverlay(NOT_DETECTED)
            requireActivity().runOnUiThread {
                showBlockingAlert(getString(R.string.eid_detection_label_2))
            }
            resetProcessingState()
            return
        } else {
            // If all checks pass
            requireActivity().runOnUiThread {
                hideHoldOnLoading()
            }
            updateOverlay(DETECTED, originalBitmap, eidCroppedBitmap)
            Log.e("EID DETECTION CONDITION", "ALL CHECK PASS")
        }
    }

    private fun resetProcessingState() {
        requireActivity().runOnUiThread {
            hideHoldOnLoading()
        }
        isProcessing = false
    }
    private fun startProcessingState() {
        requireActivity().runOnUiThread {
            showHoldOnLoading()
        }
        isProcessing = true
    }

    private fun updateOverlay(type: String, bitmap: Bitmap? = null, eidCroppedBitmap:Bitmap? = null) {
        Log.d("TYPE", type)
        requireActivity().runOnUiThread {
            when (type) {
                EID_NOT_DETECTED_IN_FRAME -> {
                    binding.bottomContainer.visibility = View.VISIBLE
                    binding.infoText.text = getString(R.string.eid_detection_label_3)
                    binding.cardOverlayView.setFrameBorderColor(Color.RED)
                }
                DETECTED_TO_FAR -> {
                    binding.bottomContainer.visibility = View.VISIBLE
                    binding.infoText.text = getString(R.string.eid_detection_label_1)
                    binding.cardOverlayView.setFrameBorderColor(Color.RED)
                }
                HOLD_ON -> {
                    binding.bottomContainer.visibility = View.VISIBLE
                    binding.infoText.text = getString(R.string.eid_detection_label_4)
                    binding.cardOverlayView.setFrameBorderColor(Color.GREEN)
                }
                DETECTED -> {
                    binding.bottomContainer.visibility = View.VISIBLE
                    binding.infoText.text = getString(R.string.eid_detection_label_4)
                    binding.cardOverlayView.setFrameBorderColor(Color.GREEN)
                    if (bitmap != null && eidCroppedBitmap != null) {
//                        binding.processedFrontEidImageView.setImageBitmap(eidCroppedBitmap)
                        if(processSide === "FRONT"){
                            frontEidDetectSuccessfully(bitmap)
                        } else {
                            backEidDetectSuccessfully(bitmap)
                        }
                    }
                }
                NOT_DETECTED -> {
                    binding.bottomContainer.visibility = View.VISIBLE
                    binding.infoText.text = getString(R.string.eid_detection_label_info)
                    binding.cardOverlayView.setFrameBorderColor(Color.WHITE)
                }
            }
        }
    }

    private fun frontEidDetectSuccessfully(bitmap: Bitmap) {
        playShotSound()

        val actualBitmap = eidDetectionService.cropBitmapToScreen(bitmap)
        val byteArray = eidDetectionService.convertBitmapToByteArrayUncompressed(actualBitmap)

        frontId = byteArray

        changeSide("HIDE")

        processSide = "BACK"

        // Auto-close the dialog after 5 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            changeSide("SHOW")
            resetProcessingState()
        }, 3000)

    }

    private fun backEidDetectSuccessfully(bitmap: Bitmap) {
            playShotSound()
            val actualBitmap = eidDetectionService.cropBitmapToScreen(bitmap)
            val backId = eidDetectionService.convertBitmapToByteArrayUncompressed(actualBitmap)
            showLoading()
            cameraService.stopCamera()
            processIdCard(frontId, backId)
    }

    private fun processIdCard(front: ByteArray, back: ByteArray) {

        val token = arguments?.getString("token") ?: ""

        // Prepare file parts
        val frontIdPart = front.let {
            val frontIdRequestBody = it.toRequestBody("image/jpeg".toMediaTypeOrNull())
            Log.d("FRONT_ID_REQUEST_BODY", frontIdRequestBody.toString())
            MultipartBody.Part.createFormData("front_id", "front_id.jpeg", frontIdRequestBody)
        }

        val backIdPart = back.let {
            val backIdRequestBody = it.toRequestBody("image/jpeg".toMediaTypeOrNull())
            Log.d("BACK_ID_REQUEST_BODY", backIdRequestBody.toString())
            MultipartBody.Part.createFormData("back_id", "back_id.jpeg", backIdRequestBody)
        }

        val call = appPackage.processIdCard(
            authorization = "Bearer $token",
            front_id = frontIdPart,
            back_id = backIdPart
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
        Log.e("$TAG ERROR", "$message ${response?.code() ?: ""}")
        response?.let { Log.e("$TAG ERROR_RESPONSE", "$response") }
        jsonObject?.let { Log.e("$TAG RESPONSE_ERROR", it.toString()) }
        exception?.let { Log.e("$TAG JSON_PARSE_ERROR", it.message, it) }
        throwable?.let { Log.e("$TAG NETWORK_ERROR", it.message, it) }
        onFailure(message)
    }

    private fun onSuccess(token : String) {
        val scope: Bundle? = arguments

        if(scope?.getString("scope") === commonHelper.DOCUMENT_OR_FACE_TYPE){
            // Create another Bundle with data
            val currentBundle = Bundle().apply {
                putString("token", token)
            }
            findNavController().navigate(R.id.action_eidDetection_to_FaceDetectionFragment, currentBundle)
        } else {

        }

        // Merge bundles
//        val mergedBundle = Bundle().apply {
//            putAll(previousBundle)
//            putAll(currentBundle)
//        }


    }
    private fun onFailure(message: String) {
        commonHelper.showMessage(message)
        findNavController().navigate(R.id.action_eidDetection_to_HomeFragment)
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
        model.close() // If the model needs to be closed or released
        Log.d("RUN onDestroy", "SUCCESSFULLY")
    }

    companion object {
        private const val CAMERA_REQUEST_CODE = 100
        private val TAG = EidDetectionFragment::class.simpleName
        private const val EID_NOT_DETECTED_IN_FRAME = "EID_NOT_DETECTED_IN_FRAME"
        private const val DETECTED_TO_FAR = "DETECTED_TO_FAR"
        private const val HOLD_ON = "HOLD_ON"
        private const val DETECTED = "DETECTED"
        private const val NOT_DETECTED = "NOT_DETECTED"
    }
}