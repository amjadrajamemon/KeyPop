package com.aetherkeys.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.max

/**
 * Lightweight, non-interactive keyboard size preview. Its canvas stays attached
 * while a SeekBar is dragged, so no input/control is detached or recreated.
 * Reads the current preferences on invalidate; no allocations of Android keys.
 */
class KeyPopSizingPreview(context: Context): View(context) {
    private val prefs=context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect=RectF()
    private val letters=listOf("qwertyuiop","asdfghjkl","⇧zxcvbnm⌫")
    private fun dp(value:Float)=value*resources.displayMetrics.density

    init { contentDescription="Live keyboard size preview" }

    fun refresh() { invalidate() }

    override fun onMeasure(widthSpec:Int,heightSpec:Int) {
        val target=dp(170f).toInt()
        val height=resolveSize(target,heightSpec)
        setMeasuredDimension(resolveSize(suggestedMinimumWidth,widthSpec),height)
    }

    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        val skin=KeyPopSkins.active(context)
        val w=width.toFloat()
        val h=height.toFloat()
        if(w<=0f || h<=0f)return
        paint.style=Paint.Style.FILL
        paint.color=KeyPopGlass.blend(skin.surface,skin.background,.30f)
        rect.set(0f,0f,w,h)
        canvas.drawRoundRect(rect,dp(12f),dp(12f),paint)

        val percent=prefs.getInt("keyboardWidthPercent",100).coerceIn(65,100)/100f
        val layoutWidth=(w-dp(14f))*percent
        val left=(w-layoutWidth)/2f
        val topInset=dp(9f)
        val bottomInset=dp(7f)
        val numberRow=prefs.getBoolean("numberRow",true)
        val keyHeight=dp(prefs.getInt("height",51).coerceIn(36,90)*.325f)
        val spacing=dp(prefs.getInt("spacing",4).coerceIn(1,10)*.62f)
        val radius=dp(prefs.getInt("round",13).coerceIn(3,40)*.28f)
        val font=dp((prefs.getInt("font",19).coerceIn(12,32)*.49f).coerceIn(7f,13f))
        val keyset=KeyPopKeysets.selectedId(context)
        val geometry=KeyboardPresetCatalog.selected(context)
        val rows=if(numberRow)listOf("1234567890")+letters else letters
        val footer=KeyPopKeysets.footer(keyset,false)
        val totalHeight=dp(11f)+(rows.size+1)*keyHeight+rows.size*spacing
        // Keep baseline fixed: rising key height increases the preview upward.
        val baseline=h-bottomInset
        val firstY=max(topInset,baseline-totalHeight)
        val actualHeight=((baseline-firstY-dp(11f)-rows.size*spacing)/
            (rows.size+1)).coerceAtLeast(dp(7f))
        val capHeight=actualHeight.coerceAtMost(keyHeight)
        paint.color=skin.accent
        rect.set(left+dp(4f),firstY,left+dp(46f),firstY+dp(3f))
        canvas.drawRoundRect(rect,dp(2f),dp(2f),paint)

        fun key(label:String,x:Float,y:Float,keyWidth:Float,strong:Boolean) {
            if(keyWidth<=1f)return
            paint.color=if(strong)skin.accent else skin.key
            rect.set(x,y,x+keyWidth,y+capHeight)
            val corner=radius.coerceAtMost(capHeight/2f)
            canvas.drawRoundRect(rect,corner,corner,paint)
            paint.color=if(strong)Color.WHITE else skin.ink
            paint.textAlign=Paint.Align.CENTER
            paint.textSize=if(label.length>2)font*.7f else font
            paint.typeface=android.graphics.Typeface.DEFAULT
            canvas.drawText(label,x+keyWidth/2f,y+capHeight*.68f,paint)
        }
        var y=firstY+dp(11f)
        rows.forEachIndexed {rowIndex,row ->
            val side=if(row.length==9)dp(11f) else dp(3f)
            val available=layoutWidth-2*side
            val weights=row.map { KeyboardPresetCatalog.keyWeight(geometry.id,it.toString()) }
            val total=weights.sum()
            val free=(available-spacing*(row.length-1)).coerceAtLeast(1f)
            var x=left+side
            row.forEachIndexed {index,ch->
                val symbol=ch.toString()
                val keyWidth=free*weights[index]/total
                key(symbol,x,y,keyWidth,symbol=="⇧" || symbol=="⌫")
                x+=keyWidth+spacing
            }
            y+=capHeight+spacing
        }
        val weights=footer.map{KeyboardPresetCatalog.keyWeight(geometry.id,it)}
        val space=(layoutWidth-dp(6f)-spacing*(footer.size-1)).coerceAtLeast(5f)
        val sum=weights.sum()
        var x=left+dp(3f)
        footer.forEachIndexed {index,token ->
            val keyWidth=space*weights[index]/sum
            val label=if(token=="SPACE")"English" else token
            key(label,x,y,keyWidth,token=="↵")
            x+=keyWidth+spacing
        }
        paint.textAlign=Paint.Align.LEFT
    }
}
