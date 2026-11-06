package com.aetherkeys.keyboard

import org.junit.Assert.*
import org.junit.Test

class KeyPopCustomizationTest {
    @Test fun fiveBaseArrangementsAreAvailable() {
        assertEquals(listOf("QWERTY","AZERTY","QWERTZ","Dvorak","Colemak"),KeyPopLayouts.names)
        assertEquals(3,KeyPopLayouts.languages.size)
    }
    @Test fun customKeysRejectControlCharacters() {
        assertTrue(KeyPopLayouts.validKey("ڻ"))
        assertTrue(KeyPopLayouts.validKey("جھ"))
        assertTrue(KeyPopLayouts.validKey("@"))
        assertFalse(KeyPopLayouts.validKey(""))
        assertFalse(KeyPopLayouts.validKey("\n"))
        assertFalse(KeyPopLayouts.validKey("abcde"))
    }
    @Test fun searchableEmojiCatalogHasUsefulRange() {
        assertTrue(KeyPopEmoji.entries.size >= 150)
        assertTrue(KeyPopEmoji.search("pizza").any { it.symbol == "🍕" })
        assertTrue(KeyPopEmoji.search("keyboard").any { it.symbol == "⌨️" })
        assertTrue(KeyPopEmoji.search("rose").any { it.symbol == "🌹" })
        assertTrue(KeyPopEmoji.search("anabsurdstringnomatch").isEmpty())
        assertTrue(KeyPopEmoji.categories.contains("Favorites"))
    }
}
