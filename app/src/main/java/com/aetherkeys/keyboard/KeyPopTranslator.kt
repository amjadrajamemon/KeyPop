package com.aetherkeys.keyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** User-initiated translation; cloud service requires explicit opt-in and HTTPS. */
object KeyPopTranslator {
    private val main = Handler(Looper.getMainLooper())
    fun translate(context:Context,source:String,target:String,text:String,
                  remoteEnabled:Boolean,endpoint:String,
                  done:(Result<String>)->Unit) {
        if(text.isBlank()) { done(Result.failure(IllegalArgumentException("Nothing to translate")));return }
        if(source==target) { done(Result.success(text));return }
        // Genuine Sindhi translation must route through the installed model.
        // Never silently replace unknown sentences with a 30-phrase dictionary,
        // nor send keyboard text over a network without explicit user opt-in.
        if(KeyPopOfflineSindhiEngine.supports(source,target)) {
            if(KeyPopOfflineSindhiEngine.ready(context,source,target)) {
                KeyPopOfflineSindhiEngine.translate(context,source,target,text,done)
                return
            }
            KeyPopSindhiPhrasebook.translate(source,target,text)?.let {
                done(Result.success(it));return
            }
            if(!remoteEnabled || !endpoint.startsWith("https://")) {
                val selected=KeyPopOfflineSindhiEngine.chosen(source,target)
                done(Result.failure(IllegalStateException(
                    "Required Sindhi offline pack not installed: "+
                    (selected?.name?:"unsupported direction")+
                    ". Open KeyPop → Language Packs and install all models.")))
                return
            }
        }
        val from=TranslateLanguage.fromLanguageTag(source)
        val to=TranslateLanguage.fromLanguageTag(target)
        if(from!=null && to!=null &&
            !KeyPopOfflineSindhiEngine.supports(source,target)) {
            val engine=Translation.getClient(TranslatorOptions.Builder()
                .setSourceLanguage(from).setTargetLanguage(to).build())
            engine.downloadModelIfNeeded(DownloadConditions.Builder().build())
                .addOnSuccessListener {
                    engine.translate(text).addOnSuccessListener { result ->
                        engine.close();done(Result.success(result))
                    }.addOnFailureListener { error ->engine.close();done(Result.failure(error)) }
                }.addOnFailureListener { error ->engine.close();done(Result.failure(error)) }
            return
        }
        if(!remoteEnabled || !endpoint.startsWith("https://")) {
            done(Result.failure(IllegalStateException("Sindhi translation requires a configured HTTPS translation provider.")));return
        }
        Thread {
            val result=runCatching {
                val connection=(URL(endpoint).openConnection() as HttpURLConnection)
                try {
                    connection.requestMethod="POST";connection.connectTimeout=12000
                    connection.readTimeout=18000
                    connection.setRequestProperty("Content-Type","application/json; charset=utf-8")
                    connection.doOutput=true
                    val json=JSONObject().put("source",source).put("target",target).put("text",text)
                    connection.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
                    if(connection.responseCode !in 200..299) throw IllegalStateException("Translation provider error")
                    JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                        .getString("translatedText")
                } finally { connection.disconnect() }
            }
            main.post { done(result) }
        }.start()
    }
}
