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

    /** Only a messaging field's advertised SEND action can be treated as an
     * automatic send. Search/Go must never silently submit a translated chat. */
    fun supportsSendAction(action:Int):Boolean =
        action==android.view.inputmethod.EditorInfo.IME_ACTION_SEND

    /** An optional physical Enter fallback only makes sense when the host
     * doesn't advertise a more specific action; some apps insert a newline. */
    fun allowEnterSendFallback(action:Int,enabled:Boolean):Boolean =
        enabled && action in setOf(
            android.view.inputmethod.EditorInfo.IME_ACTION_NONE,
            android.view.inputmethod.EditorInfo.IME_ACTION_UNSPECIFIED)

    /** Never send a stale preview from a different language pair or engine. */
    fun canReusePreview(panelOpen:Boolean,preview:String,original:String,
                        previewOriginal:String,expectedPair:String,previewPair:String,
                        currentGoogleMode:Boolean,previewGoogleMode:Boolean):Boolean =
        panelOpen && preview.isNotBlank() && original==previewOriginal &&
            expectedPair==previewPair && currentGoogleMode==previewGoogleMode
}
