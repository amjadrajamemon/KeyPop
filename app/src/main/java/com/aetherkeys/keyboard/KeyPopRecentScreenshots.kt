package com.aetherkeys.keyboard

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/** Screenshots stored by Android are independent of primary clipboard events.
 * All MediaStore reads require the user's full Photos permission. */
object KeyPopRecentScreenshots {
    const val MAX_AGE_SECONDS=10*60L
    fun isScreenshot(name:String,path:String):Boolean {
        val n=name.lowercase(java.util.Locale.ROOT)
        val p=path.lowercase(java.util.Locale.ROOT)
        return n.contains("screenshot") || p.contains("screenshots") ||
            n.startsWith("screen_shot") || n.startsWith("screen-shot") ||
            n.startsWith("screencapture") || n.startsWith("screen_capture") ||
            p.contains("screen_capture") || p.contains("screenrecord/screenshot")
    }
    fun granted(context:Context):Boolean {
        val permission=if(Build.VERSION.SDK_INT>=33)
            Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE
        return context.checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED
    }
    fun partiallyGranted(context:Context):Boolean =
        Build.VERSION.SDK_INT>=34 &&
            context.checkSelfPermission(
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )==PackageManager.PERMISSION_GRANTED && !granted(context)

    fun recent(context:Context,ageSeconds:Long=MAX_AGE_SECONDS,
               limit:Int=80):List<Uri> {
        if(!granted(context))return emptyList()
        val projection=if(Build.VERSION.SDK_INT>=29)
            arrayOf(MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.RELATIVE_PATH,
                MediaStore.Images.Media.IS_PENDING)
        else arrayOf(MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED)
        val minTime=System.currentTimeMillis()/1000L-ageSeconds.coerceAtLeast(1L)
        val found=ArrayList<Uri>()
        val cursor=context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,projection,
            MediaStore.Images.Media.DATE_ADDED+" >= ?",
            arrayOf(minTime.toString()),
            MediaStore.Images.Media.DATE_ADDED+" DESC")
            ?:return found
        cursor.use {
            val id=it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val name=it.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
            val path=it.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
            val pending=it.getColumnIndex(MediaStore.Images.Media.IS_PENDING)
            var checked=0
            while(it.moveToNext() && checked++<250 &&
                found.size<limit.coerceIn(1,200)) {
                if(pending>=0 && it.getInt(pending)!=0)continue
                val file=if(name>=0)it.getString(name).orEmpty() else ""
                val folder=if(path>=0)it.getString(path).orEmpty() else ""
                if(isScreenshot(file,folder)) {
                    found.add(ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,it.getLong(id)))
                }
            }
        }
        return found
    }
    fun latest(context:Context):Uri? {
        if(!context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
                .getBoolean("autoScreenshotSuggestions",true))return null
        return recent(context,MAX_AGE_SECONDS,1).firstOrNull()
    }
}
