package com.aetherkeys.keyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/** Local, opt-in timing data only; never records content, app targets, or credentials. */
object KeyPopDiagnostics {
    private val recent=ArrayList<Long>()
    fun percentile(samples:List<Long>,percent:Int):Long {
        if(samples.isEmpty())return 0L
        val ordered=samples.sorted()
        val rank=((ordered.lastIndex*percent.coerceIn(0,100))/100.0).toInt()
        return ordered[rank]
    }
    @Synchronized fun observeKeyboardBuild(context:Context,elapsedNanos:Long) {
        if(!context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
                .getBoolean("performanceMetrics",false))return
        if(elapsedNanos<=0L || elapsedNanos>10_000_000_000L)return
        recent.add(elapsedNanos/1_000_000L)
        if(recent.size>100)recent.removeAt(0)
    }
    @Synchronized fun flush(context:Context) {
        if(recent.isEmpty())return
        val prefs=context.getSharedPreferences("keypop_performance",Context.MODE_PRIVATE)
        val old=prefs.getString("viewBuildMs","").orEmpty().split(",")
            .mapNotNull{it.toLongOrNull()}
        val all=(old+recent).takeLast(200)
        prefs.edit().putString("viewBuildMs",all.joinToString(",")).apply()
        recent.clear()
    }
    @Synchronized fun measurements(context:Context):List<Long> {
        val prefs=context.getSharedPreferences("keypop_performance",Context.MODE_PRIVATE)
        val saved=prefs.getString("viewBuildMs","").orEmpty().split(",")
            .mapNotNull{it.toLongOrNull()}
        return saved+recent
    }
    @Synchronized fun clear(context:Context) {
        recent.clear()
        context.getSharedPreferences("keypop_performance",Context.MODE_PRIVATE).edit().clear().apply()
    }
    fun summary(context:Context):String {
        val values=measurements(context)
        return if(values.isEmpty())"No local keyboard-view samples yet."
        else "KeyPop keyboard view-construction time (not end-to-end latency):\n"+
            "Samples: "+values.size+"\n"+
            "Median: "+percentile(values,50)+" ms\n"+
            "95th percentile: "+percentile(values,95)+" ms\n"+
            "Min: "+values.minOrNull()+" ms · Max: "+values.maxOrNull()+" ms\n"+
            "Device: Android "+android.os.Build.VERSION.RELEASE+
            "\nNote: this is not a comparative benchmark."
    }
}

class KeyPopDiagnosticsActivity:Activity() {
    private lateinit var body:LinearLayout
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val margin=dp(20)
        body=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(margin,margin,margin,margin)
        }
        setContentView(body);render()
    }
    private fun render(){
        body.removeAllViews()
        body.addView(TextView(this).apply{
            text="KEYPOP  /  PERFORMANCE LAB";textSize=22f
            setPadding(0,0,0,dp(14))
        })
        body.addView(TextView(this).apply{
            text="Only opt-in keyboard view-construction timing samples are collected. Typed words, URLs, clipboard contents and passwords are never recorded."
            textSize=13f
            setPadding(0,0,0,dp(14))
        })
        body.addView(TextView(this).apply{
            text=KeyPopDiagnostics.summary(this@KeyPopDiagnosticsActivity)
            textSize=16f
            setPadding(0,0,0,dp(14))
        })
        body.addView(Button(this).apply{
            text="Share anonymized timing summary"
            setOnClickListener {
                val report=KeyPopDiagnostics.summary(this@KeyPopDiagnosticsActivity)
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type="text/plain";putExtra(Intent.EXTRA_TEXT,report)
                },"Share KeyPop performance"))
            }
        })
        body.addView(Button(this).apply{
            text="Clear all timing measurements"
            setOnClickListener{
                KeyPopDiagnostics.clear(this@KeyPopDiagnosticsActivity)
                render()
            }
        })
        body.addView(TextView(this).apply{
            text="True Gboard/SwiftKey comparisons still require identical devices, scripted typing, p95 input latency, memory/battery/thermal measurements and accessibility testing."
            textSize=13f
            setPadding(0,dp(12),0,0)
        })
    }
}
