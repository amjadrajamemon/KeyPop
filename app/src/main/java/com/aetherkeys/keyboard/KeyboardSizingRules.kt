package com.aetherkeys.keyboard

/** Pure, deterministic bounds for IME sizing and transient Android navbar insets. */
object KeyboardSizingRules {
    fun safeBottom(insetPx:Int,minimumPx:Int):Int =
        insetPx.coerceAtLeast(minimumPx.coerceAtLeast(0))

    /** A transient zero inset must never pull keys into the navigation controls.
     * Orientation changes reset the previous value in the IME service. */
    fun stableSafeBottom(previousPx:Int,reportedPx:Int,minimumPx:Int):Int =
        maxOf(previousPx.coerceAtLeast(0),reportedPx.coerceAtLeast(0),
            minimumPx.coerceAtLeast(0))

    /** Resize editors scroll inside this viewport rather than growing the IME
     * window larger than the available phone screen. */
    fun boundedPanelHeight(screenHeightPx:Int,preferredPx:Int):Int =
        minOf(preferredPx.coerceAtLeast(1),
            (screenHeightPx.coerceAtLeast(1)*0.50f).toInt().coerceAtLeast(1))

    fun keyHeight(requested:Int):Int=requested.coerceIn(36,90)
    fun widthPercent(requested:Int):Int=requested.coerceIn(65,100)
    fun bottomLift(requested:Int):Int=requested.coerceIn(0,96)
    fun centeredLeft(screenWidth:Int,keyboardWidth:Int,horizontalOffset:Int):Int =
        (((screenWidth-keyboardWidth)/2)+horizontalOffset)
            .coerceIn(0,(screenWidth-keyboardWidth).coerceAtLeast(0))
}
