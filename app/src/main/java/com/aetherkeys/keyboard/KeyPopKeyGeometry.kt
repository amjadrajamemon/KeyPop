package com.aetherkeys.keyboard

import android.content.Context

/** Saved per-key width and height, covering letters and editing controls. */
object KeyPopKeyGeometry {
    private fun key(language:Int,row:Int,column:Int)="keyWidth_"+language+"_"+row+"_"+column
    fun valid(language:Int,row:Int,column:Int):Boolean =
        language in 0..2 && row in 0..2 && column in 0..9
    fun width(context:Context,language:Int,row:Int,column:Int):Int {
        if(!valid(language,row,column))return 100
        return context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
            .getInt(key(language,row,column),100).coerceIn(65,175)
    }
    fun setWidth(context:Context,language:Int,row:Int,column:Int,percent:Int):Boolean {
        if(!valid(language,row,column)||percent !in 65..175)return false
        context.getSharedPreferences("keypop",Context.MODE_PRIVATE).edit()
            .putInt(key(language,row,column),percent).apply()
        return true
    }
    fun reset(context:Context,language:Int) {
        val edit=context.getSharedPreferences("keypop",Context.MODE_PRIVATE).edit()
        for(row in 0..2)for(column in 0..9)edit.remove(key(language,row,column))
        edit.apply()
    }

    /** Physical key dimensions, including space, shift, backspace and punctuation.
     * A non-letter control is shared by its symbol, while letters are indexed
     * by language+row+column and remain editable independently. */
    private fun controlId(label:String):String = when(label){
        "SPACE" -> "SPACE";"⇧" -> "SHIFT";"⌫" -> "BACKSPACE"
        "↵" -> "ENTER";"," -> "COMMA";"." -> "PERIOD"
        "🌐" -> "GLOBE";"☺" -> "EMOJI";"☺," -> "EMOJI_COMMA"
        "?123" -> "SYMBOLS";"ABC" -> "LETTERS"
        else -> when {
            label.length==1 && label[0].isDigit() -> "DIGIT_"+label
            label.length==1 && !label[0].isLetter() ->
                "CHAR_"+label[0].code
            else -> ""
        }
    }
    private fun heightKey(language:Int,row:Int,column:Int,label:String):String {
        val control=controlId(label)
        return if(control.isNotEmpty())"keyHeightControl_"+control
            else "keyHeight_"+language+"_"+row+"_"+column
    }
    fun heightPercent(context:Context,language:Int,row:Int,column:Int,
                      label:String):Int {
        val control=controlId(label)
        if(control.isEmpty() && !valid(language,row,column))return 100
        return context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
            .getInt(heightKey(language,row,column,label),100).coerceIn(70,180)
    }
    fun setHeightPercent(context:Context,language:Int,row:Int,column:Int,
                         label:String,percent:Int):Boolean {
        if(percent !in 70..180 ||
            (controlId(label).isEmpty() && !valid(language,row,column)))return false
        context.getSharedPreferences("keypop",Context.MODE_PRIVATE).edit()
            .putInt(heightKey(language,row,column,label),percent).apply()
        return true
    }
    fun widthPercent(context:Context,language:Int,row:Int,column:Int,
                      label:String):Int {
        val control=controlId(label)
        if(control.isNotEmpty())return context.getSharedPreferences("keypop",
            Context.MODE_PRIVATE).getInt("keyWidthControl_"+control,100).coerceIn(65,190)
        return width(context,language,row,column)
    }
    fun setWidthPercent(context:Context,language:Int,row:Int,column:Int,
                        label:String,percent:Int):Boolean {
        val control=controlId(label)
        if(control.isNotEmpty()) {
            if(percent !in 65..190)return false
            context.getSharedPreferences("keypop",Context.MODE_PRIVATE).edit()
                .putInt("keyWidthControl_"+control,percent).apply()
            return true
        }
        return setWidth(context,language,row,column,percent.coerceAtMost(175))
    }
    fun resetAllDimensions(context:Context) {
        val prefs=context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
        val edit=prefs.edit()
        prefs.all.keys.filter{
            it.startsWith("keyWidth_")||it.startsWith("keyHeight_")||
            it.startsWith("keyWidthControl_")||it.startsWith("keyHeightControl_")
        }.forEach { edit.remove(it) }
        edit.apply()
    }
    fun keys(context:Context):Map<String,Int> {
        val p=context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
        return p.all.mapNotNull { (k,v)->
            if(k.matches(Regex("keyWidth_[012]_[012]_[0-9]")) && v is Int && v in 65..175)
                k to v else null
        }.toMap()
    }
}
