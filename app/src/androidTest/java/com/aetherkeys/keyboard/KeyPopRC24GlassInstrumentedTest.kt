package com.aetherkeys.keyboard

import android.content.Context
import android.graphics.drawable.GradientDrawable
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Guard the low-overdraw teal theme against regressions to outlined layers. */
@RunWith(AndroidJUnit4::class)
class KeyPopRC24GlassInstrumentedTest {
    @Test fun materialTealKeysUseOneFilledDrawableInsteadOfThreeGlassLayers() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val preferences=context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
        val original=preferences.getInt("theme",0)
        val previouslySet=preferences.contains("theme")
        try {
            preferences.edit().putInt("theme",10).commit()
            val skin=KeyPopSkins.all[10]
            assertTrue(KeyPopGlass.key(context,skin,false) is GradientDrawable)
            assertTrue(KeyPopGlass.key(context,skin,true) is GradientDrawable)
            assertTrue(KeyPopGlass.panel(context,skin) is GradientDrawable)
            assertTrue(KeyPopGlass.background(context,skin) is GradientDrawable)
        } finally {
            val editor=preferences.edit()
            if(previouslySet)editor.putInt("theme",original)
            else editor.remove("theme")
            editor.commit()
        }
    }
}
