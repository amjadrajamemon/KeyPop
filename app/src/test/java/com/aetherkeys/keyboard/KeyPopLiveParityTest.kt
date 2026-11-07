package com.aetherkeys.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards against the duplicated keys and frozen suggestions seen on devices. */
class KeyPopLiveParityTest {
    @Test fun noFooterDuplicatesTheBackspaceOnTypingRow() {
        for(preset in KeyboardPresetCatalog.all) {
            val letters=KeyboardPresetCatalog.foot(preset.id,false)
            val symbols=KeyboardPresetCatalog.foot(preset.id,true)
            assertFalse("Duplicate backspace on ${preset.name}", "⌫" in letters)
            assertFalse("Duplicate backspace on symbol footer", "⌫" in symbols)
            assertEquals(1,letters.count{it=="SPACE"})
            assertEquals(1,symbols.count{it=="SPACE"})
            assertEquals(1,letters.count{it=="↵"})
        }
    }

    @Test fun gboardFooterUsesCompactPunctuationAndLanguageKeys() {
        assertEquals(listOf("?123","☺,","🌐","SPACE",".","↵"),
            KeyboardPresetCatalog.foot(KeyboardPresetCatalog.MATERIAL,false))
        assertEquals(10,KeyPopRowParity.digits.distinct().size)
    }

    @Test fun englishCompletionsChangeForEachPrefix() {
        val keyb=KeyPopTypingIntelligence.suggestions(0,"keyb","")
        val trans=KeyPopTypingIntelligence.suggestions(0,"trans","")
        assertTrue("keyboard" in keyb)
        assertTrue("translate" in trans)
        assertFalse(keyb==trans)
        assertEquals("you",KeyPopTypingIntelligence.nextWords(0,"thank").first())
    }

    @Test fun urduAndSindhiOfferOfflineCompletions() {
        assertTrue("مهرباني" in KeyPopTypingIntelligence.suggestions(2,"مهرب",""))
        assertTrue("شکریہ" in KeyPopTypingIntelligence.suggestions(1,"شک",""))
    }
}
