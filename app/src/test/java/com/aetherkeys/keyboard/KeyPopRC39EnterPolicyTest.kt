package com.aetherkeys.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.*
import org.junit.Test

/** Behavioral expectations reported by RC38 users on WhatsApp and elsewhere. */
class KeyPopRC39EnterPolicyTest {
    private val text=InputType.TYPE_CLASS_TEXT
    @Test fun whatsappEnterMustNotTriggerSendAction() {
        for(pkg in listOf("com.whatsapp","com.whatsapp.w4b")) {
            assertTrue(KeyPopEnterPolicy.insertsNewline(
                text or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
                EditorInfo.IME_ACTION_SEND,pkg))
            assertTrue(KeyPopEnterPolicy.insertsNewline(
                text,EditorInfo.IME_ACTION_SEND,pkg))
        }
    }
    @Test fun multilineOtherAppsShouldCreateNewLineNotSendMessage() {
        assertTrue(KeyPopEnterPolicy.insertsNewline(
            text or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
            EditorInfo.IME_ACTION_SEND,"com.example.chat"))
        assertTrue(KeyPopEnterPolicy.insertsNewline(
            text or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
            EditorInfo.IME_ACTION_NONE,"com.example.notes"))
        assertTrue(KeyPopEnterPolicy.insertsNewline(
            text,EditorInfo.IME_FLAG_NO_ENTER_ACTION,"com.example.notes"))
    }
    @Test fun dedicatedGoSearchNextAndDoneActionsRemainIntact() {
        for(action in listOf(EditorInfo.IME_ACTION_SEARCH,EditorInfo.IME_ACTION_GO,
            EditorInfo.IME_ACTION_NEXT,EditorInfo.IME_ACTION_DONE,
            EditorInfo.IME_ACTION_PREVIOUS)) {
            assertFalse(KeyPopEnterPolicy.insertsNewline(text,action,"com.whatsapp"))
        }
        assertFalse(KeyPopEnterPolicy.insertsNewline(
            text,EditorInfo.IME_ACTION_SEND,"com.example.singleLine"))
        assertFalse(KeyPopEnterPolicy.insertsNewline(
            InputType.TYPE_CLASS_NUMBER,EditorInfo.IME_ACTION_DONE,"com.whatsapp"))
    }
    @Test fun passwordEmailAndUrlFieldsAreNotTreatedAsChatComposers() {
        for(variation in listOf(InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)) {
            assertFalse(KeyPopEnterPolicy.insertsNewline(
                text or variation,EditorInfo.IME_ACTION_SEND,"com.whatsapp"))
        }
    }
}
