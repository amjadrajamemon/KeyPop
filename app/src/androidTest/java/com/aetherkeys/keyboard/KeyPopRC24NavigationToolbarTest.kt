package com.aetherkeys.keyboard

import android.content.Intent
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Regression gates for a single-step settings exit and reliable vector actions. */
@RunWith(AndroidJUnit4::class)
class KeyPopRC24NavigationToolbarTest {
    @Test fun backFromDeepSettingsSectionExitsWithoutHistoryTrail() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val intent=Intent(context,MainActivity::class.java)
            .putExtra("openSection","keysets")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                activity.onBackPressed()
                assertTrue("Back from deep settings must finish, not stack another page",
                    activity.isFinishing)
            }
        }
    }

    @Test fun everyThemeKeyUsesSingleDrawableInsteadOfLayerStack() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
        val original=prefs.getInt("theme",0)
        try {
            KeyPopSkins.all.forEachIndexed { index,skin ->
                prefs.edit().putInt("theme",index).commit()
                assertTrue("Theme $index still draws stacked key layers",
                    KeyPopGlass.key(context,skin) is GradientDrawable)
            }
        } finally {
            prefs.edit().putInt("theme",original).commit()
        }
    }

    @Test fun everyToolbarActionRendersWithoutEmojiFontDependency() {
        for(kind in KeyPopToolbarGlyph.Kind.values()) {
            val bitmap=Bitmap.createBitmap(48,48,Bitmap.Config.ARGB_8888)
            try {
                val icon=KeyPopToolbarGlyph(kind,Color.WHITE)
                icon.setBounds(0,0,48,48)
                icon.draw(Canvas(bitmap))
                var marked=0
                for(y in 0 until 48)for(x in 0 until 48)
                    if(Color.alpha(bitmap.getPixel(x,y))>0)marked++
                assertTrue("Icon $kind must render visible strokes",marked>12)
            } finally {
                bitmap.recycle()
            }
        }
    }
}
