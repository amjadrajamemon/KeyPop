package com.aetherkeys.keyboard

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView

/**
 * Consent-based first-launch offline language setup.
 * Android cannot run custom code during installation; this runs on first app opening.
 */
class KeyPopModelManagerActivity:Activity() {
    private val bg=0xFF082D2B.toInt()
    private val surface=0xFF19534F.toInt()
    private val ink=0xFFF0FFFA.toInt()
    private lateinit var root:LinearLayout
    private lateinit var english:CheckBox
    private lateinit var urdu:CheckBox
    private lateinit var wifiOnly:CheckBox
    private lateinit var englishStatus:TextView
    private lateinit var urduStatus:TextView
    private lateinit var voiceStatus:TextView
    private lateinit var detail:TextView
    private lateinit var progress:ProgressBar
    private var downloading=0
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    private fun line(value:String,size:Float=14f)=TextView(this).apply{
        text=value;textSize=size;setTextColor(ink)
        setPadding(dp(3),dp(5),dp(3),dp(5))
    }
    private fun action(label:String,fn:()->Unit)=Button(this).apply{
        text=label;textSize=13f;isAllCaps=false
        setTextColor(ink)
        background=GradientDrawable().apply{
            setColor(surface);cornerRadius=dp(13).toFloat()
        }
        setOnClickListener{fn()}
    }
    private fun button(label:String,fn:()->Unit){
        root.addView(action(label,fn),LinearLayout.LayoutParams(-1,dp(46)).apply{
            topMargin=dp(7)
        })
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=bg
        window.navigationBarColor=bg
        val first=intent.getBooleanExtra("firstRun",false)
        val scroll=ScrollView(this).apply{isFillViewport=true;setBackgroundColor(bg)}
        root=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(18),dp(18),dp(18),dp(30))
        }
        scroll.addView(root)
        setContentView(scroll)
        root.addView(line("KEYPOP  /  LANGUAGE ENGINE",12f))
        root.addView(line(if(first)"Set up offline languages" else "Offline Language Packs",25f))
        root.addView(line("Download packs now, or skip and download later in Settings. " +
            "Downloads require your permission and are started after first launch, not during Android installation.",13f))
        root.addView(line("English & Urdu: on-device ML Kit translation models. " +
            "Common English, Urdu and Sindhi phrases are already included in KeyPop. " +
            "Full Sindhi translation is not supplied by ML Kit.",12f))
        english=CheckBox(this).apply{
            text="English text translation";setTextColor(ink);isChecked=true
        }
        urdu=CheckBox(this).apply{
            text="Urdu text translation";setTextColor(ink);isChecked=true
        }
        wifiOnly=CheckBox(this).apply{
            text="Download on Wi-Fi only (uncheck for mobile data)";setTextColor(ink);isChecked=false
        }
        root.addView(english);root.addView(urdu);root.addView(wifiOnly)
        englishStatus=line("English: checking...",13f)
        urduStatus=line("Urdu: checking...",13f)
        root.addView(englishStatus);root.addView(urduStatus)
        root.addView(line("Sindhi • سنڌي: basic phrasebook included with KeyPop (no download required). Full-sentence offline Sindhi translation is not currently available through the ML Kit engine.",13f))
        progress=ProgressBar(this).apply{isIndeterminate=true;visibility=View.GONE}
        root.addView(progress,LinearLayout.LayoutParams(-1,dp(6)))
        detail=line("No download requested yet.",12f)
        root.addView(detail)
        button("Download selected text language packs"){downloadSelected()}
        button("Refresh installed model status"){refreshModels()}
        root.addView(line("SINDHI OFFLINE DOWNLOAD — SEPARATE APP",15f))
        root.addView(line("Google Translate offers a downloadable Sindhi offline pack. This pack belongs to Google Translate and cannot be imported into KeyPop's ML Kit engine. Open Google Translate, select Sindhi, and use the download icon next to that language.",12f))
        button("Open Google Translate to download Sindhi") {
            try {
                val launch=packageManager.getLaunchIntentForPackage(
                    "com.google.android.apps.translate")
                val fallback=Intent(Intent.ACTION_VIEW,Uri.parse(
                    "https://play.google.com/store/apps/details?id=com.google.android.apps.translate"))
                startActivity(launch ?: fallback)
                detail.text="In Google Translate, select Sindhi and tap its download icon. This installs the pack in Google Translate, not KeyPop."
            } catch(error:Exception) {
                detail.text="Unable to open Google Translate: "+(error.localizedMessage?:"No compatible browser or store installed")
            }
        }
        if(!first) {
            button("Remove English text model"){
                KeyPopLanguageModels.deleteText("en"){_,msg->detail.text=msg;refreshModels()}
            }
            button("Remove Urdu text model"){
                KeyPopLanguageModels.deleteText("ur"){_,msg->detail.text=msg;refreshModels()}
            }
        }
        root.addView(line("VOICE PACKS (ANDROID PROVIDER)",15f))
        voiceStatus=line(KeyPopLanguageModels.voiceAvailability(this),12f)
        root.addView(voiceStatus)
        root.addView(line("KeyPop does not currently bundle speech-recognition model files. " +
            "Android 13+ can request offline models from the installed speech service; " +
            "availability and consent are controlled by your device/provider.",12f))
        button("Request English offline speech"){
            KeyPopLanguageModels.requestOnDeviceVoice(this,"en"){voiceStatus.text=it}
        }
        button("Request Urdu offline speech"){
            KeyPopLanguageModels.requestOnDeviceVoice(this,"ur"){voiceStatus.text=it}
        }
        root.addView(line("Sindhi: common typed phrases work offline. Full offline Sindhi voice and text models are not yet available in this build.",12f))
        button(if(first)"Skip / Finish setup" else "Done"){finish()}
        refreshModels()
    }
    private fun refreshModels(){
        KeyPopLanguageModels.checkText("en"){_,message->englishStatus.text="English: "+message}
        KeyPopLanguageModels.checkText("ur"){_,message->urduStatus.text="Urdu: "+message}
    }
    private fun downloadSelected(){
        val languages=buildList{
            if(english.isChecked)add("en")
            if(urdu.isChecked)add("ur")
        }
        if(languages.isEmpty()){
            detail.text="Select at least one language to download."
            return
        }
        downloading=languages.size
        progress.visibility=View.VISIBLE
        detail.text="Preparing "+downloading+" model download(s) using "+
            (if(wifiOnly.isChecked)"Wi-Fi only" else "Wi-Fi or mobile data")+
            ". Google ML Kit does not report byte-by-byte progress."
        languages.forEach{lang->
            KeyPopLanguageModels.downloadText(lang,wifiOnly.isChecked){ok,message->
                if(isFinishing || isDestroyed)return@downloadText
                detail.text=(if(ok)"✓ " else "⚠ ")+
                    (KeyPopLanguageModels.languages[lang]?:lang)+": "+message
                downloading--
                if(downloading<=0){
                    progress.visibility=View.GONE
                    refreshModels()
                }
            }
        }
    }
}
