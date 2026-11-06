package com.aetherkeys.keyboard

/** Geometry and alternate characters for a single, nonredundant number row. */
object KeyPopRowParity {
    val digits=listOf("1","2","3","4","5","6","7","8","9","0")
    fun hasNumberRow(enabled:Boolean,symbols:Boolean,numericField:Boolean):Boolean =
        enabled && !symbols && !numericField
    fun alternates(all:List<String>,label:String,dedicatedNumberRow:Boolean):List<String> =
        if(dedicatedNumberRow && label.length==1 && label.first().isLetter())
            all.filterNot { it.length==1 && it.first() in '0'..'9' }.distinct()
        else all.distinct()
}
