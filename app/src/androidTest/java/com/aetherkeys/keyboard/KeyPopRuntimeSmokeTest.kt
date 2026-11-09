package com.aetherkeys.keyboard

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Switch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
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
                    "Choose KeyPop as active keyboard",
                    "Choose language packs · download selected directions")) {
                    assertTrue("Missing home control: "+label,text.contains(label))
                }
            }
        }
    }
    @Test fun packManagerShowsThreeLanguagesAndDownloadControls() {
        ActivityScenario.launch(KeyPopModelManagerActivity::class.java).use {scenario->
            scenario.onActivity {activity->
                val text=visibleText(activity.window.decorView)
                for(label in listOf("Choose translation directions","English → Urdu",
                    "Urdu → English","English → Sindhi","Sindhi → English",
                    "Urdu → Sindhi","Sindhi → Urdu","Download selected packs",
                    "Automatic network retry: up to three attempts. Interrupted Sindhi files are preserved and resume without restarting from zero.")) {
                    assertTrue("Missing language pack control: "+label,text.contains(label))
                }
                assertTrue("Obsolete redundant Sindhi manual buttons still visible",
                    !text.contains("Experimental: prepare English"))
            }
        }
    }
    @Test fun advancedSettingsIsNotAnEmptyWindow() {
        ActivityScenario.launch(MainActivity::class.java).use {scenario->
            scenario.onActivity { activity->
                val text=visibleText(activity.window.decorView)
                assertTrue("Advanced settings did not render",text.contains("KeyPop") &&
                    text.contains("Your keyboard, your settings"))
            }
        }
    }
    @Test fun settingsDashboardShowsCategoriesAndTranslation() {
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            scenario.onActivity{activity->
                fun find(root:View,label:String):TextView?{
                    if(root is TextView && root.text?.toString()==label && root.isShown)return root
                    if(root is ViewGroup)for(index in 0 until root.childCount){
                        val item=find(root.getChildAt(index),label)
                        if(item!=null)return item
                    }
                    return null
                }
                for(label in listOf("Keyboard Setup","Offline Packs","Customize Keyboard","Translation"))
                    assertTrue("Missing visible settings section: "+label,
                        find(activity.window.decorView,label)!=null)
                val translation=find(activity.window.decorView,"Translation")
                // The whole navigation card is the touch target, not only its text label.
                var clickable:View?=translation
                while(clickable!=null && !clickable.hasOnClickListeners())
                    clickable=clickable.parent as? View
                assertTrue("Translation section does not open",clickable!=null)
                clickable?.performClick()
                assertTrue("Translation settings screen was blank",
                    find(activity.window.decorView,"TRANSLATION ENGINE")!=null)
                assertTrue("Missing direction-selective download action within translation",
                    find(activity.window.decorView,"Choose and download translation packs")!=null)
            }
        }
    }
    @Test fun autocorrectPunctuationAndGrammarTogglesPersist() {
        ActivityScenario.launch(MainActivity::class.java).use {scenario->
            scenario.onActivity { activity->
                fun find(node:View,text:String):TextView? {
                    if(node is TextView && node.text?.toString()==text && node.isShown)
                        return node
                    if(node is ViewGroup)for(i in 0 until node.childCount) {
                        val match=find(node.getChildAt(i),text)
                        if(match!=null)return match
                    }
                    return null
                }
                var category:View?=find(activity.window.decorView,"Typing & Size")
                while(category!=null && !category.hasOnClickListeners())
                    category=category.parent as? View
                assertTrue("Typing settings inaccessible",category!=null)
                category?.performClick()
                val prefs=activity.getSharedPreferences("keypop",Context.MODE_PRIVATE)
                val typingText=visibleText(activity.window.decorView)
                assertTrue("Missing live resize entry",typingText.contains("Resize keys"))
                for(label in listOf("Suggestion font size","Suggestion strip height")) {
                    assertTrue("Missing suggestion adjustment: $label",
                        typingText.contains(label+":"))
                }
                for((label,key) in listOf(
                    "Auto-correct spelling" to "autoCorrect",
                    "Auto punctuation" to "autoPunctuate",
                    "Grammar hints (suggest only)" to "grammarHints",
                    "Apply high-confidence grammar fixes on Space" to "autoGrammarApply")) {
                    val item=find(activity.window.decorView,label)
                    assertTrue("Missing settings switch: $label",item!=null)
                    val row=item?.parent as? ViewGroup
                    val toggle=(0 until (row?.childCount?:0)).map{row?.getChildAt(it)}
                        .filterIsInstance<Switch>().firstOrNull()
                    assertTrue("No switch for $label",toggle!=null)
                    val original=toggle?.isChecked?:false
                    toggle?.isChecked=!original
                    assertEquals("Switch not saved: $key",!original,
                        prefs.getBoolean(key,original))
                    toggle?.isChecked=original
                }
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
