package com.aetherkeys.keyboard

import java.util.Locale
import kotlin.math.abs

/**
 * Bounded offline spelling ranker. Operates only on short candidate windows
 * assembled by the SCOWL prefix buckets on the prediction worker.
 * No disk, network, InputConnection, or allocations proportional to vocabulary.
 */
object KeyPopFuzzyRanker {
    data class Match(val word:String,val distance:Int,val score:Int,val tier:Int)

    /**
     * Optimal-string-alignment distance with a two-error cutoff.
     * Three reusable rows instead of allocating an (n+1)x(m+1) matrix for
     * each potential candidate. Adjacent swaps count as one typo.
     */
    fun distanceAtMost2(a:String,b:String):Int {
        if(abs(a.length-b.length)>2)return 3
        var twoBack=IntArray(b.length+1)
        var previous=IntArray(b.length+1){it}
        var current=IntArray(b.length+1)
        for(i in 1..a.length) {
            current[0]=i
            var rowMin=current[0]
            for(j in 1..b.length) {
                val substitute=if(a[i-1]==b[j-1])0 else 1
                var value=minOf(previous[j]+1,current[j-1]+1,previous[j-1]+substitute)
                if(i>1 && j>1 && a[i-1]==b[j-2] && a[i-2]==b[j-1])
                    value=minOf(value,twoBack[j-2]+1)
                current[j]=value
                rowMin=minOf(rowMin,value)
            }
            // A transposition can reduce the cost from the previous row;
            // do not prune a row until more than two unmatched positions remain.
            if(rowMin>2 && i>=a.length)return 3
            val recycle=twoBack
            twoBack=previous
            previous=current
            current=recycle
        }
        return previous[b.length].coerceAtMost(3)
    }

    fun rank(typed:String,candidates:Sequence<Pair<String,Int>>,
             parsed:KeyPopPredictionContext.Parsed,
             limit:Int=8):List<Match> {
        val input=typed.lowercase(Locale.ROOT)
        if(input.length !in 4..22 || !input.all{it in 'a'..'z'})return emptyList()
        return candidates
            .filter { (word,_) -> word!=input &&
                abs(word.length-input.length)<=2 && word.length in 3..24 }
            .distinctBy {it.first}
            .mapNotNull { (word,tier) ->
                val distance=distanceAtMost2(input,word)
                if(distance !in 1..2)return@mapNotNull null
                val familiarity=when {
                    tier<=10->85
                    tier<=20->55
                    tier<=35->20
                    else->0
                }
                val score=(if(distance==1)240 else 126)+familiarity+
                    (if(word.first()==input.first())20 else 0)+
                    (if(word.startsWith(input.take(2)))16 else 0)-
                    14*abs(word.length-input.length)+
                    KeyPopPredictionContext.score(parsed,input,word)
                Match(word,distance,score,tier)
            }
            .sortedWith(compareByDescending<Match>{it.score}
                .thenBy{it.distance}.thenBy{it.tier}.thenBy{it.word})
            .take(limit.coerceIn(1,16))
            .toList()
    }

    /**
     * Offer ambiguous alternatives as chips but do not silently rewrite a
     * valid word, close ties, or an unfamiliar personal name on Space.
     */
    fun automatic(matches:List<Match>,aggressive:Boolean=true):String? {
        val best=matches.firstOrNull()?:return null
        val runnerUp=matches.getOrNull(1)
        val margin=if(runnerUp==null)Int.MAX_VALUE else best.score-runnerUp.score
        // Aggressive mode spends confidence on clear typos, never close ties.
        // The existing settings toggle affects correction strictness as well
        // as the number of presented prediction candidates.
        val oneEditMargin=if(aggressive)32 else 55
        val twoEditMargin=if(aggressive)75 else 115
        return when {
            best.tier>20 ->null
            best.distance==1 && margin>=oneEditMargin ->best.word
            best.distance==2 && best.tier<=10 && margin>=twoEditMargin ->best.word
            else ->null
        }
    }
}
