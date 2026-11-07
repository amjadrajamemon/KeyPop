package com.aetherkeys.keyboard

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Test the handoff intent/URL locally; do not actually send data to Google. */
@RunWith(AndroidJUnit4::class)
class GoogleTranslateHandoffTest {
    @Test fun appHandoffTargetsOnlyGoogleTranslate() {
        val example="Please translate this into Urdu."
        val intent=KeyPopGoogleTranslateHandoff.appIntent(example)
        assertEquals(Intent.ACTION_SEND,intent.action)
        assertEquals("text/plain",intent.type)
        assertEquals("com.google.android.apps.translate",intent.`package`)
        assertEquals(example,intent.getStringExtra(Intent.EXTRA_TEXT))
    }
    @Test fun webHandoffEncodesLanguagesAndSpecialText() {
        val text="Hello & آپ کیسے ہیں؟"
        val uri=KeyPopGoogleTranslateHandoff.webUri(text,"en","ur")
        assertEquals("https",uri.scheme)
        assertEquals("translate.google.com",uri.host)
        assertEquals("en",uri.getQueryParameter("sl"))
        assertEquals("ur",uri.getQueryParameter("tl"))
        assertEquals(text,uri.getQueryParameter("text"))
        assertTrue(uri.toString().contains("op=translate"))
    }
    @Test fun autoToSindhiExternalChoice() {
        val uri=KeyPopGoogleTranslateHandoff.webUri("Good evening","auto","sd")
        assertEquals("auto",uri.getQueryParameter("sl"))
        assertEquals("sd",uri.getQueryParameter("tl"))
    }
}
