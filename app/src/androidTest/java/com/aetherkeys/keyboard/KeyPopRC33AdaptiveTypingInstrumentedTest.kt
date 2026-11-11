package com.aetherkeys.keyboard

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android dictionary index and Keystore-backed learning tests. */
@RunWith(AndroidJUnit4::class)
class KeyPopRC33AdaptiveTypingInstrumentedTest {
    private val context:Context get()=
        InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun personalCorrectionRejectionSurvivesRecreatingLanguageModel() {
        val prefs=context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
        val oldLearning=prefs.all["learningEnabled"]
        val oldHistory=prefs.all["personalHistoryLearning"]
        prefs.edit().putBoolean("learningEnabled",true)
            .putBoolean("personalHistoryLearning",true).commit()
        val first=KeyPopPersonalLanguageModel(context)
        first.clear()
        try {
            first.feedback(0,"tge","the",false)
            first.feedback(0,"tge","the",false)
            val second=KeyPopPersonalLanguageModel(context)
            assertTrue("Undo feedback did not persist in keyed SQLite model",
                (second.rejectionWeights(0,"tge",listOf("the"))["the"]?:0)>=2)
            first.feedback(0,"tge","the",true)
            assertTrue("An accepted correction should counterbalance undo history",
                (second.rejectionWeights(0,"tge",listOf("the"))["the"]?:0)>=1)
            second.clear()
            assertTrue(second.rejectionWeights(0,"tge",listOf("the")).isEmpty())
        } finally {
            first.clear()
            val edit=prefs.edit()
            if(oldLearning is Boolean)edit.putBoolean("learningEnabled",oldLearning)
            else edit.remove("learningEnabled")
            if(oldHistory is Boolean)edit.putBoolean("personalHistoryLearning",oldHistory)
            else edit.remove("personalHistoryLearning")
            edit.commit()
        }
    }
    @Test fun lexicalLookupHandlesBeginningOmissionsAndGlideRepeatedLetters() {
        val lexicon=KeyPopLexicon(context)
        lexicon.warmup()
        assertTrue(lexicon.isReady())
        assertTrue("hello" in lexicon.suggestions(0,"helo",8))
        assertEquals("hello",lexicon.glide(0,listOf("h","e","l","o")))
        assertEquals("to predict",lexicon.joinedWordCorrection(0,"topredict"))
        assertNull(lexicon.correction(0,"keyboard"))
    }
    @Test fun nativeScriptSuggestionsAndEnglishPrefixCorrectionsAreAvailableOffline() {
        val lexicon=KeyPopLexicon(context)
        lexicon.warmup()
        assertTrue(lexicon.suggestions(1,"معلو",8).contains("معلومات"))
        assertTrue(lexicon.suggestions(2,"مهرب",8).contains("مهرباني"))
        val cases=mapOf(
            "tge" to "the","vreate" to "create",
            "sugetions" to "suggestions","shoukd" to "should",
            "grammer" to "grammar","screeenshots" to "screenshots",
            "worke" to "worked","likew" to "likewise",
            "improvem" to "improvements","sugges" to "suggestions"
        )
        val failures=cases.filter { (input,expected) ->
            expected !in lexicon.suggestions(0,input,8)
        }
        assertTrue("Regression in known user-reported typed stems: $failures",
            failures.isEmpty())
    }
}
