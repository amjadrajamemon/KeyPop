package com.aetherkeys.keyboard

import android.text.InputType
import org.junit.Assert.*
import org.junit.Test

/** Regression suite for reported destructive autocorrection and transfer issues. */
class KeyPopRC9ForensicRegressionTest {
    @Test fun highConfidenceTyposCorrectAndShowAsSuggestions() {
        for((typed,expected) in mapOf(
            "tge" to "the","teh" to "the","shoukd" to "should",
            "mroe" to "more","adn" to "and","screeshots" to "screenshots")) {
            assertEquals("Wrong correction for $typed",expected,
                KeyPopTypingIntelligence.correction(0,typed))
            assertTrue("Suggestion missing for $typed",
                KeyPopTypingIntelligence.suggestions(0,typed,"").contains(expected))
        }
    }
    @Test fun protectValidWordsPluralAndSimilarSpellingsFromReplacement() {
        for(valid in listOf("word","work","more","screenshot","screenshots","words",
            "should","their","there","something","important","using","today")) {
            assertNull("Valid word wrongly replaced: $valid",
                KeyPopTypingIntelligence.correction(0,valid))
        }
        assertEquals("word",KeyPopTypingRules.preservesCase("word","word"))
    }
    @Test fun preserveCapitalizationWhenCorrectingConfirmedTypos() {
        assertEquals("The",KeyPopTypingRules.preservesCase("Tge","the"))
        assertEquals("SHOULD",KeyPopTypingRules.preservesCase("SHOUKD","should"))
        assertEquals("should",KeyPopTypingRules.preservesCase("shoukd","should"))
    }
    @Test fun autocorrectAndPunctuationCanBothBeCompletelyDisabled() {
        val plain=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
        assertFalse(KeyPopTypingRules.autocorrectEnabled(false,plain))
        assertFalse(KeyPopTypingRules.autoPunctuationEnabled(false,plain))
        assertTrue(KeyPopTypingRules.autocorrectEnabled(true,plain))
        assertTrue(KeyPopTypingRules.autoPunctuationEnabled(true,plain))
    }
    @Test fun protectedEditorsNeverGetAutocorrectOrSmartPunctuation() {
        val unsafe=listOf(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
            InputType.TYPE_CLASS_PHONE)
        for(type in unsafe) {
            assertFalse("Autocorrect leaked into special editor $type",
                KeyPopTypingRules.autocorrectEnabled(true,type))
            assertFalse("Punctuation leaked into special editor $type",
                KeyPopTypingRules.autoPunctuationEnabled(true,type))
        }
    }
    @Test fun transferMathHandlesUnknownZeroAndRealProgress() {
        val mb=KeyPopTransferMetrics.MIB
        assertEquals("100.0",KeyPopTransferMetrics.mb(100*mb))
        assertEquals("300.0",KeyPopTransferMetrics.mb(
            KeyPopTransferMetrics.remaining(100*mb,400*mb)))
        assertEquals(25,KeyPopTransferMetrics.percent(100*mb,400*mb))
        assertEquals(0,KeyPopTransferMetrics.percent(0L,0L))
        assertEquals(150L,KeyPopTransferMetrics.etaSeconds(
            100*mb,400*mb,2.0*mb))
        assertNull(KeyPopTransferMetrics.etaSeconds(100*mb,400*mb,0.0))
        assertEquals("2m 30s",KeyPopTransferMetrics.elapsed(150L))
    }
    @Test fun backspaceUndoOnlyRestoresImmediateConfirmedCorrections() {
        assertTrue(KeyPopTypingRules.canUndoRecentCorrection(
            "should ","shoukd","should",600L))
        assertTrue(KeyPopTypingRules.canUndoRecentCorrection(
            "The ","Tge","The",1000L))
        assertFalse(KeyPopTypingRules.canUndoRecentCorrection(
            "should work ","shoukd","should",200L))
        assertFalse(KeyPopTypingRules.canUndoRecentCorrection(
            "should ","shoukd","should",6001L))
        assertFalse(KeyPopTypingRules.canUndoRecentCorrection(
            "word ","shoukd","should",100L))
    }
    @Test fun onlyScreenshotNamesAndDirectoriesQualify() {
        assertTrue(KeyPopRecentScreenshots.isScreenshot("Screenshot_20261107.png","Pictures/"))
        assertTrue(KeyPopRecentScreenshots.isScreenshot("IMG_001.png","Pictures/Screenshots/"))
        assertFalse(KeyPopRecentScreenshots.isScreenshot("IMG_001.png","DCIM/Camera/"))
    }
}
