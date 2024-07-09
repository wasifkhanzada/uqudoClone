package com.example.practice_android_4.camera

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.util.DisplayMetrics
import android.util.Log
import android.view.MotionEvent
import android.view.WindowManager
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.view.PreviewView
import androidx.camera.core.*
import androidx.camera.core.impl.CameraCaptureCallback
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.reflect.KFunction1

class CameraService(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val onImageCaptured: (ImageProxy, Bitmap) -> Unit,
) {

    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var cameraSelector: CameraSelector
    private lateinit var cameraExecutor: ExecutorService

    fun startCamera(lensFacing: Int) {
        cameraExecutor = Executors.newSingleThreadExecutor()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            setupCamera(lensFacing)
        }, ContextCompat.getMainExecutor(context))
    }

    fun stopCamera() {
        cameraProvider.unbindAll()
        cameraExecutor.shutdown()
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

    private fun setupCamera(lensFacing: Int) {
        cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

//        val (screenWidth, screenHeight) = getScreenDimensions()
        val screenWidth = 768
        val screenHeight = 1024

        Log.d("previewView", "${previewView.display.width} ${previewView.display.height}")

        val preview = Preview.Builder()
            .setTargetResolution(android.util.Size(screenWidth, screenHeight))
            .setTargetRotation(previewView.display.rotation)
//            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
//            .setTargetAspectRatio(AspectRatio.RATIO_16_9) // Set aspect ratio here

            .build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(android.util.Size(screenWidth, screenHeight))
            .setTargetRotation(previewView.display.rotation)
            .build().also {
                it.setAnalyzer(cameraExecutor) { imageProxy ->
                    Log.d("imageProxy", "Resolution: ${imageProxy.width} x ${imageProxy.height}")
                    //get Bitmap into the imageProxy
                    var bitmap = imageProxy.toBitmap()
                    onImageCaptured(imageProxy, bitmap) // Pass ImageProxy to the fragment for processing
                }
            }

        try {
            val camera = cameraProvider.bindToLifecycle(
                lifecycleOwner, cameraSelector, preview, imageAnalysis
            )
//            if (isFocusStable(camera)) {

//            }
//            // Enable touch to focus
            enableTouchToFocus(camera)
            // Monitor auto-focus state
            monitorFocusState(camera)
        } catch (e: Exception) {
            Log.e(TAG, "Camera binding failed", e)
        }
    }

    private fun getScreenDimensions(): Pair<Int, Int> {
        val metrics = DisplayMetrics()
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        return Pair(width, height)
    }

    private fun enableTouchToFocus(camera: Camera) {
        previewView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val factory = previewView.meteringPointFactory
                val point = factory.createPoint(event.x, event.y)

                val action = FocusMeteringAction.Builder(point).build()
                camera.cameraControl.startFocusAndMetering(action)
            }
            true
        }
    }

    private fun monitorFocusState(camera: Camera) {
        camera.cameraInfo.cameraState.observe(lifecycleOwner) { state ->
            when (state.type) {
                CameraState.Type.PENDING_OPEN -> Log.d(TAG, "CameraState: Pending Open")
                CameraState.Type.OPENING -> Log.d(TAG, "CameraState: Opening")
                CameraState.Type.OPEN -> Log.d(TAG, "CameraState: Open")
                CameraState.Type.CLOSING -> Log.d(TAG, "CameraState: Closing")
                CameraState.Type.CLOSED -> Log.d(TAG, "CameraState: Closed")
            }
            state.error?.let { error ->
                when (error.code) {
                    CameraState.ERROR_STREAM_CONFIG -> Log.e(TAG, "Stream config error")
                    CameraState.ERROR_CAMERA_IN_USE -> Log.e(TAG, "Camera in use")
                    CameraState.ERROR_MAX_CAMERAS_IN_USE -> Log.e(TAG, "Max cameras in use")
                    CameraState.ERROR_OTHER_RECOVERABLE_ERROR -> Log.e(TAG, "Other recoverable error")
                    CameraState.ERROR_CAMERA_DISABLED -> Log.e(TAG, "Camera disabled")
                    CameraState.ERROR_CAMERA_FATAL_ERROR -> Log.e(TAG, "Fatal error")
                    CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED -> Log.e(TAG, "Do not disturb mode enabled")
                    else -> {

                    }
                }
            }

            camera.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(previewView.meteringPointFactory.createPoint(previewView.width / 2f, previewView.height / 2f)).build())
        }
    }



    companion object {
        private const val TAG = "CameraService"
    }
}