package com.aetherkeys.keyboard

import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

/** Editors are responsible for their own action flags. A visible Enter key must
 * still insert a newline into multiline content, especially WhatsApp drafts.
 * IME_ACTION_SEND is a request to show a Send key, not a requirement to send
 * when the keyboard visibly offers Enter. */
object KeyPopEnterPolicy {
    fun insertsNewline(info:EditorInfo?):Boolean {
        if(info==null)return true
        val inputType=info.inputType
        if(inputType and InputType.TYPE_MASK_CLASS!=InputType.TYPE_CLASS_TEXT)return false
        val variation=inputType and InputType.TYPE_MASK_VARIATION
        if(variation in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        ))return false
        val action=info.imeOptions and EditorInfo.IME_MASK_ACTION
        if(action in setOf(EditorInfo.IME_ACTION_SEARCH,EditorInfo.IME_ACTION_GO,
            EditorInfo.IME_ACTION_NEXT,EditorInfo.IME_ACTION_DONE,
            EditorInfo.IME_ACTION_PREVIOUS))return false
        if(info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION!=0)return true
        val pkg=info.packageName.orEmpty()
        if(pkg=="com.whatsapp" || pkg=="com.whatsapp.w4b")return true
        return inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE!=0
    }
}

/** Uses InputConnection's selected-text replacement semantics rather than
 * deleteSurroundingText, which does not reliably remove selected content.
 * No clipboard writes or persistence of selected text occur. */
object KeyPopEditorActions {
    enum class Deletion { SELECTED_TEXT, CODE_POINT, FAILED }

    fun deleteBackward(connection:InputConnection?,selectionReported:Boolean=false):Deletion {
        if(connection==null)return Deletion.FAILED
        val selected=runCatching {
            connection.getSelectedText(0)?.isNotEmpty()==true
        }.getOrDefault(false)
        if(selected || selectionReported) {
            if(runCatching { connection.commitText("",1) }.getOrDefault(false))
                return Deletion.SELECTED_TEXT
            // A few web editors reject an empty commit: try the standard DEL.
            if(sendBackspace(connection))return Deletion.SELECTED_TEXT
            return Deletion.FAILED
        }
        if(runCatching { connection.deleteSurroundingTextInCodePoints(1,0) }
                .getOrDefault(false))return Deletion.CODE_POINT
        if(runCatching { connection.deleteSurroundingText(1,0) }
                .getOrDefault(false))return Deletion.CODE_POINT
        return if(sendBackspace(connection))Deletion.CODE_POINT else Deletion.FAILED
    }

    private fun sendBackspace(connection:InputConnection):Boolean =
        runCatching {
            val down=connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DEL))
            val up=connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DEL))
            down && up
        }.getOrDefault(false)
}
