package com.aetherkeys.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android editable/input-connection assertions for Backspace behavior. */
@RunWith(AndroidJUnit4::class)
class KeyPopRC39EditorActionsInstrumentedTest {
    private fun withEditor(text:String,start:Int,end:Int,check:(EditText)->Unit) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val editor=EditText(instrumentation.targetContext).apply {
                inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                setText(text)
                setSelection(start,end)
            }
            check(editor)
        }
    }

    @Test fun selectAllBackspaceDeletesEntireSelection() {
        val value="This text\nshould disappear in one press"
        withEditor(value,0,value.length) { editor ->
            val connection=editor.onCreateInputConnection(EditorInfo())
            assertNotNull("Need live InputConnection",connection)
            assertEquals(KeyPopEditorActions.Deletion.SELECTED_TEXT,
                KeyPopEditorActions.deleteBackward(connection,true))
            assertEquals("",editor.text.toString())
        }
    }

    @Test fun deletingMiddleSelectionDoesNotDeleteAdjacentCharacters() {
        withEditor("abCDEfg",2,5) { editor ->
            val connection=editor.onCreateInputConnection(EditorInfo())
            assertEquals(KeyPopEditorActions.Deletion.SELECTED_TEXT,
                KeyPopEditorActions.deleteBackward(connection,true))
            assertEquals("abfg",editor.text.toString())
        }
    }

    @Test fun normalBackspaceRemovesOneUnicodeCodePoint() {
        val original="A\uD83D\uDE42"
        withEditor(original,original.length,original.length) { editor ->
            val connection=editor.onCreateInputConnection(EditorInfo())
            assertEquals(KeyPopEditorActions.Deletion.CODE_POINT,
                KeyPopEditorActions.deleteBackward(connection,false))
            assertEquals("A",editor.text.toString())
        }
    }

    @Test fun whatsappEnterWithSendFlagCommitsNewline() {
        withEditor("Message",7,7) { editor ->
            val info=EditorInfo().apply {
                packageName="com.whatsapp"
                inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                imeOptions=EditorInfo.IME_ACTION_SEND
            }
            assertTrue(KeyPopEnterPolicy.insertsNewline(info))
            val connection=editor.onCreateInputConnection(EditorInfo())
            assertTrue(connection.commitText("\n",1))
            assertEquals("Message\n",editor.text.toString())
        }
    }
}
