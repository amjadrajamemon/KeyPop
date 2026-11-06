package com.aetherkeys.keyboard

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

/** User-initiated external handoff. The on-device KeyPop engine never calls this. */
object KeyPopGoogleTranslateHandoff {
    const val PACKAGE="com.google.android.apps.translate"
    val languages=listOf("en","ur","sd")
    fun appIntent(text:String)=Intent(Intent.ACTION_SEND).apply {
        type="text/plain"
        putExtra(Intent.EXTRA_TEXT,text)
        setPackage(PACKAGE)
    }
    fun webUri(text:String,from:String,to:String):Uri=
        Uri.Builder().scheme("https").authority("translate.google.com").path("/")
            .appendQueryParameter("sl",if(from in languages)from else "auto")
            .appendQueryParameter("tl",if(to in languages)to else "ur")
            .appendQueryParameter("text",text).appendQueryParameter("op","translate").build()
}

/** A dedicated Activity guarantees proper window ownership when started from an IME. */
class KeyPopGoogleTranslateActivity:Activity() {
    private val bg=0xFF101D2B.toInt()
    private val fg=Color.WHITE
    private val muted=0xFFB8CADC.toInt()
    private lateinit var input:EditText
    private lateinit var source:Spinner
    private lateinit var target:Spinner
    private lateinit var status:TextView

    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    private fun text(t:String,sz:Float=15f,color:Int=fg)=TextView(this).apply {
        text=t;textSize=sz;setTextColor(color);setPadding(0,dp(5),0,dp(5))
    }
    private fun button(root:LinearLayout,name:String,action:()->Unit) {
        val control=text(name,15f).apply{
            gravity=Gravity.CENTER;minHeight=dp(47)
            background=GradientDrawable().apply {
                setColor(0xFF245268.toInt());cornerRadius=dp(11).toFloat()
                setStroke(dp(1),0xFF5CACBF.toInt())
            }
            setOnClickListener{action()}
        }
        root.addView(control,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(8)})
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=bg
        window.navigationBarColor=bg
        val outer=ScrollView(this).apply{setBackgroundColor(bg);isFillViewport=true}
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(18),dp(18),dp(18),dp(24))
        }
        outer.addView(root);setContentView(outer)
        root.addView(text("KEYPOP / GOOGLE TRANSLATE",13f,0xFF8AE7D7.toInt()))
        root.addView(text("Google Translate · optional",23f))
        root.addView(text("KeyPop's own offline translation remains the default. Opening this screen does not send your text anywhere. Choose an action below to share it with Google.",13f,muted))
        input=EditText(this).apply{
            hint="Type or review the text to translate"
            setText(intent.getStringExtra("text").orEmpty())
            setHintTextColor(muted);setTextColor(fg);textSize=16f
            minLines=3;maxLines=7
            setPadding(dp(12),dp(9),dp(12),dp(9))
            background=GradientDrawable().apply{
                setColor(0xFF26394A.toInt());cornerRadius=dp(10).toFloat()
            }
        }
        root.addView(input,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(9)})
        source=Spinner(this).apply {
            adapter=ArrayAdapter(this@KeyPopGoogleTranslateActivity,
                android.R.layout.simple_spinner_dropdown_item,listOf("Auto","English","Urdu","Sindhi"))
            val selected=intent.getStringExtra("source")?:"auto"
            setSelection((KeyPopGoogleTranslateHandoff.languages.indexOf(selected)+1).coerceIn(0,3))
        }
        target=Spinner(this).apply {
            adapter=ArrayAdapter(this@KeyPopGoogleTranslateActivity,
                android.R.layout.simple_spinner_dropdown_item,listOf("English","Urdu","Sindhi"))
            setSelection(KeyPopGoogleTranslateHandoff.languages.indexOf(
                intent.getStringExtra("target")).coerceAtLeast(0))
        }
        root.addView(text("From (website)",13f,muted));root.addView(source)
        root.addView(text("To (website)",13f,muted));root.addView(target)
        root.addView(text("Google's app selects its own languages. For preset source/target languages use the website option. Online translation can send text to Google. KeyPop cannot automatically read Google's result.",12f,muted))
        status=text("",13f,0xFF8AE7D7.toInt());root.addView(status)
        button(root,"Open Google Translate app with this text") {
            val content=input.text.toString()
            if(content.isBlank()){status.text="Type some text first.";return@button}
            try{
                startActivity(KeyPopGoogleTranslateHandoff.appIntent(content))
                finish()
            }catch(error:ActivityNotFoundException){
                val app=packageManager.getLaunchIntentForPackage(KeyPopGoogleTranslateHandoff.PACKAGE)
                if(app==null)status.text="Google Translate app not installed. Use the website instead."
                else {
                    copy(content);startActivity(app)
                    Toast.makeText(this,"Text copied. Paste it into Google Translate.",Toast.LENGTH_LONG).show()
                    finish()
                }
            }catch(error:SecurityException){
                status.text="Unable to hand off to Google Translate."
            }
        }
        button(root,"Open Google Translate website (online)") {
            val content=input.text.toString()
            if(content.isBlank()){status.text="Type some text first.";return@button}
            if(content.length>1800){
                status.text="Text too long for a web URL. Use the Google Translate app instead."
                return@button
            }
            val from=if(source.selectedItemPosition==0)"auto" else
                KeyPopGoogleTranslateHandoff.languages[source.selectedItemPosition-1]
            val to=KeyPopGoogleTranslateHandoff.languages[target.selectedItemPosition]
            try{
                startActivity(Intent(Intent.ACTION_VIEW,
                    KeyPopGoogleTranslateHandoff.webUri(content,from,to)))
            }catch(error:ActivityNotFoundException){
                status.text="No browser is available to open Google Translate."
            }
        }
        button(root,"Copy text only") {
            val content=input.text.toString()
            if(content.isBlank()){status.text="Nothing to copy.";return@button}
            copy(content);status.text="Copied without opening Google Translate."
        }
        button(root,"Back to KeyPop"){finish()}
        root.addView(text("Privacy: External translation may be processed online. Website links may expose submitted text in browser history. Do not share passwords or confidential text.",12f,muted))
    }
    private fun copy(value:String) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("KeyPop external translation source",value))
    }
}
