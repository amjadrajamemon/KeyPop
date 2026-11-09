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
    private val keyPress=ArrayList<Long>()
    private val prediction=ArrayList<Long>()
    private val editorRead=ArrayList<Long>()
    private var cacheHits=0L
    private var cacheMisses=0L
    private fun enabled(context:Context):Boolean =
        context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
            .getBoolean("performanceMetrics",false)
    private fun record(list:MutableList<Long>,elapsedNanos:Long) {
        if(elapsedNanos<=0L || elapsedNanos>10_000_000_000L)return
        list.add(elapsedNanos/1_000_000L)
        if(list.size>200)list.removeAt(0)
    }
    @Synchronized fun observeKeyPress(context:Context,elapsedNanos:Long) {
        if(enabled(context))record(keyPress,elapsedNanos)
    }
    @Synchronized fun observePredictionCompute(context:Context,elapsedNanos:Long) {
        if(enabled(context))record(prediction,elapsedNanos)
    }
    @Synchronized fun observeEditorRead(context:Context,elapsedNanos:Long) {
        if(enabled(context))record(editorRead,elapsedNanos)
    }
    @Synchronized fun observeContextCache(context:Context,hits:Int,misses:Int) {
        if(enabled(context)) {
            cacheHits+=hits.coerceAtLeast(0)
            cacheMisses+=misses.coerceAtLeast(0)
        }
    }
    fun percentile(samples:List<Long>,percent:Int):Long {
        if(samples.isEmpty())return 0L
        val ordered=samples.sorted()
        val rank=(kotlin.math.ceil(ordered.size*percent.coerceIn(0,100)/100.0).toInt()-1)
            .coerceIn(0,ordered.lastIndex)
        return ordered[rank]
    }
    @Synchronized fun observeKeyboardBuild(context:Context,elapsedNanos:Long) {
        if(!enabled(context))return
        record(recent,elapsedNanos)
    }
    @Synchronized fun flush(context:Context) {
        if(recent.isEmpty() && keyPress.isEmpty() && prediction.isEmpty() &&
            editorRead.isEmpty() && cacheHits==0L && cacheMisses==0L)return
        val prefs=context.getSharedPreferences("keypop_performance",Context.MODE_PRIVATE)
        val old=prefs.getString("viewBuildMs","").orEmpty().split(",")
            .mapNotNull{it.toLongOrNull()}
        val all=(old+recent).takeLast(200)
        val oldKeys=prefs.getString("keyPressMs","").orEmpty().split(",")
            .mapNotNull{it.toLongOrNull()}
        val oldPred=prefs.getString("predictionMs","").orEmpty().split(",")
            .mapNotNull{it.toLongOrNull()}
        val oldReads=prefs.getString("editorReadMs","").orEmpty().split(",")
            .mapNotNull{it.toLongOrNull()}
        val allHits=prefs.getLong("editorCacheHits",0L)+cacheHits
        val allMisses=prefs.getLong("editorCacheMisses",0L)+cacheMisses
        prefs.edit().putLong("editorCacheHits",allHits)
            .putLong("editorCacheMisses",allMisses)
            .putString("editorReadMs",(oldReads+editorRead).takeLast(200).joinToString(","))
            .putString("viewBuildMs",all.joinToString(","))
            .putString("keyPressMs",(oldKeys+keyPress).takeLast(200).joinToString(","))
            .putString("predictionMs",(oldPred+prediction).takeLast(200).joinToString(","))
            .apply()
        recent.clear()
        keyPress.clear()
        prediction.clear()
        editorRead.clear()
        cacheHits=0L
        cacheMisses=0L
    }
    @Synchronized fun measurements(context:Context):List<Long> {
        val prefs=context.getSharedPreferences("keypop_performance",Context.MODE_PRIVATE)
        val saved=prefs.getString("viewBuildMs","").orEmpty().split(",")
            .mapNotNull{it.toLongOrNull()}
        return saved+recent
    }
    @Synchronized fun clear(context:Context) {
        recent.clear()
        keyPress.clear()
        prediction.clear()
        editorRead.clear()
        cacheHits=0L
        cacheMisses=0L
        context.getSharedPreferences("keypop_performance",Context.MODE_PRIVATE).edit().clear().apply()
    }
    fun summary(context:Context):String {
        val view=measurements(context)
        val prefs=context.getSharedPreferences("keypop_performance",Context.MODE_PRIVATE)
        fun series(name:String,live:List<Long>):List<Long> =
            prefs.getString(name,"").orEmpty().split(",")
                .mapNotNull{it.toLongOrNull()}+live
        val keys=series("keyPressMs",synchronized(this){keyPress.toList()})
        val predictions=series("predictionMs",synchronized(this){prediction.toList()})
        val reads=series("editorReadMs",synchronized(this){editorRead.toList()})
        val hits=prefs.getLong("editorCacheHits",0L)+cacheHits
        val misses=prefs.getLong("editorCacheMisses",0L)+cacheMisses
        val reuse=if(hits+misses>0)
            "%.1f".format(Locale.ROOT,100.0*hits/(hits+misses))+"%"
            else "no samples"
        if(view.isEmpty() && keys.isEmpty() && predictions.isEmpty())
            return "No local timing samples yet. Enable private performance metrics."
        fun line(label:String,values:List<Long>):String =
            if(values.isEmpty())"$label: no samples yet"
            else "$label: n="+values.size+" · p50="+percentile(values,50)+
                " ms · p95="+percentile(values,95)+" ms · max="+values.maxOrNull()+" ms"
        return "KeyPop private timing (no typed content recorded):\n"+
            line("Keypress handler",keys)+"\n"+
            line("Prediction compute",predictions)+"\n"+
            line("Remote editor snapshot",reads)+"\n"+
            "Editor-context reuse: "+reuse+" ("+hits+" hits, "+misses+" misses)\n"+
            line("Keyboard view build",view)+"\n"+
            "Android "+android.os.Build.VERSION.RELEASE+
            "\nThese measurements do not include total remote-editor IPC "+
            "or provide a direct Gboard comparison."
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
            text="Opt-in timings capture keypress handlers, prediction computation and keyboard view builds. No typed words, URLs, clipboard contents or passwords are recorded."
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
