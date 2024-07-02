package com.example.practice_android_4.camera

import android.content.Context
import android.util.Log
import android.view.MotionEvent
import androidx.camera.view.PreviewView
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraService(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val onImageCaptured: (ImageProxy) -> Unit,
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

    private fun setupCamera(lensFacing: Int) {
        cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        val preview = Preview.Builder()
            .setTargetRotation(previewView.display.rotation)
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetRotation(previewView.display.rotation)
            .build().also {
                it.setAnalyzer(cameraExecutor) { imageProxy ->
                    onImageCaptured(imageProxy) // Pass ImageProxy to the fragment for processing
                }
            }

        try {
            val camera = cameraProvider.bindToLifecycle(
                lifecycleOwner, cameraSelector, preview, imageAnalysis
            )
            // Enable touch to focus
            enableTouchToFocus(camera)
        } catch (e: Exception) {
            Log.e(TAG, "Camera binding failed", e)
        }
    }

    private fun enableTouchToFocus(camera: Camera) {
        previewView.setOnTouchListener { view, motionEvent ->
            if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                val factory = previewView.meteringPointFactory
                val point = factory.createPoint(motionEvent.x, motionEvent.y)

                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                    .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                camera.cameraControl.startFocusAndMetering(action)
                return@setOnTouchListener true
            }
            false
        }
    }

    companion object {
        private const val TAG = "CameraService"
    }
}