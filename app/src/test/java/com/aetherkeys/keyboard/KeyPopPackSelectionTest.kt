package com.aetherkeys.keyboard

import org.junit.Assert.*
import org.junit.Test

class KeyPopPackSelectionTest {
    @Test fun eachDirectionMapsToOnlyRequiredModels() {
        assertEquals(listOf("en","ur"),KeyPopPackSelection.required(setOf("en-ur")))
        assertEquals(listOf("en-sd"),KeyPopPackSelection.required(setOf("en-sd")))
        assertEquals(listOf("sd-en"),KeyPopPackSelection.required(setOf("sd-en")))
        assertEquals(listOf("ur-sd"),KeyPopPackSelection.required(setOf("sd-ur")))
    }
    @Test fun sharedPacksDownloadedOnlyOnceForBothDirections() {
        assertEquals(listOf("en","ur"),
            KeyPopPackSelection.required(setOf("en-ur","ur-en")))
        assertEquals(listOf("ur-sd"),
            KeyPopPackSelection.required(setOf("ur-sd","sd-ur")))
        assertEquals(5,KeyPopPackSelection.required(KeyPopPackSelection.directions.keys).size)
    }
    @Test fun unknownDirectionIsIgnoredAndEmptySelectionBlocked() {
        assertTrue(KeyPopPackSelection.required(emptySet()).isEmpty())
        assertTrue(KeyPopPackSelection.required(setOf("unknown-pair")).isEmpty())
        assertEquals(6,KeyPopPackSelection.directions.size)
    }
    @Test fun suggestionsIncludeFuzzyPrefixAndContext() {
        assertTrue(KeyPopTypingIntelligence.suggestions(0,"keybor","").contains("keyboard"))
        assertEquals("morning",KeyPopTypingIntelligence.nextWords(0,"good").first())
        assertEquals("you",KeyPopTypingIntelligence.nextWords(0,"thank").first())
    }
}
