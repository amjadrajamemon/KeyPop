package com.aetherkeys.keyboard

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyPopRC11InstrumentedTest {
    private fun find(node:View,wanted:String):TextView? {
        if(node is TextView && node.isShown && node.text?.toString()==wanted)return node
        if(node is ViewGroup)for(i in 0 until node.childCount) {
            val hit=find(node.getChildAt(i),wanted)
            if(hit!=null)return hit
        }
        return null
    }
    @Test fun bundledVocabularyHandlesRealSentenceAndWrongFirstKey() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val lexicon=KeyPopLexicon(context)
        val suggestions=lexicon.suggestions(0,"worri",limit=6,
            previous="you",beforeCursor="Why are you worri")
        assertEquals("worried",suggestions.first())
        assertTrue("Missing worried/worrying: $suggestions",
            suggestions.take(3).contains("worrying"))
        assertFalse("Unnatural bare verb ranked too high: $suggestions",
            suggestions.take(3).contains("worry"))
        val typo=lexicon.suggestions(0,"vreate",3)
        assertEquals("create",typo.first())
    }

    @Test fun customizationSettingsExposePersistentToolbarControl() {
        ActivityScenario.launch(MainActivity::class.java).use {scenario->
            scenario.onActivity {activity->
                val view=activity.window.decorView
                fun open(label:String) {
                    var parent:View?=find(view,label)
                    while(parent!=null && !parent.hasOnClickListeners())
                        parent=parent.parent as? View
                    assertNotNull("Cannot open $label",parent)
                    parent?.performClick()
                }
                open("Customize Keyboard")
                open("Suggestion strip")
                val label=find(view,"Keep toolbar visible with suggestions")
                assertNotNull("Missing persistent toolbar toggle",label)
                val row=label?.parent as? ViewGroup
                val toggle=(0 until (row?.childCount?:0)).map{row?.getChildAt(it)}
                    .filterIsInstance<Switch>().firstOrNull()
                assertNotNull("Missing toolbar switch",toggle)
                val previous=toggle?.isChecked?:false
                toggle?.isChecked=!previous
                val prefs=activity.getSharedPreferences("keypop",Context.MODE_PRIVATE)
                assertEquals(!previous,prefs.getBoolean("alwaysShowToolbar",previous))
                toggle?.isChecked=previous
            }
        }
    }
    @Test fun translationSettingsExposeCompactLiveMode() {
        ActivityScenario.launch(MainActivity::class.java).use {scenario->
            scenario.onActivity {activity->
                val view=activity.window.decorView
                var parent:View?=find(view,"Translation")
                while(parent!=null && !parent.hasOnClickListeners())
                    parent=parent.parent as? View
                assertNotNull(parent)
                parent?.performClick()
                assertNotNull(find(view,"Compact Gboard-style translation bar"))
                assertNotNull(find(view,"Type directly in your app while KeyPop shows translations above the keyboard."))
                assertNotNull(find(view,"Show live translation while typing"))
            }
        }
    }
}
