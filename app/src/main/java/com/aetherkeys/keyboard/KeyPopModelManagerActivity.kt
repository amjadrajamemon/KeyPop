package com.aetherkeys.keyboard

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView

/**
 * Render a complete usable screen before asking Play Services about model availability.
 * Model downloads are explicit and fail visibly rather than leaving a blank surface.
 */
class KeyPopModelManagerActivity:Activity() {
    private val bg=0xFF101D2B.toInt()
    private val textColor=Color.WHITE
    private val muted=0xFFC7D8E6.toInt()
    private val teal=0xFF7FF0DA.toInt()
    private lateinit var column:LinearLayout
    private lateinit var wifi:CheckBox
    private lateinit var statusEn:TextView
    private lateinit var statusUr:TextView
    private lateinit var progress:ProgressBar
    private lateinit var status:TextView
    private var running=0
    private fun dp(x:Int)=(x*resources.displayMetrics.density).toInt()
    private fun label(t:String,size:Float=15f,color:Int=textColor)=TextView(this).apply{
        text=t;textSize=size;setTextColor(color)
        setPadding(0,dp(7),0,dp(7))
    }
    private fun button(title:String,fn:()->Unit) {
        val view=label(title,15f).apply {
            gravity=Gravity.CENTER
            minHeight=dp(51)
            background=GradientDrawable().apply {
                setColor(0xFF245268.toInt())
                cornerRadius=dp(11).toFloat()
                setStroke(dp(1),0xFF5CACBF.toInt())
            }
            setOnClickListener { fn() }
        }
        column.addView(view,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(8)})
    }
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        window.statusBarColor=bg
        window.navigationBarColor=bg
        val outer=ScrollView(this).apply {
            setBackgroundColor(bg);isFillViewport=true
        }
        column=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(18),dp(18),dp(18),dp(25))
        }
        outer.addView(column)
        setContentView(outer)

        column.addView(label("KEYPOP / OFFLINE LANGUAGES",13f,teal))
        column.addView(label("Language Pack Manager",24f))
        column.addView(label("Only English and Urdu translation models can be downloaded through the included ML Kit SDK. Models belong to this app and require Google Play services and a connection for initial download.",14f,muted))
        wifi=CheckBox(this).apply {
            text="Wi-Fi only (uncheck to use mobile data)"
            setTextColor(textColor)
            isChecked=getSharedPreferences("keypop",MODE_PRIVATE)
                .getBoolean("packWifiOnly",false)
            setOnCheckedChangeListener { _,checked->
                getSharedPreferences("keypop",MODE_PRIVATE)
                    .edit().putBoolean("packWifiOnly",checked).apply()
            }
        }
        column.addView(wifi)
        column.addView(label("ENGLISH / EN",17f,teal))
        statusEn=label("Not checked yet",13f,muted);column.addView(statusEn)
        button("Download / repair English") {download("en")}
        column.addView(label("URDU / اردو / UR",17f,teal))
        statusUr=label("Not checked yet",13f,muted);column.addView(statusUr)
        button("Download / repair Urdu") {download("ur")}
        button("Check installed translation models"){check()}
        progress=ProgressBar(this).apply{visibility=View.GONE;isIndeterminate=true}
        column.addView(progress,LinearLayout.LayoutParams(-1,dp(28)).apply {topMargin=dp(9)})
        status=label("No download in progress.",13f,muted)
        column.addView(status)

        column.addView(label("SINDHI / سنڌي / SD",18f,teal))
        column.addView(label("Sindhi keyboard typing: available immediately. 30 common phrase translations: available offline. Full unrestricted Sindhi translation: unavailable in this APK because ML Kit has no Sindhi model. No misleading download button is shown.",14f,muted))
        column.addView(label("Android speech services might offer Urdu or Sindhi dictation, but models are supplied by your device's provider, not by KeyPop.",13f,muted))
        button("Check offline voice service"){
            status.text=runCatching { KeyPopLanguageModels.voiceAvailability(this) }
                .getOrElse { "Voice service check failed: "+(it.localizedMessage?:"unknown") }
        }
        button("Request Urdu speech model (Android 13+)") {
            status.text="Requesting Urdu model from Android speech provider…"
            runCatching { KeyPopLanguageModels.requestOnDeviceVoice(this,"ur") {
                if(!isFinishing&&!isDestroyed)status.text=it
            } }.onFailure { status.text="Provider request failed: "+it.localizedMessage }
        }
        button("Close Language Pack Manager"){finish()}
        // This screen is visible before querying model availability; no hidden startup work.
    }
    private fun check() {
        status.text="Checking installed text models…"
        runCatching {
            KeyPopLanguageModels.checkText("en") { _,msg->
                if(!isFinishing&&!isDestroyed)statusEn.text="English: "+msg
            }
            KeyPopLanguageModels.checkText("ur") { _,msg->
                if(!isFinishing&&!isDestroyed)statusUr.text="Urdu: "+msg
            }
        }.onFailure {status.text="Model check could not run: "+it.localizedMessage}
    }
    private fun download(lang:String) {
        running++
        progress.visibility=View.VISIBLE
        val target=if(lang=="en")statusEn else statusUr
        val name=if(lang=="en")"English" else "Urdu"
        target.text="$name: download requested"
        status.text="$name: requesting model over "+if(wifi.isChecked)"Wi-Fi" else "Wi-Fi/mobile data"
        runCatching {
            KeyPopLanguageModels.downloadText(lang,wifi.isChecked) { ok,msg->
                if(isFinishing||isDestroyed)return@downloadText
                target.text="$name: "+msg
                status.text=if(ok)"✓ $name model installed" else "$name download did not complete: "+msg
                running=(running-1).coerceAtLeast(0)
                if(running==0)progress.visibility=View.GONE
            }
        }.onFailure {
            target.text="$name: download could not start: "+(it.localizedMessage?:"unknown")
            running=(running-1).coerceAtLeast(0)
            if(running==0)progress.visibility=View.GONE
        }
    }
}
