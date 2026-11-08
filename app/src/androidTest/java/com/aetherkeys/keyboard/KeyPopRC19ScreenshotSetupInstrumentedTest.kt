package com.aetherkeys.keyboard

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyPopRC19ScreenshotSetupInstrumentedTest {
    private fun textExists(root:View,needle:String):Boolean {
        if(root is TextView && root.text?.contains(needle,true)==true)return true
        if(root is ViewGroup)for(i in 0 until root.childCount)
            if(textExists(root.getChildAt(i),needle))return true
        return false
    }
    @Test fun screenshotAccessClearlyExplainsFullPhotoPermissionAndHistory() {
        ActivityScenario.launch(KeyPopScreenshotAccessActivity::class.java).use {scene->
            scene.onActivity{activity->
                assertTrue(textExists(activity.window.decorView,"Allow all photos"))
                assertTrue(textExists(activity.window.decorView,"screenshot"))
                assertTrue(textExists(activity.window.decorView,"history"))
            }
        }
    }
    @Test fun clipboardManagerShowsScreenshotHistoryLink() {
        ActivityScenario.launch(KeyPopClipboardActivity::class.java).use {scene->
            scene.onActivity{activity->
                assertTrue(textExists(activity.window.decorView,"Screenshots"))
                assertTrue(textExists(activity.window.decorView,"Text clips"))
                assertFalse(textExists(activity.window.decorView,
                    "No saved text yet. Screenshot images are displayed above."))
            }
        }
    }
    @Test fun imageGalleryNoLongerShowsGifFeatures() {
        ActivityScenario.launch(KeyPopScreenshotHistoryActivity::class.java).use {scene->
            scene.onActivity{activity->
                assertTrue(textExists(activity.window.decorView,"Screenshot history"))
                assertFalse(textExists(activity.window.decorView,"Your GIFs"))
            }
        }
    }
}