package com.aetherkeys.keyboard

import org.junit.Assert.*
import org.junit.Test

class KeyPopV08IntegratedTest {
    @Test fun longPressAlternatesAreIndependentOfHintsAndGlide() {
        assertTrue(KeyPopAlternates.forKey("a",0).contains("á"))
        assertTrue(KeyPopAlternates.forKey("e",0).contains("é"))
        assertTrue(KeyPopAlternates.forKey("ت",1).contains("ٹ"))
        assertTrue(KeyPopAlternates.forKey("ت",2).contains("ٺ"))
        assertTrue(KeyPopAlternates.forKey("0",0).contains("°"))
        assertTrue(KeyPopAlternates.forKey("⌫",0).isEmpty())
    }
    @Test fun punctuationKeysAreDedicatedAcrossAllPresets() {
        for(p in KeyboardPresetCatalog.all) {
            val normal=KeyboardPresetCatalog.foot(p.id,false)
            assertTrue(p.name,normal.contains("."))
            if(p.id==KeyboardPresetCatalog.MATERIAL) {
                assertTrue(p.name,normal.contains("☺"))
                assertTrue(p.name,normal.contains(","))
                assertFalse(p.name,normal.contains("?"))
                assertTrue(KeyPopAlternates.forKey(".",0).contains("?"))
            } else {
                // Other presets still expose comma either directly or by holding
                // the emoji/comma key, while question mark lives on the period hold.
                assertTrue(p.name,normal.contains(",") || normal.contains("☺,"))
                assertFalse(p.name,normal.contains("?"))
                assertTrue(KeyPopAlternates.forKey(".",0).contains("?"))
            }
        }
    }
    @Test fun nextWordPredictionsRemainAvailableOnEmptyPrefix() {
        assertEquals("morning",KeyPopTypingIntelligence.nextWords(0,"good").first())
        assertTrue(KeyPopTypingIntelligence.nextWords(1,"آپ").isNotEmpty())
        assertTrue(KeyPopTypingIntelligence.nextWords(2,"توهان").isNotEmpty())
    }
    @Test fun grammarFixesAreVisibleAndConservative() {
        assertEquals("I am",KeyPopGrammarRules.suggest("i is",0)?.replacement)
        assertEquals("he has",KeyPopGrammarRules.suggest("he have",0)?.replacement)
        assertEquals("the",KeyPopGrammarRules.suggest("teh",0)?.replacement)
        assertNull(KeyPopGrammarRules.suggest("normal sentence",0))
        assertNull(KeyPopGrammarRules.suggest("توهان",2))
    }
    @Test fun typedExampleTranslatesFullyOfflineAcrossThreeLanguages() {
        assertEquals("آپ کیسے ہیں؟",
            KeyPopSindhiPhrasebook.translate("en","ur","How are you ?"))
        assertEquals("توهان ڪيئن آهيو؟",
            KeyPopSindhiPhrasebook.translate("en","sd","How are you?"))
        assertEquals("How are you?",
            KeyPopSindhiPhrasebook.translate("sd","en","توهان ڪيئن آهيو؟"))
        assertNull(KeyPopSindhiPhrasebook.translate("en","sd","Quantum gravitation physics"))
    }
    @Test fun bundledScriptDetectionDoesNotRequireInternet() {
        assertEquals(0,KeyPopTextLanguage.detect("How are you?",1))
        assertEquals(1,KeyPopTextLanguage.detect("آپ کیسے ہیں؟",0))
        assertEquals(2,KeyPopTextLanguage.detect("توهان ڪيئن آهيو؟",0))
    }
}
