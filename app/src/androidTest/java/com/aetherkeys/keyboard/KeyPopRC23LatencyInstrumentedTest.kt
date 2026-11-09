package com.aetherkeys.keyboard

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Measures the cheap correction path and validates Android package/IME
 * integration; the exact comparison with Gboard must be done on one device. */
@RunWith(AndroidJUnit4::class)
class KeyPopRC23LatencyInstrumentedTest {
    @Test fun spaceCorrectionUsesVerifiedFastPath() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val lexicon=KeyPopLexicon(context)
        assertEquals("to predict",lexicon.fastCorrection(0,"topredict"))
        assertEquals("the",lexicon.fastCorrection(0,"tge"))
        assertNull(lexicon.fastCorrection(0,"together"))
    }
    @Test fun noRemoteReadNeededDuringLongMirroredTypingBurst() {
        val cache=KeyPopEditorContext()
        cache.seed("Write ",6)
        var remoteCalls=0
        val start=SystemClock.elapsedRealtimeNanos()
        repeat(3000) {
            cache.text()?.let{} ?:run{remoteCalls++;cache.seed("",0)}
            cache.committed("a")
        }
        val elapsed=(SystemClock.elapsedRealtimeNanos()-start)/1_000_000L
        assertEquals(0,remoteCalls)
        assertEquals(256,cache.text()!!.length)
        // This is a deliberately generous catastrophic-regression guard,
        // NOT a valid benchmark of end-to-end editor latency.
        assertTrue("RAM mirror operations unexpectedly slow: $elapsed ms",elapsed<2500)
    }
}
