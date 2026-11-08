package com.aetherkeys.keyboard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Explicit privacy consent for screenshot suggestions and archival.
 * No activity/background service attempts to bypass Android media permissions. */
class KeyPopScreenshotAccessActivity:Activity() {
    private val prefs by lazy { getSharedPreferences("keypop",MODE_PRIVATE) }
    private lateinit var info:TextView
    private lateinit var mainAction:Button
    private lateinit var toggle:Button
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(20),dp(35),dp(20),dp(20))
            setBackgroundColor(0xFF122332.toInt())
        }
        fun label(text:String,size:Float)=TextView(this).apply {
            this.text=text;setTextColor(Color.WHITE);textSize=size
            setPadding(0,dp(8),0,dp(12))
        }
        root.addView(label("KeyPop · Screenshots",23f))
        root.addView(label("Like Gboard, KeyPop can show a new screenshot "+
            "above the keyboard and offer Paste or Share. It can also save "+
            "each detected screenshot to your encrypted clipboard image "+
            "history, without changing the Android clipboard.",15f))
        root.addView(label("Android requires Photos access. On Android 14/15 "+
            "choose Allow all photos: selecting a few pictures will not grant "+
            "access to future screenshots. The gallery is read locally. "+
            "Screenshot monitoring is limited to periods when KeyPop runs; "+
            "new screenshots are checked when the keyboard reopens.",13f))
        info=label("",14f);root.addView(info)
        mainAction=Button(this).apply {
            isAllCaps=false
            text="Enable screenshot suggestions and auto-save"
            setOnClickListener{enable()}
        }
        root.addView(mainAction,LinearLayout.LayoutParams(-1,dp(55)))
        toggle=Button(this).apply {
            isAllCaps=false
            setOnClickListener {
                val current=prefs.getBoolean("autoScreenshotArchive",false)
                prefs.edit().putBoolean("autoScreenshotArchive",!current).apply()
                render()
            }
        }
        root.addView(toggle,LinearLayout.LayoutParams(-1,dp(54)))
        root.addView(Button(this).apply {
            text="Import an image manually"
            isAllCaps=false
            setOnClickListener {
                startActivity(Intent(this@KeyPopScreenshotAccessActivity,
                    KeyPopImageImportActivity::class.java))
            }
        },LinearLayout.LayoutParams(-1,dp(55)))
        setContentView(root)
        render()
    }
    private fun render() {
        val full=KeyPopRecentScreenshots.granted(this)
        val partial=KeyPopRecentScreenshots.partiallyGranted(this)
        val auto=prefs.getBoolean("autoScreenshotArchive",false)
        info.text=when {
            full -> "✓ Full photo access allowed. Automatic screenshot "+
                "history: "+if(auto)"ON" else "OFF"
            partial -> "Selected photos only: future screenshots cannot "+
                "be detected reliably. Please grant access to all photos."
            else -> "Photos permission has not been granted."
        }
        mainAction.text=if(full) "Screenshot access enabled · Open settings"
            else "Enable full photo access for screenshots"
        toggle.isEnabled=full
        toggle.text=if(auto)"Turn off automatic screenshot saving"
            else "Save detected screenshots to history automatically"
    }
    private fun enable() {
        if(KeyPopRecentScreenshots.granted(this)) {
            prefs.edit().putBoolean("autoScreenshotSuggestions",true)
                .putBoolean("screenshotClipOffer",true).apply()
            render()
            return
        }
        val permissions=when {
            Build.VERSION.SDK_INT>=34 ->arrayOf(Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT>=33 ->arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            else ->arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        requestPermissions(permissions,1921)
    }
    override fun onRequestPermissionsResult(requestCode:Int,
        permissions:Array<out String>,grantResults:IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==1921) {
            if(KeyPopRecentScreenshots.granted(this)) {
                // Both behaviors are disclosed on this opt-in screen.
                prefs.edit().putBoolean("autoScreenshotSuggestions",true)
                    .putBoolean("screenshotClipOffer",true)
                    .putBoolean("autoScreenshotArchive",true).apply()
                Toast.makeText(this,"Screenshot suggestions and encrypted "+
                    "history enabled",Toast.LENGTH_LONG).show()
            } else Toast.makeText(this,"Choose Allow all photos to detect "+
                "future screenshots automatically",Toast.LENGTH_LONG).show()
            render()
        }
    }
    override fun onResume(){super.onResume();if(::info.isInitialized)render()}
}
