package com.aetherkeys.keyboard

/** Both KeyPop offline and authorized Google Cloud support all six ordered pairs. */
object KeyPopEasyTranslateDirections {
    val codes=listOf("en","ur","sd")
    val names=listOf("English","اردو","سنڌي")
    fun supports(source:String,target:String):Boolean=
        source in codes && target in codes && source!=target
    fun allPairs():List<Pair<String,String>> = codes.flatMap{source->
        codes.filter{it!=source}.map{target->source to target}
    }
    fun nextSource(current:Int,target:Int):Int {
        val candidates=(0..2).filter{it!=target}
        return candidates.firstOrNull{it>current}?:candidates.first()
    }
    fun nextTarget(current:Int,source:Int):Int {
        val candidates=(0..2).filter{it!=source}
        return candidates.firstOrNull{it>current}?:candidates.first()
    }
}
