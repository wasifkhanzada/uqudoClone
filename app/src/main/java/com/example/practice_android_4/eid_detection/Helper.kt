package com.example.practice_android_4.eid_detection

import android.R.attr.bitmap
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.pow


open class CropSquareBitmap(val bitmap: Bitmap?, val cropSide: String?, val dimension: Int?)
open class DeviceWidthHeight(val width: Int, val height: Int)

class Helper {

    val toFrontDetect = arrayOf("eid-front", "eid-front-old")
    val toBackDetect = arrayOf("eid-back", "eid-back-old")
    val toFarThreshold = 50
    val toGlareThreshold = 252
    val eidScore = 0.95
    val imageProcessorTargetSize = 384
    val imageBrightnessThreshold = 110
    val imageBlurryThreshold = 1250.0
    val insideTolerance = 80f
    val motionThreshold = 50
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

    fun calculateBitmapBrightness(bitmap: Bitmap, threshold: Int): Boolean {
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
        Log.d("(totalBrightness / totalPixels)", (totalBrightness / totalPixels).toString())
        return (totalBrightness / totalPixels) < threshold
    }

    fun detectMotionBlur(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height

        val sobelX = arrayOf(
            intArrayOf(-1, 0, 1),
            intArrayOf(-2, 0, 2),
            intArrayOf(-1, 0, 1)
        )

        val sobelY = arrayOf(
            intArrayOf(-1, -2, -1),
            intArrayOf(0, 0, 0),
            intArrayOf(1, 2, 1)
        )

        var sumX = 0
        var sumY = 0

        for (x in 1 until width - 1) {
            for (y in 1 until height - 1) {
                var pixelX = 0
                var pixelY = 0

                for (i in -1..1) {
                    for (j in -1..1) {
                        val pixel = bitmap.getPixel(x + i, y + j)
                        val intensity = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3

                        pixelX += intensity * sobelX[i + 1][j + 1]
                        pixelY += intensity * sobelY[i + 1][j + 1]
                    }
                }

                sumX += Math.abs(pixelX)
                sumY += Math.abs(pixelY)
            }
        }

        val edgeMagnitude = Math.sqrt((sumX * sumX + sumY * sumY).toDouble()).toInt()
        val sharpness = edgeMagnitude / (width * height)
        Log.d("MOTION_BLUR sharpness", sharpness.toString())
        // Threshold for determining blur
        return sharpness < motionThreshold
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

    fun isCardInsideFrame(card: Rect, frame: RectF): Boolean {
        return frame.contains(card.left.toFloat(), card.top.toFloat()) &&
                frame.contains(card.right.toFloat(), card.bottom.toFloat())
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



    // Convert bitmap to grayscale
    private fun toGrayscale2(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val grayscaleBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val gray = (0.3 * r + 0.59 * g + 0.11 * b).toInt()
                grayscaleBitmap.setPixel(x, y, Color.rgb(gray, gray, gray))
            }
        }
        return grayscaleBitmap
    }
    // Apply a simple Gaussian blur (3x3 kernel)
    private fun applyGaussianBlur(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val blurredBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val kernel = arrayOf(
            arrayOf(1, 2, 1),
            arrayOf(2, 4, 2),
            arrayOf(1, 2, 1)
        )

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var sum = 0
                var kernelSum = 0

                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val pixel = bitmap.getPixel(x + kx, y + ky) and 0xFF
                        sum += pixel * kernel[ky + 1][kx + 1]
                        kernelSum += kernel[ky + 1][kx + 1]
                    }
                }

                val blurredPixel = sum / kernelSum
                blurredBitmap.setPixel(x, y, Color.rgb(blurredPixel, blurredPixel, blurredPixel))
            }
        }

        return blurredBitmap
    }
    // Apply Laplacian operator
    private fun applyLaplacian2(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val laplacianBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val kernel = arrayOf(
            arrayOf(0, 1, 0),
            arrayOf(1, -4, 1),
            arrayOf(0, 1, 0)
        )

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var sum = 0

                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val pixel = bitmap.getPixel(x + kx, y + ky) and 0xFF
                        sum += pixel * kernel[ky + 1][kx + 1]
                    }
                }

                val laplacianPixel = 128 + sum // Shift to positive range
                laplacianBitmap.setPixel(x, y, Color.rgb(laplacianPixel, laplacianPixel, laplacianPixel))
            }
        }

        return laplacianBitmap
    }
    // Calculate variance of the Laplacian
    private fun calculateVariance2(bitmap: Bitmap): Double {
        val width = bitmap.width
        val height = bitmap.height
        var mean = 0.0
        var meanSquare = 0.0

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y) and 0xFF
                mean += pixel
                meanSquare += pixel.toDouble().pow(2.0)
            }
        }

        val numPixels = width * height
        mean /= numPixels
        meanSquare /= numPixels

        return meanSquare - mean.pow(2.0)
    }
    // Function to detect sharpness
    fun detectSharpness(bitmap: Bitmap): Double {
        val grayscaleBitmap = toGrayscale2(bitmap)
        val blurredBitmap = applyGaussianBlur(grayscaleBitmap)
        val laplacianBitmap = applyLaplacian2(blurredBitmap)
        return calculateVariance2(laplacianBitmap)
    }
    companion object {
        private const val TAG = "EidDetectionHelper"
    }
}