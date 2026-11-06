package com.aetherkeys.keyboard

/** Best-effort script detection, entirely local and never a cloud language detector. */
object KeyPopTextLanguage {
    private val sindhiDistinct="ٻڀٿٺٽڄڃڇڌڏڊڍڙڳڦڻڪھ"
    fun detect(text:String,fallback:Int=0):Int {
        val content=text.trim()
        if(content.isEmpty())return fallback.coerceIn(0,2)
        if(content.any{sindhiDistinct.contains(it)})return 2
        val isArabic=content.any {
            it.code in 0x0600..0x06FF || it.code in 0x0750..0x077F
        }
        if(isArabic)return if(fallback==2)2 else 1
        if(content.any{it in 'a'..'z'||it in 'A'..'Z'})return 0
        return fallback.coerceIn(0,2)
    }
}
