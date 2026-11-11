package com.aetherkeys.keyboard

import java.text.Normalizer
import java.util.Locale

/**
 * Shared Unicode-aware tokenizer for predictions, learned text and editor
 * boundaries. Combining vowel marks belong to their base letters; apostrophes
 * inside contractions belong to the word rather than creating a new token.
 * Do not strip Urdu/Sindhi distinctions during prediction.
 */
object KeyPopUnicodeWords {
    private val token=Regex("[\\p{L}\\p{M}]+(?:['’][\\p{L}\\p{M}]+)*")
    private val trailing=Regex("[\\p{L}\\p{M}]+(?:['’][\\p{L}\\p{M}]+)*$")

    fun trailingWord(text:String):String=trailing.find(text)?.value.orEmpty()
    fun tokens(text:String,limit:Int=40):List<String> =
        token.findAll(text.takeLast(512)).map{normalize(it.value)}.take(limit).toList()

    fun normalize(value:String):String =
        Normalizer.normalize(value,Normalizer.Form.NFC)
            .lowercase(Locale.ROOT).replace('\u0640'.toString(),"")

    fun isToken(value:String):Boolean =
        value.length in 2..32 && token.matchEntire(value)!=null &&
            value.first().isLetter()
}
