package com.example.practice_android_4.face_detection

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.nio.ByteBuffer
import kotlin.math.absoluteValue
import kotlin.math.ceil
import kotlin.math.pow

data class FaceContours(
    val face: List<PointF>,
    val leftEyebrowTop: List<PointF>,
    val leftEyebrowBottom: List<PointF>,
    val rightEyebrowTop: List<PointF>,
    val rightEyebrowBottom: List<PointF>,
    val leftEye: List<PointF>,
    val rightEye: List<PointF>,
    val upperLipTop: List<PointF>,
    val upperLipBottom: List<PointF>,
    val lowerLipTop: List<PointF>,
    val lowerLipBottom: List<PointF>,
    val noseBridge: List<PointF>,
    val noseBottom: List<PointF>
)

class Helper {

    val angleThreshold = 10.0 // Adjust this threshold as needed
    val eyeOpenProbabilityThreshold = 0.6f // Typically, a probability > 0.5 indicates open eyes
    val smileProbabilityThreshold = 0.5f // Use Float type for the threshold
    val faceToFarThreshold = 0.15
    val faceToCloseThreshold = 0.19
    val imageBlurryThreshold = 1000.0
    val imageBrightnessThreshold = 100

    fun setupFaceDetector(): FaceDetector {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
            //.setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            //.setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            // .enableTracking()

        return FaceDetection.getClient(options)
    }

    fun getFaceSize(faceBounds: Rect, frameWidth: Int, frameHeight: Int): Double {
        // Calculate face area
        val faceArea = faceBounds.width() * faceBounds.height()

        // Calculate frame area
        val frameArea = frameWidth * frameHeight

        // Calculate relative size (percentage of frame occupied by the face)
        val relativeFaceSize = faceArea.toDouble() / frameArea.toDouble()

        return relativeFaceSize
    }

    // Function to check if the face is straight based on Euler angles
    fun isFaceStraight(face: Face, threshold: Double): Boolean {
        // Check if all Euler angles are within the defined threshold
        return (face.headEulerAngleX.absoluteValue < threshold
                && face.headEulerAngleY.absoluteValue < threshold
                && face.headEulerAngleZ.absoluteValue < threshold)
    }

    // Function to check if both eyes are open based on the eye open probability
    fun areEyesOpen(face: Face, threshold: Float): Boolean {
        val leftEyeOpen = face.leftEyeOpenProbability ?: 0.0f
        val rightEyeOpen = face.rightEyeOpenProbability ?: 0.0f

        // Check if both eyes are open based on the defined threshold
        return (leftEyeOpen > threshold && rightEyeOpen > threshold)
    }

    // Function to check if the mouth is open or smiling based on the probabilities
    fun isSmiling(face: Face, smileThreshold: Float): Boolean {
        val smileProbability = face.smilingProbability ?: 0.0f
        Log.d("Smile", "Smile probability: $smileProbability")
        // Check if either the mouth is open or the face is smiling based on the defined thresholds
        return smileProbability > smileThreshold
    }

    fun getBoxRect(overlay: OvalOverlayView, imageRectWidth: Float, imageRectHeight: Float, faceBoundingBox: Rect): RectF {

        val scaleX = overlay.width.toFloat() / imageRectHeight
        val scaleY = overlay.height.toFloat() / imageRectWidth
        val scale = scaleX.coerceAtLeast(scaleY)

        val offsetX = (overlay.width.toFloat() - ceil(imageRectHeight * scale)) / 2.0f
        val offsetY = (overlay.height.toFloat() - ceil(imageRectWidth * scale)) / 2.0f

        val mappedBox = RectF().apply {
            left = faceBoundingBox.right * scale + offsetX
            top = faceBoundingBox.top * scale + offsetY
            right = faceBoundingBox.left * scale + offsetX
            bottom = faceBoundingBox.bottom * scale + offsetY
        }

        // Adjust the width and height by subtracting 10 units
        mappedBox.left -= 50
        mappedBox.top -= 50
        mappedBox.right += 50
        mappedBox.bottom += 50

        val centerX = overlay.width.toFloat() / 2

        return mappedBox.apply {
            left =( centerX + (centerX - left))
            right = centerX - (right - centerX)
        }
    }

    // Helper function to convert ByteBuffer to ByteArray
    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()    // Rewind the buffer to zero
        val data = ByteArray(remaining())
        get(data)   // Copy the buffer into a byte array
        return data // Return the byte array
    }

    fun calculateBitmapBrightness(bitmap: Bitmap): Double {
        var totalBrightness = 0.0
        val width = bitmap.width
        val height = bitmap.height
        val totalPixels = width * height

        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = bitmap.getPixel(x, y)
                val brightness = Color.red(pixel) * 0.299 + Color.green(pixel) * 0.587 + Color.blue(pixel) * 0.114
                totalBrightness += brightness
            }
        }

        return totalBrightness / totalPixels
    }

    fun getFaceContours (face: Face): FaceContours {
        val contours = FaceContours(
                face.allContours.find { it.faceContourType == FaceContour.FACE }?.points ?: emptyList(),
        face.allContours.find { it.faceContourType == FaceContour.LEFT_EYEBROW_TOP }?.points ?: emptyList(),
        face.allContours.find { it.faceContourType == FaceContour.LEFT_EYEBROW_BOTTOM }?.points ?: emptyList(),
        face.allContours.find { it.faceContourType == FaceContour.RIGHT_EYEBROW_TOP }?.points ?: emptyList(),
        face.allContours.find { it.faceContourType == FaceContour.RIGHT_EYEBROW_BOTTOM }?.points ?: emptyList(),
        face.allContours.find { it.faceContourType == FaceContour.LEFT_EYE }?.points ?: emptyList(),
        face.allContours.find { it.faceContourType == FaceContour.RIGHT_EYE }?.points ?: emptyList(),
        face.allContours.find { it.faceContourType == FaceContour.UPPER_LIP_TOP }?.points ?: emptyList(),
        face.allContours.find { it.faceContourType == FaceContour.UPPER_LIP_BOTTOM }?.points ?: emptyList(),
        face.allContours.find { it.faceContourType == FaceContour.LOWER_LIP_TOP }?.points ?: emptyList(),
        face.allContours.find { it.faceContourType == FaceContour.LOWER_LIP_BOTTOM }?.points ?: emptyList(),
        face.allContours.find { it.faceContourType == FaceContour.NOSE_BRIDGE }?.points ?: emptyList(),
        face.allContours.find { it.faceContourType == FaceContour.NOSE_BOTTOM }?.points ?: emptyList()
        )
        return contours
    }

    fun isImageBlurry(bitmap: Bitmap, threshold: Double): Boolean {
        val grayscaleBitmap = bitmap.toGrayscale()
        val laplacianBitmap = grayscaleBitmap.applyLaplacian()
        val variance = laplacianBitmap.calculateVariance()
        Log.d("BLURRY", variance.toString())
        return variance < threshold
    }

    private fun Bitmap.toGrayscale(): Bitmap {
        val width = this.width
        val height = this.height
        val grayscaleBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = this.getPixel(x, y)
                val gray = (Color.red(pixel) * 0.299 + Color.green(pixel) * 0.587 + Color.blue(pixel) * 0.114).toInt()
                val grayPixel = Color.rgb(gray, gray, gray)
                grayscaleBitmap.setPixel(x, y, grayPixel)
            }
        }

        return grayscaleBitmap
    }
    private fun Bitmap.applyLaplacian(): Bitmap {
        val kernel = arrayOf(
            intArrayOf(0, 1, 0),
            intArrayOf(1, -4, 1),
            intArrayOf(0, 1, 0)
        )

        val width = this.width
        val height = this.height
        val laplacianBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (x in 1 until width - 1) {
            for (y in 1 until height - 1) {
                var laplacianValue = 0

                for (i in -1..1) {
                    for (j in -1..1) {
                        val pixel = this.getPixel(x + i, y + j)
                        val gray = Color.red(pixel)
                        laplacianValue += gray * kernel[i + 1][j + 1]
                    }
                }

                val newPixel = 255 - laplacianValue
                val clampedPixel = Color.rgb(newPixel.coerceIn(0, 255), newPixel.coerceIn(0, 255), newPixel.coerceIn(0, 255))
                laplacianBitmap.setPixel(x, y, clampedPixel)
            }
        }

        return laplacianBitmap
    }
    private fun Bitmap.calculateVariance(): Double {
        val width = this.width
        val height = this.height

        var sum = 0.0
        var sumOfSquares = 0.0
        val totalPixels = width * height

        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = this.getPixel(x, y)
                val gray = Color.red(pixel)
                sum += gray
                sumOfSquares += gray * gray
            }
        }

        val mean = sum / totalPixels
        val meanOfSquares = sumOfSquares / totalPixels
        return meanOfSquares - mean.pow(2)
    }

    fun cropBitmap(originalBitmap: Bitmap, x: Int, y: Int, width: Int, height: Int): Bitmap {
        // Ensure x and y are within bounds
        val validX = Math.max(0, x)
        val validY = Math.max(0, y)

        // Adjust width and height if they exceed the original bitmap's dimensions
        val validWidth = Math.min(width, originalBitmap.width - validX)
        val validHeight = Math.min(height, originalBitmap.height - validY)

        // Create the cropped bitmap
        return Bitmap.createBitmap(originalBitmap, validX, validY, validWidth, validHeight)
    }

    fun imageProxyToByteArray(image: ImageProxy): ByteArray {
        val planes = image.planes
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return bytes
    }

    companion object {
        private const val TAG = "FaceDetectionHelper"
    }
}