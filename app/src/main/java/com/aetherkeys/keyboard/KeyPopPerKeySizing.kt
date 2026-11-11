package com.aetherkeys.keyboard

import android.content.Context

/**
 * Persistent, language-aware per-key dimensions for all visible character keys,
 * Shift, Backspace, Space, Enter, comma, period and the number row.
 * A coordinate identifies a physical key in a rendered row, not the current
 * letter's value, so a swapped/remapped key does not lose its edited geometry.
 *
 * Rows 0..2: alphabet, row 3: footer, row 4: optional number row.
 * Height is an additive delta in dp; width is a proportional weight.
 */
object KeyPopPerKeySizing {
    data class Target(val language:Int,val row:Int,val column:Int,
                      val label:String) {
        val display:String get()=when(label) {
            "SPACE" -> "Spacebar"
            "⇧" -> "Shift / Case"
            "⌫" -> "Backspace"
            "↵" -> "Enter"
            "," -> "Comma"
            "." -> "Period"
            else -> label
        }
    }
    const val MIN_WIDTH=60
    const val MAX_WIDTH=240
    const val MIN_HEIGHT=-16
    const val MAX_HEIGHT=32
    private fun name(kind:String,language:Int,row:Int,column:Int)=
        "perKey_"+kind+"_"+language+"_"+row+"_"+column
    fun valid(language:Int,row:Int,column:Int):Boolean=
        language in 0..2 && row in 0..4 && column in 0..12
    fun width(context:Context,language:Int,row:Int,column:Int):Int {
        if(!valid(language,row,column))return 100
        return context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
            .getInt(name("width",language,row,column),100)
            .coerceIn(MIN_WIDTH,MAX_WIDTH)
    }
    fun heightOffset(context:Context,language:Int,row:Int,column:Int):Int {
        if(!valid(language,row,column))return 0
        return context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
            .getInt(name("height",language,row,column),0)
            .coerceIn(MIN_HEIGHT,MAX_HEIGHT)
    }
    fun setWidth(context:Context,language:Int,row:Int,column:Int,value:Int):Boolean {
        if(!valid(language,row,column)||value !in MIN_WIDTH..MAX_WIDTH)return false
        context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
            .edit().putInt(name("width",language,row,column),value).apply()
        return true
    }
    fun setHeightOffset(context:Context,language:Int,row:Int,column:Int,value:Int):Boolean {
        if(!valid(language,row,column)||value !in MIN_HEIGHT..MAX_HEIGHT)return false
        context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
            .edit().putInt(name("height",language,row,column),value).apply()
        return true
    }
    fun reset(context:Context,language:Int) {
        if(language !in 0..2)return
        val e=context.getSharedPreferences("keypop",Context.MODE_PRIVATE).edit()
        for(row in 0..4)for(column in 0..12) {
            e.remove(name("width",language,row,column))
            e.remove(name("height",language,row,column))
        }
        e.apply()
    }
    fun totalHeight(base:Int,label:String,delta:Int):Int =
        (base+KeyboardPresetCatalog.controlHeightBonus(label)+delta).coerceIn(32,132)
    fun weight(presetWeight:Float,widthPercent:Int,layoutWidthPercent:Int=100):Float =
        presetWeight*(widthPercent.coerceIn(MIN_WIDTH,MAX_WIDTH)/100f)*
            (layoutWidthPercent.coerceIn(65,175)/100f)
}
