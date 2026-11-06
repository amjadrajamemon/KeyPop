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
        KeyPopSkin("Gboard-style · Material Teal Glass", "Dark teal high definition keycaps", 0xFF003D39.toInt(), 0xFF0B514A.toInt(), 0xFF29625F.toInt(), 0xFF079E91.toInt(), 0xFFF4FFFD.toInt(), 1),
        KeyPopSkin("SwiftKey-style · Flow Crystal", "Deep blue luminous glass", 0xFF0E1E32.toInt(), 0xFF203E5A.toInt(), 0xFF3E607C.toInt(), 0xFF59C6FF.toInt(), Color.WHITE, 0),
        KeyPopSkin("Samsung-style · One UI Glass", "Structured graphite glass keys", 0xFF151A22.toInt(), 0xFF293646.toInt(), 0xFF475769.toInt(), 0xFF9ED1FE.toInt(), Color.WHITE, 2),
        KeyPopSkin("Speed Type", "Bold oversized", 0xFF101724.toInt(), 0xFF2B3651.toInt(), 0xFF465479.toInt(), 0xFFFE8E69.toInt(), Color.WHITE, 3),
        KeyPopSkin("Airy Comfort", "Spacious daylight", 0xFFE9EEF5.toInt(), 0xFFCBD5E3.toInt(), 0xFFFFFFFF.toInt(), 0xFF6687CB.toInt(), 0xFF23344E.toInt(), 0)
    )
    fun index(context: Context) = context.getSharedPreferences("keypop", Context.MODE_PRIVATE)
        .getInt("theme", 0).coerceIn(0, all.lastIndex)
    fun active(context: Context): KeyPopSkin {
        val prefs=context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
        val original=all[index(context)]
        val themed=if(prefs.contains("customAccent"))
            original.copy(accent=prefs.getInt("customAccent",original.accent)) else original
        if(!prefs.getBoolean("highContrast",false))return themed
        // Contrast mode improves legibility without changing profile identity.
        val bg=themed.background
        val luminance=android.graphics.Color.luminance(bg)
        val ink=if(luminance>0.45)Color.BLACK else Color.WHITE
        return themed.copy(ink=ink,outline=true)
    }
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
        val s = if(selection==KeyPopSkins.index(context)) KeyPopSkins.active(context)
                else KeyPopSkins.all[selection]
        val w = width.toFloat()
        val h = height.toFloat()
        KeyPopBackdropDrawable(selection,s).apply {
            if(selection==KeyPopSkins.index(context) &&
                context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
                    .getBoolean("animatedThemes",false) &&
                android.animation.ValueAnimator.areAnimatorsEnabled()) {
                phase=(android.os.SystemClock.uptimeMillis()%8000L)/8000f
            }
            setBounds(0,0,width,height)
        }.draw(canvas)
        if(selection==KeyPopSkins.index(context) && isShown &&
            context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
                .getBoolean("animatedThemes",false))
            postInvalidateDelayed(100L)
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
                val round=KeyPopSkins.radius(s,context.getSharedPreferences("keypop",Context.MODE_PRIVATE).getInt("round",13))
                canvas.drawRoundRect(rect,dp(round),dp(round),paint)
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


/** Resolution independent illustrated themes. Drawn once when the keyboard is invalidated. */
class KeyPopBackdropDrawable(private val index:Int,private val skin:KeyPopSkin):
    android.graphics.drawable.Drawable() {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    var phase:Float=0f
        set(value){field=value.coerceIn(0f,1f);invalidateSelf()}
    private fun wave():Float=
        0.5f + kotlin.math.sin(phase.toDouble()*6.283185307179586).toFloat()*0.5f
    private fun alpha(color:Int,a:Int)=(color and 0x00FFFFFF) or (a.coerceIn(0,255) shl 24)
    override fun draw(canvas:Canvas) {
        val rect=bounds
        val w=rect.width().toFloat();val h=rect.height().toFloat()
        if(w<=0f||h<=0f)return
        canvas.save()
        canvas.translate(rect.left.toFloat(),rect.top.toFloat())
        p.style=Paint.Style.FILL
        p.shader=LinearGradient(0f,0f,w,h,skin.background,skin.surface,Shader.TileMode.CLAMP)
        canvas.drawRect(0f,0f,w,h,p);p.shader=null
        val accent=skin.accent
        when(index) {
            10 -> {
                p.shader=android.graphics.RadialGradient(w*.5f,0f,h*1.3f,
                    intArrayOf(alpha(0xFF5DE7D3.toInt(),45),alpha(accent,0)),
                    null,Shader.TileMode.CLAMP)
                canvas.drawRect(0f,0f,w,h,p);p.shader=null
                p.style=Paint.Style.STROKE;p.strokeWidth=1.5f
                p.color=alpha(0xFFB8FFF0.toInt(),35)
                canvas.drawLine(0f,1f,w,1f,p)
                p.style=Paint.Style.FILL
            }
            0,9 -> { // Nebula and AMOLED cosmic stars
                for(i in 0..24) {
                    val x=((i*97)%101)/101f*w
                    val y=((i*41+9)%87)/87f*h
                    p.color=alpha(accent,(if(index==9)45 else 70)+(wave()*40).toInt())
                    canvas.drawCircle(x,y,if(i%5==0)3.2f else 1.3f,p)
                }
                p.style=Paint.Style.STROKE;p.strokeWidth=3f
                p.color=alpha(accent,125)
                canvas.drawRoundRect(5f,5f,w-5f,h-5f,20f,20f,p)
                p.style=Paint.Style.FILL
            }
            1,8,14 -> { // translucent frost/paper geometries
                p.color=alpha(accent,18)
                for(i in 0..10) {
                    val x=i*w/10f
                    canvas.drawRoundRect(x-16f,0f,x+14f,h,7f,7f,p)
                }
                p.color=alpha(accent,26)
                canvas.drawCircle(w*.83f,h*.13f,h*.42f,p)
            }
            2,12 -> { // cyber matrix
                p.style=Paint.Style.STROKE;p.strokeWidth=1f
                p.color=alpha(accent,if(index==2)65 else 28)
                val spacing=if(index==2)23f else 15f
                var x=0f
                while(x<w){canvas.drawLine(x,0f,x,h,p);x+=spacing}
                var y=0f
                while(y<h){canvas.drawLine(0f,y,w,y,p);y+=spacing}
                p.style=Paint.Style.FILL
            }
            3,6 -> { // warm glow / orchid bloom
                p.shader=android.graphics.RadialGradient(w*.78f,h*.12f,h*.8f,
                    intArrayOf(alpha(accent,98),alpha(accent,0)),null,Shader.TileMode.CLAMP)
                canvas.drawRect(0f,0f,w,h,p);p.shader=null
                p.style=Paint.Style.STROKE;p.color=alpha(accent,65);p.strokeWidth=3f
                for(i in 0..3) canvas.drawCircle(w*.72f,h*.3f,30f+i*26f,p)
                p.style=Paint.Style.FILL
            }
            4,11 -> { // aurora motion motifs, static and light
                p.style=Paint.Style.STROKE;p.strokeWidth=4f
                for(i in 0..3) {
                    p.color=alpha(accent,20+i*12+(wave()*18).toInt())
                    val path=android.graphics.Path().apply {
                        moveTo(-20f,h*(.23f+i*.16f))
                        cubicTo(w*.32f,h*(.13f+i*.16f),w*.61f,h*(.52f+i*.06f),w+20f,h*(.23f+i*.12f))
                    }
                    canvas.drawPath(path,p)
                }
                p.style=Paint.Style.FILL
            }
            5 -> { // gold precision
                p.style=Paint.Style.STROKE;p.color=alpha(accent,85);p.strokeWidth=2f
                canvas.drawRoundRect(6f,6f,w-6f,h-6f,7f,7f,p)
                canvas.drawLine(w*.78f,5f,w*.95f,h*.22f,p)
                canvas.drawLine(w*.05f,h*.8f,w*.22f,h-5f,p)
                p.style=Paint.Style.FILL
            }
            7 -> { // retro pixels
                p.color=alpha(accent,55)
                for(i in 0..12) for(j in 0..7) {
                    if((i*3+j*7)%5==0) {
                        val x=i*w/13f;val y=j*h/8f
                        canvas.drawRect(x,y,x+7f,y+7f,p)
                    }
                }
            }
            13 -> { // speed lines
                p.style=Paint.Style.STROKE;p.strokeWidth=3.5f
                p.color=alpha(accent,52)
                for(i in 0..8) {
                    val x=i*w/8f
                    canvas.drawLine(x,-5f,x+w*.13f,h+5f,p)
                }
                p.style=Paint.Style.FILL
            }
            else -> {}
        }
        canvas.restore()
    }
    override fun setAlpha(alpha:Int){p.alpha=alpha;invalidateSelf()}
    override fun setColorFilter(filter:android.graphics.ColorFilter?){p.colorFilter=filter;invalidateSelf()}
    @Deprecated("Deprecated in Java")
    override fun getOpacity():Int=android.graphics.PixelFormat.TRANSLUCENT
}
