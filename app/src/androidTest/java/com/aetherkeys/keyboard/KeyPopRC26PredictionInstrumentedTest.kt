package com.aetherkeys.keyboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Device-level spelling acceptance checks using the bundled full dictionary. */
@RunWith(AndroidJUnit4::class)
class KeyPopRC26PredictionInstrumentedTest {
    private fun lexicon():KeyPopLexicon {
        val result=KeyPopLexicon(
            InstrumentationRegistry.getInstrumentation().targetContext)
        result.warmup()
        return result
    }

    @Test fun twoEditMisspellingsFindDictionarySuggestions() {
        val lexicon=lexicon()
        val suggestions=lexicon.suggestions(0,"sugetions",limit=8)
        assertTrue("Two-edit suggestions typo not recovered: "+suggestions,
            "suggestions" in suggestions)
        assertTrue("First-key substitution not recovered",
            "create" in lexicon.suggestions(0,"vreate",limit=8))
    }

    @Test fun incompleteWordsUseContextRatherThanBareVerb() {
        val lexicon=lexicon()
        val results=lexicon.suggestions(0,"worri",limit=6,
            previous="you",beforeCursor="Why are you worri")
        assertTrue("Need worried: "+results,"worried" in results)
        assertTrue("Need worrying: "+results,"worrying" in results)
        assertFalse("Unnatural bare verb ranked high: "+results,
            "worry" in results.take(3))
    }

    @Test fun ambiguousWordsAreOfferedWithoutDestructiveAutoReplacement() {
        val lexicon=lexicon()
        val results=lexicon.suggestions(0,"worke",limit=8)
        assertTrue("Expected worked/worker: "+results,
            "worked" in results && "worker" in results)
        assertNull("Ambiguous stem must not auto-replace",
            lexicon.correction(0,"worke"))
    }

    @Test fun validWordsAndManuallyAddedNamesAreProtected() {
        val lexicon=lexicon()
        assertNull("Valid word was changed",lexicon.correction(0,"work"))
        assertEquals("the",lexicon.fastCorrection(0,"tge"))
        assertEquals("should",lexicon.fastCorrection(0,"shoukd"))
        assertTrue("Likewise completion unavailable",
            "likewise" in lexicon.suggestions(0,"likew",limit=8))
        assertTrue("Improvements completion unavailable",
            "improvements" in lexicon.suggestions(0,"improvem",limit=8))
    }

    @Test fun fuzzyRankerCountsSwapsAsOneError() {
        assertEquals(1,KeyPopFuzzyRanker.distanceAtMost2("teh","the"))
        assertEquals(2,KeyPopFuzzyRanker.distanceAtMost2("sugetions","suggestions"))
        assertEquals(3,KeyPopFuzzyRanker.distanceAtMost2("abcdef","mnopqr"))
        val ambiguous=listOf(
            KeyPopFuzzyRanker.Match("worked",1,320,10),
            KeyPopFuzzyRanker.Match("worker",1,320,10))
        assertNull(KeyPopFuzzyRanker.automatic(ambiguous))
        assertEquals("create",KeyPopFuzzyRanker.automatic(listOf(
            KeyPopFuzzyRanker.Match("create",1,320,10),
            KeyPopFuzzyRanker.Match("crate",2,190,10))))
    }
}
