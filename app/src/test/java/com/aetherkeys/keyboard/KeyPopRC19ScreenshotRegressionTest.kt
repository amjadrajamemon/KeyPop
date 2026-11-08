package com.aetherkeys.keyboard

import org.junit.Assert.*
import org.junit.Test

/** Redmi Note 13 and Android 15 screenshot naming regression. */
class KeyPopRC19ScreenshotRegressionTest {
    @Test fun commonAndroidAndRedmiScreenshotNamesAreRecognized() {
        assertTrue(KeyPopRecentScreenshots.isScreenshot(
            "Screenshot_2026-11-08-13-45-29-001_com.whatsapp.png","DCIM/Screenshots/"))
        assertTrue(KeyPopRecentScreenshots.isScreenshot(
            "Screenshot_20261108_134529.png","Pictures/Screenshots/"))
        assertTrue(KeyPopRecentScreenshots.isScreenshot(
            "ScreenCapture_11-08-2026.png","DCIM/"))
        assertTrue(KeyPopRecentScreenshots.isScreenshot(
            "IMG_5344.png","Pictures/Screenshots/"))
        assertTrue(KeyPopRecentScreenshots.isScreenshot(
            "Screen_Shot_001.jpg","Pictures/"))
        assertFalse(KeyPopRecentScreenshots.isScreenshot(
            "IMG_20261108_034555.jpg","DCIM/Camera/"))
        assertFalse(KeyPopRecentScreenshots.isScreenshot(
            "Meeting_notes.png","Documents/"))
    }
    @Test fun screenshotLookbackKeepsRecentCapturesAvailableAfterKeyboardReopen() {
        assertTrue(KeyPopRecentScreenshots.MAX_AGE_SECONDS>=600)
    }
}