package com.example.practice_android_4.face_detection

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

open class OvalDimension(val left: Float, val top: Float, val right: Float, val bottom: Float, val width: Float, val height: Float)


class OvalOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val ovalAspectRatio = 1.5f // Example: width to height ratio

    // Define the oval dimensions
    private val ovalRect = RectF()
    // Property to keep track of the current animator

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // Required for clearing part of the canvas
    }

    private val paint = Paint().apply {
        color = Color.parseColor("#80000000")// Light black color with 50% opacity
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint().apply {
        color = Color.RED // Border color
        style = Paint.Style.STROKE // Border style
        strokeWidth = 5f // Border thickness
        isAntiAlias = true // Smooth edges
    }

    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    // Method to check get Oval Dimensions
    fun ovalDimension(): OvalDimension {
        // Calculate the frame dimensions with a fixed aspect ratio
        val availableWidth = width * 0.99f // 70% of the view's width
        val availableHeight = height * 0.55f // 50% of the view's height

        val ovalWidth: Float
        val ovalHeight: Float

        // Calculate oval dimensions to maintain the aspect ratio
        if (availableWidth * ovalAspectRatio <= availableHeight) {
            ovalWidth = availableWidth
            ovalHeight = availableWidth * ovalAspectRatio
        } else {
            ovalHeight = availableHeight
            ovalWidth = availableHeight / ovalAspectRatio
        }

        // Center the oval
        val left = (width - ovalWidth) / 2
        val top = (height - ovalHeight) / 4
        // val top = 400f
        val right = left + ovalWidth
        val bottom = top + ovalHeight

        return OvalDimension(left, top, right, bottom, ovalWidth, ovalHeight)
    }

    fun getOvalDimension(): RectF {
        val dimension = ovalDimension()
        return RectF(dimension.left, dimension.top, dimension.right, dimension.bottom)
    }

    // Adjust this to match your desired oval size and position
    private fun updateOvalDimensions() {
        val dimension = ovalDimension()
        ovalRect.set(dimension.left, dimension.top, dimension.right, dimension.bottom)
    }

    // Method to check if a bounding box is inside the oval
    fun isBoundingBoxInsideOval(boundingBox: RectF): Boolean {
        return ovalRect.contains(boundingBox)
    }

    // Method to change the border color
    fun changeBorderColor(color: Int) {
        borderPaint.color = color
        invalidate() // Redraw the view with the new color
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Update the oval dimensions based on the current view size
        updateOvalDimensions()

        // Draw the semi-transparent background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // Clear the oval area to make it transparent
        canvas.drawOval(ovalRect, clearPaint)

        // Draw the oval border
        canvas.drawOval(ovalRect, borderPaint)

    }
}
