package com.aetherkeys.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyPopNumberRowParityTest {
    @Test fun alphabeticNumberRowAppearsOnlyWhenEnabled() {
        assertEquals(10,KeyPopRowParity.digits.size)
        assertEquals(10,KeyPopRowParity.digits.distinct().size)
        assertTrue(KeyPopRowParity.hasNumberRow(true,false,false))
        assertFalse(KeyPopRowParity.hasNumberRow(false,false,false))
        assertFalse(KeyPopRowParity.hasNumberRow(true,true,false))
        assertFalse(KeyPopRowParity.hasNumberRow(true,false,true))
    }
    @Test fun dedicatedNumbersSuppressDuplicateLetterNumberHints() {
        assertEquals(listOf("%","é"),
            KeyPopRowParity.alternates(listOf("%","3","é"),"e",true))
        assertEquals(listOf("3","é"),
            KeyPopRowParity.alternates(listOf("3","é"),"e",false))
        assertTrue(KeyPopRowParity.alternates(listOf("1"),"q",true).isEmpty())
    }
    @Test fun longHoldDotContainsSymbolsForAllLanguages() {
        for(lang in 0..2) {
            val symbols=KeyPopAlternates.forKey(".",lang)
            assertEquals(12,symbols.size)
            assertEquals(12,symbols.distinct().size)
            assertTrue("?" in symbols || "؟" in symbols)
        }
    }
}
