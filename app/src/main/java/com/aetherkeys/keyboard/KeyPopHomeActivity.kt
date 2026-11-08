package com.aetherkeys.keyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Launch-safe keyboard setup. No model downloads, vault access, or heavy theme previews
 * are initialized until the user explicitly opens those features.
 */
class KeyPopHomeActivity : Activity() {
    private val bg = 0xFF101D2B.toInt()
    private val ink = Color.WHITE
    private val muted = 0xFFC1D4E2.toInt()
    private val teal = 0xFF70E7D3.toInt()
    private lateinit var keyboardStatus: TextView
    private fun dp(value:Int) = (value * resources.displayMetrics.density).toInt()
    private fun text(value:String,size:Float=15f,color:Int=ink,bold:Boolean=false):TextView =
        TextView(this).apply {
            text=value; textSize=size;setTextColor(color)
            if(bold)typeface=Typeface.create("sans-serif-medium",Typeface.BOLD)
            setPadding(0,dp(7),0,dp(7))
        }
    private fun addButton(parent:LinearLayout,title:String,onPress:()->Unit) {
        val control=text(title,16f,ink,true).apply {
            gravity=Gravity.CENTER
            minHeight=dp(54)
            background=GradientDrawable().apply {
                setColor(0xFF245268.toInt())
                cornerRadius=dp(12).toFloat()
                setStroke(dp(1),0xFF579FC0.toInt())
            }
            setOnClickListener {
                try { onPress() }
                catch(ex:Exception) {
                    Toast.makeText(this@KeyPopHomeActivity,
                        "Unable to open: "+(ex.localizedMessage?:"Android service unavailable"),
                        Toast.LENGTH_LONG).show()
                }
            }
        }
        parent.addView(control,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(11)})
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=bg
        window.navigationBarColor=bg
        val scroll=ScrollView(this).apply {
            isFillViewport=true
            setBackgroundColor(bg)
        }
        val column=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(20),dp(18),dp(20),dp(26))
        }
        ViewCompat.setOnApplyWindowInsetsListener(column){view,insets->
            val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(dp(20),dp(18)+bars.top,dp(20),dp(26)+bars.bottom)
            insets
        }
        scroll.addView(column)
        setContentView(scroll)
        ViewCompat.requestApplyInsets(scroll)

        column.addView(text("KEYPOP  ·  v1.0.18 RC19",29f,teal,true))
        column.addView(text("Keyboard Setup",24f,ink,true))
        column.addView(text("English ⇄ اردو  •  سنڌي",17f,muted))
        column.addView(text("To type, enable KeyPop in Android settings and choose it as the active keyboard. Installing an APK does not activate it.",14f,muted))
        keyboardStatus=text("Checking keyboard activation…",15f,ink,true)
        column.addView(keyboardStatus)
        addButton(column,"1  Enable KeyPop keyboard") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        addButton(column,"2  Choose KeyPop as active keyboard") {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showInputMethodPicker()
        }
        column.addView(text("Keyboard test field",15f,teal,true))
        column.addView(EditText(this).apply {
            hint="Tap here to test the selected keyboard"
            setHintTextColor(muted)
            setTextColor(ink)
            textSize=17f
            minLines=2
            setPadding(dp(12),dp(10),dp(12),dp(10))
            background=GradientDrawable().apply {
                setColor(0xFF27384E.toInt())
                cornerRadius=dp(10).toFloat()
            }
        },LinearLayout.LayoutParams(-1,-2))
        addButton(column,"Choose language packs · download selected directions") {
            startActivity(Intent(this,KeyPopModelManagerActivity::class.java))
        }
        addButton(column,"📷 Enable automatic screenshots and clipboard history") {
            startActivity(Intent(this,KeyPopScreenshotAccessActivity::class.java))
        }
        column.addView(text("For Gboard-style screenshot offers, grant "+
            "Allow all photos in the permission dialog. KeyPop can then "+
            "save new captures to its encrypted screenshot archive.",13f,muted))
        addButton(column,"Settings · Themes · Layouts · Translation") {
            startActivity(Intent(this,MainActivity::class.java)
                .putExtra("openSection","settingsHome"))
        }
        column.addView(text("English ↔ Urdu works offline with downloaded Google ML Kit packs. Sindhi model files are experimental and must pass independent translation checks.",13f,muted))
        val settings=getSharedPreferences("keypop",MODE_PRIVATE)
        if(!settings.getBoolean("firstSindhiOfferV100",false)) {
            settings.edit().putBoolean("firstSindhiOfferV100",true).apply()
            column.post {
                if(!isFinishing && !isDestroyed) {
                    AlertDialog.Builder(this)
                        .setTitle("Choose offline translation packs")
                        .setMessage("Select only the English, Urdu or Sindhi translation directions you need. Review the list and download with progress tracking.")
                        .setPositiveButton("Choose packs") {_,_->
                            startActivity(Intent(this,KeyPopModelManagerActivity::class.java))
                        }
                        .setNegativeButton("Later",null).show()
                }
            }
        }
    }
    override fun onResume() {
        super.onResume()
        if(!::keyboardStatus.isInitialized)return
        val active=runCatching {
            Settings.Secure.getString(contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty()
        }.getOrDefault("")
        val selected=active.startsWith("$packageName/") &&
            active.contains("AetherImeService")
        keyboardStatus.text=if(selected)
            "✓ KeyPop is your active keyboard"
        else "KeyPop has not yet been selected as the active keyboard"
        keyboardStatus.setTextColor(if(selected)teal else 0xFFFFD39C.toInt())
    }
}
