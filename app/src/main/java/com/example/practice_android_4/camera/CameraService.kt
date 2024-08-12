package com.example.practice_android_4.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import android.view.MotionEvent
import androidx.camera.view.PreviewView
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import org.opencv.android.OpenCVLoader
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraService(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val onImageCaptured: (ImageProxy, Bitmap) -> Unit,
) {

    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var cameraSelector: CameraSelector
    private lateinit var cameraExecutor: ExecutorService

    private lateinit var imageCapture: ImageCapture

    init {
        // Initialize OpenCV
        OpenCVLoader.initDebug()
    }

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

    private fun ImageProxy.toBitmap(): Bitmap {
        val nv21 = yuv420888ToNv21(this)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 50, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }
    private fun yuv420888ToNv21(image: ImageProxy): ByteArray {
        val yBuffer = image.planes[0].buffer // Y
        val uBuffer = image.planes[1].buffer // U
        val vBuffer = image.planes[2].buffer // V

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        return nv21
    }
    private fun Bitmap.rotate(degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }


    private fun setupCamera(lensFacing: Int) {
        cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        val screenWidth = 768
        val screenHeight = 1024

        val preview = Preview.Builder()
            .setTargetResolution(android.util.Size(screenWidth, screenHeight))
            .setTargetRotation(previewView.display.rotation)
            .build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

//        imageCapture = ImageCapture.Builder()
//            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
//            .build()

//        val imageAnalysis = ImageAnalysis.Builder()
//            .setTargetResolution(android.util.Size(screenWidth, screenHeight))
//            .setTargetRotation(previewView.display.rotation)
//            .build().also {
//                it.setAnalyzer(cameraExecutor) { imageProxy ->
//                    Log.d("imageProxy", "Resolution: ${imageProxy.width} x ${imageProxy.height}")
//                    var bitmap = imageProxy.toBitmap()
//                    onImageCaptured(imageProxy, bitmap) // Pass ImageProxy to the fragment for processing
//                }
//            }

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(android.util.Size(screenWidth, screenHeight))
            .setTargetRotation(previewView.display.rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
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

            val cameraControl = camera.cameraControl
//            cameraControl.startFocusAndMetering()
            // Disable auto-exposure
//            cameraControl.enableTorch(false) // Ensure torch is off
//            cameraControl.setAuto(0) // Set exposure compensation to 0

            // Optional: Manually control exposure time and sensitivity
            // val aeConfig = AutoExposureConfig.Builder()
            //     .setMode(AutoExposureConfig.MODE_OFF)
            //     .build()
            // cameraControl.setAutoExposureConfig(aeConfig)

            // Enable touch to focus
            enableTouchToFocus(camera)
        } catch (e: Exception) {
            Log.e(TAG, "Camera binding failed", e)
        }
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
    companion object {
        private const val TAG = "CameraService"
        private const val BLUR_THRESHOLD = 100.0
        private const val LOW_LIGHT_THRESHOLD = 100.0f // Adjust this value based on your requirements
    }
}