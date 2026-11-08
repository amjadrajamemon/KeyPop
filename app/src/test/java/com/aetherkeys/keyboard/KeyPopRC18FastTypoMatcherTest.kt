package com.aetherkeys.keyboard

import org.junit.Assert.*
import org.junit.Test

/** Fast correction candidate filter must preserve normal spelling behavior. */
class KeyPopRC18FastTypoMatcherTest {
    @Test fun oneEditCandidatesAcceptTranspositionsAndNearbyKeystrokes() {
        assertTrue(KeyPopTypingIntelligence.oneEditAway("teh","the"))
        assertTrue(KeyPopTypingIntelligence.oneEditAway("vreate","create"))
        assertTrue(KeyPopTypingIntelligence.oneEditAway("grammer","grammar"))
        assertTrue(KeyPopTypingIntelligence.oneEditAway("worke","worked"))
        assertTrue(KeyPopTypingIntelligence.oneEditAway("worr","worri"))
        assertFalse(KeyPopTypingIntelligence.oneEditAway("keyboard","keyboard"))
        assertFalse(KeyPopTypingIntelligence.oneEditAway("keyboard","kud"))
        assertFalse(KeyPopTypingIntelligence.oneEditAway("worry","worried"))
    }
    @Test fun wordPredictionsRespectAuxiliaryContext() {
        val result=KeyPopTypingIntelligence.suggestions(
            0,"worri","you",limit=8,
            dictionary=listOf("worry","worried","worrying"),
            beforeCursor="Why are you worri")
        assertTrue(result.contains("worried"))
        assertTrue(result.contains("worrying"))
        assertFalse(result.contains("worry"))
    }
}