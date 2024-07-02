package com.example.practice_android_4.eid_detection

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import androidx.camera.core.ImageProxy
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.abs
import kotlin.math.atan2

open class CropSquareBitmap(val bitmap: Bitmap?, val cropSide: String?, val dimension: Int?)

class Helper {

    val toFrontDetect = arrayOf("eid-front", "eid-front-old")
    val toBackDetect = arrayOf("eid-back", "eid-back-old")
    val toFarThreshold = 50
    val toGlareThreshold = 252
    val eidScore = 0.98
    val imageProcessorTargetSize = 384
    val imageBrightnessThreshold = 110
    val imageBlurryThreshold = 1550.0
    val insideTolerance = 80f

    fun getBoxRect(overlayView: CardBoxOverlay, imageRectWidth: Float, imageRectHeight: Float, cardBoundingBox: Rect): RectF {

        val scaleX = overlayView.width.toFloat() / imageRectHeight
        val scaleY = overlayView.height.toFloat() / imageRectWidth
        val scale = scaleX.coerceAtLeast(scaleY)

//        overlayView.mScale = scale

        val offsetX = (overlayView.width.toFloat() - ceil(imageRectHeight * scale)) / 2.0f
        val offsetY = (overlayView.height.toFloat() - ceil(imageRectWidth * scale)) / 2.0f

//        overlayView.mOffsetX = offsetX
//        overlayView.mOffsetY = offsetY

        val mappedBox = RectF().apply {
            left = cardBoundingBox.left * scale + offsetX
            top = cardBoundingBox.top * scale + offsetY
            right = cardBoundingBox.right * scale + offsetX
            bottom = cardBoundingBox.bottom * scale + offsetY
        }

        Log.d("CardBox", "Mapped RectF: $mappedBox from cardBoundingBox: $cardBoundingBox with scale: $scale and offsets: ($offsetX, $offsetY)")
        return mappedBox

    }

    fun detectGlare(mutableBitmap: Bitmap): Boolean {
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

                if(gray > 200) {
                    Log.d("GLARE", gray.toString())
                }


                // Threshold to find bright spots
                if (gray > toGlareThreshold) { // Threshold value for bright spots
                    flag = true
                    break
                }
            }
        }

        return flag
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

    // Function to check if two RectF objects match within a tolerance of 5f
    fun isRectFMatchWithinTolerance(rect1: RectF, rect2: RectF, tolerance: Float): Boolean {
        return abs(rect1.left - rect2.left) > tolerance ||
                abs(rect1.top - rect2.top) > tolerance ||
                abs(rect1.right - rect2.right) > tolerance ||
                abs(rect1.bottom - rect2.bottom) > tolerance
    }

    fun imageProxyToByteArray(image: ImageProxy): ByteArray {
        val planes = image.planes
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return bytes
    }

    companion object {
        private const val TAG = "EidDetectionHelper"
    }
}