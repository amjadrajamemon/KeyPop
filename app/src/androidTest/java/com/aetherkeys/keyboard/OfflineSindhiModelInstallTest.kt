package com.aetherkeys.keyboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Downloads all actual model weights before the mandatory airplane-mode test. */
@RunWith(AndroidJUnit4::class)
class OfflineSindhiModelInstallTest {
    @Test fun installAllThreeIndicTrans2OnDeviceModels() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val complete=CountDownLatch(1)
        val latest=AtomicReference("Not started")
        val accepted=KeyPopSindhiOnnxPack.downloadAll(context) { event->
            latest.set(event.message)
            if(event.complete)complete.countDown()
        }
        assertTrue("Downloader already running",accepted)
        assertTrue("Download timeout; last update: "+latest.get(),
            complete.await(55,TimeUnit.MINUTES))
        for(pack in KeyPopSindhiOnnxPack.bundles) {
            val absent=KeyPopSindhiOnnxPack.missing(context,pack)
            assertTrue("Missing "+pack.name+" model files: "+absent+
                "; status: "+latest.get(),absent.isEmpty())
        }
    }
}
