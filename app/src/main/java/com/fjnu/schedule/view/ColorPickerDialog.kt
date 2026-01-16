package com.fjnu.schedule.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import kotlin.math.roundToInt

object ColorPickerDialog {
    fun show(context: Context, initialColor: Int, onSelected: (Int) -> Unit) {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = dpToPx(context, 16)
            setPadding(padding, padding, padding, padding)
        }

        val preview = View(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(context, 40)
            )
        }
        val hexLabel = TextView(context)
        container.addView(preview)
        container.addView(hexLabel)

        val hsv = FloatArray(3)
        Color.colorToHSV(initialColor, hsv)
        val initialAlpha = Color.alpha(initialColor)

        val hueLabel = TextView(context)
        val satLabel = TextView(context)
        val valLabel = TextView(context)
        val alphaLabel = TextView(context)
        val hueSlider = Slider(context).apply {
            valueFrom = 0f
            valueTo = 360f
            value = hsv[0].roundToInt().toFloat()
            stepSize = 1f
        }
        val satSlider = Slider(context).apply {
            valueFrom = 0f
            valueTo = 100f
            value = (hsv[1] * 100f).roundToInt().toFloat()
            stepSize = 1f
        }
        val valSlider = Slider(context).apply {
            valueFrom = 0f
            valueTo = 100f
            value = (hsv[2] * 100f).roundToInt().toFloat()
            stepSize = 1f
        }
        val alphaSlider = Slider(context).apply {
            valueFrom = 0f
            valueTo = 255f
            value = initialAlpha.toFloat().roundToInt().toFloat()
            stepSize = 1f
        }

        fun updatePreview() {
            val hue = hueSlider.value
            val sat = satSlider.value / 100f
            val value = valSlider.value / 100f
            val alpha = alphaSlider.value.toInt()
            val color = Color.HSVToColor(alpha, floatArrayOf(hue, sat, value))
            preview.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(context, 6).toFloat()
                setColor(color)
            }
            hexLabel.text = String.format("Current: #%08X", color)
            hueLabel.text = "Hue: ${hueSlider.value.toInt()}"
            satLabel.text = "Saturation: ${satSlider.value.toInt()}%"
            valLabel.text = "Value: ${valSlider.value.toInt()}%"
            alphaLabel.text = "Alpha: ${alphaSlider.value.toInt()}"
        }

        container.addView(hueLabel)
        container.addView(hueSlider)
        container.addView(satLabel)
        container.addView(satSlider)
        container.addView(valLabel)
        container.addView(valSlider)
        container.addView(alphaLabel)
        container.addView(alphaSlider)

        hueSlider.addOnChangeListener { _, _, _ -> updatePreview() }
        satSlider.addOnChangeListener { _, _, _ -> updatePreview() }
        valSlider.addOnChangeListener { _, _, _ -> updatePreview() }
        alphaSlider.addOnChangeListener { _, _, _ -> updatePreview() }
        updatePreview()

        MaterialAlertDialogBuilder(context)
            .setTitle("Color Picker")
            .setView(container)
            .setPositiveButton("OK") { _, _ ->
                val color = Color.HSVToColor(
                    alphaSlider.value.toInt(),
                    floatArrayOf(
                        hueSlider.value,
                        satSlider.value / 100f,
                        valSlider.value / 100f
                    )
                )
                onSelected(color)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density + 0.5f).toInt()
    }
}
