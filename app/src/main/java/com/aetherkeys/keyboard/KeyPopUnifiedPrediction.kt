package com.aetherkeys.keyboard

import java.util.Locale

/**
 * Single transparent ranker for spelling, dictionary and personalized words.
 * This ranks suggestions aggressively but NEVER chooses an automatic edit:
 * the autocorrection safety gate remains separate.
 */
object KeyPopUnifiedPrediction {
    private data class Ranked(val word:String,val score:Int,val ordinal:Int)

    fun rank(language:Int,typed:String,before:String,personal:List<String>,
             dictionary:List<String>,correction:String?=null,
             rejected:Map<String,Int> = emptyMap(),limit:Int=8):List<String> {
        val input=KeyPopUnicodeWords.normalize(typed)
        val source=(listOfNotNull(correction)+dictionary+personal).distinct()
        val personalized=personal.map{KeyPopUnicodeWords.normalize(it)}.toSet()
        val parsed=if(language==0)KeyPopPredictionContext.parse(before,typed) else null
        val results=ArrayList<Ranked>()
        val seen=HashSet<String>()
        for((ordinal,candidate) in source.withIndex()) {
            val name=KeyPopUnicodeWords.normalize(candidate)
            if(name.isBlank() || name==input || !seen.add(name))continue
            val exact=input.isNotEmpty() && name.startsWith(input)
            val edit=if(!exact && language==0 && input.length>=3)
                KeyPopFuzzyRanker.distanceAtMost2(input,name.take(input.length))
                else 3
            val joined=candidate.contains(' ') && candidate.replace(" ","")==input
            // Personal next-word candidates are already conditioned on the
            // previous two words in their encrypted local frequency model.
            if(input.isNotEmpty() && !exact && edit>2 &&
                !joined && candidate!=correction)continue
            val grammar=if(parsed!=null)
                KeyPopPredictionContext.score(parsed,typed,candidate) else 0
            if(grammar<=-200)continue
            val isCorrection=correction!=null &&
                candidate.equals(correction,ignoreCase=true)
            var score=when {
                input.isEmpty()->180
                isCorrection->455
                exact->410-(name.length-input.length).coerceAtLeast(0)*6
                joined->365
                edit==1->300
                edit==2->230
                else->180
            }
            if(name in personalized)score+=if(input.isEmpty())105 else 45
            if(name in dictionary)score+=18
            score+=grammar
            score-=(ordinal.coerceAtMost(16)*3)
            score-=(rejected[name]?:0).coerceIn(0,10)*90
            results.add(Ranked(candidate,score,ordinal))
        }
        return results.sortedWith(compareByDescending<Ranked>{it.score}
            .thenBy{it.ordinal}.thenBy{it.word.lowercase(Locale.ROOT)})
            .take(limit.coerceIn(1,8)).map{it.word}
    }
}
