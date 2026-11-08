package com.aetherkeys.keyboard

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class KeyPopRC21PersonalArchiveInstrumentedTest {
    @Test fun learnedPhrasesAreContextSpecificAndCanBeErased() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("keypop",0).edit()
            .putBoolean("personalHistoryLearning",true)
            .putBoolean("learningEnabled",true).commit()
        val model=KeyPopPersonalLanguageModel(context)
        model.clear()
        try {
            repeat(3){model.observe(0,"Why are you ","worried")}
            model.observe(0,"Why are you ","worrying")
            assertEquals("worried",model.candidates(0,"Why are you ","wor",3).first())
            // Global personal frequency also suggests prior words in new contexts.
            assertTrue(model.candidates(0,"I might ","wor",3).contains("worried"))
            val added=model.learnFromSavedClip(0,
                "Please review the report before Thursday morning.")
            assertTrue(added>2)
            assertTrue(model.candidates(0,"Please review the ","rep",3)
                .contains("report"))
            model.clear()
            assertTrue(model.candidates(0,"Why are you ","wor",3).isEmpty())
        }finally{model.clear()}
    }
    @Test fun typingLexiconRepairsTopredictWithoutBreakingKnownWords() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val lexicon=KeyPopLexicon(context)
        lexicon.warmup()
        assertEquals("to predict",lexicon.correction(0,"topredict"))
        assertTrue(lexicon.suggestions(0,"topredict").contains("to predict"))
        assertNull(lexicon.joinedWordCorrection(0,"together"))
    }
    @Test fun screenshotArchiveIsTransactionalAcrossInstancesAndDeduplicates() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val one=KeyPopImageStore(context)
        val two=KeyPopImageStore(context)
        val marker="rc21-test-"+System.nanoTime()
        val unique=marker.toByteArray()
        val first=one.count()
        try {
            assertTrue(one.saveBytes(unique,"image/png",marker))
            assertTrue(two.saveBytes(unique,"image/png",marker))
            assertEquals(first+1,one.count())
            val found=two.search(marker)
            assertEquals(1,found.size)
            two.pin(found.single().id)
            assertTrue(one.search(marker).single().pinned)
            two.rename(found.single().id,"updated-"+marker)
            assertEquals(1,one.search("updated-"+marker).size)
        }finally{
            one.search(marker).forEach{one.delete(it.id)}
            one.search("updated-"+marker).forEach{one.delete(it.id)}
        }
    }
    @Test fun fullBackupRejectsWrongPasswordBeforeAnyImportAndPreservesMedia() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val textStore=ClipStore(context)
        val pictures=KeyPopImageStore(context)
        val token="rc21-full-backup-"+System.nanoTime()
        val path=File(context.cacheDir,token+".kpfb")
        val uri=Uri.fromFile(path)
        val password="backup-password-2026"
        val bytes=token.toByteArray()
        try {
            textStore.save(token)
            assertTrue(pictures.saveBytes(bytes,"image/png",token))
            val exported=KeyPopFullBackup.export(context,uri,password)
            assertTrue(exported.first>=1)
            assertTrue(exported.second>=1)
            assertTrue(runCatching{
                KeyPopFullBackup.restore(context,uri,"wrong-password")
            }.isFailure)
            val beforeClips=textStore.search(token).size
            val beforeImages=pictures.search(token).size
            val imported=KeyPopFullBackup.restore(context,uri,password)
            assertEquals(exported,imported)
            assertEquals(beforeClips+1,textStore.search(token).size)
            // Image SHA-256 deduplication prevents duplicate archive imports.
            assertEquals(beforeImages,pictures.search(token).size)
        }finally{
            textStore.search(token).forEach{textStore.delete(it.id)}
            pictures.search(token).forEach{pictures.delete(it.id)}
            path.delete()
        }
    }
}
