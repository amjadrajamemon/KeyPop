package com.aetherkeys.keyboard

import org.junit.Assert.*
import org.junit.Test

/** Reports from real-device RC10 review, not just hand-picked typo mapping. */
class KeyPopRC11ContextRegressionTest {
    @Test fun grammarContextRanksParticiplesInsteadOfBareWorry() {
        val sentence="Why are you worri"
        val ranked=KeyPopTypingIntelligence.suggestions(
            0,"worri","you",limit=8,
            dictionary=listOf("worry","worried","worrying","worries","worrisome"),
            wordTiers=mapOf("worry" to 10,"worried" to 10,
                "worrying" to 10,"worries" to 10,"worrisome" to 35),
            beforeCursor=sentence)
        assertEquals("worried",ranked.first())
        assertTrue("Worrying should be in the top suggestions: $ranked",
            ranked.take(3).contains("worrying"))
        assertFalse("Bare worry must be excluded in this context: $ranked",
            ranked.contains("worry"))
    }
    @Test fun beAuxiliaryDoesNotBiasBareInfinitivesAcrossAllContexts() {
        assertTrue(KeyPopPredictionContext.score(
            "Why are you worri","worri","worried") >
            KeyPopPredictionContext.score("Why are you worri","worri","worry"))
        assertTrue(KeyPopPredictionContext.score(
            "You should worri","worri","worry") >
            KeyPopPredictionContext.score("You should worri","worri","worrying"))
    }
    @Test fun neighboringKeyTypoVreateOffersCreate() {
        val choices=KeyPopTypingIntelligence.suggestions(
            0,"vreate","",limit=3,dictionary=listOf("create","great","created"))
        assertEquals("create",choices.first())
        assertEquals("create",KeyPopTypingIntelligence.correction(0,"vreate"))
    }
    @Test fun properCaseUsesEditorContextRatherThanStoredDictionaryCase() {
        assertEquals("please",KeyPopPredictionContext.wordCase(
            "Please","","You are welcome "))
        assertEquals("Please",KeyPopPredictionContext.wordCase(
            "please","","You are welcome. "))
        assertEquals("I",KeyPopPredictionContext.wordCase("i","","and "))
        assertEquals("worked",KeyPopPredictionContext.wordCase(
            "Worked","worke","We "))
        assertEquals("Worked",KeyPopPredictionContext.wordCase(
            "worked","Worke",""))
        assertEquals("WORKED",KeyPopPredictionContext.wordCase(
            "worked","WORKE","this "))
    }
    @Test fun selectedSuggestionsDoNotGlueToNextWordOrDoubleSpaces() {
        assertEquals("worked ",KeyPopPredictionContext.committedWord("worked",""))
        assertEquals("worked ",KeyPopPredictionContext.committedWord("worked","next"))
        assertEquals("worked",KeyPopPredictionContext.committedWord("worked"," next"))
        assertEquals("worked",KeyPopPredictionContext.committedWord("worked",", next"))
        assertEquals("worked",KeyPopPredictionContext.committedWord("worked","."))
    }
    @Test fun punctuationAndToolbarPolicies() {
        assertTrue(KeyPopPredictionContext.startsSentence("Hello?  "))
        assertTrue(KeyPopPredictionContext.startsSentence("كيف الحال؟ "))
        assertFalse(KeyPopPredictionContext.startsSentence("why are you "))
        assertTrue(KeyPopPredictionContext.toolbarVisible(true,true))
        assertTrue(KeyPopPredictionContext.toolbarVisible(true,false))
        assertFalse(KeyPopPredictionContext.toolbarVisible(false,true))
        assertTrue(KeyPopPredictionContext.toolbarVisible(false,false))
    }
}
