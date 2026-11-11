package com.aetherkeys.keyboard

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyPopRC34ResizeAndNavigationInstrumentedTest {
    private val context:Context get()=
        InstrumentationRegistry.getInstrumentation().targetContext
    private fun allLabels(root:View):List<String> {
        val labels=ArrayList<String>()
        fun visit(v:View) {
            if(v is TextView)labels.add(v.text?.toString().orEmpty())
            if(v is ViewGroup)for(i in 0 until v.childCount)visit(v.getChildAt(i))
        }
        visit(root)
        return labels
    }
    private fun clickText(root:View,needle:String):Boolean {
        fun scan(v:View):View? {
            if(v is TextView && v.text?.toString()==needle && v.isShown)return v
            if(v is ViewGroup)for(i in 0 until v.childCount)
                scan(v.getChildAt(i))?.let{return it}
            return null
        }
        val found=scan(root)?:return false
        var target:View?=found
        while(target!=null && !target.hasOnClickListeners())
            target=target.parent as? View
        target?.performClick()
        return target!=null
    }
    @Test fun perKeyDimensionsPersistAndResetIndependently() {
        KeyPopKeyGeometry.resetAllDimensions(context)
        try {
            assertEquals(100,KeyPopKeyGeometry.widthPercent(context,0,-1,-1,"SPACE"))
            assertTrue(KeyPopKeyGeometry.setHeightPercent(context,0,-1,-1,"SPACE",140))
            assertTrue(KeyPopKeyGeometry.setWidthPercent(context,0,-1,-1,"SPACE",165))
            assertTrue(KeyPopKeyGeometry.setWidthPercent(context,0,0,0,"q",145))
            assertTrue(KeyPopKeyGeometry.setHeightPercent(context,0,0,0,"q",122))
            assertTrue(KeyPopKeyGeometry.setHeightPercent(context,1,0,0,"ق",155))
            assertTrue(KeyPopKeyGeometry.setWidthPercent(context,0,-1,-1,"↵",160))
            assertTrue(KeyPopKeyGeometry.setWidthPercent(context,0,-1,-1,",",175))
            assertTrue(KeyPopKeyGeometry.setWidthPercent(context,0,-1,-1,"5",180))
            assertEquals(140,KeyPopKeyGeometry.heightPercent(context,2,-1,-1,"SPACE"))
            assertEquals(145,KeyPopKeyGeometry.widthPercent(context,0,0,0,"q"))
            assertEquals(100,KeyPopKeyGeometry.widthPercent(context,1,0,0,"ق"))
            assertEquals(155,KeyPopKeyGeometry.heightPercent(context,1,0,0,"ق"))
            assertEquals(180,KeyPopKeyGeometry.widthPercent(context,0,-1,-1,"5"))
            KeyPopKeyGeometry.resetAllDimensions(context)
            assertEquals(100,KeyPopKeyGeometry.heightPercent(context,1,0,0,"ق"))
            assertEquals(100,KeyPopKeyGeometry.widthPercent(context,0,-1,-1,"↵"))
        }finally {KeyPopKeyGeometry.resetAllDimensions(context)}
    }
    @Test fun interactivePreviewUpdatesRealKeyBoundsOnEdit() {
        val preview=KeyPopLiveKeyEditor(context){_,_,_->}
        val defaultSpace=KeyPopKeyGeometry.heightPercent(context,0,-1,-1,"SPACE")
        val content=preview
        try {
            assertEquals(100,defaultSpace)
            val footer=content.getChildAt(content.childCount-1) as ViewGroup
            val spaceIndex=KeyPopKeysets.footer(
                KeyPopKeysets.selectedId(context),false).indexOf("SPACE")
            assertTrue(spaceIndex>=0)
            val before=footer.getChildAt(spaceIndex).layoutParams.height
            assertTrue(KeyPopKeyGeometry.setHeightPercent(context,0,-1,-1,"SPACE",150))
            content.refresh()
            val updated=content.getChildAt(content.childCount-1) as ViewGroup
            val after=updated.getChildAt(spaceIndex).layoutParams.height
            assertTrue("Spacebar preview must physically get taller",after>before)
        }finally{KeyPopKeyGeometry.resetAllDimensions(context)}
    }
    @Test fun settingsBackReturnsOneLevelAndResizeControlsAreLabeled() {
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            scenario.onActivity{activity->
                assertTrue(clickText(activity.window.decorView,"Typing & Size"))
                assertTrue(allLabels(activity.window.decorView).any{
                    it.contains("PREDICTIONS & GRAMMAR")})
                activity.onBackPressed()
                assertTrue(allLabels(activity.window.decorView).any{
                    it.contains("Your keyboard, your settings")})
                assertTrue(clickText(activity.window.decorView,"Typing & Size"))
                assertTrue(clickText(activity.window.decorView,"Resize keys"))
                val labels=allLabels(activity.window.decorView)
                assertTrue(labels.any{it.contains("Selected key width")})
                assertTrue(labels.any{it.contains("Selected key height")})
                assertTrue(labels.any{it.contains("WHOLE KEYBOARD")})
            }
        }
    }
}
