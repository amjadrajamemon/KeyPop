package com.aetherkeys.keyboard

import android.content.Context
import org.json.JSONArray

/** On-device, per-language customizable key layout registry. */
object KeyPopLayouts {
    val names=listOf("QWERTY","AZERTY","QWERTZ","Dvorak","Colemak")
    val languages=listOf("English","اردو","سنڌي")
    private val layouts=listOf(
        listOf("qwertyuiop","asdfghjkl","zxcvbnm"),
        listOf("azertyuiop","qsdfghjklm","wxcvbn"),
        listOf("qwertzuiop","asdfghjkl","yxcvbnm"),
        listOf("',.pyfgcrl","aoeuidhtns",";qjkxbmwvz"),
        listOf("qwfpgjluy;","arstdhneio","zxcvbkm"))
        .map { rows->rows.map { row->row.map { it.toString() } } }
    private val urdu=listOf(
        listOf("ق","و","ع","ر","ت","ے","ء","ی","ہ","پ"),
        listOf("ا","س","د","ف","گ","ح","ج","ک","ل"),
        listOf("ز","ش","چ","ط","ب","ن","م"))
    private val sindhi=listOf(
        listOf("ق","و","ع","ر","ت","ي","ء","ڀ","ه","پ"),
        listOf("ا","س","د","ف","ڳ","ح","ج","ڪ","ل"),
        listOf("ز","ش","چ","ٽ","ٻ","ن","م"))
    fun variant(context:Context):Int=context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
        .getInt("englishLayout",0).coerceIn(0,names.lastIndex)
    fun factory(context:Context,lang:Int):List<List<String>> = when(lang) {
        1->urdu;2->sindhi;else->layouts[variant(context)]
    }
    // Prevent user-entered characters from being interpreted as privileged control actions.
    private val reserved=setOf("SPACE","ABC","?123","⇧","⌫","↵","🌐")
    fun validKey(key:String):Boolean=key.isNotBlank() &&
        key !in reserved &&
        key.codePointCount(0,key.length) in 1..4 &&
        key.none { Character.isISOControl(it) }

    fun parse(raw:String,factory:List<List<String>>):List<List<String>>? {
        return try {
            val array=JSONArray(raw)
            if(array.length()!=3)return null
            (0 until 3).map { i ->
                val row=array.getJSONArray(i)
                if(row.length()!=factory[i].size)return null
                (0 until row.length()).map { j->
                    val value=row.getString(j)
                    if(!validKey(value))return null
                    value
                }
            }
        }catch(_:Exception){null}
    }
    fun active(context:Context,lang:Int):List<List<String>> {
        val base=factory(context,lang)
        val saved=context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
            .getString("customLayout_$lang",null)?:return base
        return parse(saved,base)?:base
    }
    private fun save(context:Context,lang:Int,rows:List<List<String>>) {
        val baseline=factory(context,lang)
        require(rows.size==baseline.size && rows.indices.all {rows[it].size==baseline[it].size})
        require(rows.flatten().all(::validKey))
        val result=JSONArray()
        rows.forEach { row ->
            val items=JSONArray()
            row.forEach { items.put(it) }
            result.put(items)
        }
        context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
            .edit().putString("customLayout_$lang",result.toString()).apply()
    }
    fun swap(context:Context,lang:Int,ar:Int,ac:Int,br:Int,bc:Int):Boolean {
        val rows=active(context,lang).map {it.toMutableList()}
        if(ar !in rows.indices || br !in rows.indices ||
            ac !in rows[ar].indices || bc !in rows[br].indices)return false
        val a=rows[ar][ac];rows[ar][ac]=rows[br][bc];rows[br][bc]=a
        save(context,lang,rows);return true
    }
    fun replace(context:Context,lang:Int,row:Int,col:Int,key:String):Boolean {
        if(!validKey(key))return false
        val rows=active(context,lang).map {it.toMutableList()}
        if(row !in rows.indices || col !in rows[row].indices)return false
        rows[row][col]=key;save(context,lang,rows);return true
    }
    fun reset(context:Context,lang:Int) {
        context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
            .edit().remove("customLayout_$lang").apply()
    }
    fun choose(context:Context,index:Int) {
        if(index !in names.indices)return
        context.getSharedPreferences("keypop",Context.MODE_PRIVATE).edit()
            .putInt("englishLayout",index).remove("customLayout_0").apply()
    }
}
