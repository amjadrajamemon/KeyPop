package com.aetherkeys.keyboard

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.ModelDownloadListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel

/** Explicit, consent-driven ML Kit model setup; never silently sends typed text. */
object KeyPopLanguageModels {
    val supportedText=listOf("en","ur")
    val languages=mapOf("en" to "English","ur" to "Urdu","sd" to "Sindhi")
    private val modelManager get()=RemoteModelManager.getInstance()
    // Retain the recognition client while the Android provider is still reporting progress.
    private val pendingVoiceRequests=LinkedHashSet<SpeechRecognizer>()
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private fun releaseVoice(recognizer:SpeechRecognizer) {
        if(pendingVoiceRequests.remove(recognizer))recognizer.destroy()
    }

    fun knownText(lang:String):Boolean = lang in supportedText
    private fun model(lang:String):TranslateRemoteModel? =
        TranslateLanguage.fromLanguageTag(lang)?.let {
            TranslateRemoteModel.Builder(it).build()
        }

    fun checkText(lang:String,callback:(Boolean?,String)->Unit) {
        val m=if(knownText(lang))model(lang) else null
        if(m==null) {
            callback(null,"ML Kit has no downloadable translation pack for this language")
            return
        }
        try {
            modelManager.isModelDownloaded(m)
                .addOnSuccessListener { callback(it,if(it)"Installed • available offline" else "Not installed") }
                .addOnFailureListener { callback(null,"Unable to check: "+(it.localizedMessage?:"unknown error")) }
        } catch(error:Exception) {
            callback(null,"Model manager unavailable: "+(error.localizedMessage?:"check Google Play services"))
        }
    }
    fun downloadText(lang:String,wifiOnly:Boolean,callback:(Boolean,String)->Unit) {
        val m=if(knownText(lang))model(lang) else null
        if(m==null) {
            callback(false,"This language model is unavailable")
            return
        }
        val builder=DownloadConditions.Builder()
        if(wifiOnly)builder.requireWifi()
        try {
            modelManager.download(m,builder.build())
                .addOnSuccessListener { callback(true,"Installed • ready to translate offline") }
                .addOnFailureListener {
                    callback(false,"Download failed: "+(it.localizedMessage?:"unknown error")+
                        ". Check connectivity, Google Play services, storage, and the Wi-Fi-only switch.")
                }
        } catch(error:Exception) {
            callback(false,"Cannot start download: "+(error.localizedMessage?:"model manager unavailable"))
        }
    }
    fun deleteText(lang:String,callback:(Boolean,String)->Unit) {
        val m=if(knownText(lang))model(lang) else null
        if(m==null){callback(false,"Unsupported language");return}
        modelManager.deleteDownloadedModel(m)
            .addOnSuccessListener { callback(true,"Model removed") }
            .addOnFailureListener { callback(false,"Unable to remove: "+(it.localizedMessage?:"unknown error")) }
    }
    fun voiceAvailability(context:Context):String = when {
        Build.VERSION.SDK_INT<31 ->
            "On-device recognition API unavailable on this Android version; check installed speech service"
        SpeechRecognizer.isOnDeviceRecognitionAvailable(context) ->
            "On-device speech service available; a language model may still be missing"
        else ->"No on-device speech service installed. KeyPop cannot supply its own speech model yet"
    }
    private fun speechIntent(lang:String)=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE,when(lang){"ur"->"ur-PK";"sd"->"sd-PK";else->"en-US"})
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true)
    }
    /**
     * Model downloads belong to the installed Android RecognitionService. Calling this method
     * requests (not guarantees) an offline model; a provider may show its own consent UI.
     * API 34+ reports progress. No voice model bytes are bundled by KeyPop.
     */
    fun requestOnDeviceVoice(context:Context,lang:String,status:(String)->Unit) {
        if(lang=="sd") {
            status("Sindhi offline speech availability depends on the installed Android provider")
            return
        }
        if(Build.VERSION.SDK_INT<33) {
            status("Android 13+ is required to request an offline speech pack from the system")
            return
        }
        if(!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            status("No on-device recognizer installed; set up offline voice in Android speech settings")
            return
        }
        val speech=try { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }
            catch(e:Exception) {status("Speech service error: "+(e.message?:"unavailable"));return}
        pendingVoiceRequests.add(speech)
        val timeout=Runnable {
            if(pendingVoiceRequests.contains(speech)) {
                releaseVoice(speech)
                status("Voice request is still unconfirmed. Check Android speech settings.")
            }
        }
        handler.postDelayed(timeout,120_000L)
        try {
            val request=speechIntent(lang)
            if(Build.VERSION.SDK_INT>=34) {
                speech.triggerModelDownload(request,context.mainExecutor,object:ModelDownloadListener {
                    override fun onProgress(completedPercent:Int) {
                        status("Voice provider download: $completedPercent%")
                    }
                    override fun onSuccess() {
                        handler.removeCallbacks(timeout);releaseVoice(speech)
                        status("Android speech service confirms offline model ready")
                    }
                    override fun onScheduled() {
                        handler.removeCallbacks(timeout);releaseVoice(speech)
                        status("Speech model download scheduled by Android provider")
                    }
                    override fun onError(error:Int) {
                        handler.removeCallbacks(timeout);releaseVoice(speech)
                        status("Android speech model download failed (error $error)")
                    }
                })
            } else {
                speech.triggerModelDownload(request)
                status("Request sent to Android speech service; check its offline-language settings")
                handler.postDelayed({
                    handler.removeCallbacks(timeout);releaseVoice(speech)
                },15_000L)
            }
        } catch(e:Exception) {
            handler.removeCallbacks(timeout);releaseVoice(speech)
            status("Speech model request failed: "+(e.message?:"unavailable"))
        }
    }
}
