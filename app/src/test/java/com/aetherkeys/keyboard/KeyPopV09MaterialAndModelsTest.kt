package com.aetherkeys.keyboard

import org.junit.Assert.*
import org.junit.Test

class KeyPopV09MaterialAndModelsTest {
    @Test fun materialPresetUsesTealAndVisibleNumberRow() {
        val preset=KeyboardPresetCatalog.byId(KeyboardPresetCatalog.MATERIAL)!!
        assertEquals(10,preset.skin)
        assertTrue(preset.numberRow)
        assertTrue(preset.suggestions)
        assertTrue(preset.name.contains("Teal"))
        val teal=KeyPopSkins.all[10]
        assertTrue(teal.name.contains("Glass"))
        assertTrue(android.graphics.Color.green(teal.background)>
            android.graphics.Color.red(teal.background))
    }
    @Test fun screenshotFooterKeepsCriticalActions() {
        val normal=KeyboardPresetCatalog.foot(KeyboardPresetCatalog.MATERIAL,false)
        assertEquals("?123",normal.first())
        assertTrue(normal.contains("☺,"))
        assertTrue(normal.contains("🌐"))
        assertTrue(normal.contains("SPACE"))
        assertTrue(normal.contains("."))
        assertTrue(normal.contains("?"))
        assertTrue(normal.contains("↵"))
    }
    @Test fun materialSymbolHintsAndLongPressAreDecoupledFromVisualSkin() {
        assertEquals("%",KeyPopMaterialHints.candidates("q",0,true).first())
        assertEquals("\\",KeyPopMaterialHints.candidates("w",0,true).first())
        assertTrue(KeyPopMaterialHints.candidates("e",0,true).contains("é"))
        assertTrue(KeyPopMaterialHints.candidates("ت",2,true).contains("ٺ"))
        assertEquals(KeyPopAlternates.forKey("a",0),
            KeyPopMaterialHints.candidates("a",0,false))
    }
    @Test fun languagePackSupportCannotBeMisrepresented() {
        assertTrue(KeyPopLanguageModels.knownText("en"))
        assertTrue(KeyPopLanguageModels.knownText("ur"))
        assertFalse(KeyPopLanguageModels.knownText("sd"))
        assertEquals("Sindhi",KeyPopLanguageModels.languages["sd"])
    }
}
