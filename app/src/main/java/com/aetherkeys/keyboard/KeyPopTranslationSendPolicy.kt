package com.aetherkeys.keyboard

/** Pure policy shared by live Easy Translate and the keyboard Send key. */
object KeyPopTranslationSendPolicy {
    data class Direction(val source:Int,val target:Int)
    fun select(panelOpen:Boolean,selectedSource:Int,selectedTarget:Int,
               detectedSource:Int,settingsTarget:Int):Direction =
        Direction(
            if(panelOpen && selectedSource in 0..2)selectedSource else detectedSource.coerceIn(0,2),
            if(panelOpen)selectedTarget.coerceIn(0,2) else settingsTarget.coerceIn(0,2)
        )

    /** Never send a stale preview from a different language pair or engine. */
    fun canReusePreview(panelOpen:Boolean,preview:String,original:String,
                        previewOriginal:String,expectedPair:String,previewPair:String,
                        currentGoogleMode:Boolean,previewGoogleMode:Boolean):Boolean =
        panelOpen && preview.isNotBlank() && original==previewOriginal &&
            expectedPair==previewPair && currentGoogleMode==previewGoogleMode
}
