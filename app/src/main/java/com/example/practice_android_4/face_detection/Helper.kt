package com.example.practice_android_4.face_detection

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.absoluteValue
import kotlin.math.ceil
import kotlin.math.pow

class Helper {
    private val ANGLE_THRESHOLD = 10.0
    private val TOO_FAR_THRESHOLD = 0.15
    private val TOO_CLOSE_THRESHOLD = 0.19
    private val EYE_OPEN_THRESHOLD = 0.6f
    private val SMILE_THRESHOLD = 0.5f
    private val LOW_LIGHT_THRESHOLD = 100.0
    private val BLUR_THRESHOLD = 100.0
    private val MOTION_BLUR_THRESHOLD = 0.025

    fun setupFaceDetector(): FaceDetector {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
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
    fun isFaceStraight(face: Face): Boolean {
        // Check if all Euler angles are within the defined threshold
        return (face.headEulerAngleX.absoluteValue < ANGLE_THRESHOLD
                && face.headEulerAngleY.absoluteValue < ANGLE_THRESHOLD
                && face.headEulerAngleZ.absoluteValue < ANGLE_THRESHOLD)
    }

    // Function to check if both eyes are open based on the eye open probability
    fun areEyesOpen(face: Face): Boolean {
        val leftEyeOpen = face.leftEyeOpenProbability ?: 0.0f
        val rightEyeOpen = face.rightEyeOpenProbability ?: 0.0f

        // Check if both eyes are open based on the defined threshold
        return (leftEyeOpen > EYE_OPEN_THRESHOLD && rightEyeOpen > EYE_OPEN_THRESHOLD)
    }

    fun areEyesStraight(face: Face, leftEye: PointF, rightEye: PointF): Boolean {
        val leftEyeOpen = face.leftEyeOpenProbability ?: return false
        val rightEyeOpen = face.rightEyeOpenProbability ?: return false

        // Ensure both eyes are open
        if (leftEyeOpen < 0.5 || rightEyeOpen < 0.5) {
            return false
        }

        // Calculate the angle between the eyes
        val eyeDeltaX = rightEye.x - leftEye.x
        val eyeDeltaY = rightEye.y - leftEye.y
        val angle = Math.toDegrees(Math.atan2(eyeDeltaY.toDouble(), eyeDeltaX.toDouble()))

        Log.d("Angle", "Angle: $angle")

        // Check if the angle is within a reasonable range (e.g., -10 to 10 degrees)
        return angle in -10.0..10.0
    }

    // Function to check if the mouth is open or smiling based on the probabilities
    fun isSmiling(face: Face): Boolean {
        val smileProbability = face.smilingProbability ?: 0.0f
        Log.d("Smile", "Smile probability: $smileProbability")
        // Check if either the mouth is open or the face is smiling based on the defined thresholds
        return smileProbability > SMILE_THRESHOLD
    }

    fun isFaceTooFar(faceSize: Double): Boolean {
        return faceSize < TOO_FAR_THRESHOLD
    }

    fun isFaceTooClose(faceSize: Double): Boolean {
        return faceSize > TOO_CLOSE_THRESHOLD
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

    // Helper function to convert ByteBuffer to ByteArray
    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()    // Rewind the buffer to zero
        val data = ByteArray(remaining())
        get(data)   // Copy the buffer into a byte array
        return data // Return the byte array
    }
    fun convertBitmapToByteArrayUncompressed(bitmap: Bitmap): ByteArray {
        /*
             compress method takes quality as one of the parameters.
             For quality, the range of value expected is 0 - 100 where,
             0 - compress for a smaller size, 100 - compress for max quality.
            */
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
        val bitmapData = byteArrayOutputStream.toByteArray()
        return bitmapData

    }

    /**
     * Downscale the given bitmap to the specified maximum width while maintaining the aspect ratio.
     *
     * @param bitmap The original bitmap to downscale.
     * @param maxWidth The maximum width of the downscaled bitmap.
     * @return The downscaled bitmap with the same aspect ratio.
     */
    fun downscaleBitmapWithWidth(bitmap: Bitmap, maxWidth: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        // Calculate the aspect ratio
        val aspectRatio = width.toFloat() / height.toFloat()

        // Calculate the new height maintaining the aspect ratio
        val newHeight = (maxWidth / aspectRatio).toInt()

        // Create the scaled bitmap
        return Bitmap.createScaledBitmap(bitmap, maxWidth, newHeight, true)
    }

    fun isLowLight(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        var sum = 0
        var pixelCount = 0

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                // Convert the pixel to grayscale using luminance formula
                val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                sum += luminance
                pixelCount++
            }
        }

        val avgLuminance = sum / pixelCount.toFloat()
        Log.d("avgLuminance", avgLuminance.toString())

        return avgLuminance <= LOW_LIGHT_THRESHOLD
    }

    fun isImageBlurry(bitmap: Bitmap): Boolean {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2GRAY)

        val laplacian = Mat()
        Imgproc.Laplacian(mat, laplacian, CvType.CV_64F)
        val laplacianArray = DoubleArray(laplacian.rows() * laplacian.cols())
        laplacian.get(0, 0, laplacianArray)
        var mean = 0.0
        for (value in laplacianArray) {
            mean += value.pow(2.0)
        }
        mean /= laplacianArray.size

        Log.d("mean", mean.toString())

        return mean < BLUR_THRESHOLD
    }

    fun isImageMotionBlurry(bitmap: Bitmap): Boolean {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2GRAY)

        val edges = Mat()
        Imgproc.Canny(mat, edges, 50.0, 150.0)

        val nonZeroEdges = Core.countNonZero(edges)
        val totalPixels = mat.rows() * mat.cols()

        val edgeDensity = nonZeroEdges.toDouble() / totalPixels

        Log.d("edgeDensity", edgeDensity.toString())

        return edgeDensity < MOTION_BLUR_THRESHOLD
    }

    companion object {

        private const val TAG = "FaceDetectionHelper"
    }
}