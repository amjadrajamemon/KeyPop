package com.aetherkeys.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.widget.RadioButton
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** User-reported Redmi search box and invisible theme/layout labels. */
@RunWith(AndroidJUnit4::class)
class KeyPopRC36SearchAndOptionsInstrumentedTest {
    private val context:Context get()=
        InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun googleStyleSearchInputsKeepWordsVisibleWithoutSilentEditsOrHistory() {
        val search=InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        assertTrue("Search boxes must show locally enabled suggestions",
            KeyPopTypingRules.textAllowsSuggestions(search))
        assertFalse("Search boxes must not silently auto-replace text",
            KeyPopTypingRules.autocorrectEnabled(true,search))
        assertFalse("Search boxes must not learn the user's queries",
            KeyPopTypingRules.textAllowsLearning(search))
        for(secret in listOf(InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI)) {
            assertFalse("Sensitive fields must not expose suggestions",
                KeyPopTypingRules.textAllowsSuggestions(
                    InputType.TYPE_CLASS_TEXT or secret))
        }
    }

    @Test fun themeAndLayoutOptionLabelsHaveReadableContrastOnLightSystemDialogs() {
        val measured=BooleanArray(4)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val row=RadioButton(context).apply{
                text="Gboard-style Material Teal Glass"
                textSize=15f
            }
            KeyPopOptionStyle.decorate(context,row)
            val surface=(row.background as GradientDrawable).color?.defaultColor
            measured[0]=row.currentTextColor==Color.WHITE
            measured[1]=surface==KeyPopOptionStyle.SURFACE
            measured[2]=row.buttonTintList?.defaultColor==KeyPopOptionStyle.ACCENT
            measured[3]=KeyPopOptionStyle.contrastRatio()>=7.0
        }
        assertTrue("Theme name text should not disappear",measured[0])
        assertTrue("Options require a contrasting dark background",measured[1])
        assertTrue("Radio selection controls should be visible",measured[2])
        assertTrue("Option text contrast below WCAG AAA standard",measured[3])
    }
}
