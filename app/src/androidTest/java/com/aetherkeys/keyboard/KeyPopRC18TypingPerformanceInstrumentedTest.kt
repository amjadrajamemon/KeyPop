package com.aetherkeys.keyboard

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Performance smoke checks on Android emulator; not a Gboard benchmark.
 * Warm-index behavior must stay bounded even with one-letter fragments. */
@RunWith(AndroidJUnit4::class)
class KeyPopRC18TypingPerformanceInstrumentedTest {
    @Test fun dictionaryIsPreloadedAndOneLetterDecodeDoesNotScanEntireVocabulary() {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val engine=KeyPopLexicon(ctx)
        val warmedAt=SystemClock.elapsedRealtime()
        engine.warmup()
        assertTrue(engine.isReady())
        val loadMs=SystemClock.elapsedRealtime()-warmedAt
        val fragments=listOf("k","ke","key","w","wo","worri","vreate","sugge")
        val times=ArrayList<Long>()
        for(repeat in 0 until 10)for(fragment in fragments) {
            val start=SystemClock.elapsedRealtimeNanos()
            val result=engine.suggestions(0,fragment,8,"you",
                "Why are you $fragment")
            assertTrue("No results for $fragment",result.isNotEmpty())
            times.add((SystemClock.elapsedRealtimeNanos()-start)/1_000_000L)
        }
        val p95=KeyPopDiagnostics.percentile(times,95)
        println("KEYPOP_RC18_PERFORMANCE dictionary_cold_load_ms=$loadMs "+
            "warm_prediction_p50_ms=${KeyPopDiagnostics.percentile(times,50)} "+
            "warm_prediction_p95_ms=$p95 samples=${times.size}")
        // A generous smoke guard; real-device p95 still requires manual
        // Gboard/KeyPop tests under identical screen and thermal conditions.
        assertTrue("Warm prediction stalls longer than 250ms, p95=$p95",p95<250L)
    }

    @Test fun typosAndGrammarRemainAvailableAfterPerformanceOptimizations() {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val engine=KeyPopLexicon(ctx)
        // Verify main-thread-style lookup remains safe before first load.
        assertEquals("create",engine.correction(0,"vreate"))
        engine.warmup()
        val words=engine.suggestions(0,"worri",8,"you","Why are you worri")
        assertTrue(words.contains("worried"))
        assertTrue(words.contains("worrying"))
        assertFalse(words.contains("worry"))
        assertEquals("keyboard",engine.correction(0,"keyboardd"))
        assertEquals("grammar",engine.correction(0,"grammer"))
    }
}
