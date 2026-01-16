package com.fjnu.schedule.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class HsvPaletteView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var hue: Float = 0f
        set(value) {
            val clamped = value.coerceIn(0f, 360f)
            if (field == clamped) return
            field = clamped
            rebuildShader()
            invalidate()
        }
    var saturation: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }
    var value: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var onColorChanged: ((Float, Float) -> Unit)? = null

    private val palettePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val indicatorStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1f)
        color = Color.BLACK
    }

    private val indicatorRect = RectF()
    private var shaderDirty = true

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildShader()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (shaderDirty) {
            rebuildShader()
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), palettePaint)
        val x = saturation * (width - 1)
        val y = (1f - value) * (height - 1)
        val radius = dpToPx(6f)
        indicatorRect.set(x - radius, y - radius, x + radius, y + radius)
        canvas.drawOval(indicatorRect, indicatorPaint)
        canvas.drawOval(indicatorRect, indicatorStrokePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val x = event.x.coerceIn(0f, (width - 1).toFloat())
                val y = event.y.coerceIn(0f, (height - 1).toFloat())
                val newSaturation = if (width > 1) x / (width - 1).toFloat() else 0f
                val newValue = if (height > 1) 1f - (y / (height - 1).toFloat()) else 0f
                saturation = newSaturation
                value = newValue
                onColorChanged?.invoke(saturation, value)
                return true
            }
            MotionEvent.ACTION_UP -> performClick()
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun rebuildShader() {
        if (width <= 0 || height <= 0) {
            shaderDirty = true
            return
        }
        val hueColor = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
        val saturationShader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            0f,
            Color.WHITE,
            hueColor,
            Shader.TileMode.CLAMP
        )
        val valueShader = LinearGradient(
            0f,
            0f,
            0f,
            height.toFloat(),
            Color.TRANSPARENT,
            Color.BLACK,
            Shader.TileMode.CLAMP
        )
        palettePaint.shader = ComposeShader(saturationShader, valueShader, PorterDuff.Mode.MULTIPLY)
        shaderDirty = false
    }

    private fun dpToPx(dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }
}
