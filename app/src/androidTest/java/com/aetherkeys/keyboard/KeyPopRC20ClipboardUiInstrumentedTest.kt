package com.aetherkeys.keyboard

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** RC20 regression: a focused, tabbed clipboard that never displays empty
 * pagination or the old multi-button screenshot rows. */
@RunWith(AndroidJUnit4::class)
class KeyPopRC20ClipboardUiInstrumentedTest {
    private fun findButton(root:View,label:String):Button? {
        if(root is Button && root.text.toString()==label)return root
        if(root is ViewGroup)for(i in 0 until root.childCount)
            findButton(root.getChildAt(i),label)?.let{return it}
        return null
    }
    private fun findInput(root:View):EditText? {
        if(root is EditText)return root
        if(root is ViewGroup)for(i in 0 until root.childCount)
            findInput(root.getChildAt(i))?.let{return it}
        return null
    }
    private fun hasText(root:View,needle:String):Boolean {
        if(root is TextView && root.text?.toString()?.contains(needle,true)==true)
            return true
        if(root is ViewGroup)for(i in 0 until root.childCount)
            if(hasText(root.getChildAt(i),needle))return true
        return false
    }
    @Test fun textAndScreenshotTabsSwitchWithoutMixedArchiveSections() {
        ActivityScenario.launch(KeyPopClipboardActivity::class.java).use {scene->
            scene.onActivity {activity->
                val root=activity.window.decorView
                assertTrue(hasText(root,"Clipboard"))
                assertNotNull(findButton(root,"Text clips"))
                val screenshots=findButton(root,"Screenshots")
                assertNotNull(screenshots)
                assertEquals("Search saved text",findInput(root)?.hint?.toString())
                assertFalse(hasText(root,"Page 1 · 0 clips"))
                screenshots!!.performClick()
                assertEquals("Search screenshots",findInput(root)?.hint?.toString())
                assertFalse(hasText(root,"No saved text yet. Screenshots appear"))
                findButton(root,"Text clips")!!.performClick()
                assertEquals("Search saved text",findInput(root)?.hint?.toString())
            }
        }
    }
    @Test fun screenshotArchiveHasCompactMenuBasedActions() {
        ActivityScenario.launch(KeyPopScreenshotHistoryActivity::class.java).use {scene->
            scene.onActivity{activity->
                val root=activity.window.decorView
                assertTrue(hasText(root,"Screenshot history"))
                assertTrue(hasText(root,"Encrypted and stored"))
                assertFalse(hasText(root,"Your GIFs"))
                assertFalse(hasText(root,"GIF & Sticker"))
            }
        }
    }
}
