package com.aetherkeys.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import kotlin.math.min

data class KeyPopSkin(
    val name: String, val subtitle: String,
    val background: Int, val surface: Int, val key: Int, val accent: Int,
    val ink: Int, val shape: Int, val outline: Boolean = false
)

object KeyPopSkins {
    // Ten original styles and five familiar-layout-inspired styling profiles.
    // Inspired styles are not copies or endorsements of third party products.
    val all = listOf(
        KeyPopSkin("Nebula Prism", "Holographic", 0xFF080D26.toInt(), 0xFF201C53.toInt(), 0xFF37317A.toInt(), 0xFFB869FF.toInt(), Color.WHITE, 0),
        KeyPopSkin("Arctic Glass", "Frosted light", 0xFFEAF5FF.toInt(), 0xFFC7E2EF.toInt(), 0xFFFFFFFF.toInt(), 0xFF1688B7.toInt(), 0xFF15354B.toInt(), 1),
        KeyPopSkin("Cyber Pulse", "Electric grid", 0xFF05141A.toInt(), 0xFF053D46.toInt(), 0xFF0A3039.toInt(), 0xFF2EF9DD.toInt(), Color.WHITE, 2, true),
        KeyPopSkin("Sunset Ember", "Warm glow", 0xFF301023.toInt(), 0xFF69213C.toInt(), 0xFF8C3458.toInt(), 0xFFFFAC52.toInt(), Color.WHITE, 3),
        KeyPopSkin("Aurora Wave", "Northern lights", 0xFF071A27.toInt(), 0xFF16394D.toInt(), 0xFF14545E.toInt(), 0xFF68F0BB.toInt(), Color.WHITE, 0),
        KeyPopSkin("Royal Obsidian", "Gold precision", 0xFF111216.toInt(), 0xFF303039.toInt(), 0xFF202027.toInt(), 0xFFF0C47C.toInt(), 0xFFFFF5D9.toInt(), 2, true),
        KeyPopSkin("Orchid Bloom", "Soft lavender", 0xFF25162C.toInt(), 0xFF594165.toInt(), 0xFF674573.toInt(), 0xFFFF9BD7.toInt(), Color.WHITE, 1),
        KeyPopSkin("Retro Pixel", "Arcade classic", 0xFF161D30.toInt(), 0xFF1E3654.toInt(), 0xFF30536C.toInt(), 0xFFF3DA62.toInt(), 0xFFFFFFFF.toInt(), 4, true),
        KeyPopSkin("Zen Paper", "Quiet minimal", 0xFFF1F1ED.toInt(), 0xFFE5E7E3.toInt(), 0xFFFFFFFF.toInt(), 0xFF3B6F6A.toInt(), 0xFF263534.toInt(), 1),
        KeyPopSkin("AMOLED Infinity", "Pure black", Color.BLACK, 0xFF11111A.toInt(), 0xFF20202D.toInt(), 0xFF9F86FF.toInt(), Color.WHITE, 2, true),
        KeyPopSkin("Classic Compact", "Familiar minimal", 0xFFDBE0E8.toInt(), 0xFFDBE0E8.toInt(), 0xFFFFFFFF.toInt(), 0xFF377AEF.toInt(), 0xFF1B2535.toInt(), 1),
        KeyPopSkin("Flow Rounded", "Soft fluid keys", 0xFF17212C.toInt(), 0xFF263646.toInt(), 0xFF394C5C.toInt(), 0xFF76BDFF.toInt(), Color.WHITE, 0),
        KeyPopSkin("Galaxy Precision", "Clean geometry", 0xFF20242B.toInt(), 0xFF343943.toInt(), 0xFF4D525C.toInt(), 0xFF84C7FF.toInt(), Color.WHITE, 2),
        KeyPopSkin("Speed Type", "Bold oversized", 0xFF101724.toInt(), 0xFF2B3651.toInt(), 0xFF465479.toInt(), 0xFFFE8E69.toInt(), Color.WHITE, 3),
        KeyPopSkin("Airy Comfort", "Spacious daylight", 0xFFE9EEF5.toInt(), 0xFFCBD5E3.toInt(), 0xFFFFFFFF.toInt(), 0xFF6687CB.toInt(), 0xFF23344E.toInt(), 0)
    )
    fun index(context: Context) = context.getSharedPreferences("keypop", Context.MODE_PRIVATE)
        .getInt("theme", 0).coerceIn(0, all.lastIndex)
    fun active(context: Context) = all[index(context)]
    fun radius(skin: KeyPopSkin, user: Int): Float = when (skin.shape) {
        0 -> user.toFloat() * 1.35f
        1 -> user.toFloat()
        2 -> min(6, user).toFloat()
        3 -> min(10, user).toFloat()
        else -> 1f
    }
}

class KeyboardPreview(context: Context, initial: Int = 0): View(context) {
    var selection: Int = initial
        set(value) { field = value.coerceIn(0, KeyPopSkins.all.lastIndex); invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun dp(n: Float) = n * resources.displayMetrics.density
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, dp(136f).toInt())
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = KeyPopSkins.all[selection]
        val w = width.toFloat()
        val h = height.toFloat()
        paint.shader = LinearGradient(0f, 0f, w, h, s.background, s.surface, Shader.TileMode.CLAMP)
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(0f, 0f, w, h), dp(16f), dp(16f), paint)
        paint.shader = null
        val topY = dp(10f)
        paint.color = s.accent
        paint.textSize = dp(10f)
        paint.isFakeBoldText = true
        canvas.drawText("✦  " + s.name.uppercase(), dp(12f), topY + dp(9f), paint)
        paint.isFakeBoldText = false
        val rows = listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")
        val top = dp(28f)
        val gap = dp(3f)
        val rowHeight = (h - top - dp(9f)) / 3f
        for (i in rows.indices) {
            val row = rows[i]
            val side = if (i == 0) dp(9f) else if (i == 1) dp(22f) else dp(39f)
            val widthAvailable = w - side * 2
            val kw = (widthAvailable - gap * (row.length - 1)) / row.length
            for (j in row.indices) {
                val x = side + j * (kw + gap)
                val y = top + i * rowHeight
                val rect = RectF(x, y, x + kw, y + rowHeight - gap)
                paint.color = s.key
                paint.style = Paint.Style.FILL
                canvas.drawRoundRect(rect, dp(KeyPopSkins.radius(s, 9)), dp(KeyPopSkins.radius(s, 9)), paint)
                if (s.outline) {
                    paint.color = s.accent
                    paint.strokeWidth = dp(0.7f)
                    paint.style = Paint.Style.STROKE
                    canvas.drawRoundRect(rect, dp(5f), dp(5f), paint)
                    paint.style = Paint.Style.FILL
                }
                paint.color = s.ink
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = dp(10f)
                canvas.drawText(row[j].toString(), x + kw / 2, y + (rowHeight - gap) * .64f, paint)
                paint.textAlign = Paint.Align.LEFT
            }
        }
    }
}
