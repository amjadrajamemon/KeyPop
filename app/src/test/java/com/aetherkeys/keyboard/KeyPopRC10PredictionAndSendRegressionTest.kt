package com.aetherkeys.keyboard

import org.junit.Assert.*
import org.junit.Test

/** Reports from the Redmi Note 14 RC9 screenshots plus generalized regressions. */
class KeyPopRC10PredictionAndSendRegressionTest {
    @Test fun userReportedIncompleteWordsHaveUsefulTopThreeCompletions() {
        val cases=mapOf(
            "doi" to "doing",
            "worke" to "worked",
            "likew" to "likewise",
            "improvem" to "improvements",
            "translat" to "translate",
            "predicti" to "prediction",
            "downloa" to "download",
            "sugges" to "suggestions"
        )
        for((fragment,expected) in cases) {
            val candidates=KeyPopTypingIntelligence.suggestions(0,fragment,"",limit=3)
            assertTrue("Missing $expected for $fragment: $candidates",expected in candidates)
        }
    }

    @Test fun broadTieredLexiconPrefersFamiliarWordsWithoutForcingReplacements() {
        val candidates=KeyPopTypingIntelligence.suggestions(
            0,"doi","",limit=3,
            dictionary=listOf("doilies","doing","doily"),
            wordTiers=mapOf("doilies" to 35,"doing" to 10,"doily" to 35)
        )
        assertEquals("doing",candidates.first())
        for(original in listOf("doi","worke","likew","improvem",
                "work","word","screenshots","improvements","likewise"))
            assertNull("Incomplete or legitimate word was forcibly corrected: $original",
                KeyPopTypingIntelligence.correction(0,original))
    }

    @Test fun sendFollowsVisibleTranslationPairNotStaleGlobalSetting() {
        val selected=KeyPopTranslationSendPolicy.select(
            panelOpen=true,selectedSource=-1,selectedTarget=2,
            detectedSource=0,settingsTarget=1
        )
        assertEquals(0,selected.source)
        assertEquals(2,selected.target)
        val manual=KeyPopTranslationSendPolicy.select(true,2,0,0,1)
        assertEquals(2,manual.source)
        assertEquals(0,manual.target)
        val hidden=KeyPopTranslationSendPolicy.select(false,2,2,0,1)
        assertEquals(0,hidden.source)
        assertEquals(1,hidden.target)
    }

    @Test fun sendOnlyReusesPreviewWhenAllRelevantPropertiesMatch() {
        assertTrue(KeyPopTranslationSendPolicy.canReusePreview(
            true,"مان ٺيڪ آهيان","I am fine","I am fine",
            "en-sd","en-sd",false,false))
        assertFalse(KeyPopTranslationSendPolicy.canReusePreview(
            true,"مان ٺيڪ آهيان","I am fine","I was fine",
            "en-sd","en-sd",false,false))
        assertFalse(KeyPopTranslationSendPolicy.canReusePreview(
            true,"مان ٺيڪ آهيان","I am fine","I am fine",
            "en-sd","en-ur",false,false))
        assertFalse(KeyPopTranslationSendPolicy.canReusePreview(
            true,"مان ٺيڪ آهيان","I am fine","I am fine",
            "en-sd","en-sd",true,false))
    }

    @Test fun successfulTransferShowsNoPhantomRemainingBytes() {
        val s=KeyPopTransferMetrics.completedSummary(
            verifiedBytes=(544.6*KeyPopTransferMetrics.MIB).toLong(),
            selectedCount=2,elapsedSeconds=13L)
        assertTrue(s.contains("2 selected model packs"))
        assertTrue(s.contains("Verified files on device: 544.6 MB"))
        assertTrue(s.contains("Remaining download: none"))
        assertFalse(s.contains("588.0"))
        assertFalse(s.contains("43.4"))
    }
}
