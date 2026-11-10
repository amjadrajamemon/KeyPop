package com.aetherkeys.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** Original implementations of familiar mobile keyboard ergonomics. No third-party UI assets. */
data class KeyboardErgonomics(
    val id:Int,val name:String,val description:String,val reference:String,
    val skin:Int,val height:Int,val spacing:Int,val radius:Int,val font:Int,
    val numberRow:Boolean,val suggestions:Boolean
)
object KeyboardPresetCatalog {
    const val CUSTOM=0
    const val MATERIAL=1
    const val FLOW=2
    const val ONE_UI=3
    const val GESTURE=4
    const val FACETED=5
    val all=listOf(
        KeyboardErgonomics(0,"KeyPop Custom","Keep your own settings and design","Original KeyPop",
            0,51,4,13,17,true,true),
        KeyboardErgonomics(1,"Gboard-style · Material Teal Glass","Compact seven-action toolbar, hints, numbers and dark-teal geometry",
            "Gboard screenshot-inspired, original KeyPop design",10,42,3,11,19,true,true),
        KeyboardErgonomics(2,"SwiftKey-style · Flow","Roomier punctuation keys, round edges and suggestion-first layout",
            "Microsoft SwiftKey-inspired",11,56,3,19,18,false,true),
        KeyboardErgonomics(3,"Samsung-style · One UI","Structured key grid, dedicated number row and utility row",
            "Samsung Keyboard-inspired",12,52,3,7,17,true,true),
        KeyboardErgonomics(4,"Gesture Minimal","Larger letter targets and simplified bottom row",
            "Fleksy-inspired",13,61,5,11,19,false,true),
        KeyboardErgonomics(5,"Faceted Comfort","Staggered offset keys with an original angular appearance",
            "Typewise-inspired",14,58,5,5,18,false,true)
    )
    fun selected(context:Context):KeyboardErgonomics {
        val id=context.getSharedPreferences("keypop",Context.MODE_PRIVATE).getInt("keyboardPreset",0)
        return all.firstOrNull{it.id==id} ?: all.first()
    }
    fun byId(id:Int)=all.firstOrNull{it.id==id}
    /** Geometry selection must not replace theme, key characters, long-press
     * symbols, number row, font preferences, or the user's custom accent.
     * Freeze the old coupled preset's keyset once for existing installs. */
    fun apply(context:Context,id:Int):Boolean {
        val preset=byId(id)?:return false
        val prefs=context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
        val old=prefs.getInt("keyboardPreset",MATERIAL)
        val edit=prefs.edit()
        if(!prefs.contains(KeyPopKeysets.KEY))
            edit.putInt(KeyPopKeysets.KEY,KeyPopKeysets.legacy(old))
        edit.putInt("keyboardPreset",id)
        if(id!=CUSTOM) {
            // Geometry owns key height and spacing, but NEVER skin colors,
            // font, symbol mapping, number row, or accessibility settings.
            edit.putInt("height",preset.height).putInt("spacing",preset.spacing)
        }
        return edit.commit()
    }
    fun foot(id:Int, symbols:Boolean):List<String> {
        // Backspace belongs to the last typing row, never a second footer key.
        // Punctuation/symbols are available on hold of the period key.
        if(symbols)return listOf("ABC","🌐",",","SPACE",".","↵")
        if(id==MATERIAL || id==CUSTOM)
            return listOf("?123",",","☺","🌐","SPACE",".","↵")
        return listOf("?123",",","🌐","SPACE",".","↵")
    }
    /** Wider default targets for frequently used editing / punctuation keys.
     * Widths remain proportional within each row; the keyboard never widens
     * past the available screen. This affects all geometry + keyset combinations
     * without overwriting a user's individual letter-key widths. */
    fun keyWeight(id:Int,label:String):Float = when(label){
        "SPACE" -> when(id) { MATERIAL->4.43f;FLOW->5.4f;GESTURE->5.8f;FACETED->5f;else->4.3f }
        "?123","ABC" -> if(id==GESTURE)1.1f else if(id==MATERIAL)1.57f else 1.4f
        "," -> if(id==MATERIAL)1.85f else 1.85f
        "☺" -> if(id==MATERIAL)0.85f else 1.0f
        "☺," -> if(id==MATERIAL)0.85f else 1.12f
        "⇧","⌫" -> if(id==MATERIAL)2.55f else 2.40f
        "↵" ->if(id==MATERIAL)2.35f else 2.20f
        "." ->if(id==MATERIAL)1.85f else 1.85f
        "?" ->if(id==MATERIAL)0.7f else 1f
        "🌐" ->if(id==MATERIAL)1f else 1.2f
        else ->if(id==GESTURE)1.12f else 1f
    }

    /** Give the five frequently used controls visibly taller keycaps too,
     * without overlapping adjacent rows or altering the surrounding letters.
     * The touch target and the painted key background both grow. */
    fun controlHeightBonus(token:String):Int =
        if(token in setOf("⇧","⌫","↵",",","."))8 else 0

    /** Typography follows the wider touch targets but still respects the
     * selected font size; an action caption such as "⇄ Send" stays readable. */
    fun keyTextSize(token:String,caption:String,base:Float):Float = when {
        token in setOf("⇧","⌫","↵",",",".") && caption.length<=2 ->
            (base*1.28f).coerceIn(16f,32f)
        caption.length>3 -> (base*.70f).coerceIn(12f,24f)
        else -> base.coerceIn(12f,32f)
    }
}

/** Offline lightweight preview showing real rows, geometry and number-row differences. */
class KeyboardPresetPreview(
    context:Context,
    private val candidate:KeyboardErgonomics,
    private val previewKeyset:Int?=null
):View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private fun dp(v:Float)=v*resources.displayMetrics.density
    override fun onMeasure(w:Int,h:Int) {
        setMeasuredDimension(MeasureSpec.getSize(w),dp(154f).toInt())
    }
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        val skin=KeyPopSkins.active(context)
        val keyset=previewKeyset ?:KeyPopKeysets.selectedId(context)
        val w=width.toFloat()
        val h=height.toFloat()
        val material=keyset==KeyPopKeysets.GBOARD
        paint.shader=android.graphics.LinearGradient(0f,0f,w,h,
            KeyPopGlass.blend(skin.background,Color.WHITE,0.10f),
            KeyPopGlass.blend(skin.background,Color.BLACK,0.20f),
            android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRoundRect(0f,0f,w,h,dp(10f),dp(10f),paint)
        paint.shader=null
        val toolbarHeight=if(material)dp(24f) else dp(7f)
        if(material){
            val bar=listOf("▦","▤","📷","⚙","⇄","◈","🎙")
            val cell=w/bar.size
            bar.forEachIndexed { i,label->
                paint.color=skin.ink
                paint.textSize=dp(12f)
                paint.typeface=android.graphics.Typeface.DEFAULT_BOLD
                paint.textAlign=Paint.Align.CENTER
                canvas.drawText(label,(i+.5f)*cell,dp(15f),paint)
            }
        }
        val footerHeight=dp(28f)
        val rows=mutableListOf("QWERTYUIOP","ASDFGHJKL","ZXCVBNM")
        val numberRow=context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
            .getBoolean("numberRow",true)
        if(numberRow)rows.add(0,"1234567890")
        val gap=dp((candidate.spacing*0.7f).coerceIn(2f,4f))
        val available=h-toolbarHeight-footerHeight-dp(7f)-gap*(rows.size-1)
        val rowHeight=available/rows.size
        fun keyCap(label:String,x:Float,y:Float,kw:Float,kh:Float,primary:Boolean,cornerHint:String?){
            val color=if(primary)skin.accent else skin.key
            val radius=dp((if(material)6 else candidate.radius.coerceIn(5,18)/1.7f).toFloat())
            val top=KeyPopGlass.blend(color,Color.WHITE,0.14f)
            val lower=KeyPopGlass.blend(color,Color.BLACK,0.12f)
            paint.shader=android.graphics.LinearGradient(x,y,x,y+kh,top,lower,
                android.graphics.Shader.TileMode.CLAMP)
            canvas.drawRoundRect(x,y,x+kw,y+kh,radius,radius,paint)
            paint.shader=null
            paint.textAlign=Paint.Align.CENTER
            paint.typeface=android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL)
            paint.color=if(primary)Color.WHITE else skin.ink
            paint.textSize=dp(if(label.length>=5)7.8f else 10f)
            canvas.drawText(label,x+kw/2f,y+kh*.65f,paint)
            if(cornerHint!=null){
                paint.textSize=dp(5.7f)
                paint.textAlign=Paint.Align.RIGHT
                paint.color=skin.ink
                canvas.drawText(cornerHint,x+kw-dp(2f),y+dp(6f),paint)
            }
        }
        rows.forEachIndexed { rowIndex,keys->
            val visual=if(rowIndex==rows.lastIndex)"⇧"+keys+"⌫" else keys
            val y=toolbarHeight+rowIndex*(rowHeight+gap)
            val side=when{
                candidate.id==KeyboardPresetCatalog.FACETED && rowIndex%2==1->dp(13f)
                keys.length==9->dp(16f)
                else->dp(4.5f)
            }
            val weights=visual.map{ KeyboardPresetCatalog.keyWeight(candidate.id,it.toString()) }
            val sum=weights.sum()
            val free=(w-2*side-gap*(visual.length-1)).coerceAtLeast(1f)
            var cursor=side
            visual.forEachIndexed { i,ch->
                val key=ch.toString()
                val kw=free*weights[i]/sum
                val hint=if(ch.isLetter())
                    KeyPopRowParity.alternates(
                        KeyPopKeysets.alternates(keyset,key.lowercase(),0),
                        key,numberRow).firstOrNull() else null
                keyCap(key,cursor,y,kw,rowHeight,key in listOf("⇧","⌫"),hint)
                cursor+=kw+gap
            }
        }
        val footer=KeyPopKeysets.footer(keyset,false)
        val weights=footer.map{KeyboardPresetCatalog.keyWeight(candidate.id,it)}
        val total=weights.sum()
        val free=w-dp(8f)-gap*(footer.size-1)
        var cursor=dp(4f)
        val y=h-footerHeight-dp(3f)
        footer.forEachIndexed { i,label->
            val kw=(free*weights[i]/total)
            val show=when(label){"SPACE"->"English";"☺,"->"☺";"↵"->"↵";else->label}
            keyCap(show,cursor,y,kw,footerHeight-dp(3f),label=="↵",null)
            cursor+=kw+gap
        }
        paint.shader=null
        paint.textAlign=Paint.Align.LEFT
    }
}
