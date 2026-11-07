package com.aetherkeys.keyboard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
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
            ContextCompat.startForegroundService(context,
                Intent(context,KeyPopModelDownloadService::class.java)
                    .putExtra(EXTRA_BUNDLE,"selected")
                    .putStringArrayListExtra("directions",ArrayList(selected)))
        }
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
    private fun update(message:String,percent:Int,active:Boolean,failed:Boolean=false) {
        getSharedPreferences(PREFS,MODE_PRIVATE).edit()
            .putBoolean("active",active)
            .putBoolean("finished",!active)
            .putBoolean("failed",failed)
            .putString("message",message)
            .putInt("percent",percent)
            .putLong("updatedAt",System.currentTimeMillis()).apply()
        notifier.notify(NOTIFICATION_ID,notice(message,percent,active,failed))
    }
    private fun done(message:String,failed:Boolean,startId:Int) {
        update(message,if(failed)-1 else 100,false,failed)
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
    /** Sequential selected-model downloads with clear per-pack progress. */
    private fun downloadSelected(startId:Int,selected:Set<String>) {
        val plan=KeyPopPackSelection.required(selected)
        if(plan.isEmpty()) {done("Choose at least one translation direction.",true,startId);return}
        fun progressPosition(index:Int,part:Double=0.0):Int =
            ((100.0*(index+part.coerceIn(0.0,1.0))/plan.size)).toInt().coerceIn(0,99)
        fun step(index:Int) {
            if(index>=plan.size) {
                done("Selected packs downloaded. Confirm translation accuracy before use.",false,startId)
                return
            }
            val key=plan[index]
            val position="${index+1}/${plan.size}"
            update("Installing pack $position: $key",progressPosition(index),true)
            if(key=="en" || key=="ur") {
                KeyPopLanguageModels.downloadText(key,false){ok,message->
                    if(ok)step(index+1)
                    else done("Pack $key failed: $message",true,startId)
                }
                return
            }
            val bundle=KeyPopSindhiOnnxPack.bundles.firstOrNull{it.id==key}
            if(bundle==null) {done("Unknown language pack: $key",true,startId);return}
            val started=KeyPopSindhiOnnxPack.download(this,bundle) { p->
                if(p.complete) {
                    if(p.failed)done("Pack $key failed: "+p.message,true,startId)
                    else step(index+1)
                } else {
                    val part=((p.index-1).coerceAtLeast(0)+
                        (if(p.total!=null && p.total>0L)
                            (p.bytes.toDouble()/p.total).coerceIn(0.0,1.0) else 0.0))/
                        p.count.coerceAtLeast(1)
                    update("Pack $position · "+bundle.name+" · "+
                        p.fileName+" · "+(p.bytes/1048576L)+" MB",
                        progressPosition(index,part),true)
                }
            }
            if(!started)done("Another download is active. Retry selected packs.",true,startId)
        }
        step(0)
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        prepareChannel()
        if(!busy.compareAndSet(false,true))return START_NOT_STICKY
        startForeground(NOTIFICATION_ID,notice("Preparing download…",-1,true))
        val requested=intent?.getStringExtra(EXTRA_BUNDLE)?:"all"
        update("Connecting to language model server…",-1,true)
        runCatching {
            if(requested=="selected")
                downloadSelected(startId,intent?.getStringArrayListExtra("directions")
                    .orEmpty().toSet())
            else if(requested=="all_languages")allLanguages(startId)
            else sindhi(startId,false,if(requested=="all")null else requested)
        }.onFailure{
            done("Unable to start transfer: "+(it.localizedMessage?:"unknown"),true,startId)
        }
        return START_NOT_STICKY
    }
}
