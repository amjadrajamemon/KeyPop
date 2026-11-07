package com.aetherkeys.keyboard

/** Tiny on-keyboard draft editor for Gboard-style live translation.
 * Draft text stays inside IME memory and never reaches the host app until the
 * user explicitly accepts the translated output. */
object KeyPopTranslationDraft {
    private val punctuation=setOf('.',',','!','?',':',';','،','؛','؟','۔','-','(',')')
    fun handles(key:String):Boolean =
        key in setOf("SPACE","⌫","↵") ||
        (key.length==1 && (key[0].isLetterOrDigit() || key[0] in punctuation))
    fun edit(current:String,key:String,shift:Boolean=false,autoCap:Boolean=true,
             autoPunctuation:Boolean=true):String {
        if(key=="⌫")return current.dropLast(1)
        if(key=="SPACE")return current+" "
        if(key.length!=1)return current
        val letter=key[0]
        if(autoPunctuation && letter in setOf('.',',','!','?','؛','،','؟','۔',':') &&
            current.trimEnd().lastOrNull()?.isLetter()==true)
            return current.trimEnd()+letter+" "
        val normalized=if(letter.isLetter() && (shift ||
            (autoCap && KeyPopPredictionContext.startsSentence(current))))
            key.uppercase() else key
        return current+normalized
    }
}
