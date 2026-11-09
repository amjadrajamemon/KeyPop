package com.aetherkeys.keyboard

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Lightweight, consistent 24dp line-art icons for the IME toolbar.
 * No emoji fonts, bitmap decoding, text layout or animation per frame.
 * A separate ImageButton provides a large accessible touch target.
 */
class KeyPopToolbarGlyph(
    private val kind:Kind,
    private val tint:Int
):Drawable() {
    enum class Kind {
        GRID, CLIPBOARD, SCREENSHOT, SETTINGS, TRANSLATE,
        THEMES, MICROPHONE, RESIZE, EMOJI, CHECK, PASTE
    }

    private val line=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color=tint
        style=Paint.Style.STROKE
        strokeWidth=1.85f
        strokeCap=Paint.Cap.ROUND
        strokeJoin=Paint.Join.ROUND
    }
    private val solid=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color=tint
        style=Paint.Style.FILL
    }
    private fun segment(canvas:Canvas,x:Float,y:Float,a:Float,b:Float) =
        canvas.drawLine(x,y,a,b,line)

    override fun draw(canvas:Canvas) {
        if(bounds.isEmpty)return
        val checkpoint=canvas.save()
        canvas.translate(bounds.left.toFloat(),bounds.top.toFloat())
        canvas.scale(bounds.width()/24f,bounds.height()/24f)
        when(kind) {
            Kind.GRID -> {
                for(x in listOf(3f,13f))for(y in listOf(3f,13f))
                    canvas.drawRoundRect(x,y,x+8f,y+8f,1.5f,1.5f,line)
            }
            Kind.CLIPBOARD,Kind.PASTE -> {
                canvas.drawRoundRect(5f,5f,19f,21f,2f,2f,line)
                canvas.drawRoundRect(9f,3f,15f,7f,1.3f,1.3f,line)
                if(kind==Kind.PASTE) {
                    segment(canvas,9f,12f,15f,12f)
                    segment(canvas,9f,16f,13f,16f)
                } else {
                    segment(canvas,9f,12f,15f,12f)
                    segment(canvas,9f,16f,15f,16f)
                }
            }
            Kind.SCREENSHOT -> {
                canvas.drawRoundRect(3f,6f,21f,19f,2f,2f,line)
                segment(canvas,8f,6f,9f,4f)
                segment(canvas,9f,4f,15f,4f)
                segment(canvas,15f,4f,16f,6f)
                canvas.drawCircle(12f,12.5f,3.3f,line)
            }
            Kind.SETTINGS -> {
                canvas.drawCircle(12f,12f,3.3f,line)
                canvas.drawCircle(12f,12f,7.3f,line)
                for(i in 0 until 8) {
                    val angle=i*PI/4.0
                    val ax=(12+7.5*cos(angle)).toFloat()
                    val ay=(12+7.5*sin(angle)).toFloat()
                    val bx=(12+9.7*cos(angle)).toFloat()
                    val by=(12+9.7*sin(angle)).toFloat()
                    segment(canvas,ax,ay,bx,by)
                }
            }
            Kind.TRANSLATE -> {
                solid.typeface=Typeface.create("sans-serif-medium",Typeface.BOLD)
                solid.textSize=12f
                canvas.drawText("A",3.4f,11.5f,solid)
                solid.textSize=12f
                canvas.drawText("ع",14f,20f,solid)
                segment(canvas,12f,5f,21f,5f)
                segment(canvas,18f,2.5f,21f,5f)
                segment(canvas,21f,5f,18f,7.5f)
                segment(canvas,12f,19f,3f,19f)
                segment(canvas,6f,16.5f,3f,19f)
                segment(canvas,3f,19f,6f,21.5f)
            }
            Kind.THEMES -> {
                canvas.drawCircle(12f,12f,9f,line)
                for((x,y) in listOf(8f to 8f,14.6f to 7.2f,
                    18f to 12f,8f to 16f))canvas.drawCircle(x,y,1.35f,solid)
                canvas.drawCircle(13.3f,15.7f,2.8f,line)
            }
            Kind.MICROPHONE -> {
                canvas.drawRoundRect(9f,3f,15f,15f,3f,3f,line)
                canvas.drawArc(6f,7f,18f,20f,0f,180f,false,line)
                segment(canvas,12f,20f,12f,22f)
                segment(canvas,9f,22f,15f,22f)
            }
            Kind.RESIZE -> {
                segment(canvas,12f,3f,12f,21f)
                segment(canvas,8.5f,6.5f,12f,3f)
                segment(canvas,15.5f,6.5f,12f,3f)
                segment(canvas,8.5f,17.5f,12f,21f)
                segment(canvas,15.5f,17.5f,12f,21f)
                segment(canvas,5f,12f,19f,12f)
            }
            Kind.EMOJI -> {
                canvas.drawCircle(12f,12f,9f,line)
                canvas.drawCircle(9f,10f,1f,solid)
                canvas.drawCircle(15f,10f,1f,solid)
                canvas.drawArc(7f,8f,17f,18f,15f,150f,false,line)
            }
            Kind.CHECK -> {
                segment(canvas,4f,12f,10f,18f)
                segment(canvas,10f,18f,20f,6f)
            }
        }
        canvas.restoreToCount(checkpoint)
    }
    override fun setAlpha(alpha:Int) {
        line.alpha=alpha
        solid.alpha=alpha
        invalidateSelf()
    }
    override fun setColorFilter(colorFilter:ColorFilter?) {
        line.colorFilter=colorFilter
        solid.colorFilter=colorFilter
        invalidateSelf()
    }
    @Deprecated("Deprecated in Java")
    override fun getOpacity():Int=PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth():Int=24
    override fun getIntrinsicHeight():Int=24
}
