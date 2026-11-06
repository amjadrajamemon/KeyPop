package com.aetherkeys.keyboard

import org.junit.Assert.*
import org.junit.Test

class KeyPopParityTest {
    @Test fun allFifteenVisualProfilesHaveUniqueIdentities() {
        assertEquals(15,KeyPopSkins.all.size)
        assertEquals(15,KeyPopSkins.all.map{it.name}.distinct().size)
        assertTrue(KeyPopSkins.all.take(10).all{it.name.isNotBlank()})
        assertTrue(KeyPopSkins.all.drop(10).all{it.subtitle.isNotBlank()})
    }
    @Test fun loginsRequireAnExactMatchingOrigin() {
        assertEquals("example.com",VaultTarget.canonical("https://example.com/login"))
        assertTrue(VaultTarget.matches("example.com","https://example.com/account"))
        assertFalse(VaultTarget.matches("example.com","https://example.com.evil.test"))
        assertFalse(VaultTarget.matches("example.com","evil.example.com"))
        assertFalse(VaultTarget.matches("example.com","another.com"))
        assertEquals("",VaultTarget.canonical("http://example.com"))
        assertEquals("",VaultTarget.canonical("user:secret@host.com"))
    }
    @Test fun appPackagesUseExactMatching() {
        assertEquals("com.example.app",VaultTarget.canonical("com.example.app"))
        assertTrue(VaultTarget.matches("com.example.app","com.example.app"))
        assertFalse(VaultTarget.matches("com.example.app","com.example.app.fake"))
    }
    @Test fun everyPremiumSkinHasVisibleKeyColorsAndContrastTargets() {
        for(skin in KeyPopSkins.all) {
            assertNotEquals(0,skin.background)
            assertNotEquals(0,skin.key)
            assertNotEquals(0,skin.ink)
        }
    }
}
