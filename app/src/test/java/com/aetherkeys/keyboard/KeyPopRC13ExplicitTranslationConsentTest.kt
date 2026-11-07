package com.aetherkeys.keyboard

import android.view.inputmethod.EditorInfo
import org.junit.Assert.*
import org.junit.Test

/** A saved legacy flag alone must never silently change the Send key. */
class KeyPopRC13ExplicitTranslationConsentTest {
    @Test fun normalSendNeverEntersTranslateModeByDefault() {
        assertFalse(KeyPopTranslationSendPolicy.mayTranslateOnSend(
            panelOpen=false, explicitlyEnabled=false,targetConfirmed=false))
        assertFalse(KeyPopTranslationSendPolicy.mayTranslateOnSend(
            panelOpen=false, explicitlyEnabled=false,targetConfirmed=true))
        assertFalse(KeyPopTranslationSendPolicy.mayTranslateOnSend(
            panelOpen=false, explicitlyEnabled=true,targetConfirmed=false))
    }
    @Test fun manualTranslatorAndFullyConfirmedOptionWork() {
        assertTrue(KeyPopTranslationSendPolicy.mayTranslateOnSend(
            panelOpen=true, explicitlyEnabled=false,targetConfirmed=false))
        assertTrue(KeyPopTranslationSendPolicy.mayTranslateOnSend(
            panelOpen=false, explicitlyEnabled=true,targetConfirmed=true))
    }
    @Test fun onlyRealSendActionIsTreatedAsAnAutomaticSend() {
        assertTrue(KeyPopTranslationSendPolicy.supportsSendAction(EditorInfo.IME_ACTION_SEND))
        assertFalse(KeyPopTranslationSendPolicy.supportsSendAction(EditorInfo.IME_ACTION_SEARCH))
        assertFalse(KeyPopTranslationSendPolicy.supportsSendAction(EditorInfo.IME_ACTION_GO))
    }
}
