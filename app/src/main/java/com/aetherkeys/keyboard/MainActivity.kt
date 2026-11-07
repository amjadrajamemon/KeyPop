package com.aetherkeys.keyboard

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * Fast, self-contained settings. Sections are created only on navigation;
 * previous eager construction of all the theme previews could leave the
 * page blank/unresponsive on real devices despite an emulator smoke pass.
 */
class MainActivity:Activity() {
    private val prefs by lazy {getSharedPreferences("keypop",Context.MODE_PRIVATE)}
    private val base=0xFF0F1D29.toInt()
    private val card=0xFF172D3C.toInt()
    private val edge=0xFF35586B.toInt()
    private val accent=0xFF79E9D6.toInt()
    private val ink=Color.WHITE
    private val muted=0xFFB8CCD9.toInt()
    private lateinit var content:LinearLayout
    private lateinit var scroller:ScrollView
    private var section="settingsHome"
    private var downloadMessage:TextView?=null
    private var downloadProgress:ProgressBar?=null
    private val ticker=Handler(Looper.getMainLooper())
    private val refresh=object:Runnable {
        override fun run(){
            if(section=="languagePacks")showTransfer()
            ticker.postDelayed(this,850)
        }
    }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun text(s:String,size:Float=14f,color:Int=ink,bold:Boolean=false)=TextView(this).apply{
        text=s;textSize=size;setTextColor(color)
        if(bold)typeface=Typeface.create("sans-serif-medium",Typeface.BOLD)
        setPadding(0,dp(3),0,dp(3))
    }
    private fun shape(color:Int,r:Int=14,border:Int?=null)=GradientDrawable().apply{
        setColor(color);cornerRadius=dp(r).toFloat()
        if(border!=null)setStroke(dp(1),border)
    }
    private fun block():LinearLayout=LinearLayout(this).apply{
        orientation=LinearLayout.VERTICAL
        setPadding(dp(13),dp(12),dp(13),dp(12))
        background=shape(card,16,edge)
    }
    private fun addBlock(build:LinearLayout.()->Unit) {
        val box=block()
        box.build()
        content.addView(box,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(12)})
    }
    private fun action(parent:LinearLayout,label:String,description:String?=null,run:()->Unit){
        val button=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            gravity=Gravity.CENTER_VERTICAL
            minimumHeight=dp(49)
            setPadding(dp(12),dp(9),dp(12),dp(9))
            background=shape(0xFF244B60.toInt(),12,0xFF4D8494.toInt())
            isClickable=true;isFocusable=true
            setOnClickListener{run()}
        }
        button.addView(text(label,15f,ink,true))
        if(description!=null)button.addView(text(description,12f,muted))
        parent.addView(button,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(8)})
    }
    private fun heading(title:String,subtitle:String?=null) {
        content.addView(text(title,22f,ink,true))
        if(subtitle!=null)content.addView(text(subtitle,13f,muted))
        content.addView(View(this),LinearLayout.LayoutParams(1,dp(12)))
    }
    private fun option(parent:LinearLayout,title:String,key:String,default:Boolean=false){
        val row=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;minimumHeight=dp(48)}
        val label=text(title,14f,ink)
        row.addView(label,LinearLayout.LayoutParams(0,-2,1f))
        val toggle=Switch(this).apply{
            isChecked=prefs.getBoolean(key,default)
            setOnCheckedChangeListener{_,value->prefs.edit().putBoolean(key,value).apply()}
        }
        row.addView(toggle)
        parent.addView(row)
    }
    private fun range(parent:LinearLayout,title:String,key:String,min:Int,max:Int,value:Int){
        val label=text("",14f,ink)
        val seek=SeekBar(this).apply{
            this.max=max-min
            progress=(prefs.getInt(key,value)-min).coerceIn(0,max-min)
        }
        label.text=title+": "+(seek.progress+min)
        seek.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(bar:SeekBar?,progress:Int,byUser:Boolean) {
                label.text=title+": "+(progress+min)
                if(byUser)prefs.edit().putInt(key,progress+min).apply()
            }
            override fun onStartTrackingTouch(bar:SeekBar?){}
            override fun onStopTrackingTouch(bar:SeekBar?){}
        })
        parent.addView(label);parent.addView(seek)
    }
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        window.statusBarColor=base
        window.navigationBarColor=base
        scroller=ScrollView(this).apply{
            setBackgroundColor(base)
            isFillViewport=true
        }
        content=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(17),dp(24),dp(17),dp(26))
        }
        scroller.addView(content)
        // Attach the window before building any optional settings subsection.
        setContentView(scroller)
        section=intent.getStringExtra("openSection")?:"settingsHome"
        if(section=="settings")section="typing"
        if(section=="languagePacks")section="languagePacks"
        if(section !in setOf("settingsHome","typing","languagePacks","themes",
                "layouts","translation","security","tools","onboarding"))
            section="settingsHome"
        navigate(section)
    }
    override fun onResume(){
        super.onResume()
        ticker.removeCallbacks(refresh)
        ticker.post(refresh)
    }
    override fun onPause(){
        ticker.removeCallbacks(refresh)
        super.onPause()
    }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed(){
        if(section!="settingsHome")navigate("settingsHome")
        else super.onBackPressed()
    }
    private fun navigate(destination:String){
        section=destination
        downloadMessage=null;downloadProgress=null
        content.removeAllViews()
        content.addView(text("KEYPOP  /  SETTINGS",13f,accent,true))
        if(section!="settingsHome") {
            action(content,"‹  All Settings"){navigate("settingsHome")}
        }
        try {
            when(section){
                "settingsHome"->dashboard()
                "onboarding"->setup()
                "languagePacks"->offlinePacks()
                "themes"->themes()
                "layouts"->layouts()
                "typing"->typing()
                "translation"->translation()
                "security"->privacy()
                "tools"->tools()
                else->dashboard()
            }
        }catch(e:Exception){
            // Fail visibly in the current window, not as an all-green blank page.
            heading("Unable to display this section")
            content.addView(text(e.localizedMessage?:"Unexpected settings error",14f,muted))
            action(content,"Go to Settings"){navigate("settingsHome")}
        }
        scroller.post{scroller.scrollTo(0,0)}
    }
    private fun dashboard(){
        heading("KeyPop Settings","Your keyboard, your settings")
        addBlock{
            addView(text("YOUR KEYBOARD",12f,accent,true))
            action(this,"Keyboard Setup","Enable, choose and test KeyPop"){navigate("onboarding")}
            action(this,"Offline Packs","Download every English, Urdu and Sindhi pack in one tap"){navigate("languagePacks")}
            action(this,"Themes","15 keyboard styles"){navigate("themes")}
            action(this,"Layouts","Keyboard presets and arrangement"){navigate("layouts")}
        }
        addBlock{
            addView(text("PREFERENCES",12f,accent,true))
            action(this,"Typing & Size","Key dimensions, feedback, suggestions"){navigate("typing")}
            action(this,"Translation","Easy Translate, Google Cloud, default language"){navigate("translation")}
            action(this,"Clipboard & Security","Private data and saved logins"){navigate("security")}
            action(this,"Tools","Personal dictionary, layout lab and more"){navigate("tools")}
        }
    }
    private fun setup(){
        heading("Keyboard Setup","Activate your keyboard in Android settings")
        addBlock{
            action(this,"Enable KeyPop keyboard"){
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
            action(this,"Choose KeyPop as active keyboard"){
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showInputMethodPicker()
            }
            addView(text("Test typing below",13f,accent,true))
            val input=EditText(this@MainActivity).apply{
                hint="Type with KeyPop";setHintTextColor(muted);setTextColor(ink)
                minLines=2;setPadding(dp(12),dp(8),dp(12),dp(8))
                background=shape(0xFF244052.toInt(),10)
            }
            addView(input)
        }
    }
    private fun startDownload(){
        if(Build.VERSION.SDK_INT>=33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),719)
        runCatching{KeyPopModelDownloadService.startAllLanguages(this)}
            .onSuccess{
                downloadMessage?.text="Starting all language packs…"
                downloadProgress?.visibility=View.VISIBLE
            }.onFailure{
                downloadMessage?.text="Unable to start: "+(it.localizedMessage?:"unknown error")
            }
    }
    private fun showTransfer(){
        val message=downloadMessage?:return
        val saved=getSharedPreferences(KeyPopModelDownloadService.PREFS,MODE_PRIVATE)
        val busy=saved.getBoolean("active",false)
        val finished=saved.getBoolean("finished",false)
        val percent=saved.getInt("percent",-1)
        message.text=saved.getString("message","No download started.").orEmpty()
        downloadProgress?.visibility=if(busy||finished)View.VISIBLE else View.GONE
        downloadProgress?.isIndeterminate=busy&&percent<0
        if(percent>=0)downloadProgress?.progress=percent.coerceIn(0,100)
    }
    private fun offlinePacks(){
        heading("Offline Language Packs","One download for English, Urdu and Sindhi")
        addBlock{
            addView(text("One-tap installer",18f,ink,true))
            addView(text("Includes English ↔ Urdu ML Kit and all three Sindhi neural bundles (~978 MB plus English/Urdu). The download starts when you press the button; Wi-Fi and at least 1.2 GB free storage are recommended.",13f,muted))
            action(this,"Download All Languages"){
                startDownload()
            }
            downloadMessage=text("No download started.",13f,accent)
            addView(downloadMessage)
            downloadProgress=ProgressBar(this@MainActivity,null,android.R.attr.progressBarStyleHorizontal).apply{
                max=100;visibility=View.GONE
            }
            addView(downloadProgress)
            action(this,"Check download details"){
                startActivity(Intent(this@MainActivity,KeyPopModelManagerActivity::class.java))
            }
        }
        showTransfer()
    }
    private fun themes(){
        heading("Themes","Select a style to apply to your keyboard")
        addBlock {
            KeyPopSkins.all.forEachIndexed{index,skin->
                val item=LinearLayout(this@MainActivity).apply{
                    orientation=LinearLayout.HORIZONTAL
                    gravity=Gravity.CENTER_VERTICAL
                    setPadding(dp(8),dp(6),dp(8),dp(6))
                }
                val swatch=View(this@MainActivity).apply{
                    background=shape(skin.key,11,skin.accent)
                }
                item.addView(swatch,LinearLayout.LayoutParams(dp(48),dp(43)))
                val label=LinearLayout(this@MainActivity).apply{
                    orientation=LinearLayout.VERTICAL;setPadding(dp(12),0,0,0)
                    addView(text(skin.name,14f,ink,true))
                    addView(text(skin.subtitle,12f,muted))
                }
                item.addView(label,LinearLayout.LayoutParams(0,-2,1f))
                item.setOnClickListener {
                    prefs.edit().putInt("theme",index)
                        .putInt("keyboardPreset",KeyboardPresetCatalog.CUSTOM)
                        .remove("customAccent").apply()
                    Toast.makeText(this@MainActivity,"Applied: "+skin.name,Toast.LENGTH_SHORT).show()
                    navigate("themes")
                }
                item.isClickable=true
                addView(item)
            }
        }
    }
    private fun layouts(){
        heading("Layouts","Apply the keyboard arrangement you prefer")
        addBlock{
            KeyboardPresetCatalog.all.forEach{preset->
                action(this,preset.name,preset.description){
                    val applied=KeyboardPresetCatalog.apply(this@MainActivity,preset.id)
                    Toast.makeText(this@MainActivity,
                        if(applied)"Applied: "+preset.name else "Could not apply layout",
                        Toast.LENGTH_SHORT).show()
                }
            }
            action(this,"Open Layout Lab"){
                startActivity(Intent(this@MainActivity,KeyPopLayoutStudioActivity::class.java))
            }
        }
    }
    private fun typing(){
        heading("Typing & Size","Adjust keyboard behavior and dimensions")
        addBlock{
            range(this,"Key height","height",36,90,51)
            range(this,"Key size","font",12,24,17)
            range(this,"Key spacing","spacing",1,10,4)
            range(this,"Keyboard width %","keyboardWidthPercent",65,100,100)
            range(this,"Key corner radius","round",3,26,13)
            option(this,"Number row","numberRow",true)
            option(this,"Word suggestions","suggestions",true)
            option(this,"Autocorrection","autoCorrect")
            option(this,"Grammar hints","grammarHints",true)
            option(this,"Roman Urdu / Sindhi","romanMode")
            option(this,"Double-space punctuation","doubleSpace",true)
            option(this,"Haptic feedback","haptics")
            option(this,"Keyboard sounds","sound")
            option(this,"Spacebar cursor gesture","spaceCursor",true)
            option(this,"High contrast","highContrast")
        }
    }
    private fun translation(){
        heading("TRANSLATION ENGINE","English ⇄ Urdu ⇄ Sindhi · all six directions")
        addBlock {
            addView(text("KeyPop Offline is the default. Use ⇄ on the keyboard for live translation, language swapping, Insert, Replace and Copy.",13f,muted))
            action(this,"Download All Languages"){startDownload()}
            downloadMessage=text("",13f,accent);addView(downloadMessage)
            downloadProgress=ProgressBar(this@MainActivity,null,
                android.R.attr.progressBarStyleHorizontal).apply{max=100;visibility=View.GONE}
            addView(downloadProgress)
            addView(text("Automatic target language",14f,ink,true))
            listOf("English","اردو","سنڌي").forEachIndexed{index,label->
                action(this,label){
                    prefs.edit().putInt("autoTarget",index).apply()
                    Toast.makeText(this@MainActivity,"Target: "+label,Toast.LENGTH_SHORT).show()
                }
            }
        }
        addBlock {
            addView(text("GOOGLE CLOUD · OPTIONAL",14f,accent,true))
            addView(text("To use live Google Cloud Translation, supply your own Google Cloud API key. Google may charge for translation and receives text only if you enable this option and choose the Google engine.",13f,muted))
            val field=EditText(this@MainActivity).apply{
                hint=if(KeyPopCloudTranslation.configured(this@MainActivity))
                    "Key saved · enter to replace" else "Google Cloud Translation API key"
                inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                setSingleLine(true);setTextColor(ink);setHintTextColor(muted)
            }
            addView(field)
            action(this,"Save Google Cloud API key"){
                runCatching{KeyPopCloudTranslation.saveKey(this@MainActivity,field.text.toString())}
                    .onSuccess{
                        field.text.clear()
                        Toast.makeText(this@MainActivity,"API key encrypted and saved",Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(this@MainActivity,
                            it.localizedMessage?:"Unable to save key",Toast.LENGTH_LONG).show()
                    }
            }
            option(this,"Enable online Google Cloud translation","googleCloudConsent")
            action(this,"Remove Google Cloud key"){
                KeyPopCloudTranslation.clearKey(this@MainActivity)
                prefs.edit().putBoolean("googleCloudConsent",false).apply()
                navigate("translation")
            }
            action(this,"Open Google Translate external alternative"){
                startActivity(Intent(this@MainActivity,KeyPopGoogleTranslateActivity::class.java))
            }
            option(this,"Translate on Send","autoTranslate")
        }
        showTransfer()
    }
    private fun privacy(){
        heading("Clipboard & Security","Privacy and local data")
        addBlock {
            option(this,"Clipboard history","history")
            option(this,"High contrast labels","highContrast")
            option(this,"Private performance metrics","performanceMetrics")
            action(this,"Manage saved passwords"){
                startActivity(Intent(this@MainActivity,KeyPopVaultActivity::class.java))
            }
            action(this,"Manage clipboard"){
                startActivity(Intent(this@MainActivity,KeyPopMediaStudioActivity::class.java))
            }
        }
    }
    private fun tools(){
        heading("Tools","Advanced keyboard utilities")
        addBlock {
            action(this,"Personal Dictionary"){
                startActivity(Intent(this@MainActivity,KeyPopDictionaryActivity::class.java))
            }
            action(this,"Layout Lab"){
                startActivity(Intent(this@MainActivity,KeyPopLayoutStudioActivity::class.java))
            }
            action(this,"Design Backup"){
                startActivity(Intent(this@MainActivity,KeyPopProfileActivity::class.java))
            }
            action(this,"Voice Translate"){
                startActivity(Intent(this@MainActivity,KeyPopVoiceActivity::class.java))
            }
            action(this,"Media Shelf"){
                startActivity(Intent(this@MainActivity,KeyPopMediaStudioActivity::class.java))
            }
            action(this,"Performance Diagnostics"){
                startActivity(Intent(this@MainActivity,KeyPopDiagnosticsActivity::class.java))
            }
        }
    }
}
