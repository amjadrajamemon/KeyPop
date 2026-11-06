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

/**
 * User-initiated foreground download with notification + persistent progress.
 * Android may terminate the process; retained *.part files resume on retry.
 */
class KeyPopModelDownloadService:Service() {
    companion object {
        const val PREFS="keypop_model_transfer"
        private const val CHANNEL="keypop_model_downloads"
        private const val NOTIFICATION_ID=4133
        private const val EXTRA_BUNDLE="bundle"
        fun start(context:Context,bundleId:String?=null) {
            ContextCompat.startForegroundService(context,
                Intent(context,KeyPopModelDownloadService::class.java)
                    .putExtra(EXTRA_BUNDLE,bundleId?:"all"))
        }
    }

    override fun onBind(intent:Intent?):IBinder?=null
    private val notifier by lazy {getSystemService(NotificationManager::class.java)}

    private fun prepareChannel() {
        notifier.createNotificationChannel(NotificationChannel(CHANNEL,
            "KeyPop language pack downloads",NotificationManager.IMPORTANCE_LOW).apply{
            description="Offline Sindhi translation model transfer progress"
        })
    }

    private fun notice(message:String,percent:Int,active:Boolean,failed:Boolean=false):Notification {
        val open=PendingIntent.getActivity(this,0,
            Intent(this,KeyPopModelManagerActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this,CHANNEL)
            .setSmallIcon(if(active)android.R.drawable.stat_sys_download
                else android.R.drawable.stat_sys_download_done)
            .setContentTitle(when{
                active->"KeyPop · downloading offline language packs"
                failed->"KeyPop · download failed, tap to retry"
                else->"KeyPop · language packs installed"
            })
            .setContentText(message.take(125))
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(active)
            .setProgress(100,percent.coerceAtLeast(0),active&&percent<0)
            .build()
    }

    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        prepareChannel()
        startForeground(NOTIFICATION_ID,notice("Preparing model transfer…",-1,true))
        // Duplicate taps should not reset a running transfer or destroy its service.
        if(KeyPopSindhiOnnxPack.isDownloading())return START_NOT_STICKY
        val id=intent?.getStringExtra(EXTRA_BUNDLE)?:"all"
        val bundle=KeyPopSindhiOnnxPack.bundles.firstOrNull{it.id==id}
        getSharedPreferences(PREFS,MODE_PRIVATE).edit()
            .putBoolean("active",true).putBoolean("finished",false)
            .putBoolean("failed",false)
            .putString("message","Connecting to model server for "+
                (bundle?.name?:"all three Sindhi model packs")+"…")
            .putInt("percent",-1)
            .putLong("updatedAt",System.currentTimeMillis()).apply()

        val callback:(KeyPopSindhiOnnxPack.Progress)->Unit={progress->
            val percent=if(progress.total!=null && progress.total>0L)
                ((100L*progress.bytes.coerceAtLeast(0L))/progress.total)
                    .coerceIn(0L,100L).toInt() else -1
            val bytes=if(progress.bytes>0L)
                " · "+(progress.bytes/1048576L)+" MB"+
                (if(progress.total!=null && progress.total>0L)
                    " / "+(progress.total/1048576L)+" MB" else "")
                else ""
            val message=progress.message+
                (if(progress.fileName.isNotBlank())
                    " · file "+progress.index+"/"+progress.count else "")+
                bytes+(if(percent>=0)" · "+percent+"%" else "")
            getSharedPreferences(PREFS,MODE_PRIVATE).edit()
                .putBoolean("active",!progress.complete)
                .putBoolean("finished",progress.complete)
                .putBoolean("failed",progress.failed)
                .putString("message",message)
                .putInt("percent",if(progress.complete&&!progress.failed)100 else percent)
                .putLong("updatedAt",System.currentTimeMillis()).apply()
            notifier.notify(NOTIFICATION_ID,notice(message,percent,
                !progress.complete,progress.failed))
            if(progress.complete) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                notifier.notify(NOTIFICATION_ID,notice(message,100,false,progress.failed))
                stopSelfResult(startId)
            }
        }
        val started=if(id=="all")KeyPopSindhiOnnxPack.downloadAll(this,callback)
            else if(bundle!=null)KeyPopSindhiOnnxPack.download(this,bundle,callback)
            else false
        if(!started && !KeyPopSindhiOnnxPack.isDownloading()){
            callback(KeyPopSindhiOnnxPack.Progress(id,"",0,0,0,null,
                "Invalid model pack request.",true,true))
        }
        return START_NOT_STICKY
    }
}
