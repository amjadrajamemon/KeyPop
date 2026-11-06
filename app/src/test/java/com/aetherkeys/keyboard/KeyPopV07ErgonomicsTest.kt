package com.aetherkeys.keyboard

import org.junit.Assert.*
import org.junit.Test

class KeyPopV07ErgonomicsTest {
    @Test fun sixOriginalPresetsAreSelectableWithoutCloningThirdPartyAssets() {
        assertEquals(6,KeyboardPresetCatalog.all.size)
        assertEquals((0..5).toList(),KeyboardPresetCatalog.all.map{it.id})
        assertTrue(KeyboardPresetCatalog.all.all{it.name.isNotBlank() && it.description.isNotBlank()})
        assertEquals("Gboard-style · Material",KeyboardPresetCatalog.byId(1)?.name)
        assertNull(KeyboardPresetCatalog.byId(9))
    }
    @Test fun allPresetsHaveFunctionalSpacebarAndEditingKeys() {
        KeyboardPresetCatalog.all.forEach{preset->
            val normal=KeyboardPresetCatalog.foot(preset.id,false)
            assertTrue(normal.contains("SPACE"))
            assertTrue(normal.contains("↵"))
            assertTrue(normal.contains("?123"))
            assertTrue(KeyboardPresetCatalog.keyWeight(preset.id,"SPACE")>3f)
        }
        assertTrue(KeyboardPresetCatalog.foot(2,true).contains("ABC"))
    }
    @Test fun navInsetNeverReducesSystemSafeArea() {
        assertEquals(42,KeyboardSizingRules.safeBottom(42,12))
        assertEquals(12,KeyboardSizingRules.safeBottom(0,12))
        assertEquals(12,KeyboardSizingRules.safeBottom(-1,12))
    }
    @Test fun resizeControlsAreBoundedAndCentered() {
        assertEquals(36,KeyboardSizingRules.keyHeight(-1))
        assertEquals(90,KeyboardSizingRules.keyHeight(200))
        assertEquals(65,KeyboardSizingRules.widthPercent(12))
        assertEquals(100,KeyboardSizingRules.widthPercent(999))
        assertEquals(0,KeyboardSizingRules.bottomLift(-4))
        assertEquals(96,KeyboardSizingRules.bottomLift(121))
        assertEquals(100,KeyboardSizingRules.centeredLeft(400,200,0))
        assertEquals(0,KeyboardSizingRules.centeredLeft(400,200,-999))
        assertEquals(200,KeyboardSizingRules.centeredLeft(400,200,999))
    }
}
