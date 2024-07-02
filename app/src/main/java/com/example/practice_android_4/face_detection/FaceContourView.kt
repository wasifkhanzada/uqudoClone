package com.example.practice_android_4.face_detection

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.practice_android_4.R


class FaceContourView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var faceContours: List<FaceContours> = listOf()
    private val paint: Paint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.teal_200) // Replace with your desired color
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    fun setFaceContours(contours: FaceContours) {
        faceContours = listOf(contours)
        invalidate() // Redraw the view
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw each contour
        faceContours.forEach { contour ->
            drawContour(canvas, contour.face)
            drawContour(canvas, contour.leftEyebrowTop)
            drawContour(canvas, contour.leftEyebrowBottom)
            drawContour(canvas, contour.rightEyebrowTop)
            drawContour(canvas, contour.rightEyebrowBottom)
            drawContour(canvas, contour.leftEye)
            drawContour(canvas, contour.rightEye)
            drawContour(canvas, contour.upperLipTop)
            drawContour(canvas, contour.upperLipBottom)
            drawContour(canvas, contour.lowerLipTop)
            drawContour(canvas, contour.lowerLipBottom)
            drawContour(canvas, contour.noseBridge)
            drawContour(canvas, contour.noseBottom)
        }
    }

    private fun drawContour(canvas: Canvas, points: List<PointF>) {
        if (points.isNotEmpty()) {
            val path = android.graphics.Path()
            path.moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) {
                path.lineTo(points[i].x, points[i].y)
            }
            path.close() // Close the path if you want to connect the last point to the first
            canvas.drawPath(path, paint)
        }
    }
}