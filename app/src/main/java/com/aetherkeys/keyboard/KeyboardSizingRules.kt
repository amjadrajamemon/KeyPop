package com.aetherkeys.keyboard

/** Pure sizing rules shared by the Android IME and unit tests. */
object KeyboardSizingRules {
    fun safeBottom(insetPx:Int,minimumPx:Int):Int =
        insetPx.coerceAtLeast(minimumPx.coerceAtLeast(0))
    fun keyHeight(requested:Int):Int=requested.coerceIn(36,90)
    fun widthPercent(requested:Int):Int=requested.coerceIn(65,100)
    fun bottomLift(requested:Int):Int=requested.coerceIn(0,96)
    fun centeredLeft(screenWidth:Int,keyboardWidth:Int,horizontalOffset:Int):Int =
        (((screenWidth-keyboardWidth)/2)+horizontalOffset)
            .coerceIn(0,(screenWidth-keyboardWidth).coerceAtLeast(0))
}
