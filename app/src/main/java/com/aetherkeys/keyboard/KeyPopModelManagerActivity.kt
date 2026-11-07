package com.aetherkeys.keyboard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** One lightweight selection screen: directions -> minimal models -> visible progress. */
class KeyPopModelManagerActivity:Activity() {
    private val bg=0xFF0F1D29.toInt()
    private val white=Color.WHITE
    private val muted=0xFFC1D4E2.toInt()
    private val teal=0xFF79E9D6.toInt()
    private lateinit var info:TextView
    private lateinit var progress:ProgressBar
    private lateinit var progressLabel:TextView
    private lateinit var metrics:TextView
    private lateinit var english:TextView
    private lateinit var urdu:TextView
    private lateinit var sindhi:TextView
    private lateinit var summary:TextView
    private lateinit var startButton:TextView
    private val boxes=linkedMapOf<String,CheckBox>()
    private val handler=Handler(Looper.getMainLooper())
    private val poll=object:Runnable {
        override fun run(){showState();handler.postDelayed(this,850)}
    }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun label(message:String,size:Float=15f,color:Int=white)=TextView(this).apply {
        text=message;textSize=size;setTextColor(color)
        setPadding(0,dp(6),0,dp(6))
    }
    private fun selected()=boxes.filterValues{it.isChecked}.keys.toSet()
    private fun selectionChanged() {
        val chosen=selected()
        summary.text=KeyPopPackSelection.selectionLabel(chosen)
        startButton.isEnabled=chosen.isNotEmpty()
        startButton.alpha=if(chosen.isNotEmpty())1f else .45f
        getSharedPreferences("keypop",MODE_PRIVATE).edit()
            .putStringSet("selectedTranslationDirections",chosen).apply()
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=bg;window.navigationBarColor=bg
        val scroll=ScrollView(this).apply{setBackgroundColor(bg);isFillViewport=true}
        val column=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(18),dp(20),dp(18),dp(22))
        }
        ViewCompat.setOnApplyWindowInsetsListener(column){view,insets->
            val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(dp(18),dp(16)+bars.top,dp(18),dp(20)+bars.bottom)
            insets
        }
        scroll.addView(column);setContentView(scroll)
        ViewCompat.requestApplyInsets(scroll)
        column.addView(label("KEYPOP · OFFLINE TRANSLATION",12f,teal))
        column.addView(label("Choose translation directions",22f))
        column.addView(label("Select only what you need. Shared models download once; installed packs are reused.",13f,muted))
        val saved=getSharedPreferences("keypop",MODE_PRIVATE)
            .getStringSet("selectedTranslationDirections",setOf("en-ur","ur-en"))
            .orEmpty()
        for((id,description) in KeyPopPackSelection.directions) {
            val check=CheckBox(this).apply {
                text=description
                textSize=16f
                setTextColor(white)
                isChecked=id in saved
                buttonTintList=android.content.res.ColorStateList.valueOf(teal)
                minHeight=dp(43)
                setOnCheckedChangeListener{_,_->selectionChanged()}
            }
            boxes[id]=check
            column.addView(check,LinearLayout.LayoutParams(-1,dp(46)))
        }
        summary=label("",13f,teal)
        column.addView(summary)
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        fun quick(text:String,choose:Boolean){
            row.addView(label(text,13f,teal).apply{
                gravity=Gravity.CENTER
                setOnClickListener {
                    boxes.values.forEach{it.isChecked=choose}
                    selectionChanged()
                }
            },LinearLayout.LayoutParams(0,dp(42),1f))
        }
        quick("Select all",true);quick("Clear selection",false)
        column.addView(row)
        startButton=label("Download selected packs",17f,white).apply {
            gravity=Gravity.CENTER;minHeight=dp(54)
            background=GradientDrawable().apply{
                setColor(0xFF23586A.toInt());cornerRadius=dp(13).toFloat()
                setStroke(dp(1),0xFF6BBBC2.toInt())
            }
            setOnClickListener {
                val chosen=selected()
                if(chosen.isEmpty())return@setOnClickListener
                if(Build.VERSION.SDK_INT>=33&&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=
                    PackageManager.PERMISSION_GRANTED)
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),718)
                runCatching{KeyPopModelDownloadService.startSelected(this@KeyPopModelManagerActivity,chosen)}
                    .onSuccess{info.text="Preparing the ${KeyPopPackSelection.required(chosen).size} selected packs…"}
                    .onFailure{info.text="Unable to start: "+it.localizedMessage}
            }
        }
        column.addView(startButton,LinearLayout.LayoutParams(-1,dp(54)).apply{
            topMargin=dp(10);bottomMargin=dp(8)
        })
        info=label("No transfer started.",14f,teal);column.addView(info)
        progressLabel=label("0% complete",14f,white)
        column.addView(progressLabel)
        progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{
            max=100;visibility=View.GONE
        }
        column.addView(progress,LinearLayout.LayoutParams(-1,dp(24)))
        metrics=label("",14f,muted).apply {
            setPadding(dp(12),dp(8),dp(12),dp(8))
            background=GradientDrawable().apply {
                setColor(0xFF172F39.toInt());cornerRadius=dp(9).toFloat()
            }
        }
        column.addView(metrics,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(8)})
        column.addView(label("Automatic network retry: up to three attempts. Interrupted Sindhi files are preserved and resume without restarting from zero.",12f,muted))
        column.addView(label("Installed model status",16f))
        english=label("English · checking…",13f,muted);column.addView(english)
        urdu=label("Urdu · checking…",13f,muted);column.addView(urdu)
        sindhi=label("Sindhi · checking…",13f,muted);column.addView(sindhi)
        column.addView(label("Note: ML Kit supports English and Urdu offline. Sindhi bundles are experimental downloads, not yet proof of functioning full-sentence offline translation.",12f,muted))
        column.addView(label("‹ Back to Settings",14f,teal).apply{
            setOnClickListener{
                startActivity(Intent(this@KeyPopModelManagerActivity,MainActivity::class.java)
                    .putExtra("openSection","settingsHome"))
                finish()
            }
        })
        selectionChanged();checkInstalled()
    }
    private fun checkInstalled() {
        KeyPopLanguageModels.checkText("en"){_,status->
            if(!isFinishing&&!isDestroyed)english.text="English · "+status
        }
        KeyPopLanguageModels.checkText("ur"){_,status->
            if(!isFinishing&&!isDestroyed)urdu.text="Urdu · "+status
        }
        updateSindhi()
    }
    private fun updateSindhi() {
        val done=KeyPopSindhiOnnxPack.bundles.count {
            KeyPopSindhiOnnxPack.missing(this,it).isEmpty()
        }
        sindhi.text="Sindhi · "+done+"/3 model bundles downloaded"
    }
    private fun showState() {
        if(!::info.isInitialized)return
        val state=getSharedPreferences(KeyPopModelDownloadService.PREFS,MODE_PRIVATE)
        val active=state.getBoolean("active",false)
        val finished=state.getBoolean("finished",false)
        val pct=state.getInt("percent",-1)
        val last=state.getLong("updatedAt",0L)
        info.text=if(active && last>0L && System.currentTimeMillis()-last>180000L)
            "No progress reported for 3 minutes. Check the network and retry."
        else state.getString("message","No transfer started.").orEmpty()
        progress.visibility=if(active||finished)View.VISIBLE else View.GONE
        progress.isIndeterminate=active&&pct<0
        if(pct>=0)progress.progress=pct.coerceIn(0,100)
        progressLabel.text=when {
            active && pct<0 -> "Preparing the selected packs…"
            active -> "$pct% complete · downloading / resuming"
            finished && state.getBoolean("failed",false) -> "Paused at $pct% · tap Download to resume"
            finished -> "Download completed · 100%"
            else -> "No download currently running"
        }
        val downloaded=state.getLong("downloadedBytes",0L)
        val estimated=state.getLong("estimatedBytes",0L)
        val rate=java.lang.Double.longBitsToDouble(state.getLong(
            "speedBytesBits",java.lang.Double.doubleToRawLongBits(0.0)))
        val started=state.getLong("startedAt",0L)
        val elapsed=if(started>0L)(System.currentTimeMillis()-started).coerceAtLeast(0L)/1000L else 0L
        if(estimated>0L) {
            val time=KeyPopTransferMetrics.etaSeconds(downloaded,estimated,rate)
                ?.let{KeyPopTransferMetrics.elapsed(it)}?:"Calculating…"
            metrics.text="Downloaded: "+KeyPopTransferMetrics.mb(downloaded)+" MB\n"+
                "Estimated total: ~"+KeyPopTransferMetrics.mb(estimated)+" MB\n"+
                "Remaining: ~"+KeyPopTransferMetrics.mb(
                    KeyPopTransferMetrics.remaining(downloaded,estimated))+" MB\n"+
                "Speed: "+(if(rate>1024)KeyPopTransferMetrics.mb(rate.toLong())+" MB/s"
                    else "Measuring…")+"\n"+
                "Elapsed: "+KeyPopTransferMetrics.elapsed(elapsed)+"\n"+
                "Estimated time left: "+time+"\n"+
                "Model bytes are approximate; Google ML Kit does not expose exact download size."
        } else if(active) {
            metrics.text="Google ML Kit download in progress. The ML Kit API does not provide "+
                "exact transferred MB, speed or ETA.\nElapsed: "+
                KeyPopTransferMetrics.elapsed(elapsed)
        } else {
            metrics.text="Choose language packs above. Exact model bytes and ETA appear "+
                "where supplied by the model host."
        }
        if(finished)updateSindhi()
    }
    override fun onResume() {
        super.onResume();handler.removeCallbacks(poll);handler.post(poll)
        if(::english.isInitialized)checkInstalled()
    }
    override fun onPause(){handler.removeCallbacks(poll);super.onPause()}
}
