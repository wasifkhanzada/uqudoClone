package com.example.practice_android_4.eid_detection

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.View
import kotlin.math.ceil

open class CardBoxOverlay(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private val lock = Any()
    private val cardBoxes: MutableList<CardBox> = mutableListOf()
    var mScale: Float? = null
    var mOffsetX: Float? = null
    var mOffsetY: Float? = null

    abstract class CardBox(private val overlay: CardBoxOverlay) {

        abstract fun draw(canvas: Canvas?)

        fun getBoxRect(imageRectWidth: Float, imageRectHeight: Float, cardBoundingBox: Rect): RectF {
            val scaleX = overlay.width.toFloat() / imageRectHeight
            val scaleY = overlay.height.toFloat() / imageRectWidth
            val scale = scaleX.coerceAtLeast(scaleY)

//            overlay.mScale = scale

            val offsetX = (overlay.width.toFloat() - ceil(imageRectHeight * scale)) / 2.0f
            val offsetY = (overlay.height.toFloat() - ceil(imageRectWidth * scale)) / 2.0f

//            overlay.mOffsetX = offsetX
//            overlay.mOffsetY = offsetY

            val mappedBox = RectF().apply {
                left = cardBoundingBox.left * scale + offsetX
                top = cardBoundingBox.top * scale + offsetY
                right = cardBoundingBox.right * scale + offsetX
                bottom = cardBoundingBox.bottom * scale + offsetY
            }
//            helper.getBoxRect(overlay, imageRectWidth, imageRectHeight, cardBoundingBox)
            return mappedBox
        }
    }

    fun clear() {
        synchronized(lock) { cardBoxes.clear() }
        postInvalidate()
    }

    fun add(cardBox: CardBox) {
        synchronized(lock) { cardBoxes.add(cardBox) }
        postInvalidate()
    }

    init {
        Log.d("CardBoxOverlay", "CardBoxOverlay initialized")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        Log.d("CardBoxOverlay", "onDraw called")
        synchronized(lock) {
            for (graphic in cardBoxes) {
                Log.d("CardBoxOverlay", "Drawing card box: $graphic")
                graphic.draw(canvas)
            }
        }
    }
}