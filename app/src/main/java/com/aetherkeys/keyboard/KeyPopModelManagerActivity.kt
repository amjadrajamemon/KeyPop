package com.aetherkeys.keyboard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView

/** Compact status monitor; the same one-click installer lives on Home + Settings. */
class KeyPopModelManagerActivity:Activity() {
    private val bg=0xFF0F1D29.toInt()
    private val white=Color.WHITE
    private val muted=0xFFC1D4E2.toInt()
    private val teal=0xFF79E9D6.toInt()
    private lateinit var info:TextView
    private lateinit var progress:ProgressBar
    private lateinit var english:TextView
    private lateinit var urdu:TextView
    private lateinit var sindhi:TextView
    private val handler=Handler(Looper.getMainLooper())
    private val poll=object:Runnable{
        override fun run(){showState();handler.postDelayed(this,800)}
    }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun label(message:String,size:Float=15f,color:Int=white)=TextView(this).apply{
        text=message;textSize=size;setTextColor(color)
        setPadding(0,dp(7),0,dp(7))
    }
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        window.statusBarColor=bg
        window.navigationBarColor=bg
        val scroll=ScrollView(this).apply{setBackgroundColor(bg);isFillViewport=true}
        val column=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(18),dp(21),dp(18),dp(24))
        }
        ViewCompat.setOnApplyWindowInsetsListener(column){view,insets->
            val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(dp(18),dp(18)+bars.top,dp(18),dp(24)+bars.bottom)
            insets
        }
        scroll.addView(column);setContentView(scroll)
        ViewCompat.requestApplyInsets(scroll)
        column.addView(label("KEYPOP  /  OFFLINE LANGUAGES",13f,teal))
        column.addView(label("Language Pack Manager",24f))
        column.addView(label("English · اردو · سنڌي",17f,teal))
        column.addView(label("One download installs English and Urdu ML Kit models and all three Sindhi neural model packs (~978 MB plus English/Urdu). Allow about 1.2 GB free storage and preferably use Wi-Fi.",13f,muted))
        val button=label("Download All Languages",17f,white).apply{
            gravity=Gravity.CENTER;minHeight=dp(54)
            background=GradientDrawable().apply{
                setColor(0xFF23586A.toInt());cornerRadius=dp(13).toFloat()
                setStroke(dp(1),0xFF6BBBC2.toInt())
            }
            setOnClickListener{
                if(Build.VERSION.SDK_INT>=33&&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=
                        PackageManager.PERMISSION_GRANTED)
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),718)
                runCatching{KeyPopModelDownloadService.startAllLanguages(this@KeyPopModelManagerActivity)}
                    .onSuccess{info.text="Starting download in the background…"}
                    .onFailure{info.text="Start failed: "+it.localizedMessage}
            }
        }
        column.addView(button,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(10)})
        info=label("No transfer started.",14f,teal);column.addView(info)
        progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{
            max=100;visibility=View.GONE
        }
        column.addView(progress,LinearLayout.LayoutParams(-1,dp(22)))
        column.addView(label("Installed model status",17f,white))
        english=label("ENGLISH · checking…",13f,muted);column.addView(english)
        urdu=label("URDU · checking…",13f,muted);column.addView(urdu)
        sindhi=label("SINDHI · checking…",13f,muted);column.addView(sindhi)
        val settings=label("‹  Back to Settings",14f,teal).apply{
            setPadding(0,dp(18),0,dp(12))
            setOnClickListener {
                startActivity(Intent(this@KeyPopModelManagerActivity,MainActivity::class.java)
                    .putExtra("openSection","settingsHome"))
                finish()
            }
        }
        column.addView(settings)
        checkInstalled()
    }
    private fun checkInstalled(){
        KeyPopLanguageModels.checkText("en"){_,status->
            if(!isFinishing&&!isDestroyed)english.text="ENGLISH · "+status
        }
        KeyPopLanguageModels.checkText("ur"){_,status->
            if(!isFinishing&&!isDestroyed)urdu.text="URDU · "+status
        }
        updateSindhi()
    }
    private fun updateSindhi(){
        val done=KeyPopSindhiOnnxPack.bundles.count{
            KeyPopSindhiOnnxPack.missing(this,it).isEmpty()
        }
        sindhi.text="SINDHI · "+done+"/3 neural model bundles installed"
    }
    private fun showState(){
        if(!::info.isInitialized)return
        val state=getSharedPreferences(KeyPopModelDownloadService.PREFS,MODE_PRIVATE)
        val active=state.getBoolean("active",false)
        val finished=state.getBoolean("finished",false)
        val pct=state.getInt("percent",-1)
        val last=state.getLong("updatedAt",0L)
        info.text=if(active&&last>0L&&System.currentTimeMillis()-last>180000L)
            "No progress for 3 minutes. Tap Download All Languages to retry."
        else state.getString("message","No transfer started.").orEmpty()
        progress.visibility=if(active||finished)View.VISIBLE else View.GONE
        progress.isIndeterminate=active&&pct<0
        if(pct>=0)progress.progress=pct.coerceIn(0,100)
        if(finished){updateSindhi()}
    }
    override fun onResume(){
        super.onResume();handler.removeCallbacks(poll);handler.post(poll)
        if(::english.isInitialized)checkInstalled()
    }
    override fun onPause(){handler.removeCallbacks(poll);super.onPause()}
}
