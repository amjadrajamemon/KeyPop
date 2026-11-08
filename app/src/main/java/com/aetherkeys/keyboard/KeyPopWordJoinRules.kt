package com.aetherkeys.keyboard

import java.util.Locale

/** Conservative correction of accidental missing spaces. Never split a
 * legitimate dictionary word, a user-defined name, or an ambiguous token. */
object KeyPopWordJoinRules {
    private val prefixes=listOf("to","in","of","on","at","by","is","it",
        "we","be","my","up","do","no","the","and","for","can","will")
    private val verified=mapOf("topredict" to "to predict")
    fun split(word:String,valid:(String)->Boolean):String? {
        val input=word.lowercase(Locale.ROOT)
        if(input.length !in 6..28 || !input.all{it in 'a'..'z'})return null
        if(valid(input))return null
        verified[input]?.let {
            val parts=it.split(" ")
            if(parts.all(valid))return it
        }
        val candidates=prefixes.mapNotNull { prefix ->
            if(!input.startsWith(prefix) || input.length-prefix.length<4)return@mapNotNull null
            val suffix=input.substring(prefix.length)
            if(valid(prefix) && valid(suffix))prefix+" "+suffix else null
        }.distinct()
        return candidates.singleOrNull()
    }
}
