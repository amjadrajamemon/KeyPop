package com.aetherkeys.keyboard

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/**
 * KeyPop screenshot suggestion source. A just-captured image is shown as an
 * inline thumbnail only after the user grants full media-image permission.
 * On Android 14+, partial-photo selection is NOT full gallery access.
 * No screenshot data is uploaded or archived without a separate action.
 */
object KeyPopRecentScreenshots {
    const val MAX_AGE_SECONDS=5*60L

    fun isScreenshot(name:String,path:String):Boolean {
        val n=name.lowercase()
        val p=path.lowercase()
        return n.contains("screenshot") || p.contains("screenshots") ||
            n.startsWith("screen_shot") || n.startsWith("screen-shot") ||
            n.startsWith("screencapture") || p.contains("screen_capture")
    }

    fun granted(context:Context):Boolean {
        val permission=if(Build.VERSION.SDK_INT>=33)
            Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE
        return context.checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED
    }

    fun latest(context:Context):Uri? {
        // The full gallery permission is an explicit user opt-in. We never
        // bypass it with overlays, accessibility, or media-projection capture.
        if(!context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
                .getBoolean("autoScreenshotSuggestions",true) || !granted(context))
            return null
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
        cursor.use {
            val idIndex=it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameIndex=it.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
            val pathIndex=it.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
            var inspected=0
            while(it.moveToNext() && inspected++<60) {
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
