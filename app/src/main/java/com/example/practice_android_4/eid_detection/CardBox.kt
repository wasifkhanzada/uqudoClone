package com.example.practice_android_4.eid_detection

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log

class CardBox(
    overlay: CardBoxOverlay,
    private val cardBoundingBox: Rect,
    private val imageRect: Rect
) : CardBoxOverlay.CardBox(overlay) {

    private val paint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 6.0f
    }

    override fun draw(canvas: Canvas?) {
        val rect = getBoxRect(
            imageRectWidth = imageRect.width().toFloat(),
            imageRectHeight = imageRect.height().toFloat(),
            cardBoundingBox = cardBoundingBox
        )
        canvas?.drawRect(rect, paint)
        Log.d("CardBox", "Drawing Rect: $rect with paint: $paint")
    }
}