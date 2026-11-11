package com.aetherkeys.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo

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
    /** Local suggestions can be available even when automatic edits and learning are disabled. */
    fun textAllowsSuggestions(inputType:Int,imeOptions:Int=0):Boolean {
        if(inputType and InputType.TYPE_MASK_CLASS!=InputType.TYPE_CLASS_TEXT)return false
        return when(inputType and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_PHONETIC -> false
            InputType.TYPE_TEXT_VARIATION_URI ->
                imeOptions and EditorInfo.IME_MASK_ACTION==EditorInfo.IME_ACTION_SEARCH ||
                    inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS!=0
            else -> true
        }
    }

    fun isAddressEditor(inputType:Int):Boolean =
        inputType and InputType.TYPE_MASK_CLASS==InputType.TYPE_CLASS_TEXT &&
            inputType and InputType.TYPE_MASK_VARIATION==InputType.TYPE_TEXT_VARIATION_URI

    /** A combined search/address box can predict words in a query without touching a URL. */
    fun isAddressToken(before:String):Boolean =
        before.takeLastWhile{!it.isWhitespace()}.any{it in "./:@?#%\\=&+"}

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
