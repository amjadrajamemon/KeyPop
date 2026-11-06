package com.aetherkeys.keyboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Release gate: real English ↔ Urdu ML Kit translations after CI disables all networks. */
@RunWith(AndroidJUnit4::class)
class OfflineEnglishUrduAcceptanceTest {
    private fun offline(source:String,target:String,input:String):String {
        val done=CountDownLatch(1)
        val result=AtomicReference<Result<String>>()
        KeyPopTranslator.translate(InstrumentationRegistry.getInstrumentation().targetContext,
            source,target,input,false,"") {
            result.set(it)
            done.countDown()
        }
        assertTrue("Offline translation timeout: "+source+" → "+target,
            done.await(90,TimeUnit.SECONDS))
        val output=result.get().getOrElse {
            throw AssertionError("Offline "+source+" → "+target+" error: "+it.message,it)
        }.trim()
        assertTrue("Empty result for "+source+" → "+target,output.length>3)
        assertFalse("Input was echoed instead of translated",output.equals(input.trim(),true))
        return output
    }
    @Test fun englishToUrduWorksWithNoInternet() {
        val result=offline("en","ur","Please send the report tomorrow morning.")
        assertTrue("Expected Urdu characters: "+result,
            result.any { it.code in 0x0600..0x06FF || it.code in 0x0750..0x077F })
    }
    @Test fun urduToEnglishWorksWithNoInternet() {
        val result=offline("ur","en","براہ کرم کل صبح مجھے رپورٹ بھیج دیں۔")
        assertTrue("Expected Latin English characters: "+result,
            result.any { it in 'a'..'z' || it in 'A'..'Z' })
    }
}
