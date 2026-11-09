package com.aetherkeys.keyboard

import android.content.Context

/** Independently selectable key contents and long-press symbol behavior.
 * These original KeyPop designs reference familiar ergonomic conventions,
 * without copying proprietary third-party keyboard assets. */
data class KeyPopKeyset(val id:Int,val name:String,val description:String)

object KeyPopKeysets {
    const val GBOARD=1
    const val SWIFTKEY=2
    const val SAMSUNG=3
    const val KEY="keysetPreset"
    val all=listOf(
        KeyPopKeyset(GBOARD,"Gboard-style keys & symbols",
            "Dedicated comma and emoji keys, symbol hints and period popup"),
        KeyPopKeyset(SWIFTKEY,"SwiftKey-style keys & symbols",
            "Punctuation-first long-press choices and spacious bottom actions"),
        KeyPopKeyset(SAMSUNG,"Samsung-style keys & symbols",
            "Samsung-inspired punctuation, utility keys and symbol choices")
    )
    fun legacy(geometryId:Int):Int=when(geometryId) {
        KeyboardPresetCatalog.FLOW -> SWIFTKEY
        KeyboardPresetCatalog.ONE_UI -> SAMSUNG
        else -> GBOARD
    }
    fun valid(id:Int)=all.any{it.id==id}
    fun selectedId(context:Context):Int {
        val p=context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
        val id=p.getInt(KEY,legacy(p.getInt("keyboardPreset",KeyboardPresetCatalog.MATERIAL)))
        return if(valid(id))id else GBOARD
    }
    fun apply(context:Context,id:Int):Boolean {
        if(!valid(id))return false
        return context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
            .edit().putInt(KEY,id).commit()
    }
    fun footer(id:Int,symbols:Boolean):List<String> {
        if(symbols)return listOf("ABC","🌐",",","SPACE",".","↵")
        return when(id) {
            GBOARD -> listOf("?123",",","☺","🌐","SPACE",".","↵")
            SWIFTKEY -> listOf("?123",",","🌐","SPACE",".","↵")
            SAMSUNG -> listOf("?123",",","☺","SPACE",".","↵")
            else -> listOf("?123",",","☺","🌐","SPACE",".","↵")
        }
    }
    private val samsungSymbols=mapOf(
        "q" to listOf("1"),"w" to listOf("2"),"e" to listOf("3"),
        "r" to listOf("4"),"t" to listOf("5"),"y" to listOf("6"),
        "u" to listOf("7"),"i" to listOf("8"),"o" to listOf("9"),"p" to listOf("0"),
        "a" to listOf("@"),"s" to listOf("#"),"d" to listOf("$"),
        "f" to listOf("%"),"g" to listOf("&"),"h" to listOf("-"),
        "j" to listOf("+"),"k" to listOf("("),"l" to listOf(")"),
        "z" to listOf("*"),"x" to listOf("\""),"c" to listOf("'"),
        "v" to listOf(":"),"b" to listOf(";"),"n" to listOf("!"),"m" to listOf("?")
    )
    private val swiftPeriod=listOf("?","!",",","…",":",";","/","@","#","-")
    private val samsungPeriod=listOf("!","?","…",",","@","#",";",":","(",")")
    fun alternates(id:Int,label:String,language:Int):List<String> {
        val normal=KeyPopAlternates.forKey(label,language)
        if(language!=0)return normal
        return when(id) {
            GBOARD->KeyPopMaterialHints.candidates(label,language,true)
            SWIFTKEY->if(label==".")swiftPeriod else normal
            SAMSUNG->if(label==".")samsungPeriod
                else (samsungSymbols[label].orEmpty()+normal).distinct()
            else->normal
        }
    }
    fun symbolRows(id:Int,extended:Boolean):List<List<String>> {
        val digits=listOf("1","2","3","4","5","6","7","8","9","0")
        return when(id) {
            SWIFTKEY -> if(extended)
                listOf(listOf("~","€","£","¥","•","√","π","÷","×","∆"),
                    listOf("§","©","®","™","°","✓","∞","≈","≠"),
                    listOf("«","»","„","“","”","•","…","¤","¢"))
                else listOf(digits,
                    listOf("@","#","$","%","&","-","+","(",")"),
                    listOf("*","\"",":",";","!","?","/","_"))
            SAMSUNG -> if(extended)
                listOf(listOf("~","¤","|","•","√","π","÷","×","£","€"),
                    listOf("°","©","®","™","✓","∞","≈","≠","§"),
                    listOf("…","«","»","“","”","‘","’","¢","¥"))
                else listOf(digits,
                    listOf("!","@","#","$","%","^","&","*","(",")"),
                    listOf("-","_","+","=","/","?",";",":"))
            else -> if(extended)
                listOf(listOf("~","€","£","¥","•","√","π","÷","×","∆"),
                    listOf("{","}","[","]","<",">","©","®","™","✓"),
                    listOf("،","؛","؟","«","»","–","—","…","°"))
                else listOf(digits,
                    listOf("@","#","$","%","&","-","+","(",")","/"),
                    listOf("*","\"","'",";",":","!","?","_","="))
        }
    }
}

/** Pure compatibility model: changing one axis leaves others unchanged. */
data class KeyPopComposition(val skin:Int,val geometry:Int,val keyset:Int) {
    fun withSkin(id:Int)=copy(skin=id)
    fun withGeometry(id:Int)=copy(geometry=id)
    fun withKeyset(id:Int)=copy(keyset=id)
}
