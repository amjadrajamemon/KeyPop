package com.aetherkeys.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyPopV091SindhiTest {
    @Test fun bundledSindhiPhrasesWorkWithoutAnyDownload() {
        assertTrue(KeyPopSindhiPhrasebook.size()>0)
        assertEquals("سلام",KeyPopSindhiPhrasebook.translate("en","sd","Hello"))
        assertEquals("Hello",KeyPopSindhiPhrasebook.translate("sd","en","سلام"))
    }

    @Test fun unknownSindhiTranslationIsNotFabricated() {
        assertNull(KeyPopSindhiPhrasebook.translate("en","sd","A unique sentence not in the phrasebook"))
    }
}
