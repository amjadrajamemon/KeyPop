package com.aetherkeys.keyboard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Voice → recognize → translate → preview → insert/send, presented within KeyPop.
 * On-device Android ASR is preferred. Android speech models are NOT bundled by
 * KeyPop; an explicit provider fallback can use a separate installed service.
 */
class KeyPopVoiceActivity:Activity(),RecognitionListener {
    companion object {
        const val RESULT_ACTION="com.aetherkeys.keyboard.VOICE_RESULT"
        private const val AUDIO_REQUEST=91
        private val codes=listOf("en","ur","sd")
        private val locales=listOf("en-US","ur-PK","sd-PK")
        private val names=listOf("English","اردو","سنڌي")
    }
    private var source=0
    private var target=1
    private var allowProvider=false
    private var recognizer:SpeechRecognizer?=null
    private lateinit var status:TextView
    private lateinit var speech:EditText
    private lateinit var translated:EditText
    private lateinit var sourceLabel:TextView
    private lateinit var targetLabel:TextView
    private var translatedReady=false
    private fun dp(x:Int)=(x*resources.displayMetrics.density).toInt()
    private fun line(t:String,sp:Float)=TextView(this).apply{
        text=t;textSize=sp;setTextColor(0xFFEAF0FF.toInt())
        setPadding(dp(4),dp(7),dp(4),dp(7))
    }
    private fun button(t:String,action:()->Unit)=Button(this).apply{
        text=t;isAllCaps=false;textSize=13f
        setOnClickListener{action()}
    }
    private fun row(vararg entries:Pair<String,()->Unit>):LinearLayout {
        val r=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        entries.forEach{(name,fn)->
            r.addView(button(name,fn),LinearLayout.LayoutParams(0,dp(48),1f))
        }
        return r
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        source=when(intent.getStringExtra("lang")) {
            "ur-PK"->1;"sd-PK"->2;else->0
        }
        val configured=intent.getIntExtra("target",-1)
        target=if(configured in 0..2 && configured!=source)configured
            else if(source==0)1 else 0
        window.statusBarColor=0xFF0D1527.toInt()
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(17),dp(18),dp(17),dp(20))
            setBackgroundColor(0xFF0D1527.toInt())
        }
        root.addView(line("KEYPOP  /  VOICE TRANSLATE",13f))
        root.addView(line("Speak, translate, send",24f))
        root.addView(line("Recognition uses an installed Android speech engine. KeyPop prefers local speech models. Translation uses its offline phrasebook or downloaded on-device models; unrestricted offline voice translation is not yet bundled.",12f))
        sourceLabel=line("",15f);targetLabel=line("",15f)
        root.addView(sourceLabel)
        root.addView(row(
            "English" to {source=0;renderLabels()},
            "اردو" to {source=1;renderLabels()},
            "سنڌي" to {source=2;renderLabels()}
        ))
        root.addView(targetLabel)
        root.addView(row(
            "English" to {target=0;renderLabels()},
            "اردو" to {target=1;renderLabels()},
            "سنڌي" to {target=2;renderLabels()}
        ))
        status=line("Ready. Tap Start listening to use local recognition.",12f)
        root.addView(status)
        root.addView(row(
            "🎙 Start listening" to {beginRecognition()},
            "Stop" to {recognizer?.stopListening()}
        ))
        root.addView(button("Use Android speech provider if no on-device recognizer exists"){
            allowProvider=true
            status.text="Provider fallback enabled for this session; Android's provider may use a network."
            beginRecognition()
        })
        speech=EditText(this).apply{
            hint="Recognized speech (editable before translation)"
            setHintTextColor(0xFF98AACB.toInt());setTextColor(Color.WHITE)
            minLines=2;maxLines=5
        }
        root.addView(speech)
        root.addView(button("Translate the text above") {translate()})
        translated=EditText(this).apply{
            hint="Translated output preview"
            setHintTextColor(0xFF98AACB.toInt());setTextColor(Color.WHITE)
            minLines=2;maxLines=5
        }
        root.addView(translated)
        root.addView(row(
            "Insert translated text" to {deliver(false)},
            "Translate & Send" to {deliver(true)}
        ))
        root.addView(line("Send is permitted only when the original app exposes an Android Send action. Otherwise the translated text is inserted for manual sending.",12f))
        val scrolling=ScrollView(this).apply{isFillViewport=true}
        scrolling.addView(root)
        setContentView(scrolling)
        renderLabels()
    }
    private fun renderLabels() {
        sourceLabel.text="Speak / source: "+names[source]
        targetLabel.text="Translate into: "+names[target]
        translatedReady=false
    }
    private fun beginRecognition() {
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),AUDIO_REQUEST);return
        }
        recognizer?.destroy();recognizer=null
        val local=Build.VERSION.SDK_INT>=31 &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        if(!local && !allowProvider) {
            status.text="No on-device recognition model is available. Install an offline Android speech model or explicitly use the provider fallback."
            return
        }
        if(!local && !SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text="No compatible speech recognizer is installed on this device."
            return
        }
        try{
            recognizer=if(local && Build.VERSION.SDK_INT>=31)
                SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            else SpeechRecognizer.createSpeechRecognizer(this)
            recognizer?.setRecognitionListener(this)
            recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE,locales[source])
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true)
            })
            status.text="Listening in "+names[source]+
                (if(local)" (on-device recognizer)" else " (Android provider)")
        } catch(e:Exception){
            status.text="Speech could not start: "+(e.message?:"unavailable")
            recognizer?.destroy();recognizer=null
        }
    }
    private fun translate() {
        val sourceText=speech.text.toString().trim()
        if(sourceText.isEmpty()) {
            Toast.makeText(this,"Speak or enter some text first",Toast.LENGTH_SHORT).show()
            return
        }
        if(source==target) {
            translated.setText(sourceText)
            translatedReady=true
            status.text="Same source and target language: original text retained."
            return
        }
        status.text="Translating… (offline phrases first)"
        val prefs=getSharedPreferences("keypop",MODE_PRIVATE)
        KeyPopTranslator.translate(codes[source],codes[target],sourceText,
            prefs.getBoolean("remoteTranslate",false),
            prefs.getString("translateEndpoint","").orEmpty()) voiceResult@{ outcome->
            if(isFinishing||isDestroyed)return@voiceResult
            outcome.onSuccess{
                translated.setText(it);translatedReady=true
                status.text="Translation is ready. Review before inserting/sending."
            }.onFailure{
                translatedReady=false
                status.text="Translation unavailable: "+(it.message?:"model not installed")
            }
        }
    }
    private fun deliver(send:Boolean) {
        val text=translated.text.toString().trim()
        if(!translatedReady||text.isEmpty()){
            status.text="Translate the source first and review the result."
            return
        }
        sendBroadcast(Intent(RESULT_ACTION).setPackage(packageName)
            .putExtra("text",text).putExtra("send",send))
        Toast.makeText(this,
            if(send)"Returning translation to the original app" else "Inserting translation",
            Toast.LENGTH_SHORT).show()
        finish()
    }
    override fun onRequestPermissionsResult(req:Int,permissions:Array<out String>,grants:IntArray){
        super.onRequestPermissionsResult(req,permissions,grants)
        if(req==AUDIO_REQUEST && grants.firstOrNull()==PackageManager.PERMISSION_GRANTED)
            beginRecognition()
        else status.text="Microphone permission is needed for voice typing."
    }
    override fun onReadyForSpeech(params:Bundle?){status.text="Speak now…"}
    override fun onBeginningOfSpeech(){status.text="Listening…"}
    override fun onRmsChanged(rmsdB:Float){}
    override fun onBufferReceived(buffer:ByteArray?){}
    override fun onEndOfSpeech(){status.text="Recognizing speech…"}
    override fun onError(error:Int){status.text="Speech recognition error "+error+
        " — please type text or install the language's offline recognition model."}
    override fun onResults(results:Bundle?){
        val recognized=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull().orEmpty()
        if(recognized.isNotBlank()){
            speech.setText(recognized);translate()
        } else status.text="No speech was recognized."
    }
    override fun onPartialResults(results:Bundle?){
        val recognized=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull().orEmpty()
        if(recognized.isNotEmpty() && !translatedReady)speech.setText(recognized)
    }
    override fun onEvent(eventType:Int,params:Bundle?){}
    override fun onDestroy(){recognizer?.destroy();recognizer=null;super.onDestroy()}
}
