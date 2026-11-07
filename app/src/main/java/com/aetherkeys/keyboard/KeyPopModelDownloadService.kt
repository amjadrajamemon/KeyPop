package com.aetherkeys.keyboard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean

/** Foreground installer for all 3 Sindhi bundles AND English + Urdu ML Kit. */
class KeyPopModelDownloadService:Service() {
    companion object {
        const val PREFS="keypop_model_transfer"
        private const val CHANNEL="keypop_model_downloads"
        private const val NOTIFICATION_ID=4133
        private const val EXTRA_BUNDLE="bundle"
        private val busy=AtomicBoolean(false)
        fun start(context:Context,bundleId:String?=null) {
            ContextCompat.startForegroundService(context,
                Intent(context,KeyPopModelDownloadService::class.java)
                    .putExtra(EXTRA_BUNDLE,bundleId?:"all"))
        }
        /** Legacy all-packs shortcut remains available for existing callers. */
        fun startAllLanguages(context:Context) =
            startSelected(context,KeyPopPackSelection.directions.keys)
        fun startSelected(context:Context,directions:Collection<String>) {
            val selected=directions.filter{it in KeyPopPackSelection.directions}.distinct()
            require(selected.isNotEmpty()){"Choose at least one translation direction"}
            context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
                .putStringSet("selectedDirections",selected.toSet()).apply()
            ContextCompat.startForegroundService(context,
                Intent(context,KeyPopModelDownloadService::class.java)
                    .putExtra(EXTRA_BUNDLE,"selected")
                    .putStringArrayListExtra("directions",ArrayList(selected)))
        }
    }

    private val main=Handler(Looper.getMainLooper())
    private var waitingForNetwork:ConnectivityManager.NetworkCallback?=null
    private fun stopNetworkWait() {
        val callback=waitingForNetwork?:return
        waitingForNetwork=null
        runCatching {
            (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                .unregisterNetworkCallback(callback)
        }
    }
    private fun hasNetworkConnection():Boolean {
        val cm=getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps=cm.getNetworkCapabilities(cm.activeNetwork)
        return caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)==true
    }
    /** Wait for connectivity without discarding partial transfer files. */
    private fun retryWhenOnline(resume:()->Unit):Boolean {
        if(hasNetworkConnection())return false
        stopNetworkWait()
        val cm=getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback=object:ConnectivityManager.NetworkCallback(){
            override fun onAvailable(network:Network) {
                main.post {
                    if(waitingForNetwork===this) {
                        stopNetworkWait()
                        resume()
                    }
                }
            }
        }
        waitingForNetwork=callback
        return try {
            cm.registerDefaultNetworkCallback(callback)
            true
        }catch(e:Exception) {
            waitingForNetwork=null
            false
        }
    }
    private var transferPlan:List<String> = emptyList()
    private var previousBytes=0L
    private var previousElapsed=0L
    private var rateBytesPerSecond=0.0
    private var lastShownElapsed=0L
    private var lastPercent=0
    private fun currentBytes():Long =
        transferPlan.asSequence().mapNotNull{key->
            KeyPopSindhiOnnxPack.bundles.firstOrNull{it.id==key}
        }.sumOf{KeyPopSindhiOnnxPack.downloadedBytes(this,it)}
    private fun estimatedBytes():Long =
        transferPlan.asSequence().mapNotNull{key->
            KeyPopSindhiOnnxPack.bundles.firstOrNull{it.id==key}
        }.sumOf{KeyPopSindhiOnnxPack.estimatedBytes(it)}
    private fun measuredDetails(loaded:Long,estimate:Long):String {
        val now=SystemClock.elapsedRealtime()
        val seconds=(now-previousElapsed)/1000.0
        if(previousElapsed>0L && seconds>=0.5) {
            val delta=(loaded-previousBytes).coerceAtLeast(0L)
            val instant=delta/seconds
            rateBytesPerSecond=if(delta>0L) {
                if(rateBytesPerSecond<=0.0)instant
                else rateBytesPerSecond*0.65+instant*0.35
            }else if(seconds>4.0)0.0 else rateBytesPerSecond
            previousBytes=loaded
            previousElapsed=now
        }else if(previousElapsed==0L){
            previousBytes=loaded
            previousElapsed=now
        }
        return KeyPopTransferMetrics.summary(loaded,estimate,rateBytesPerSecond,
            transferPlan.any{it=="en"||it=="ur"})
    }
    private fun transient(message:String):Boolean {
        val m=message.lowercase()
        return listOf("insufficient","storage","unauthorized","http 401",
            "http 403","http 404","invalid","unsupported",
            "permission denied","checksum").none{it in m}
    }
    override fun onBind(intent:Intent?):IBinder?=null
    private val notifier by lazy {getSystemService(NotificationManager::class.java)}
    private fun prepareChannel() {
        notifier.createNotificationChannel(NotificationChannel(CHANNEL,
            "KeyPop language pack downloads",NotificationManager.IMPORTANCE_LOW).apply {
            description="English, Urdu and Sindhi offline model progress"
        })
    }
    private fun notice(message:String,percent:Int,active:Boolean,failed:Boolean=false):Notification{
        val open=PendingIntent.getActivity(this,0,
            Intent(this,KeyPopModelManagerActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this,CHANNEL)
            .setSmallIcon(if(active)android.R.drawable.stat_sys_download
                else android.R.drawable.stat_sys_download_done)
            .setContentTitle(when{
                active->"KeyPop · downloading offline languages"
                failed->"KeyPop · download failed · tap to retry"
                else->"KeyPop · language models installed"
            })
            .setContentText(message.take(120))
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(active)
            .setProgress(100,percent.coerceAtLeast(0),active&&percent<0)
            .build()
    }
    private fun update(message:String,percent:Int,active:Boolean,failed:Boolean=false,
                       force:Boolean=false) {
        val now=SystemClock.elapsedRealtime()
        if(active&&!force&&now-lastShownElapsed<450L)return
        lastShownElapsed=now
        val progress=if(percent<0)lastPercent else percent.coerceIn(0,100)
        lastPercent=progress
        val loaded=currentBytes()
        val estimate=estimatedBytes()
        val details=measuredDetails(loaded,estimate)
        getSharedPreferences(PREFS,MODE_PRIVATE).edit()
            .putBoolean("active",active)
            .putBoolean("finished",!active)
            .putBoolean("failed",failed)
            .putString("message",message)
            .putString("details",details)
            .putLong("downloadedBytes",loaded)
            .putLong("estimatedBytes",estimate)
            .putLong("speedBytesBits",java.lang.Double.doubleToRawLongBits(rateBytesPerSecond))
            .putInt("percent",progress)
            .putLong("updatedAt",System.currentTimeMillis()).apply()
        notifier.notify(NOTIFICATION_ID,notice(message+" · "+details,progress,active,failed))
    }
    private fun done(message:String,failed:Boolean,startId:Int) {
        stopNetworkWait()
        update(message,if(failed)lastPercent else 100,false,failed,true)
        getSharedPreferences(PREFS,MODE_PRIVATE).edit()
            .putLong("finishedAt",System.currentTimeMillis()).apply()
        busy.set(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        notifier.notify(NOTIFICATION_ID,notice(message,if(failed)-1 else 100,false,failed))
        stopSelfResult(startId)
    }
    private fun sindhi(startId:Int,fromAll:Boolean,bundleId:String?=null) {
        val bundle=KeyPopSindhiOnnxPack.bundles.firstOrNull{it.id==bundleId}
        val reporter:(KeyPopSindhiOnnxPack.Progress)->Unit={progress->
            val fraction=if(progress.total!=null&&progress.total>0L)
                (progress.bytes.toDouble()/progress.total.toDouble()).coerceIn(0.0,1.0)
                else 0.0
            val position=KeyPopSindhiOnnxPack.bundles.indexOfFirst{it.id==progress.bundleId}
                .coerceAtLeast(0)
            val overall=if(fromAll) {
                (8.0+90.0*((position*10+(progress.index-1).coerceAtLeast(0)+fraction)/30.0))
                    .toInt().coerceIn(8,98)
            } else {
                ((100.0*((position*10+(progress.index-1).coerceAtLeast(0)+fraction)/30.0)))
                    .toInt().coerceIn(0,98)
            }
            val message=progress.message +
                if(progress.fileName.isNotBlank())" · file "+progress.index+"/"+
                    progress.count+" · "+(progress.bytes/1048576L)+" MB" else ""
            if(progress.complete)
                done(if(progress.failed)"Download stopped: "+progress.message
                    else "All requested offline language models installed.",progress.failed,startId)
            else update(message,overall,true)
        }
        val ok=if(bundleId==null)KeyPopSindhiOnnxPack.downloadAll(this,reporter)
            else if(bundle!=null)KeyPopSindhiOnnxPack.download(this,bundle,reporter)
            else false
        if(!ok)done("Unable to begin Sindhi download. Retry after other downloads finish.",
            true,startId)
    }
    private fun allLanguages(startId:Int) {
        update("Downloading English translation model…",0,true)
        KeyPopLanguageModels.downloadText("en",false){englishOk,englishMessage->
            if(!englishOk){
                done("English model failed: "+englishMessage,true,startId)
            }else{
                update("English ready · downloading Urdu model…",4,true)
                KeyPopLanguageModels.downloadText("ur",false){urduOk,urduMessage->
                    if(!urduOk){
                        done("Urdu model failed: "+urduMessage,true,startId)
                    }else{
                        update("English + Urdu ready · installing 3 Sindhi packs…",8,true)
                        sindhi(startId,true)
                    }
                }
            }
        }
    }
    /** Selected, resumable downloads. Failures retry 3 times with backoff.
     * Verified files and .part ranges are retained by KeyPopSindhiOnnxPack. */
    private fun downloadSelected(startId:Int,selected:Set<String>) {
        val plan=KeyPopPackSelection.required(selected)
        if(plan.isEmpty()) {
            done("Choose at least one translation direction.",true,startId);return
        }
        transferPlan=plan
        val transfer=getSharedPreferences(PREFS,MODE_PRIVATE)
        if(!transfer.getBoolean("active",false))
            transfer.edit().putLong("startedAt",System.currentTimeMillis()).apply()
        previousElapsed=0L;rateBytesPerSecond=0.0
        val packCount=plan.size
        fun progressPosition(index:Int,part:Double=0.0):Int =
            (100.0*(index+part.coerceIn(0.0,1.0))/packCount).toInt().coerceIn(0,99)
        fun step(index:Int,retry:Int=0) {
            if(index>=packCount) {
                val unverified=plan.filter { key ->
                    val pack=KeyPopSindhiOnnxPack.bundles.firstOrNull { it.id==key }
                    pack!=null && KeyPopSindhiOnnxPack.missing(this,pack).isNotEmpty()
                }
                if(unverified.isNotEmpty())
                    done("Files still missing for "+unverified.joinToString()+". Tap Download to resume.",
                        true,startId)
                else done("All "+packCount+" selected packs verified on device. Test a translation.",
                    false,startId)
                return
            }
            val key=plan[index]
            val position="${index+1}/$packCount"
            fun fail(message:String) {
                if(retry<3 && transient(message)) {
                    val delay=when(retry){0->2000L;1->5000L;else->12000L}
                    update("Pack $position · $key · network issue, retry ${retry+1}/3 in "+
                        "${delay/1000}s (partial download retained)",
                        progressPosition(index),true,force=true)
                    main.postDelayed({step(index,retry+1)},delay)
                }else if(transient(message) && retryWhenOnline{step(index,0)}) {
                    update("Offline: waiting for network. Pack $position · $key will "+
                        "automatically resume when internet returns.",progressPosition(index),
                        true,force=true)
                }else done("Pack $key failed after ${retry+1} attempts: $message. "+
                    "Partial model files are saved; tap Download to resume.",true,startId)
            }
            update("Pack $position · $key"+(if(retry>0)" · resuming attempt ${retry+1}" else ""),
                progressPosition(index),true,force=true)
            if(key=="en"||key=="ur") {
                KeyPopLanguageModels.downloadText(key,false){ok,message->
                    if(ok)step(index+1)
                    else fail(message)
                }
                return
            }
            val bundle=KeyPopSindhiOnnxPack.bundles.firstOrNull{it.id==key}
            if(bundle==null){done("Unsupported pack: $key",true,startId);return}
            val started=KeyPopSindhiOnnxPack.download(this,bundle){p->
                if(p.complete){
                    if(p.failed)fail(p.message)
                    else {
                        update("Pack $position · ${bundle.name} downloaded",
                            progressPosition(index,1.0),true,force=true)
                        step(index+1)
                    }
                }else {
                    val transferred=KeyPopSindhiOnnxPack.downloadedBytes(this,bundle)
                    val estimated=KeyPopSindhiOnnxPack.estimatedBytes(bundle)
                    val fraction=if(estimated>0L)
                        (transferred.toDouble()/estimated).coerceIn(0.0,0.99)
                        else 0.0
                    update("Pack $position · ${bundle.name} · "+
                        "file ${p.index}/${p.count} · ${p.fileName}",
                        progressPosition(index,fraction),true)
                }
            }
            if(!started)fail("Another model transfer is active")
        }
        update("Preparing $packCount selected offline packs…",0,true,force=true)
        step(0)
    }
    override fun onDestroy() {
        stopNetworkWait()
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        prepareChannel()
        val state=getSharedPreferences(PREFS,MODE_PRIVATE)
        if(intent==null&&!state.getBoolean("active",false)){
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if(!busy.compareAndSet(false,true))return START_STICKY
        startForeground(NOTIFICATION_ID,notice("Preparing download…",-1,true))
        val requested=intent?.getStringExtra(EXTRA_BUNDLE)?:"selected"
        val selected=intent?.getStringArrayListExtra("directions")?.toSet()
            ?:state.getStringSet("selectedDirections",emptySet()).orEmpty()
        runCatching {
            if(requested=="selected")downloadSelected(startId,selected)
            else if(requested=="all_languages")allLanguages(startId)
            else sindhi(startId,false,if(requested=="all")null else requested)
        }.onFailure{
            done("Unable to start transfer: "+(it.localizedMessage?:"unknown"),true,startId)
        }
        return START_STICKY
    }
}
