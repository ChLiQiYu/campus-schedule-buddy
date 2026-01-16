package com.fjnu.schedule.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import com.fjnu.schedule.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object ColorPickerDialog {
    fun show(context: Context, initialColor: Int, onSelected: (Int) -> Unit) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_color_picker, null)
        val paletteView = view.findViewById<HsvPaletteView>(R.id.view_hsv_palette)
        val hueView = view.findViewById<HueBarView>(R.id.view_hue_bar)
        val preview = view.findViewById<View>(R.id.view_color_preview)
        val hexInput = view.findViewById<EditText>(R.id.et_hex_color)

        val startColor = Color.RED
        val hsv = FloatArray(3)
        Color.colorToHSV(startColor, hsv)
        paletteView.hue = hsv[0]
        paletteView.saturation = hsv[1]
        paletteView.value = hsv[2]
        hueView.hue = hsv[0]

        fun updateColor(color: Int, updateInput: Boolean) {
            preview.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(color)
            }
            if (updateInput) {
                hexInput.setText(String.format("#%06X", 0xFFFFFF and color))
            }
        }

        fun currentColor(): Int {
            return Color.HSVToColor(floatArrayOf(paletteView.hue, paletteView.saturation, paletteView.value))
        }

        updateColor(currentColor(), updateInput = true)

        paletteView.onColorChanged = { _, _ ->
            updateColor(currentColor(), updateInput = true)
        }

        hueView.onHueChanged = { hue ->
            paletteView.hue = hue
            updateColor(currentColor(), updateInput = true)
        }

        var updating = false
        hexInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (updating) return
                val text = s?.toString()?.trim().orEmpty()
                val parsed = parseHexColor(text) ?: return
                val hsvUpdate = FloatArray(3)
                Color.colorToHSV(parsed, hsvUpdate)
                updating = true
                paletteView.hue = hsvUpdate[0]
                paletteView.saturation = hsvUpdate[1]
                paletteView.value = hsvUpdate[2]
                hueView.hue = hsvUpdate[0]
                updateColor(parsed, updateInput = false)
                updating = false
            }
        })

        MaterialAlertDialogBuilder(context)
            .setTitle("选择颜色")
            .setView(view)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                onSelected(currentColor())
            }
            .show()
    }

    private fun parseHexColor(input: String): Int? {
        val text = input.trim()
        if (!text.startsWith("#")) return null
        return try {
            Color.parseColor(text)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
