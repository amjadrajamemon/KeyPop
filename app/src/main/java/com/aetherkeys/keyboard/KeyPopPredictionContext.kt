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

    data class Parsed(val hasWords:Boolean,val be:Boolean,val have:Boolean,val bare:Boolean)
    /** Parse once per keystroke rather than retokenizing on every candidate. */
    fun parse(beforeCursor:String,fragment:String):Parsed {
        val preceding=if(beforeCursor.endsWith(fragment,ignoreCase=true))
            beforeCursor.dropLast(fragment.length) else beforeCursor
        val tokens=terms.findAll(preceding).map{it.value.lowercase(Locale.ROOT)}
            .toList().takeLast(5)
        if(tokens.isEmpty())return Parsed(false,false,false,false)
        val last=tokens.last()
        val second=tokens.getOrNull(tokens.lastIndex-1)
        val third=tokens.getOrNull(tokens.lastIndex-2)
        val pronoun=last in setOf("you","we","i","he","she","they","it")
        val be=last in beVerbs || (second in beVerbs&&pronoun) ||
            (third in beVerbs&&second in setOf("you","we","he","she","they","it") &&
                last in setOf("still","really","not","so"))
        val have=last in haveVerbs||(second in haveVerbs&&pronoun)
        val bare=last in bareAux||(second in bareAux&&pronoun)
        return Parsed(true,be,have,bare)
    }
    fun score(parsed:Parsed,fragment:String,candidate:String):Int {
        val typed=fragment.lowercase(Locale.ROOT)
        val word=candidate.lowercase(Locale.ROOT)
        if(!parsed.hasWords||typed.isBlank()||word.isBlank())return 0
        var points=0
        if(parsed.be) {
            when {
                word.endsWith("ing")->points+=225
                word.endsWith("ed")||word in setOf("gone","done","known","seen","taken")->
                    points+=225
                word in baseVerbs->points-=260
            }
        } else if(parsed.have) {
            when {
                word.endsWith("ed")||word in setOf("done","seen","gone","known","taken")->
                    points+=180
                word in baseVerbs->points-=110
            }
        } else if(parsed.bare) {
            when {
                word in baseVerbs->points+=120
                word.endsWith("ing")->points-=75
            }
        }
        if(typed.endsWith("i")&&(word.endsWith("ing")||word.endsWith("ied")))
            points+=30
        // When a user types a plausible stem-ending slip (worri -> worry),
        // favour the inflection of the same verb over unrelated -ing forms
        // (working/wording). Only applies after a be-auxiliary, where an
        // -ing participle is grammatically plausible. Bounded, offline.
        if(parsed.be && typed.length>=4 && typed.endsWith("i") &&
            word.startsWith(typed.dropLast(1)+"y") && word.endsWith("ing"))
            points+=85
        return points
    }
    fun score(beforeCursor:String,fragment:String,candidate:String):Int =
        score(parse(beforeCursor,fragment),fragment,candidate)

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
