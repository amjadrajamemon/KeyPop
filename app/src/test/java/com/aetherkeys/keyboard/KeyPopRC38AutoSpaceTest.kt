package com.aetherkeys.keyboard

import org.junit.Assert.*
import org.junit.Test

/** RC38: auto-spacing must work after BOTH current-word correction and
 * next-word prediction, including Chrome's combined search/address field. */
class KeyPopRC38AutoSpaceTest {
    @Test fun acceptedCurrentWordInsertsOneSpace() {
        assertEquals("corrected ",KeyPopPredictionContext.committedWord("corrected",""))
        assertEquals("corrected ",KeyPopPredictionContext.committedWord("corrected","next"))
        assertEquals("corrected",KeyPopPredictionContext.committedWord("corrected"," next"))
        assertEquals("corrected",KeyPopPredictionContext.committedWord("corrected","."))
        assertEquals("corrected",KeyPopPredictionContext.committedWord("corrected",", more"))
    }

    @Test fun nextWordPredictionGetsSameSpaceBehavior() {
        assertEquals("tomorrow ",KeyPopPredictionContext.committedWord("tomorrow",""))
        assertEquals("مهرباني ",KeyPopPredictionContext.committedWord("مهرباني",""))
        assertEquals("معلومات ",KeyPopPredictionContext.committedWord("معلومات",""))
    }

    @Test fun combinedChromeSearchBoxAllowsWordsButProtectsUrls() {
        assertTrue(KeyPopPredictionContext.searchWordsAllowSpacing("How do I ",""))
        assertTrue(KeyPopPredictionContext.searchWordsAllowSpacing("weather in ","next"))
        assertTrue(KeyPopPredictionContext.searchWordsAllowSpacing("some query",null))
        assertFalse(KeyPopPredictionContext.searchWordsAllowSpacing("www.example.com",""))
        assertFalse(KeyPopPredictionContext.searchWordsAllowSpacing("https://example.org/p",""))
        assertFalse(KeyPopPredictionContext.searchWordsAllowSpacing("example","/path"))
        assertFalse(KeyPopPredictionContext.searchWordsAllowSpacing("example",".com"))
        assertTrue(KeyPopPredictionContext.searchWordsAllowSpacing("example"," more"))
    }

    @Test fun deferredBoundaryRestoresOnlyMissingSpace() {
        assertTrue(KeyPopPredictionContext.needsBoundarySpace("accepted","accepted"))
        assertTrue(KeyPopPredictionContext.needsBoundarySpace("I accepted","accepted"))
        assertTrue(KeyPopPredictionContext.needsBoundarySpace("I معلومات","معلومات"))
        assertFalse(KeyPopPredictionContext.needsBoundarySpace("I accepted ","accepted"))
        assertFalse(KeyPopPredictionContext.needsBoundarySpace("otheraccepted","accepted"))
        assertFalse(KeyPopPredictionContext.needsBoundarySpace("elsewhere","accepted"))
        assertFalse(KeyPopPredictionContext.needsBoundarySpace("","accepted"))
    }
}
