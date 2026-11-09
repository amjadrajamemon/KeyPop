package com.aetherkeys.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.widget.Button

/** Pixel-density independent, first-party glass and keycap rendering for KeyPop. */
object KeyPopGlass {
    private fun alpha(rgb:Int,opacity:Int)=(rgb and 0x00FFFFFF) or (opacity.coerceIn(0,255) shl 24)
    fun blend(from:Int,to:Int,t:Float):Int {
        val x=t.coerceIn(0f,1f)
        fun c(a:Int,b:Int)=(a+(b-a)*x).toInt().coerceIn(0,255)
        return Color.argb(c(Color.alpha(from),Color.alpha(to)),
            c(Color.red(from),Color.red(to)),c(Color.green(from),Color.green(to)),
            c(Color.blue(from),Color.blue(to)))
    }
    private fun shape(colors:IntArray,cornerPx:Float,border:Int):GradientDrawable=
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,colors).apply {
            cornerRadius=cornerPx
            if(Color.alpha(border)>0) setStroke(1.coerceAtLeast((cornerPx/12).toInt()),border)
        }
    /** A single GPU-friendly gradient for EVERY skin. Older builds drew
     * three stacked glass layers on each key, increasing overdraw on devices
     * with smaller GPU budgets. Preserve depth with color stops instead.
     * No white key borders; outlined skins get only a subtle accent stroke. */
    fun key(context:Context,skin:KeyPopSkin,primary:Boolean=false):Drawable {
        val dp=context.resources.displayMetrics.density
        val material=KeyPopSkins.index(context)==10
        // Material keycaps must honor the same corner-size slider as other
        // themes; otherwise the live preview would disagree with the keys.
        val radius=KeyPopSkins.radius(skin,
            context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
                .getInt("round",13)).coerceIn(3f,26f)*dp
        val base=if(primary)skin.accent else skin.key
        val light=blend(base,Color.WHITE,if(material).07f else .10f)
        val dark=blend(base,skin.background,if(material).16f else .20f)
        return GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(light,base,dark)).apply {
            cornerRadius=radius
            if(skin.outline && !material) {
                setStroke(dp.coerceAtLeast(1f).toInt(),alpha(skin.accent,36))
            }
        }
    }
    fun panel(context:Context,skin:KeyPopSkin):Drawable {
        val dp=context.resources.displayMetrics.density
        val material=KeyPopSkins.index(context)==10
        val light=blend(skin.surface,Color.WHITE,if(material).045f else .08f)
        val dark=blend(skin.surface,Color.BLACK,if(material).045f else .10f)
        return GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(light,skin.surface,dark)).apply {
            cornerRadius=(if(material)14f else 18f)*dp
            // Glass is a surface treatment, not a white-bordered card.
            if(!material) setStroke(dp.coerceAtLeast(1f).toInt(),
                alpha(skin.ink,15))
        }
    }
    fun background(context:Context,skin:KeyPopSkin):Drawable {
        val dp=context.resources.displayMetrics.density
        return shape(intArrayOf(skin.background,
            blend(skin.background,skin.surface,.13f),
            blend(skin.background,Color.BLACK,.11f)),0f,Color.TRANSPARENT)
    }
}

/** Single glyph with precisely positioned, independent corner hint. */
@android.annotation.SuppressLint("AppCompatCustomView")
class KeyPopHintKey(context:Context):Button(context) {
    var hintText:String?=null
    var hintColor:Int=Color.WHITE
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        val hint=hintText?.takeIf{it.isNotEmpty()}?:return
        val dp=resources.displayMetrics.density
        p.color=hintColor;p.alpha=220
        p.textSize=9f*resources.displayMetrics.scaledDensity
        p.typeface=android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL)
        p.textAlign=Paint.Align.RIGHT
        canvas.drawText(hint,width-5*dp,12*dp,p)
    }
}

object KeyPopMaterialHints {
    private val symbols=mapOf(
        "q" to listOf("%"),"w" to listOf("\\"),
        "e" to listOf("|"),"r" to listOf("="),
        "t" to listOf("["),"y" to listOf("]"),
        "u" to listOf("<"),"i" to listOf(">"),
        "o" to listOf("{"),"p" to listOf("}"),
        "a" to listOf("@"),"s" to listOf("#"),
        "d" to listOf("$"),"f" to listOf("_"),
        "g" to listOf("&"),"h" to listOf("-"),
        "j" to listOf("+"),"k" to listOf("("),
        "l" to listOf(")"),"z" to listOf("*"),
        "x" to listOf("\""),"c" to listOf("'"),
        "v" to listOf(":"),"b" to listOf(";"),
        "n" to listOf("!"),"m" to listOf("?")
    )
    fun candidates(label:String,language:Int,material:Boolean):List<String> {
        val ordinary=KeyPopAlternates.forKey(label,language)
        return if(material && language==0) (symbols[label].orEmpty()+ordinary).distinct()
            else ordinary
    }
}
