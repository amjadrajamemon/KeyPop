package com.aetherkeys.keyboard

import org.junit.Assert.*
import org.junit.Test

/** User-reported RC17 screenshot, typing context and gesture regressions. */
class KeyPopRC17ClipboardPredictionSwipeTest {
    @Test fun redmiScreenshotsAndOtherImagesAreDifferentiated() {
        assertTrue(KeyPopRecentScreenshots.isScreenshot(
            "Screenshot_20261108-1230.png","DCIM/Screenshots/"))
        assertTrue(KeyPopRecentScreenshots.isScreenshot(
            "ScreenCapture_123.png","Pictures/"))
        assertFalse(KeyPopRecentScreenshots.isScreenshot(
            "IMG_20261108_1230.jpg","DCIM/Camera/"))
    }
    @Test fun keyboardToolbarSwipesHaveDistinctLeftAndRightDirections() {
        assertEquals(1,KeyPopPagerSwipeRules.direction(-110f,4f,48f))
        assertEquals(-1,KeyPopPagerSwipeRules.direction(110f,4f,48f))
        assertEquals(0,KeyPopPagerSwipeRules.direction(9f,3f,48f))
        assertEquals(0,KeyPopPagerSwipeRules.direction(90f,110f,48f))
    }
    @Test fun commonUserReportedMistakesAreCorrected() {
        val examples=mapOf(
            "keyboardd" to "keyboard","grammer" to "grammar",
            "suggetions" to "suggestions","speling" to "spelling",
            "didnt" to "didn't","vreate" to "create")
        for((typed,expected) in examples)
            assertEquals("Failed for $typed",expected,
                KeyPopTypingIntelligence.correction(0,typed))
        assertNull(KeyPopTypingIntelligence.correction(0,"keyboard"))
        assertNull(KeyPopTypingIntelligence.correction(0,"work"))
    }
    @Test fun sentenceAwareCandidatesAvoidBareVerbAfterAreYou() {
        val suggestions=KeyPopTypingIntelligence.suggestions(
            0,"worri","you",limit=8,
            dictionary=listOf("worry","worried","worrying"),
            beforeCursor="Why are you worri")
        assertTrue(suggestions.contains("worried"))
        assertTrue(suggestions.contains("worrying"))
        assertFalse(suggestions.contains("worry"))
        val next=KeyPopTypingIntelligence.nextWords(0,"you","Why are you ")
        assertEquals(listOf("worried","worrying","okay"),next)
    }
    @Test fun narrowGrammarRulesKeepSubjectAgreement() {
        assertEquals("I am",KeyPopGrammarRules.suggest("I are",0)?.replacement)
        assertEquals("they have",KeyPopGrammarRules.suggest("they has",0)?.replacement)
        assertEquals("she doesn't",KeyPopGrammarRules.suggest("she don't",0)?.replacement)
        assertNull(KeyPopGrammarRules.suggest("they have",0))
    }
}
