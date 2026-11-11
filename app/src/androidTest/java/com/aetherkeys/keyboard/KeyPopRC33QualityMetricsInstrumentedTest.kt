package com.aetherkeys.keyboard

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Bounded offline quality measurements. No fabricated Gboard baseline. */
@RunWith(AndroidJUnit4::class)
class KeyPopRC33QualityMetricsInstrumentedTest {
    @Test fun benchmarkKnownTypoRecoveryAndValidWordPreservation() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val lexicon=KeyPopLexicon(context)
        lexicon.warmup()
        val report=listOf(
            "tge" to "the","shoukd" to "should",
            "vreate" to "create","sugetions" to "suggestions",
            "suggetions" to "suggestions","grammer" to "grammar",
            "speling" to "spelling","recieve" to "receive",
            "becuase" to "because","mesage" to "message",
            "screeenshots" to "screenshots","keyboardd" to "keyboard",
            "likew" to "likewise","worke" to "worked",
            "improvem" to "improvements","predicti" to "prediction",
            "downloa" to "download","translat" to "translate"
        )
        val measured=ArrayList<Long>()
        var topThree=0;var topEight=0
        for((typed,expected) in report) {
            val start=SystemClock.elapsedRealtimeNanos()
            val options=lexicon.suggestions(0,typed,8,beforeCursor="Please "+typed)
            measured.add((SystemClock.elapsedRealtimeNanos()-start)/1_000_000L)
            if(expected in options.take(3))topThree++
            if(expected in options)topEight++
        }
        val ordinary=listOf("keyboard","work","word","screenshots",
            "improvements","likewise","office","report","evidence",
            "policy","authority","their","there","the","people")
        val harmed=ordinary.filter{lexicon.correction(0,it)!=null}
        println("KEYPOP_RC33_KNOWN_CORPUS "+
            "top3="+topThree+"/"+report.size+" top8="+topEight+"/"+report.size+
            " valid_harmed="+harmed.size+"/"+ordinary.size+
            " compute_p95_ms="+KeyPopDiagnostics.percentile(measured,95))
        // Curated regression corpus checks known user reports, not general
        // linguistic accuracy or Gboard parity.
        assertTrue("Known correction suggestions regressed",
            topEight>=report.size-1)
        assertTrue("Top-three quality fell for known examples",
            topThree>=report.size-5)
        assertTrue("Legitimate text was auto-replaced: "+harmed,harmed.isEmpty())
    }
}
