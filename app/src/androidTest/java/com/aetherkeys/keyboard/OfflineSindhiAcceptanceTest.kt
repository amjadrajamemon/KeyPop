package com.aetherkeys.keyboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Mandatory offline Sindhi release tests. These MUST NOT be skipped or marked
 * @Ignore merely to obtain a green CI build. No remote translation provider,
 * and only previously-installed on-device model packs may be used.
 *
 * Current 0.9.4 is expected to FAIL because its ONNX runtime has no real
 * tokenizer/decoder; its fallback phrasebook cannot translate these sentences.
 */
@RunWith(AndroidJUnit4::class)
class OfflineSindhiAcceptanceTest {
    private fun offline(source:String,target:String,text:String):String {
        val complete=CountDownLatch(1)
        val output=AtomicReference<Result<String>>()
        KeyPopTranslator.translate(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext,
            source,target,text,false,"") {
            output.set(it)
            complete.countDown()
        }
        assertTrue("Offline translation did not respond in 90 seconds",
            complete.await(90,TimeUnit.SECONDS))
        val translation=output.get().getOrElse {
            throw AssertionError("Offline ${source} → ${target} failed: "+it.message,it)
        }.trim()
        assertTrue("Translation must not be empty",translation.length>3)
        assertFalse("Source echoed unchanged instead of translated",
            translation.equals(text,ignoreCase=true))
        return translation
    }

    private fun hasSindhiScript(value:String):Boolean = value.any {
        it in '\u067B'..'\u067D' || it in '\u0680'..'\u0684' ||
            it in '\u0686'..'\u0687' || it in '\u068C'..'\u0690' ||
            it in '\u0699'..'\u06AF' ||
            it in '\u0621'..'\u064A'
    }

    @Test fun englishToSindhiTranslatesNovelWeatherSentenceOffline() {
        val translated=offline("en","sd",
            "Tomorrow the weather will be very hot, so take enough drinking water.")
        assertTrue("Translation not in Sindhi Arabic script: "+translated,
            hasSindhiScript(translated))
    }

    @Test fun englishToSindhiTranslatesNovelOfficeSentenceOffline() {
        val translated=offline("en","sd",
            "Please review the monthly financial report before Thursday morning.")
        assertTrue("Translation not in Sindhi Arabic script: "+translated,
            hasSindhiScript(translated))
    }

    @Test fun sindhiToEnglishTranslatesNovelSchoolSentenceOffline() {
        val translated=offline("sd","en","اسان جا ٻار سڀاڻي اسڪول ويندا.")
        assertTrue("Expected English translation: "+translated,
            translated.any{it in 'A'..'Z' || it in 'a'..'z'})
    }

    @Test fun sindhiToEnglishTranslatesNovelQuestionOffline() {
        val translated=offline("sd","en","توهان اڄ آفيس ۾ ڪهڙي وقت ايندا؟")
        assertTrue("Expected English translation: "+translated,
            translated.any{it in 'A'..'Z' || it in 'a'..'z'})
    }

    @Test fun urduToSindhiMustWorkFullyOffline() {
        val translated=offline("ur","sd",
            "براہ کرم کل کی میٹنگ کے لیے ضروری دستاویزات تیار کریں۔")
        assertTrue("Expected Sindhi Arabic-script output: "+translated,
            hasSindhiScript(translated))
    }

    @Test fun sindhiToUrduMustWorkFullyOffline() {
        val translated=offline("sd","ur","اسان کي سڀاڻي گڏجاڻي لاءِ رپورٽ تيار ڪرڻي آهي.")
        assertTrue("Expected Urdu Arabic-script output: "+translated,
            hasSindhiScript(translated))
    }
}
