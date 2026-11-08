package com.aetherkeys.keyboard

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real Android encryption/database regression. Confirm portable backup
 * retains duplicate instances and pin state across archive restore. */
@RunWith(AndroidJUnit4::class)
class KeyPopRC17ClipboardBackupInstrumentedTest {
    @Test fun passwordBackupRoundTripAndInvalidPasswordAreNonDestructive() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val store=ClipStore(context)
        val marker="keypop-rc17-archive-"+System.nanoTime()+"-"
        val file=File(context.cacheDir,"rc17-archive-test.kpb")
        val pwd="test-passphrase-2026"
        try {
            store.save(marker+"one")
            store.save(marker+"one")
            store.save(marker+"two")
            val before=store.search(marker)
            assertEquals(3,before.size)
            store.pin(before.last().id,true)
            val exported=KeyPopClipboardBackup.export(context,Uri.fromFile(file),pwd)
            assertTrue(exported>=3)
            before.forEach {store.delete(it.id)}
            assertEquals(0,store.search(marker).size)
            assertTrue(runCatching {
                KeyPopClipboardBackup.restore(context,Uri.fromFile(file),"wrong-pass")
            }.isFailure)
            assertEquals(0,store.search(marker).size)
            val imported=KeyPopClipboardBackup.restore(context,Uri.fromFile(file),pwd)
            assertEquals(exported,imported)
            val after=store.search(marker)
            assertEquals(3,after.size)
            assertEquals(2,after.count{it.text==marker+"one"})
            assertTrue(after.any{it.pinned})
        } finally {
            store.search(marker).forEach{store.delete(it.id)}
            file.delete()
        }
    }
}
