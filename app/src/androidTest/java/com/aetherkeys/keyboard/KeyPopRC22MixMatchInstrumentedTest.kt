package com.aetherkeys.keyboard

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android SharedPreferences and settings-UI compatibility regression. */
@RunWith(AndroidJUnit4::class)
class KeyPopRC22MixMatchInstrumentedTest {
    @Test fun changingGeometryAndKeysetPreservesAppearanceAndUserPreferences() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
        val keys=listOf("theme","keyboardPreset","keysetPreset","height",
            "font","numberRow","customAccent","suggestions")
        val old=keys.associateWith{prefs.all[it]}
        try {
            prefs.edit().putInt("theme",12)
                .putInt("keyboardPreset",KeyboardPresetCatalog.ONE_UI)
                .putInt("keysetPreset",KeyPopKeysets.GBOARD)
                .putInt("font",23).putInt("customAccent",0xFF778899.toInt())
                .putBoolean("numberRow",false).putBoolean("suggestions",true)
                .commit()
            assertTrue(KeyboardPresetCatalog.apply(context,KeyboardPresetCatalog.FLOW))
            assertEquals(12,prefs.getInt("theme",-1))
            assertEquals(KeyPopKeysets.GBOARD,KeyPopKeysets.selectedId(context))
            assertEquals(23,prefs.getInt("font",-1))
            assertFalse(prefs.getBoolean("numberRow",true))
            assertEquals(0xFF778899.toInt(),prefs.getInt("customAccent",0))
            assertTrue(KeyPopKeysets.apply(context,KeyPopKeysets.SAMSUNG))
            assertEquals(KeyboardPresetCatalog.FLOW,
                KeyboardPresetCatalog.selected(context).id)
            assertEquals(12,prefs.getInt("theme",-1))
        }finally {
            val edit=prefs.edit()
            for((key,value) in old)when(value){
                is Int->edit.putInt(key,value)
                is Boolean->edit.putBoolean(key,value)
                is String->edit.putString(key,value)
                else->edit.remove(key)
            }
            edit.commit()
        }
    }
    private fun find(node:View,needle:String):TextView? {
        if(node is TextView && node.text?.toString()?.contains(needle)==true)return node
        if(node is ViewGroup)for(i in 0 until node.childCount) {
            find(node.getChildAt(i),needle)?.let{return it}
        }
        return null
    }
    @Test fun unifiedDesignShowsIndependentKeysAndLiveResizeEntry() {
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            scenario.onActivity{activity->
                var entry:View?=find(activity.window.decorView,"Customize Keyboard")
                assertNotNull("Missing one-screen keyboard customization",entry)
                while(entry!=null && !entry!!.hasOnClickListeners()) {
                    entry=entry!!.parent as? View
                }
                assertNotNull("Customize Keyboard cannot open",entry)
                entry!!.performClick()
                val root=activity.window.decorView
                assertNotNull("Theme selector missing",find(root,"Theme"))
                assertNotNull("Layout selector missing",find(root,"Layout"))
                assertNotNull("Keys and Symbols selector missing",find(root,"Keys & Symbols"))
                assertNotNull("Live resizing entry missing",find(root,"Resize keys"))
                assertNotNull("Active keyset not shown",
                    find(root,KeyPopKeysets.all.first {
                        it.id==KeyPopKeysets.selectedId(activity)
                    }.name))
                assertNull("Old confusing Apply control still displayed",
                    find(root,"Apply keys and symbols only"))
                assertTrue("Available keysets were lost",listOf(
                    KeyPopKeysets.GBOARD,KeyPopKeysets.SAMSUNG,KeyPopKeysets.SWIFTKEY
                ).all{KeyPopKeysets.valid(it)})
            }
        }
    }
    @Test fun switchingFromLegacyPresetDoesNotForceNewSymbolPack() {
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=c.getSharedPreferences("keypop",Context.MODE_PRIVATE)
        val oldId=prefs.all["keyboardPreset"]
        val oldKeys=prefs.all["keysetPreset"]
        try{
            prefs.edit().putInt("keyboardPreset",KeyboardPresetCatalog.ONE_UI)
                .remove("keysetPreset").commit()
            assertTrue(KeyboardPresetCatalog.apply(c,KeyboardPresetCatalog.FLOW))
            assertEquals(KeyPopKeysets.SAMSUNG,KeyPopKeysets.selectedId(c))
            assertEquals(KeyboardPresetCatalog.FLOW,
                KeyboardPresetCatalog.selected(c).id)
        }finally{
            val edit=prefs.edit()
            if(oldId is Int)edit.putInt("keyboardPreset",oldId)
            else edit.remove("keyboardPreset")
            if(oldKeys is Int)edit.putInt("keysetPreset",oldKeys)
            else edit.remove("keysetPreset")
            edit.commit()
        }
    }
}
