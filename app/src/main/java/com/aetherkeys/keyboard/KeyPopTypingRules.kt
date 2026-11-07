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
    fun autocorrectEnabled(toggle:Boolean,inputType:Int):Boolean =
        toggle && textAllowsAutomaticChanges(inputType)
    fun autoPunctuationEnabled(toggle:Boolean,inputType:Int):Boolean =
        toggle && textAllowsAutomaticChanges(inputType)
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
