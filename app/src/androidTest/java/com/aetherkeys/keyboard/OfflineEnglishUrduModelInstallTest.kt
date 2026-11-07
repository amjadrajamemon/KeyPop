package com.aetherkeys.keyboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Installs both language models before CI disables networking for translation tests. */
@RunWith(AndroidJUnit4::class)
class OfflineEnglishUrduModelInstallTest {
    @Test fun downloadEnglishAndUrduAndVerifyInstalled() {
        for(lang in listOf("en","ur")) {
            val done=CountDownLatch(1)
            val result=AtomicReference<Pair<Boolean,String>>()
            KeyPopLanguageModels.downloadText(lang,false) { ok,message ->
                result.set(ok to message)
                done.countDown()
            }
            assertTrue("Timed out downloading model "+lang,done.await(10,TimeUnit.MINUTES))
            assertTrue("Model download failure for "+lang+": "+result.get().second,
                result.get().first)
            val verified=CountDownLatch(1)
            val present=AtomicReference<Boolean?>(null)
            val details=AtomicReference("")
            KeyPopLanguageModels.checkText(lang) { installed,message ->
                present.set(installed)
                details.set(message)
                verified.countDown()
            }
            assertTrue("Model check timed out: "+lang,verified.await(60,TimeUnit.SECONDS))
            assertTrue("Offline model "+lang+" absent: "+details.get(),present.get()==true)
        }
    }
}
