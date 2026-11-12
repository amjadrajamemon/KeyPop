package com.aetherkeys.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnectionWrapper
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Reproduces the RC39 regression: WhatsApp may advertise DONE, NEXT,
 * or SEND while the user expects the visible Enter key to insert a line. */
@RunWith(AndroidJUnit4::class)
class KeyPopRC40WhatsAppEnterInstrumentedTest {
    @Test fun whatsappDoneAndNextDoNotDispatchDismissActions() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            for(pkg in listOf("com.whatsapp","com.whatsapp.w4b")) {
                for(action in listOf(EditorInfo.IME_ACTION_DONE,
                    EditorInfo.IME_ACTION_NEXT,EditorInfo.IME_ACTION_SEND)) {
                    val editor=EditText(instrumentation.targetContext).apply {
                        inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        setText("First line")
                        setSelection(text.length)
                    }
                    val hostInfo=EditorInfo().apply {
                        packageName=pkg
                        inputType=editor.inputType
                        imeOptions=action
                    }
                    val original=editor.onCreateInputConnection(EditorInfo())
                    var actionCalls=0
                    val spy=object:InputConnectionWrapper(original,false) {
                        override fun performEditorAction(actionCode:Int):Boolean {
                            actionCalls++
                            return super.performEditorAction(actionCode)
                        }
                    }
                    assertTrue("Expected newline for $pkg ($action)",
                        KeyPopEnterPolicy.insertsNewline(hostInfo))
                    // The same two operations as the service's Enter branch:
                    // commitText, not performEditorAction(DONE/NEXT/SEND).
                    assertTrue(spy.commitText("\n",1))
                    assertEquals("First line\n",editor.text.toString())
                    assertEquals("Unexpected host editor action",0,actionCalls)
                }
            }
        }
    }

    @Test fun whatsappSearchKeepsItsSearchActionPolicy() {
        val type=InputType.TYPE_CLASS_TEXT
        assertFalse(KeyPopEnterPolicy.insertsNewline(
            type,EditorInfo.IME_ACTION_SEARCH,"com.whatsapp"))
        assertFalse(KeyPopEnterPolicy.insertsNewline(
            type,EditorInfo.IME_ACTION_GO,"com.whatsapp.w4b"))
    }
}
