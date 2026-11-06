package com.aetherkeys.keyboard

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
    fun translate(source: String,target: String,text: String,remoteEnabled: Boolean,
                  endpoint: String,done:(Result<String>)->Unit) {
        if(text.isBlank()) { done(Result.failure(IllegalArgumentException("Nothing to translate")));return }
        if(source==target) { done(Result.success(text));return }
        val from=TranslateLanguage.fromLanguageTag(source)
        val to=TranslateLanguage.fromLanguageTag(target)
        if(from!=null && to!=null) {
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
