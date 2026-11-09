package com.aetherkeys.keyboard

import org.junit.Assert.*
import org.junit.Test

/** Default control geometry is shared by every independent keyset and theme. */
class KeyPopRC26ControlKeySizingTest {
    @Test fun fivePrimaryActionKeysAreLargerThanLetterKeysInAllPresets() {
        for(preset in KeyboardPresetCatalog.all) {
            for(label in listOf("⇧","⌫","↵",",",".")) {
                val weight=KeyboardPresetCatalog.keyWeight(preset.id,label)
                assertTrue("${preset.name}: $label must have a wide touch target, got $weight",
                    weight>=1.25f)
                assertTrue("${preset.name}: $label must exceed the ordinary letter width",
                    weight>1f)
            }
        }
    }

    @Test fun enlargedControlsRetainUsableSpacebarAcrossKeysets() {
        for(preset in KeyboardPresetCatalog.all)for(keyset in KeyPopKeysets.all) {
            val footer=KeyPopKeysets.footer(keyset.id,false)
            val weights=footer.map{KeyboardPresetCatalog.keyWeight(preset.id,it)}
            val sum=weights.sum()
            val space=weights[footer.indexOf("SPACE")]/sum
            assertTrue("Spacebar too narrow for ${preset.name} / ${keyset.name}",
                space>=.29f)
            assertTrue("Punctuation keys must both be dedicated",
                "," in footer && "." in footer)
            assertTrue("Enter remains a dedicated footer key","↵" in footer)
        }
    }

    @Test fun specialKeySymbolsStayLegibleAtDefaultFontSize() {
        listOf("⇧","⌫","↵",",",".").forEach {
            assertTrue(it,KeyboardPresetCatalog.keyTextSize(it,it,19f)>19f)
        }
        assertEquals(19f,KeyboardPresetCatalog.keyTextSize("a","a",19f),.001f)
        assertTrue("Send action caption must fit",
            KeyboardPresetCatalog.keyTextSize("↵","⇄ Send",19f)<19f)
    }
}
