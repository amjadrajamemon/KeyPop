package com.aetherkeys.keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.RadioButton

/**
 * Explicit high-contrast selection rows inside Android AlertDialogs.
 * A system light dialog must never render white option labels on white.
 */
object KeyPopOptionStyle {
    val SURFACE:Int = 0xFF172D3C.toInt()
    val EDGE:Int = 0xFF35586B.toInt()
    val LABEL:Int = Color.WHITE
    val ACCENT:Int = 0xFF79E9D6.toInt()

    fun decorate(context:Context,radio:RadioButton) {
        val density=context.resources.displayMetrics.density
        fun px(value:Int)=(value*density).toInt()
        radio.setTextColor(LABEL)
        radio.buttonTintList=ColorStateList.valueOf(ACCENT)
        radio.background=GradientDrawable().apply {
            setColor(SURFACE)
            cornerRadius=px(10).toFloat()
            setStroke(px(1).coerceAtLeast(1),EDGE)
        }
        radio.setPadding(px(12),px(5),px(10),px(5))
    }
    fun contrastRatio():Double {
        val lighter=Color.luminance(LABEL)
        val darker=Color.luminance(SURFACE)
        return (lighter+.05)/(darker+.05)
    }
}
