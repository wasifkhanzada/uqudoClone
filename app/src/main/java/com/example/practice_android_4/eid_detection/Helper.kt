package com.example.practice_android_4.eid_detection

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.pow


open class CropSquareBitmap(val bitmap: Bitmap?, val cropSide: String?, val dimension: Int?)
open class DeviceWidthHeight(val width: Int, val height: Int)

class Helper {

    val toFrontDetect = arrayOf("eid-front", "eid-front-old")
    val toBackDetect = arrayOf("eid-back", "eid-back-old")

    private val LOW_LIGHT_THRESHOLD = 150.0 //150.0
    private val BLUR_THRESHOLD = 700.0
    private val MOTION_BLUR_THRESHOLD = 0.028
    private val GLARE_THRESHOLD = 252
    val INSIDE_TOLERANCE = 90
    val EID_SCORE = 0.95
    val IMAGE_PROCESSOR_TARGET_SIZE = 384
    val toFarThreshold = 50



    fun getBoxRect(overlayView: CardOverlayView, imageRectWidth: Float, imageRectHeight: Float, cardBoundingBox: Rect): RectF {

        val scaleX = overlayView.width.toFloat() / imageRectHeight
        val scaleY = overlayView.height.toFloat() / imageRectWidth
        val scale = scaleX.coerceAtLeast(scaleY)

        val offsetX = (overlayView.width.toFloat() - ceil(imageRectHeight * scale)) / 2.0f
        val offsetY = (overlayView.height.toFloat() - ceil(imageRectWidth * scale)) / 2.0f

        val mappedBox = RectF().apply {
            left = cardBoundingBox.left * scale + offsetX
            top = cardBoundingBox.top * scale + offsetY
            right = cardBoundingBox.right * scale + offsetX
            bottom = cardBoundingBox.bottom * scale + offsetY
        }

        Log.d("CardBox", "Mapped RectF: $mappedBox from cardBoundingBox: $cardBoundingBox with scale: $scale and offsets: ($offsetX, $offsetY)")
        return mappedBox

    }

    fun cropToSquare (bitmap: Bitmap): CropSquareBitmap {
        var croppedSide = "height"
        val size = bitmap.width.coerceAtMost(bitmap.height)
        if (size == bitmap.height) {
            croppedSide = "width"
        }

        val xOffset = (bitmap.width - size) / 2
        val yOffset = (bitmap.height - size) / 2
        val croppedBitmap = Bitmap.createBitmap(bitmap, xOffset, yOffset, size, size)

        var difference = 0

        difference = if (croppedSide == "height") {
            (bitmap.height - croppedBitmap.height) / 2
        } else {
            (bitmap.width - croppedBitmap.width) / 2
        }

        return CropSquareBitmap(croppedBitmap, croppedSide, difference.toInt())
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

    // Function to check if two RectF objects match within a tolerance of 5f
    fun isRectFMatchWithinTolerance(rect1: RectF, rect2: RectF): Boolean {
        return abs(rect1.left - rect2.left) > INSIDE_TOLERANCE ||
                abs(rect1.top - rect2.top) > INSIDE_TOLERANCE ||
                abs(rect1.right - rect2.right) > INSIDE_TOLERANCE ||
                abs(rect1.bottom - rect2.bottom) > INSIDE_TOLERANCE
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

    fun getDeviceInfo(): DeviceWidthHeight {
        val displayMetrics = Resources.getSystem().getDisplayMetrics()
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        return DeviceWidthHeight(screenWidth, screenHeight)
    }

    fun cropBitmapToScreen(bitmap: Bitmap): Bitmap {
        val displayMetrics = Resources.getSystem().getDisplayMetrics()
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val screenAspectRatio = screenWidth.toFloat() / screenHeight

        Log.d("screen info", "${screenWidth}, ${screenHeight}, ${screenAspectRatio}")

        val bitmapWidth = bitmap.width
        val bitmapHeight = bitmap.height
        val bitmapAspectRatio = bitmapWidth.toFloat() / bitmapHeight

        val (cropWidth, cropHeight) = if (bitmapAspectRatio > screenAspectRatio) {
            // Bitmap is wider than screen
            val height = bitmapHeight
            val width = (height * screenAspectRatio).toInt()
            width to height
        } else {
            // Bitmap is taller than screen
            val width = bitmapWidth
            val height = (width / screenAspectRatio).toInt()
            width to height
        }

        val cropStartX = (bitmapWidth - cropWidth) / 2
        val cropStartY = (bitmapHeight - cropHeight) / 2

        val croppedBitmap = Bitmap.createBitmap(bitmap, cropStartX, cropStartY, cropWidth, cropHeight)

        val scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap, screenWidth, screenHeight, true)

        return scaledBitmap

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

    fun isGlareDetect(mutableBitmap: Bitmap): Boolean {
        // Get the image dimensions
        val width = mutableBitmap.width
        val height = mutableBitmap.height
        // Loop through each pixel to find bright spots
        var flag: Boolean = false
        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = mutableBitmap.getPixel(x, y)
                val red = Color.red(pixel)
                val green = Color.green(pixel)
                val blue = Color.blue(pixel)


                // Convert to grayscale
                val gray = (red + green + blue) / 3

//                if(gray > 200) {
                    Log.d("GLARE", gray.toString())
//                }


                // Threshold to find bright spots
                if (gray > GLARE_THRESHOLD) { // Threshold value for bright spots
                    flag = true
                    break
                }
            }
        }

        return flag
    }

    companion object {
        private const val TAG = "EidDetectionHelper"
    }
}