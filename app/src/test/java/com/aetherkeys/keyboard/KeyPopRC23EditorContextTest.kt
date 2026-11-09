package com.aetherkeys.keyboard

import org.junit.Assert.*
import org.junit.Test

/** Fast-path regression: mirrored commits avoid repeated remote editor reads,
 * and correctness wins over reuse after any external cursor movement. */
class KeyPopRC23EditorContextTest {
    @Test fun initialEditorReadIsReusedForEveryOrdinaryKey() {
        val cache=KeyPopEditorContext()
        var remoteReads=0
        fun before():String {
            return cache.text() ?: "Hello ".also { remoteReads++;cache.seed(it,6) }
        }
        for((i,ch) in "my name is typed with a responsive keyboard".withIndex()) {
            assertTrue(before().endsWith(if(i==0)"Hello " else
                "Hello my name is typed with a responsive keyboard"
                    .substring(0,i).takeLast(6)) || before().length>=6)
            cache.committed(ch.toString())
            assertTrue(cache.selectionChanged(7+i,7+i))
        }
        assertEquals("More than one remote read during normal typing",1,remoteReads)
        assertTrue(cache.text()!!.endsWith("responsive keyboard"))
    }
    @Test fun backspaceSurrogatesAndExternalCaretInvalidationAreSafe() {
        val c=KeyPopEditorContext()
        c.seed("hello 🙂",8)
        c.deletedCodePoint()
        assertEquals("hello ",c.text())
        assertTrue(c.selectionChanged(6,6))
        c.committed("world")
        assertEquals("hello world",c.text())
        assertFalse(c.selectionChanged(2,2))
        assertNull(c.text())
    }
    @Test fun selectedTextInvalidatesMirrorAndNextInputFetchesAgain() {
        val c=KeyPopEditorContext()
        c.seed("old sentence",12)
        assertFalse(c.selectionChanged(4,8))
        assertNull(c.text())
        c.seed("replaced text",13)
        assertTrue(c.selectionChanged(13,13))
        c.clear()
        assertFalse(c.valid())
    }
    @Test fun delayedSelectionAcknowledgmentsFromRapidTypingDoNotInvalidateMirror() {
        val c=KeyPopEditorContext()
        c.seed("hi",2)
        repeat(10){c.committed("a")}
        // The editor can report old local cursor positions after a burst.
        for(cursor in 3..12) {
            assertTrue("Unexpected mirror invalidation at $cursor",
                c.selectionChanged(cursor,cursor))
        }
        assertEquals("hi"+"a".repeat(10),c.text())
        assertFalse(c.selectionChanged(1,1))
        assertNull(c.text())
    }
        @Test fun cacheIsBoundedAndDoesNotGrowWithLongMessages() {
        val c=KeyPopEditorContext(256)
        c.seed("",0)
        repeat(30000){c.committed("a")}
        assertEquals(256,c.text()!!.length)
        assertTrue(c.selectionChanged(30000,30000))
    }
}
