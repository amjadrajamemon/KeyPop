package com.aetherkeys.keyboard

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast

/** Uses the device's speech provider; recognized languages vary by installed provider. */
class KeyPopVoiceActivity : Activity() {
    companion object {
        const val RESULT_ACTION="com.aetherkeys.keyboard.VOICE_RESULT"
        private const val REQ=71
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val lang=intent.getStringExtra("lang") ?: "en-US"
        val action=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE,lang)
            putExtra(RecognizerIntent.EXTRA_PROMPT,"Speak to type")
        }
        try { startActivityForResult(action,REQ) }
        catch(_:ActivityNotFoundException) {
            Toast.makeText(this,"No speech-recognition service found",Toast.LENGTH_LONG).show()
            finish()
        }
    }
    @Deprecated("Activity Result API")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==REQ && resultCode==RESULT_OK) {
            val text=data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if(!text.isNullOrBlank()) sendBroadcast(
                Intent(RESULT_ACTION).setPackage(packageName).putExtra("text",text))
        }
        finish()
    }
}
