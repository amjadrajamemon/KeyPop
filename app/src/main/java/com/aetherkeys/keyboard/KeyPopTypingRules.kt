package com.aetherkeys.keyboard

import android.text.InputType

/** Pure policy for keyboard assistance; shared by IME and regression tests. */
object KeyPopTypingRules {
    fun textAllowsAutomaticChanges(inputType:Int):Boolean {
        val kind=inputType and InputType.TYPE_MASK_CLASS
        if(kind!=InputType.TYPE_CLASS_TEXT)return false
        if(inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS!=0)return false
        val variation=inputType and InputType.TYPE_MASK_VARIATION
        return variation !in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_TEXT_VARIATION_FILTER,
            InputType.TYPE_TEXT_VARIATION_PHONETIC
        )
    }
    /**
     * Show explicitly enabled keyboard suggestions in ordinary text/search
     * fields even when the host sets NO_SUGGESTIONS to disable the system
     * spellchecker. The host opt-out STILL disables automatic text changes
     * and personal history learning; passwords, email, URI and other
     * restricted input variations never enter suggestion generation.
     */
    fun textAllowsSuggestions(inputType:Int):Boolean {
        val kind=inputType and InputType.TYPE_MASK_CLASS
        if(kind!=InputType.TYPE_CLASS_TEXT)return false
        val variation=inputType and InputType.TYPE_MASK_VARIATION
        return variation !in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_TEXT_VARIATION_FILTER,
            InputType.TYPE_TEXT_VARIATION_PHONETIC
        )
    }

    fun textAllowsLearning(inputType:Int):Boolean =
        textAllowsAutomaticChanges(inputType)

    fun autocorrectEnabled(toggle:Boolean,inputType:Int):Boolean =
        toggle && textAllowsAutomaticChanges(inputType)
    fun autoPunctuationEnabled(toggle:Boolean,inputType:Int):Boolean =
        toggle && textAllowsAutomaticChanges(inputType)
    /** Undo only the just-applied correction, never arbitrary editor text.
     * The editor and suffix are checked by the IME before applying this. */
    fun canUndoRecentCorrection(before:String,original:String,corrected:String,
                                elapsedMs:Long):Boolean =
        original.isNotEmpty() && corrected.isNotEmpty() &&
            elapsedMs in 0L..5000L &&
            before.endsWith(corrected+" ")
    fun preservesCase(original:String,corrected:String):String {
        if(original.isEmpty())return corrected
        return when {
            original.length>1 && original.all{!it.isLetter()||it.isUpperCase()} ->
                corrected.uppercase()
            original.first().isUpperCase() ->
                corrected.replaceFirstChar{it.uppercase()}
            else -> corrected
        }
    }
}
