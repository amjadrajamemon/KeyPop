package com.aetherkeys.keyboard

import org.junit.Assert.*
import org.junit.Test

/** A theme change cannot silently replace layout or key content, and vice versa. */
class KeyPopRC22CrossCompatibleStyleTest {
    @Test fun themeGeometryAndKeysetRemainIndependentAcrossEveryCombination() {
        for(skin in KeyPopSkins.all.indices)for(geometry in KeyboardPresetCatalog.all) {
            for(pack in KeyPopKeysets.all) {
                val selected=KeyPopComposition(skin,geometry.id,pack.id)
                assertEquals(270,KeyPopSkins.all.size*
                    KeyboardPresetCatalog.all.size*KeyPopKeysets.all.size)
                assertEquals(geometry.id,selected.withSkin((skin+1)%15).geometry)
                assertEquals(pack.id,selected.withSkin((skin+1)%15).keyset)
                assertEquals(skin,selected.withGeometry(KeyboardPresetCatalog.MATERIAL).skin)
                assertEquals(pack.id,selected.withGeometry(KeyboardPresetCatalog.MATERIAL).keyset)
                assertEquals(geometry.id,selected.withKeyset(KeyPopKeysets.GBOARD).geometry)
                assertEquals(skin,selected.withKeyset(KeyPopKeysets.GBOARD).skin)
            }
        }
    }
    @Test fun gboardKeysetOnSamsungAndMicrosoftGeometryKeepsSymbolsAndFooter() {
        for(geometry in listOf(KeyboardPresetCatalog.ONE_UI,KeyboardPresetCatalog.FLOW)) {
            val mix=KeyPopComposition(12,geometry,KeyPopKeysets.GBOARD)
            assertEquals(geometry,mix.geometry)
            assertEquals(12,mix.skin)
            assertEquals("%",KeyPopKeysets.alternates(mix.keyset,"q",0).first())
            assertTrue(KeyPopKeysets.alternates(mix.keyset,".",0).contains("?"))
            assertEquals(listOf("?123",",","☺","🌐","SPACE",".","↵"),
                KeyPopKeysets.footer(mix.keyset,false))
        }
    }
    @Test fun keysetsHaveDistinctLongPressAndFooterLayouts() {
        assertNotEquals(KeyPopKeysets.footer(KeyPopKeysets.GBOARD,false),
            KeyPopKeysets.footer(KeyPopKeysets.SWIFTKEY,false))
        assertNotEquals(KeyPopKeysets.footer(KeyPopKeysets.GBOARD,false),
            KeyPopKeysets.footer(KeyPopKeysets.SAMSUNG,false))
        assertNotEquals(KeyPopKeysets.symbolRows(KeyPopKeysets.GBOARD,false),
            KeyPopKeysets.symbolRows(KeyPopKeysets.SAMSUNG,false))
        assertNotEquals(KeyPopKeysets.alternates(KeyPopKeysets.GBOARD,"q",0).first(),
            KeyPopKeysets.alternates(KeyPopKeysets.SWIFTKEY,"q",0).first())
        assertTrue(KeyPopKeysets.alternates(KeyPopKeysets.GBOARD,"ت",2).contains("ٺ"))
    }
    @Test fun oldPresetMapsToEquivalentKeysetOnMigration() {
        assertEquals(KeyPopKeysets.GBOARD,KeyPopKeysets.legacy(KeyboardPresetCatalog.MATERIAL))
        assertEquals(KeyPopKeysets.SWIFTKEY,KeyPopKeysets.legacy(KeyboardPresetCatalog.FLOW))
        assertEquals(KeyPopKeysets.SAMSUNG,KeyPopKeysets.legacy(KeyboardPresetCatalog.ONE_UI))
        assertFalse(KeyPopKeysets.valid(0))
    }
}
