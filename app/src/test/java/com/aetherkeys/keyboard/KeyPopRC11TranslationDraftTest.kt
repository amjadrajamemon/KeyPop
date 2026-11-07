package com.aetherkeys.keyboard

import org.junit.Assert.*
import org.junit.Test

class KeyPopRC11TranslationDraftTest {
    @Test fun draftIsIsolatedAndEditsCorrectly() {
        assertTrue(KeyPopTranslationDraft.handles("SPACE"))
        assertTrue(KeyPopTranslationDraft.handles("⌫"))
        assertTrue(KeyPopTranslationDraft.handles("↵"))
        assertTrue(KeyPopTranslationDraft.handles("a"))
        assertFalse(KeyPopTranslationDraft.handles("🌐"))
        assertFalse(KeyPopTranslationDraft.handles("?123"))
        assertEquals("Hello",KeyPopTranslationDraft.edit("","h",autoCap=true)
            .let {KeyPopTranslationDraft.edit(it,"e")}.let {
                KeyPopTranslationDraft.edit(it,"l")
            }.let{KeyPopTranslationDraft.edit(it,"l")}.let{
                KeyPopTranslationDraft.edit(it,"o")
            })
        assertEquals("Hello ",KeyPopTranslationDraft.edit("Hello","SPACE"))
        assertEquals("Hello ",KeyPopTranslationDraft.edit("Hello ","SPACE"))
        assertEquals("Hello",KeyPopTranslationDraft.edit("Hello!","⌫"))
        assertEquals("Create",KeyPopPredictionContext.wordCase(
            "create","vreate",""))
    }
    @Test fun punctuationAddsOneSpaceAndCapitalizesSentenceOpening() {
        assertEquals("Hello. ",KeyPopTranslationDraft.edit("Hello ","." ))
        assertEquals("Hello. W",KeyPopTranslationDraft.edit("Hello. ","w"))
        assertEquals("Are you worried? ",KeyPopTranslationDraft.edit("Are you worried","?"))
        assertEquals("3.",KeyPopTranslationDraft.edit("3","."))
        assertEquals("Hello.",KeyPopTranslationDraft.edit("Hello",".",
            autoPunctuation=false))
    }
}
