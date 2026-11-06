package com.aetherkeys.keyboard

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Verify real Android Activity views rather than relying on Pixel Launcher UIAutomator. */
@RunWith(AndroidJUnit4::class)
class KeyPopRuntimeSmokeTest {
    private fun visibleText(root:View):String {
        val result=mutableListOf<String>()
        fun walk(node:View) {
            if(node is TextView)result.add(node.text?.toString().orEmpty())
            if(node is ViewGroup)for(i in 0 until node.childCount)walk(node.getChildAt(i))
        }
        walk(root)
        return result.joinToString(" ")
    }
    @Test fun homeShowsActivationAndLanguageControls() {
        ActivityScenario.launch(KeyPopHomeActivity::class.java).use {scenario->
            scenario.onActivity { activity->
                val text=visibleText(activity.window.decorView)
                for(label in listOf("Keyboard Setup","Enable KeyPop keyboard",
                    "Choose KeyPop as active keyboard","Language packs and model status")) {
                    assertTrue("Missing home control: "+label,text.contains(label))
                }
            }
        }
    }
    @Test fun packManagerShowsThreeLanguagesAndDownloadControls() {
        ActivityScenario.launch(KeyPopModelManagerActivity::class.java).use {scenario->
            scenario.onActivity {activity->
                val text=visibleText(activity.window.decorView)
                for(label in listOf("Language Pack Manager","ENGLISH","URDU",
                    "SINDHI","Download / repair Urdu")) {
                    assertTrue("Missing language pack control: "+label,text.contains(label))
                }
            }
        }
    }
    @Test fun advancedSettingsIsNotAnEmptyWindow() {
        ActivityScenario.launch(MainActivity::class.java).use {scenario->
            scenario.onActivity { activity->
                val text=visibleText(activity.window.decorView)
                assertTrue("Advanced settings did not render",text.contains("KeyPop") &&
                    text.contains("Your keyboard"))
            }
        }
    }
    @Test fun androidRecognizesKeyPopKeyboardService() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val imm=context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val registered=imm.inputMethodList.any { it.packageName==context.packageName &&
            it.serviceName=="com.aetherkeys.keyboard.AetherImeService" }
        assertTrue("IME not visible to Android input method manager",registered)
    }
}
