package com.aetherkeys.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exact real Android widget-style assertions for live prediction settings. */
@RunWith(AndroidJUnit4::class)
class KeyPopRC35SuggestionStyleInstrumentedTest {
    private val context:Context get()=
        InstrumentationRegistry.getInstrumentation().targetContext
    private val settings=listOf("suggestionPosition","suggestionPalette",
        "suggestionBackgroundColor","suggestionChipColor","suggestionTextColor",
        "suggestionFont","suggestionHeight","suggestionGap","suggestionCorner",
        "suggestionOpacity","suggestionBold")

    @Test fun liveAppearanceUpdatesActualPredictionButtonsAndCanBeReverted() {
        val prefs=context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
        val previous=settings.associateWith{prefs.all[it]}
        val worked=BooleanArray(6)
        try {
            prefs.edit().putInt("suggestionPosition",1)
                .putInt("suggestionPalette",6)
                .putInt("suggestionBackgroundColor",0xFF194D4B.toInt())
                .putInt("suggestionChipColor",0xFF184654.toInt())
                .putInt("suggestionTextColor",0xFFFFEB98.toInt())
                .putInt("suggestionFont",26).putInt("suggestionHeight",68)
                .putInt("suggestionGap",12).putInt("suggestionCorner",22)
                .putInt("suggestionOpacity",75)
                .putBoolean("suggestionBold",true).commit()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val row=LinearLayout(context)
                for(word in listOf("improved","improvements","improving"))
                    KeyPopSuggestionStyle.addChip(context,row,
                        Button(context).apply{text=word})
                KeyPopSuggestionStyle.applyRow(context,row)
                val mid=row.getChildAt(1) as Button
                val params=mid.layoutParams as LinearLayout.LayoutParams
                val ink=mid.currentTextColor
                val back=(row.background as GradientDrawable).color?.defaultColor
                val chip=(mid.background as GradientDrawable).color?.defaultColor
                worked[0]=KeyPopSuggestionStyle.position(context)==
                    KeyPopSuggestionStyle.BELOW_TOOLBAR
                worked[1]=kotlin.math.abs(mid.textSize/
                    context.resources.displayMetrics.scaledDensity-26f)<.9f
                worked[2]=ink==0xFFFFEB98.toInt()
                worked[3]=back!=null && Color.alpha(back) in 180..198
                worked[4]=chip==0xFF184654.toInt() &&
                    params.leftMargin>=5 && params.height>=50
                worked[5]=KeyPopSuggestionStyle.bold(context) &&
                    KeyPopSuggestionStyle.heightDp(context)==68 &&
                    KeyPopSuggestionStyle.radiusDp(context)==22
            }
            assertTrue("Above/below placement preference was lost",worked[0])
            assertTrue("Actual prediction font did not enlarge",worked[1])
            assertTrue("Actual prediction text color was ignored",worked[2])
            assertTrue("Actual row opacity was ignored",worked[3])
            assertTrue("Chip color, gap or height did not change",worked[4])
            assertTrue("Bold, corner or height preferences were lost",worked[5])
        } finally {
            val editor=prefs.edit()
            for((key,value) in previous)when(value){
                is Int->editor.putInt(key,value)
                is Boolean->editor.putBoolean(key,value)
                is String->editor.putString(key,value)
                else->editor.remove(key)
            }
            editor.commit()
        }
    }

    @Test fun standardPalettesStillInheritKeyboardThemeUnlessUserOverridesIt() {
        val prefs=context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
        val old=prefs.all["suggestionPalette"]
        try {
            prefs.edit().putInt("suggestionPalette",0).commit()
            assertEquals(KeyPopSkins.active(context).ink,
                KeyPopSuggestionStyle.textColor(context))
            prefs.edit().putInt("suggestionPalette",1).commit()
            assertEquals(0xFF1A2E3D.toInt(),
                KeyPopSuggestionStyle.textColor(context))
            assertTrue(KeyPopSuggestionStyle.paletteNames.size>=6)
        } finally {
            val e=prefs.edit()
            if(old is Int)e.putInt("suggestionPalette",old)
            else e.remove("suggestionPalette")
            e.commit()
        }
    }
}
