package com.aetherkeys.keyboard

import org.junit.Assert.*
import org.junit.Test

/** Regression: Redmi Note 13 / Android navigation buttons sometimes became
 * overlapped when IME inset events were delivered late or toggled to zero. */
class KeyPopRC16NavigationInsetRegressionTest {
    @Test fun initialInsetsReserveNavigationSpace() {
        assertEquals(42,KeyboardSizingRules.stableSafeBottom(0,42,14))
        assertEquals(48,KeyboardSizingRules.stableSafeBottom(0,0,48))
        assertEquals(14,KeyboardSizingRules.stableSafeBottom(0,0,14))
    }
    @Test fun missingOrLateInsetMustNotCollapsePreviousSafeBottom() {
        assertEquals(48,KeyboardSizingRules.stableSafeBottom(48,0,14))
        assertEquals(48,KeyboardSizingRules.stableSafeBottom(48,-4,14))
        assertEquals(64,KeyboardSizingRules.stableSafeBottom(48,64,14))
        assertEquals(64,KeyboardSizingRules.stableSafeBottom(64,12,14))
    }
    @Test fun orientationResetAcceptsNewNavigationFloor() {
        assertEquals(14,KeyboardSizingRules.stableSafeBottom(0,0,14))
        assertEquals(26,KeyboardSizingRules.stableSafeBottom(0,26,14))
    }
    @Test fun resizePanelNeverExceedsHalfScreenHeight() {
        assertEquals(200,KeyboardSizingRules.boundedPanelHeight(400,900))
        assertEquals(360,KeyboardSizingRules.boundedPanelHeight(1200,360))
        assertEquals(75,KeyboardSizingRules.boundedPanelHeight(150,1000))
        assertEquals(1,KeyboardSizingRules.boundedPanelHeight(0,100))
        assertEquals(1,KeyboardSizingRules.boundedPanelHeight(100,0))
    }
}
