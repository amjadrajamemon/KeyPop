package com.aetherkeys.keyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies actual Android prediction-row widgets and preview drawing.
 * These tests are not a substitute for a physical-phone Gboard comparison. */
@RunWith(AndroidJUnit4::class)
class KeyPopRC35SuggestionDesignInstrumentedTest {
    private val context:Context get()=
        InstrumentationRegistry.getInstrumentation().targetContext

    private fun makeRow():LinearLayout=LinearLayout(context).apply{
        orientation=LinearLayout.HORIZONTAL
        repeat(3) {
            addView(Button(context).apply{
                text=""
                minWidth=0; minimumWidth=0
                minHeight=0; minimumHeight=0
            },LinearLayout.LayoutParams(0,60,1f))
        }
    }

    @Test fun emptyPredictionRowHasVisibleReadableCenterInsteadOfThreeInvisibleButtons() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
        val row=makeRow()
        KeyPopSuggestionPresentation.waiting(row)
        assertEquals(View.VISIBLE,row.visibility)
        assertEquals(View.VISIBLE,row.getChildAt(1).visibility)
        val middle=row.getChildAt(1) as Button
        assertEquals("Type to see suggestions",middle.text.toString())
        assertFalse(middle.isEnabled)
        assertEquals(View.INVISIBLE,row.getChildAt(0).visibility)
        assertEquals(View.INVISIBLE,row.getChildAt(2).visibility)
        KeyPopSuggestionPresentation.waiting(row,true)
        assertEquals("Continue typing",(row.getChildAt(1) as Button).text.toString())
        assertEquals(View.VISIBLE,row.visibility)
        }
    }

    @Test fun whileTypingTheExactWordRemainsVisibleEvenBeforeAsyncResults() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
        val row=makeRow()
        KeyPopSuggestionPresentation.composing(row,"improvem")
        assertEquals(View.VISIBLE,row.visibility)
        val middle=row.getChildAt(1) as Button
        assertEquals("improvem",middle.text.toString())
        assertEquals(View.VISIBLE,middle.visibility)
        assertFalse(middle.isEnabled)
        KeyPopSuggestionPresentation.markAvailable(middle,"improvements")
        assertEquals("Suggestion: improvements",middle.contentDescription)
        assertEquals(View.VISIBLE,middle.visibility)
        assertTrue(middle.isEnabled)
        }
    }

    private fun screenshot(preview:KeyboardPresetPreview):Bitmap {
        val width=(330*context.resources.displayMetrics.density).toInt()
        val height=(154*context.resources.displayMetrics.density).toInt()
        preview.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY))
        preview.layout(0,0,width,preview.measuredHeight)
        return Bitmap.createBitmap(width,preview.measuredHeight,
            Bitmap.Config.ARGB_8888).also {preview.draw(Canvas(it))}
    }

    @Test fun liveDesignCanvasActuallyChangesForGeometryKeysetAndEnglishArrangement() {
        val prefs=context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
        val priorTheme=prefs.getInt("theme",0)
        val priorArrangement=prefs.getInt("englishLayout",0)
        val previousCustom=prefs.getString("customLayout_0",null)
        val results=BooleanArray(4)
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync{
                KeyPopLayouts.choose(context,0)
                prefs.edit().putInt("theme",0).commit()
                val preview=KeyboardPresetPreview(context,
                    KeyboardPresetCatalog.byId(KeyboardPresetCatalog.MATERIAL)!!,
                    KeyPopKeysets.GBOARD)
                val base=screenshot(preview)
                preview.showLive(KeyboardPresetCatalog.byId(KeyboardPresetCatalog.FACETED)!!,
                    KeyPopKeysets.GBOARD)
                val geometry=screenshot(preview)
                results[0]=!base.sameAs(geometry)
                preview.showLive(KeyboardPresetCatalog.byId(KeyboardPresetCatalog.MATERIAL)!!,
                    KeyPopKeysets.SAMSUNG)
                val keyset=screenshot(preview)
                results[1]=!base.sameAs(keyset)
                KeyPopLayouts.choose(context,3) // Dvorak must look different
                preview.showLive(KeyboardPresetCatalog.byId(KeyboardPresetCatalog.MATERIAL)!!,
                    KeyPopKeysets.GBOARD)
                val dvorak=screenshot(preview)
                results[2]=!base.sameAs(dvorak)
                KeyPopLayouts.choose(context,0)
                prefs.edit().putInt("theme",1).commit()
                preview.showLive(KeyboardPresetCatalog.byId(KeyboardPresetCatalog.MATERIAL)!!,
                    KeyPopKeysets.GBOARD)
                val light=screenshot(preview)
                results[3]=!base.sameAs(light)
                base.recycle();geometry.recycle();keyset.recycle();dvorak.recycle()
                light.recycle()
            }
            assertTrue("Changing geometry did not change preview pixels",results[0])
            assertTrue("Changing keyset did not change preview pixels",results[1])
            assertTrue("Changing English arrangement did not change preview pixels",results[2])
            assertTrue("Changing theme did not change preview pixels",results[3])
        }finally{
            prefs.edit().putInt("theme",priorTheme)
                .putInt("englishLayout",priorArrangement).apply{
                if(previousCustom==null)remove("customLayout_0")
                else putString("customLayout_0",previousCustom)
            }.commit()
        }
    }
}
