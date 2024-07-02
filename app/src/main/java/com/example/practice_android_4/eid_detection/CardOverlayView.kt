package com.example.practice_android_4.eid_detection

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.View

open class FrameDimension(val left: Float, val top: Float, val right: Float, val bottom: Float, val width: Float, val height: Float)


class CardOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // Disable hardware acceleration for this view
    }

    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#80000000") // Semi-transparent black
        style = Paint.Style.FILL
    }

    private val framePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
        pathEffect = DashPathEffect(floatArrayOf(20f, 20f), 0f) // Dashed line effect
        isAntiAlias = true
    }

    private val cornerRadius = 20f // Radius for rounded corners
    private var frameRect = RectF()

    fun frameDimension(): FrameDimension {
        // Convert mm to pixels
        val frameWidthPx = mmToPx(context, 60f)
        val frameHeightPx = mmToPx(context, 40f)

        // Calculate the frame dimensions and position
        val left = (width - frameWidthPx) / 2
        val top = (height - frameHeightPx) / 2
        val right = left + frameWidthPx
        val bottom = top + frameHeightPx

        return FrameDimension(left, top, right, bottom, frameWidthPx, frameHeightPx)
    }

    // Set the CLEAR mode for clearing the rectangle area
    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    fun getFrameDimension(): RectF {
        val dimension = frameDimension()
        return RectF(dimension.left, dimension.top, dimension.right, dimension.bottom)
    }

    // Adjust this to match your desired oval size and position
    private fun updateFrameDimensions() {
        val dimension = frameDimension()
        // Draw the clear rectangle to create the transparent area
        frameRect = RectF(dimension.left, dimension.top, dimension.right, dimension.bottom)
    }

    // Method to check if a bounding box is inside the oval
    fun isBoundingBoxInsideFrame(boundingBox: RectF): Boolean {
        return frameRect.contains(boundingBox)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw the semi-transparent background again
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        updateFrameDimensions();

        canvas.drawRoundRect(frameRect, cornerRadius, cornerRadius, clearPaint)

        // Draw the rounded rectangle frame (dashed border)
        canvas.drawRoundRect(frameRect, cornerRadius, cornerRadius, framePaint)
    }

    // Convert mm to pixels
    private fun mmToPx(context: Context, mm: Float): Float {
        val displayMetrics: DisplayMetrics = context.resources.displayMetrics
        return mm * (displayMetrics.xdpi / 25.4f)
    }

    // Method to change the frame border color
    fun setFrameBorderColor(color: Int) {
        framePaint.color = color
        invalidate() // Request to redraw the view with the new color
    }
}
