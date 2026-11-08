package com.aetherkeys.keyboard

import android.content.Context
import android.view.inputmethod.EditorInfo
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Regression checks for RC12's persistent archives and safe sending policy. */
@RunWith(AndroidJUnit4::class)
class KeyPopRC12InstrumentedTest {
    @Test fun clipboardRetainsOverSixtyCopiesAcrossStoreReopen() {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val marker="keypop-rc12-test-"+System.nanoTime()+"-"
        val history=ClipStore(ctx)
        try {
            for(i in 0 until 85)history.save(marker+i)
            val saved=history.search(marker)
            assertEquals("No automatic 60-item trim is allowed",85,saved.size)
            assertEquals(marker+"84",saved.first().text)
            val reopened=ClipStore(ctx)
            assertEquals(85,reopened.search(marker).size)
            val oldest=reopened.search(marker).last()
            reopened.pin(oldest.id,true)
            assertTrue(reopened.search(marker).last().pinned)
            reopened.delete(oldest.id)
            assertEquals(84,reopened.search(marker).size)
        } finally {
            for(item in history.search(marker))history.delete(item.id)
        }
    }
    @Test fun explicitSendNeverSubstitutesSearchOrGo() {
        assertTrue(KeyPopTranslationSendPolicy.supportsSendAction(EditorInfo.IME_ACTION_SEND))
        assertFalse(KeyPopTranslationSendPolicy.supportsSendAction(EditorInfo.IME_ACTION_GO))
        assertFalse(KeyPopTranslationSendPolicy.supportsSendAction(EditorInfo.IME_ACTION_SEARCH))
        assertFalse(KeyPopTranslationSendPolicy.allowEnterSendFallback(
            EditorInfo.IME_ACTION_UNSPECIFIED,false))
        assertTrue(KeyPopTranslationSendPolicy.allowEnterSendFallback(
            EditorInfo.IME_ACTION_NONE,true))
        assertFalse(KeyPopTranslationSendPolicy.allowEnterSendFallback(
            EditorInfo.IME_ACTION_SEARCH,true))
    }
    @Test fun screenshotDetectionDoesNotSelectOrdinaryCameraImages() {
        assertTrue(KeyPopRecentScreenshots.isScreenshot(
            "Screenshot_20261107.png","Pictures/Screenshots/"))
        assertFalse(KeyPopRecentScreenshots.isScreenshot(
            "IMG_001.jpg","DCIM/Camera/"))
    }
    @Test fun clipboardArchiveScreenShowsSearchAndRetentionInformation() {
        ActivityScenario.launch(KeyPopClipboardActivity::class.java).use { scenario->
            scenario.onActivity { activity->
                val root=activity.window.decorView
                fun find(node:View,wanted:String):Boolean {
                    if(node is TextView && node.isShown &&
                        node.text?.toString()==wanted)return true
                    if(node is ViewGroup)for(i in 0 until node.childCount)
                        if(find(node.getChildAt(i),wanted))return true
                    return false
                }
                assertTrue(find(root,"Clipboard archive"))
                assertTrue(find(root,"Load more saved clips") ||
                    find(root,"Loading saved clips…") ||
                    find(root,"No saved clips. Enable Clipboard history in KeyPop Settings.") ||
                    find(root,"No saved text yet. Screenshot images are displayed above."))
            }
        }
    }
}
