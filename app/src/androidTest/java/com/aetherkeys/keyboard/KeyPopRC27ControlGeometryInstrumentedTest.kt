package com.aetherkeys.keyboard

import android.content.Context
import android.content.pm.PackageManager
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Measures real Android LinearLayout key views, not only catalog constants. */
@RunWith(AndroidJUnit4::class)
class KeyPopRC27ControlGeometryInstrumentedTest {
    private val context:Context get() =
        InstrumentationRegistry.getInstrumentation().targetContext

    private fun dp(x:Int):Int=(x*context.resources.displayMetrics.density).toInt()

    private data class Bounds(val width:Int,val height:Int)

    private fun measureKeys(tokens:List<String>):Map<String,Bounds> {
        val row=LinearLayout(context).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=android.view.Gravity.CENTER
        }
        val buttons=tokens.map {token->
            val button=Button(context).apply {
                text=token
                minWidth=0
                minimumWidth=0
                minHeight=0
                minimumHeight=0
                setPadding(0,0,0,0)
            }
            val bonus=KeyboardPresetCatalog.controlHeightBonus(token)
            val params=LinearLayout.LayoutParams(0,dp(51+bonus),
                KeyboardPresetCatalog.keyWeight(KeyboardPresetCatalog.MATERIAL,token))
            params.leftMargin=dp(2)
            params.rightMargin=dp(2)
            params.topMargin=if(bonus>0)0 else dp(2)
            params.bottomMargin=if(bonus>0)0 else dp(2)
            row.addView(button,params)
            token to button
        }.toMap()
        row.measure(
            View.MeasureSpec.makeMeasureSpec(dp(392),View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dp(85),View.MeasureSpec.AT_MOST))
        row.layout(0,0,row.measuredWidth,row.measuredHeight)
        return buttons.mapValues { (_,button)->
            Bounds(button.width,button.height)
        }
    }

    @Test fun backspaceAndShiftAreOverTwiceTheActualLetterWidth() {
        val measured=measureKeys(listOf("⇧","z","x","c","v","b","n","m","⌫"))
        val letter=measured.getValue("z")
        for(token in listOf("⇧","⌫")) {
            val control=measured.getValue(token)
            assertTrue("$token was not visibly wider: $measured",
                control.width>=letter.width*2.20f)
            assertTrue("$token was not visibly taller: $measured",
                control.height>=letter.height+dp(6))
        }
    }

    @Test fun enterCommaAndPeriodAreLargerInMeasuredFooter() {
        val measured=measureKeys(KeyPopKeysets.footer(KeyPopKeysets.GBOARD,false))
        val globe=measured.getValue("🌐")
        for(token in listOf(",",".","↵")) {
            val control=measured.getValue(token)
            assertTrue("$token is not sufficiently wide: $measured",
                control.width>=globe.width*1.6f)
            assertTrue("$token is not visibly taller: $measured",
                control.height>=globe.height+dp(6))
        }
        assertTrue("Spacebar got lost when controls grew",
            measured.getValue("SPACE").width>globe.width*4f)
    }

    @Test fun installedPreviewHasCorrectVisibleLauncherAndKeyboardIdentity() {
        assertEquals("com.aetherkeys.keyboard.previewv135",context.packageName)
        assertTrue("Android launcher still identifies old RC23 preview",
            context.applicationInfo.loadLabel(context.packageManager)
                .toString().contains("RC35"))
        @Suppress("DEPRECATION")
        val services=context.packageManager.getPackageInfo(
            context.packageName,PackageManager.GET_SERVICES).services.orEmpty()
        val ime=services.firstOrNull { it.name.endsWith("AetherImeService") }
        assertNotNull("IME service omitted",ime)
        assertTrue("Android keyboard picker still labels RC23",
            ime!!.loadLabel(context.packageManager).toString().contains("RC35"))
    }
}
