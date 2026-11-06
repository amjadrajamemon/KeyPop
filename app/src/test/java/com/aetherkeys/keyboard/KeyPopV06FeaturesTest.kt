package com.aetherkeys.keyboard

import org.junit.Assert.*
import org.junit.Test

class KeyPopV06FeaturesTest {
    @Test fun offlineLanguagePredictionsCoverEnglishUrduAndSindhi() {
        assertTrue(KeyPopTypingIntelligence.suggestions(0,"ple","").contains("please"))
        assertTrue(KeyPopTypingIntelligence.suggestions(1,"شکر","").contains("شکریہ"))
        assertTrue(KeyPopTypingIntelligence.suggestions(2,"مهرب","").contains("مهرباني"))
    }
    @Test fun contextCanRankExpectedNextWord() {
        val result=KeyPopTypingIntelligence.suggestions(0,"mo","good")
        assertEquals("morning",result.first())
        assertTrue(KeyPopTypingIntelligence.contextualReplies(0,"How are you?")
            .any{it.contains("confirm")||it.contains("Yes")})
    }
    @Test fun conservativeCorrectionAndNoUnsafeEmptyCompletion() {
        assertEquals("message",KeyPopTypingIntelligence.correction(0,"mesage"))
        assertEquals(null,KeyPopTypingIntelligence.correction(0,"hi"))
        assertTrue(KeyPopTypingIntelligence.suggestions(0,"","").isEmpty())
    }
    @Test fun offlineWritingStaysDeterministicAndLocal() {
        assertEquals("I cannot attend.",
            KeyPopWritingTools.rewrite("i can't attend.","Formal",0))
        assertEquals("Please review the letter.",
            KeyPopWritingTools.rewrite("review the letter.","Polite",0))
        assertEquals("براہ کرم مدد کریں",
            KeyPopWritingTools.rewrite("مدد کریں","Polite",1))
    }
    @Test fun sindhiPhrasebookDoesNotPretendToTranslateUnknownSentences() {
        assertTrue(KeyPopSindhiPhrasebook.size()>=20)
        assertEquals("مهرباني",KeyPopSindhiPhrasebook.translate("en","sd","Thank you"))
        assertEquals("How are you?",KeyPopSindhiPhrasebook.translate("sd","en","توهان ڪيئن آهيو؟"))
        assertNull(KeyPopSindhiPhrasebook.translate("en","sd","Quantum computer reliability"))
    }
    @Test fun newGeometryAndMediaModelRemainBounded() {
        assertTrue(KeyPopKeyGeometry.valid(2,2,9))
        assertFalse(KeyPopKeyGeometry.valid(3,0,0))
        assertFalse(KeyPopKeyGeometry.valid(0,9,0))
        val clip=KeyPopImageClip("abc",1L,"image/gif",true,"Happy birthday.gif")
        assertEquals("Happy birthday.gif",clip.label)
    }
    @Test fun diagnosticPercentilesContainNoTypedText() {
        assertEquals(0L,KeyPopDiagnostics.percentile(emptyList(),95))
        assertEquals(5L,KeyPopDiagnostics.percentile(listOf(2,4,5,7,9),50))
        assertEquals(9L,KeyPopDiagnostics.percentile(listOf(2,4,5,7,9),95))
    }
}
