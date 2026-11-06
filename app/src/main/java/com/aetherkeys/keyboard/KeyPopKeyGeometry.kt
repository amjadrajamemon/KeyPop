package com.aetherkeys.keyboard

import android.content.Context

/** Per-key width factor, local and exportable. Control keys remain fixed. */
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
    fun keys(context:Context):Map<String,Int> {
        val p=context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
        return p.all.mapNotNull { (k,v)->
            if(k.matches(Regex("keyWidth_[012]_[012]_[0-9]")) && v is Int && v in 65..175)
                k to v else null
        }.toMap()
    }
}
