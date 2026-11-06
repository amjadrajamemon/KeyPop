package com.aetherkeys.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyPopSindhiOnnxPackTest {
    @Test fun twoDifferentRealOfflineModelDirectionsAreDeclared() {
        val packs=KeyPopSindhiOnnxPack.bundles
        assertEquals(setOf("en-sd","sd-en"),packs.map{it.id}.toSet())
        assertEquals(318,packs[0].approximateMB)
        assertEquals(270,packs[1].approximateMB)
        assertTrue(packs.all{it.repo.startsWith("hari31416/indictrans2-")})
    }
    @Test fun modelFilesContainDecoderWeightsAndTokenizers() {
        for (pack in KeyPopSindhiOnnxPack.bundles) {
            assertTrue(pack.files.contains("encoder_model.onnx"))
            assertTrue(pack.files.contains("encoder_model.onnx.data"))
            assertTrue(pack.files.contains("decoder_shared.onnx.data"))
            assertTrue(pack.files.contains("decoder_model.onnx"))
            assertTrue(pack.files.contains("decoder_with_past_model.onnx"))
            assertTrue(pack.files.contains("tokenizer_src.json"))
            assertTrue(pack.files.contains("tokenizer_tgt.json"))
            assertEquals(pack.files.size,pack.files.distinct().size)
        }
    }
    @Test fun everyDownloadUsesPinnedHttpsRepositoryRevision() {
        for(pack in KeyPopSindhiOnnxPack.bundles) {
            assertTrue(pack.revision.matches(Regex("[0-9a-f]{7,40}")))
            for(name in pack.files) {
                val url=pack.url(name)
                assertTrue(url.startsWith("https://huggingface.co/"))
                assertTrue(url.contains("/resolve/"+pack.revision+"/"))
                assertTrue(url.endsWith("/"+name+"?download=true"))
            }
        }
    }
}
