package com.aetherkeys.keyboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies the actual APK asset, not a tiny hand-coded test dictionary. */
@RunWith(AndroidJUnit4::class)
class KeyPopRC10DictionarySmokeTest {
    @Test fun bundledWordBankLoadsAndCompletesRealReportedInputs() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val count=context.assets.open("keypop_english_scowl.txt")
            .bufferedReader().useLines { lines -> lines.count() }
        assertTrue("The broad offline English word bank is missing",count>=38000)
        val lexicon=KeyPopLexicon(context)
        for((prefix,wanted) in mapOf(
            "doi" to "doing","worke" to "worked",
            "likew" to "likewise","improvem" to "improvements"
        )) {
            val top=lexicon.suggestions(0,prefix,limit=3)
            assertTrue("The installed APK failed for $prefix: $top",wanted in top)
        }
    }
}
