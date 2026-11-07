package com.aetherkeys.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyPopEasyTranslateDirectionsTest {
    @Test fun bothModesCanSelectAllSixDifferentLanguagePairs() {
        val expected=setOf("en" to "ur","ur" to "en","en" to "sd",
            "sd" to "en","ur" to "sd","sd" to "ur")
        assertEquals(expected,KeyPopEasyTranslateDirections.allPairs().toSet())
        assertEquals(6,KeyPopEasyTranslateDirections.allPairs().size)
        for((source,target) in expected)assertTrue(
            KeyPopEasyTranslateDirections.supports(source,target))
        for(code in KeyPopEasyTranslateDirections.codes)
            assertFalse(KeyPopEasyTranslateDirections.supports(code,code))
    }
    @Test fun languageCycleNeverSelectsSameLanguageForBothSides() {
        for(source in 0..2)for(target in 0..2)if(source!=target) {
            assertTrue(KeyPopEasyTranslateDirections.nextSource(source,target)!=target)
            assertTrue(KeyPopEasyTranslateDirections.nextTarget(target,source)!=source)
        }
    }
}
