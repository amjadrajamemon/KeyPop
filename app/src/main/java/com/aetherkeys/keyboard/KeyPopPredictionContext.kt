package com.aetherkeys.keyboard

import java.util.Locale

/**
 * Bounded offline contextual ranking. This is deliberately transparent grammar
 * heuristics, not a claim to reproduce Google's proprietary neural model.
 */
object KeyPopPredictionContext {
    private val terms=Regex("[A-Za-z']+")
    private val beVerbs=setOf("am","is","are","was","were","be","been","being")
    private val haveVerbs=setOf("have","has","had")
    private val bareAux=setOf("do","does","did","can","could","will","would",
        "should","may","might","must","to","shall")
    private val baseVerbs=setOf("worry","work","create","improve","write",
        "run","do","make","go","come","try","study","wait","read","help","play",
        "say","give","think","look","feel","send","download","build","fix")

    fun score(beforeCursor:String,fragment:String,candidate:String):Int {
        val typed=fragment.lowercase(Locale.ROOT)
        val word=candidate.lowercase(Locale.ROOT)
        if(typed.isBlank()||word.isBlank())return 0
        val preceding=if(beforeCursor.endsWith(fragment,ignoreCase=true))
            beforeCursor.dropLast(fragment.length) else beforeCursor
        val tokens=terms.findAll(preceding).map{it.value.lowercase(Locale.ROOT)}
            .toList().takeLast(5)
        if(tokens.isEmpty())return 0
        val last=tokens.last()
        val second=tokens.getOrNull(tokens.lastIndex-1)
        val third=tokens.getOrNull(tokens.lastIndex-2)
        // "Why are you worri" has an inverted auxiliary/pronoun pair.
        val pronoun=last in setOf("you","we","i","he","she","they","it")
        val beContext=last in beVerbs || (second in beVerbs&&pronoun) ||
            (third in beVerbs&&second in setOf("you","we","he","she","they","it") &&
                last in setOf("still","really","not","so"))
        val haveContext=last in haveVerbs||(second in haveVerbs&&pronoun)
        val bareContext=last in bareAux||(second in bareAux&&pronoun)
        var points=0
        if(beContext) {
            when {
                word.endsWith("ing")->points+=225
                word.endsWith("ed")||word in setOf("gone","done","known","seen","taken")->
                    points+=225
                word in baseVerbs->points-=260
            }
        } else if(haveContext) {
            when {
                word.endsWith("ed")||word in setOf("done","seen","gone","known","taken")->
                    points+=180
                word in baseVerbs->points-=110
            }
        } else if(bareContext) {
            when {
                word in baseVerbs->points+=120
                word.endsWith("ing")->points-=75
            }
        }
        if(typed.endsWith("i")&&(word.endsWith("ing")||word.endsWith("ied")))
            points+=30
        return points
    }

    fun startsSentence(text:String):Boolean {
        val before=text.trimEnd()
        return before.isEmpty() ||
            before.last() in setOf('.','!','?','؟','۔','\n')
    }

    fun wordCase(candidate:String,typed:String,beforeCursor:String):String {
        if(candidate.isBlank())return candidate
        val lower=candidate.lowercase(Locale.ROOT)
        if(lower=="i")return "I"
        if(candidate.any{it.code>127})return candidate
        if(typed.isNotEmpty()) {
            if(typed.length>1 && typed.all{!it.isLetter()||it.isUpperCase()})
                return lower.uppercase(Locale.ROOT)
            if(typed.first().isUpperCase())
                return lower.replaceFirstChar{it.uppercase()}
        }
        val before=if(typed.isNotBlank() &&
            beforeCursor.endsWith(typed,ignoreCase=true))
            beforeCursor.dropLast(typed.length) else beforeCursor
        return if(startsSentence(before))
            lower.replaceFirstChar{it.uppercase()}
        else lower
    }

    fun committedWord(word:String,afterCursor:String):String {
        if(word.isEmpty())return word
        val next=afterCursor.firstOrNull()
        return word + if(next==null ||
            (!next.isWhitespace() && next !in setOf('.',',','!','?','؛','،','؟','۔',':')))
            " " else ""
    }

    fun toolbarVisible(alwaysShow:Boolean,hasSuggestions:Boolean):Boolean =
        alwaysShow || !hasSuggestions
}
