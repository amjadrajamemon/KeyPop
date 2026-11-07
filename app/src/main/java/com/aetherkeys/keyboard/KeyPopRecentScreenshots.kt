package com.aetherkeys.keyboard

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/**
 * Opt-in shortcut to the latest recently captured screenshot. No photo bytes
 * are copied or uploaded. Query only when the keyboard opens, off the UI thread.
 * The phone can also offer screenshots through its regular image clipboard,
 * without granting gallery access.
 */
object KeyPopRecentScreenshots {
    const val MAX_AGE_SECONDS=30*60L
    fun isScreenshot(name:String,path:String):Boolean {
        val n=name.lowercase()
        val p=path.lowercase()
        return n.contains("screenshot") || p.contains("screenshots") ||
            n.startsWith("screen_shot") || n.startsWith("screen-shot")
    }
    fun granted(context:Context):Boolean {
        val permission=if(Build.VERSION.SDK_INT>=33)
            Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE
        return context.checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED
    }
    fun latest(context:Context):Uri? {
        if(!context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
                .getBoolean("autoScreenshotSuggestions",false) || !granted(context))return null
        val projection=if(Build.VERSION.SDK_INT>=29)
            arrayOf(MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.RELATIVE_PATH)
        else arrayOf(MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,MediaStore.Images.Media.DATE_ADDED)
        val since=System.currentTimeMillis()/1000L-MAX_AGE_SECONDS
        val cursor=context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,MediaStore.Images.Media.DATE_ADDED+" >= ?",
            arrayOf(since.toString()),MediaStore.Images.Media.DATE_ADDED+" DESC")
            ?:return null
        cursor.use{
            val idIndex=it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameIndex=it.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
            val pathIndex=it.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
            var inspected=0
            while(it.moveToNext() && inspected++<24) {
                val name=if(nameIndex>=0)it.getString(nameIndex).orEmpty() else ""
                val path=if(pathIndex>=0)it.getString(pathIndex).orEmpty() else ""
                if(isScreenshot(name,path))
                    return ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,it.getLong(idIndex))
            }
        }
        return null
    }
}
