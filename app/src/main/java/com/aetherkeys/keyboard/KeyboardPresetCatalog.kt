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
        KeyboardErgonomics(1,"Material Familiar","Compact shortcut bar, rounded keys and balanced spacing",
            "Gboard-inspired",10,49,4,16,17,false,true),
        KeyboardErgonomics(2,"Swift Flow","Large rounded keys, roomy spacebar and suggestions",
            "Microsoft SwiftKey-inspired",11,56,3,19,18,false,true),
        KeyboardErgonomics(3,"One UI Grid","Integrated number row and disciplined square key grid",
            "Samsung Keyboard-inspired",12,52,3,7,17,true,true),
        KeyboardErgonomics(4,"Gesture Minimal","Larger letter targets and simplified bottom row",
            "Fleksy-inspired",13,61,5,11,19,false,false),
        KeyboardErgonomics(5,"Faceted Comfort","Staggered offset keys with an original angular appearance",
            "Typewise-inspired",14,58,5,5,18,false,true)
    )
    fun selected(context:Context):KeyboardErgonomics {
        val id=context.getSharedPreferences("keypop",Context.MODE_PRIVATE).getInt("keyboardPreset",0)
        return all.firstOrNull{it.id==id} ?: all.first()
    }
    fun byId(id:Int)=all.firstOrNull{it.id==id}
    /** Called only after an explicit Apply action, never from preview or menu navigation. */
    fun apply(context:Context,id:Int):Boolean {
        val preset=byId(id) ?:return false
        val editor=context.getSharedPreferences("keypop",Context.MODE_PRIVATE).edit()
            .putInt("keyboardPreset",id)
        if(id!=CUSTOM) {
            editor.putInt("theme",preset.skin).putInt("height",preset.height)
                .putInt("spacing",preset.spacing).putInt("round",preset.radius)
                .putInt("font",preset.font).putBoolean("numberRow",preset.numberRow)
                .putBoolean("suggestions",preset.suggestions)
                .putInt("handMode",0).putBoolean("floating",false)
                .putBoolean("splitKeyboard",false).remove("customAccent")
        }
        return editor.commit()
    }
    fun foot(id:Int, symbols:Boolean):List<String> {
        if(symbols)return listOf("ABC","🌐",",","SPACE",".","↵","⌫")
        return when(id){
            GESTURE ->listOf("?123","🌐","SPACE","↵","⌫")
            FACETED ->listOf("?123","🌐",",","SPACE","↵","⌫")
            ONE_UI ->listOf("?123",",","🌐","SPACE",".","↵","⌫")
            else ->listOf("?123","🌐",",","SPACE",".","↵","⌫")
        }
    }
    fun keyWeight(id:Int,label:String):Float = when(label){
        "SPACE" -> when(id) { FLOW->5.4f;GESTURE->5.8f;FACETED->5f;else->4.3f }
        "?123","ABC" -> if(id==GESTURE)1.1f else 1.4f
        "🌐" ->1.2f
        else ->if(id==GESTURE)1.12f else 1f
    }
}

/** Offline lightweight preview showing real rows, geometry and number-row differences. */
class KeyboardPresetPreview(context:Context,private val candidate:KeyboardErgonomics):View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private fun dp(v:Float)=v*resources.displayMetrics.density
    override fun onMeasure(w:Int,h:Int) {
        setMeasuredDimension(MeasureSpec.getSize(w),dp(140f).toInt())
    }
    override fun onDraw(canvas:Canvas) {
        val skin=KeyPopSkins.all[candidate.skin]
        val width=width.toFloat();val height=height.toFloat()
        paint.color=skin.background
        canvas.drawRoundRect(0f,0f,width,height,dp(12f),dp(12f),paint)
        paint.color=skin.accent;paint.textSize=dp(11f);paint.isFakeBoldText=true
        canvas.drawText("KEYPOP   •   "+candidate.name.uppercase(),dp(10f),dp(16f),paint)
        paint.isFakeBoldText=false
        val rows=mutableListOf("QWERTYUIOP","ASDFGHJKL","ZXCVBNM")
        if(candidate.numberRow)rows.add(0,"1234567890")
        val upper=dp(23f)
        val total=height-upper-dp(9f)
        val gap=dp(3f)
        val rowHeight=(total-gap*(rows.size-1))/rows.size
        rows.forEachIndexed{rowIndex,keys->
            val y=upper+rowIndex*(rowHeight+gap)
            val stagger=when {
                candidate.id==KeyboardPresetCatalog.FACETED && rowIndex%2==1 ->dp(12f)
                keys.length==9 ->dp(14f)
                keys.length==7 ->dp(28f)
                else->dp(7f)
            }
            val usable=width-2*stagger
            val kw=(usable-gap*(keys.length-1))/keys.length
            keys.forEachIndexed{index,ch->
                val x=stagger+index*(kw+gap)
                paint.color=skin.key
                val corner=dp(if(candidate.id==KeyboardPresetCatalog.FACETED)4f else
                    candidate.radius.coerceAtMost(19).toFloat()/1.8f)
                canvas.drawRoundRect(x,y,x+kw,y+rowHeight,corner,corner,paint)
                paint.color=skin.ink
                paint.textSize=dp(10f);paint.textAlign=Paint.Align.CENTER
                canvas.drawText(ch.toString(),x+kw/2f,y+rowHeight*.68f,paint)
            }
        }
        paint.textAlign=Paint.Align.LEFT
    }
}
