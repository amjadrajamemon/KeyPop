package com.aetherkeys.keyboard

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
    private var previewThemeIndex=-1
    private var previewLayoutIndex=-1
    private var previewKeysetIndex=-1
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
    // Three independent selections, all on one screen. A tap immediately applies
    // the choice; the preview is rebuilt without a separate Save step.
    private fun picker(parent:LinearLayout,label:String,selected:String,onTap:()->Unit) {
        val row=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
            minimumHeight=dp(65)
            setPadding(dp(12),dp(8),dp(12),dp(8))
            background=shape(0xFF244B60.toInt(),12,0xFF4D8494.toInt())
            isClickable=true;isFocusable=true
            contentDescription="$label: $selected. Double tap to change."
            setOnClickListener {onTap()}
        }
        val labels=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
        labels.addView(text(label,12f,muted))
        labels.addView(text(selected,16f,ink,true))
        row.addView(labels,LinearLayout.LayoutParams(0,-2,1f))
        row.addView(text("⌄",20f,accent,true))
        parent.addView(row,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(9)})
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
    /**
     * Resizing keeps this canvas fixed at the top while its controls scroll.
     * Slider changes persist and redraw the canvas without rebuilding the dialog.
     */
    private fun showResizeDialog() {
        val shell=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(12),dp(8),dp(12),0)
        }
        val preview=KeyPopSizingPreview(this)
        shell.addView(preview,LinearLayout.LayoutParams(-1,dp(170)))
        val sliders=mutableListOf<Triple<SeekBar,String,Int>>()
        val controls=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(0,dp(12),0,dp(8))
        }
        fun sizeSlider(label:String,key:String,min:Int,max:Int,default:Int,unit:String) {
            val caption=text("",13f,ink)
            val bar=SeekBar(this).apply {
                this.max=max-min
                progress=prefs.getInt(key,default).coerceIn(min,max)-min
            }
            caption.text="$label: ${bar.progress+min}$unit"
            bar.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seek:SeekBar?,value:Int,user:Boolean){
                    caption.text="$label: ${value+min}$unit"
                    if(user){
                        prefs.edit().putInt(key,value+min).apply()
                        preview.refresh()
                    }
                }
                override fun onStartTrackingTouch(seek:SeekBar?){}
                override fun onStopTrackingTouch(seek:SeekBar?){}
            })
            controls.addView(caption)
            controls.addView(bar)
            sliders.add(Triple(bar,key,min))
        }
        sizeSlider("Key height","height",36,90,51," dp")
        sizeSlider("Keyboard width","keyboardWidthPercent",65,100,100,"%")
        sizeSlider("Key spacing","spacing",1,10,4," dp")
        sizeSlider("Key text","font",12,32,19," sp")
        sizeSlider("Corners","round",3,40,13," dp")
        val numberRow=Switch(this).apply {
            text="Number row"
            setTextColor(ink)
            isChecked=prefs.getBoolean("numberRow",true)
            setOnCheckedChangeListener{_,on->
                prefs.edit().putBoolean("numberRow",on).apply()
                preview.refresh()
            }
        }
        controls.addView(numberRow)
        action(controls,"Reset size"){
            prefs.edit().putInt("height",51).putInt("keyboardWidthPercent",100)
                .putInt("spacing",4).putInt("font",19).putInt("round",13)
                .putBoolean("numberRow",true).apply()
            val defaults=mapOf("height" to 51,"keyboardWidthPercent" to 100,
                "spacing" to 4,"font" to 19,"round" to 13)
            sliders.forEach{(seek,key,min)->seek.progress=defaults.getValue(key)-min}
            numberRow.isChecked=true
            preview.refresh()
        }
        val area=ScrollView(this).apply {
            isFillViewport=false
            isVerticalScrollBarEnabled=false
            addView(controls)
        }
        val height=(resources.displayMetrics.heightPixels/resources.displayMetrics.density*.42f)
            .toInt().coerceIn(220,350)
        shell.addView(area,LinearLayout.LayoutParams(-1,dp(height)))
        AlertDialog.Builder(this).setTitle("Resize Keyboard")
            .setView(shell).setPositiveButton("Done",null).show()
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
        ViewCompat.setOnApplyWindowInsetsListener(content){view,insets->
            val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(dp(17),dp(18)+bars.top,dp(17),dp(22)+bars.bottom)
            insets
        }
        scroller.addView(content)
        ViewCompat.requestApplyInsets(scroller)
        // Attach the window before building any optional settings subsection.
        setContentView(scroller)
        section=intent.getStringExtra("openSection")?:"settingsHome"
        if(section=="settings")section="typing"
        if(section=="languagePacks")section="languagePacks"
        if(section !in setOf("settingsHome","typing","languagePacks","themes",
                "layouts","keysets","design","translation","security","tools","onboarding"))
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
    // Back exits the settings task in one step. The visible "All Settings"
    // control remains available when the user intentionally wants the index.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed(){
        finish()
    }
    override fun onNewIntent(intent:Intent){
        super.onNewIntent(intent)
        setIntent(intent)
        val target=intent.getStringExtra("openSection")?:"settingsHome"
        navigate(if(target=="settings")"typing" else target)
    }
    private fun navigate(destination:String){
        section=if(destination in setOf("themes","layouts","keysets"))"design" else destination
        downloadMessage=null;downloadProgress=null
        content.removeAllViews()
        val header=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
        }
        header.addView(text("KEYPOP  /  SETTINGS",13f,accent,true),
            LinearLayout.LayoutParams(0,dp(44),1f))
        val close=text("✕  Close",14f,ink,true).apply {
            gravity=Gravity.CENTER
            setPadding(dp(12),0,dp(12),0)
            background=shape(card,11)
            contentDescription="Close Settings and return to the keyboard"
            isClickable=true;isFocusable=true
            setOnClickListener{finish()}
        }
        header.addView(close,LinearLayout.LayoutParams(-2,dp(42)))
        content.addView(header)
        if(section!="settingsHome") {
            action(content,"‹  All Settings"){navigate("settingsHome")}
        }
        try {
            when(section){
                "settingsHome"->dashboard()
                "onboarding"->setup()
                "languagePacks"->offlinePacks()
                "design"->design()
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
        addBlock {
            addView(text("01   GET STARTED",12f,accent,true))
            action(this,"Keyboard Setup","Activate KeyPop and test your keyboard"){navigate("onboarding")}
            action(this,"Offline Packs","Manage downloaded translation languages"){navigate("languagePacks")}
        }
        addBlock {
            addView(text("02   TYPING INTELLIGENCE",12f,accent,true))
            action(this,"Typing & Size",
                "Corrections, predictions, grammar, toolbar and key sizes"){navigate("typing")}
        }
        addBlock {
            addView(text("03   KEYBOARD DESIGN",12f,accent,true))
            action(this,"Customize Keyboard"){navigate("design")}
        }
        addBlock {
            addView(text("04   TRANSLATION & LANGUAGES",12f,accent,true))
            action(this,"Translation","Compact live mode, cloud or offline engines"){navigate("translation")}
        }
        addBlock {
            addView(text("05   PRIVACY & UTILITIES",12f,accent,true))
            action(this,"Clipboard & Security","Private history and saved logins"){navigate("security")}
            action(this,"Tools","Dictionary, advanced layout editor and diagnostics"){navigate("tools")}
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
        // Show the six-direction checklist before any network or storage work.
        startActivity(Intent(this,KeyPopModelManagerActivity::class.java))
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
        heading("Offline Translation Packs","Download only the directions you choose")
        addBlock{
            addView(text("Choose English ↔ Urdu ↔ Sindhi directions, then start one download with a visible progress bar.",14f,muted))
            action(this,"Choose translation directions"){startDownload()}
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
    private fun design(){
        heading("Customize Keyboard")
        val skin=KeyPopSkins.index(this)
        val preset=KeyboardPresetCatalog.selected(this)
        val keyset=KeyPopKeysets.selectedId(this)
        addBlock {
            addView(KeyboardPresetPreview(this@MainActivity,preset,keyset),
                LinearLayout.LayoutParams(-1,dp(154)).apply{bottomMargin=dp(13)})
            picker(this,"Theme",KeyPopSkins.all[skin].name) {
                val labels=KeyPopSkins.all.map{it.name}.toTypedArray()
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Theme")
                    .setSingleChoiceItems(labels,skin){dialog,index->
                        prefs.edit().putInt("theme",index).remove("customAccent").apply()
                        dialog.dismiss()
                        navigate("design")
                    }
                    .setNegativeButton("Cancel",null).show()
            }
            picker(this,"Layout",preset.name) {
                val labels=KeyboardPresetCatalog.all.map{it.name}.toTypedArray()
                val selected=KeyboardPresetCatalog.all.indexOfFirst{it.id==preset.id}
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Layout")
                    .setSingleChoiceItems(labels,selected){dialog,index->
                        val applied=KeyboardPresetCatalog.apply(
                            this@MainActivity,KeyboardPresetCatalog.all[index].id)
                        dialog.dismiss()
                        if(applied)navigate("design")
                        else Toast.makeText(this@MainActivity,
                            "Unable to change layout",Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel",null).show()
            }
            val currentKeys=KeyPopKeysets.all.first{it.id==keyset}
            picker(this,"Keys & Symbols",currentKeys.name) {
                val labels=KeyPopKeysets.all.map{it.name}.toTypedArray()
                val selected=KeyPopKeysets.all.indexOfFirst{it.id==keyset}
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Keys & Symbols")
                    .setSingleChoiceItems(labels,selected){dialog,index->
                        val applied=KeyPopKeysets.apply(
                            this@MainActivity,KeyPopKeysets.all[index].id)
                        dialog.dismiss()
                        if(applied)navigate("design")
                        else Toast.makeText(this@MainActivity,
                            "Unable to change keys",Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel",null).show()
            }
            action(this,"Resize keys"){showResizeDialog()}
        }
    }
    private fun typing(){
        heading("Typing & Size","Configure behavior first, then appearance and feedback")
        addBlock {
            addView(text("01   PREDICTIONS & GRAMMAR",13f,accent,true))
            option(this,"Word suggestions","suggestions",true)
            option(this,"Aggressive predictions","aggressivePredictions",true)
            option(this,"Predict next word","predictNext",true)
            option(this,"Learn my words on this device","learningEnabled",true)
            option(this,"Learn my sentence patterns privately","personalHistoryLearning",true)
            addView(text("KeyPop now learns short word sequences as you type "+
                "and ranks predictions using your own recent phrasing. "+
                "Only compact encrypted word statistics are kept locally; "+
                "it cannot read conversations typed before you enabled it. "+
                "Sensitive fields, passwords and URL/email fields are excluded.",12f,muted))
            option(this,"Correct joined words (e.g. topredict → to predict)",
                "correctMissingSpaces",true)
            option(this,"Auto-correct spelling","autoCorrect",true)
            option(this,"Grammar-aware suggestions","contextPredictions",true)
            option(this,"Grammar hints (suggest only)","grammarHints",true)
            option(this,"Apply high-confidence grammar fixes on Space",
                "autoGrammarApply",true)
            addView(text("Auto-grammar handles conservative rules such as "+
                "'I are' → 'I am'. Ambiguous edits stay suggestions; "+
                "passwords, URLs and special text fields are excluded.",12f,muted))
        }
        addBlock {
            addView(text("02   PUNCTUATION & CAPITALS",13f,accent,true))
            option(this,"Auto punctuation","autoPunctuate",true)
            option(this,"Double-space punctuation","doubleSpace",true)
            option(this,"Capitalize sentence beginnings","autoCap",true)
            option(this,"Auto-space after selected suggestions","suggestionSpace",true)
        }
        addBlock {
            addView(text("03   TOOLBAR & SUGGESTIONS",13f,accent,true))
            option(this,"Always show toolbar while typing","alwaysShowToolbar",true)
            addView(text("When disabled, the Material toolbar gives way to suggestions "+
                "while you type. When enabled, both remain visible.",12f,muted))
            range(this,"Suggestion font size","suggestionFont",12,24,17)
            range(this,"Suggestion strip height","suggestionHeight",38,64,45)
        }
        addBlock {
            addView(text("04   KEYBOARD SIZE",13f,accent,true))
            action(this,"Resize keys"){showResizeDialog()}
            option(this,"Roman Urdu / Sindhi","romanMode")
        }
        addBlock {
            addView(text("05   FEEDBACK & ACCESSIBILITY",13f,accent,true))
            option(this,"Haptic feedback","haptics")
            option(this,"Keyboard sounds","sound")
            option(this,"Spacebar cursor gesture","spaceCursor",true)
            option(this,"High contrast","highContrast")
        }
        addBlock {
            addView(text("06   RECENT SCREENSHOTS",13f,accent,true))
            option(this,"Show copied screenshots on keyboard","screenshotClipOffer",true)
            option(this,"Suggest new screenshots automatically",
                "autoScreenshotSuggestions",true)
            option(this,"Automatically save detected screenshots to encrypted history",
                "autoScreenshotArchive",false)
            addView(text("Screenshots are saved to Android's gallery, NOT "+
                "automatically copied to the clipboard. KeyPop can detect "+
                "new screenshots in the gallery and show a small Paste/Share "+
                "thumbnail, then independently save them to its encrypted "+
                "clipboard image history. Android 14/15 requires you to choose "+
                "'Allow all photos' for future screenshots. This feature runs "+
                "while KeyPop's input service is active; missed captures are "+
                "checked when the keyboard reopens.",12f,muted))
            action(this,"Set up full screenshot access and automatic history") {
                startActivity(Intent(this@MainActivity,
                    KeyPopScreenshotAccessActivity::class.java))
            }
            action(this,"Open saved screenshot history") {
                startActivity(Intent(this@MainActivity,
                    KeyPopScreenshotHistoryActivity::class.java))
            }
        }
    }
    private fun translation(){
        heading("TRANSLATION ENGINE","English ⇄ Urdu ⇄ Sindhi · all six directions")
        addBlock {
            addView(text("01   LIVE TRANSLATION",13f,accent,true))
            option(this,"Compact Gboard-style translation bar","compactTranslate",true)
            addView(text("Type directly in your app while KeyPop shows translations above the keyboard.",12f,muted))
            option(this,"Show live translation while typing","liveTranslateDefault",true)
            addView(text("Cloud mode translates over the internet with your Google Cloud "+
                "API key and consent. Offline mode needs downloaded language models. "+
                "Gboard's private translation service is not exposed to other keyboards.",
                12f,muted))
        }
        addBlock {
            addView(text("02   OFFLINE MODELS & DEFAULTS",13f,accent,true))
            addView(text("KeyPop Offline is the default. Use ⇄ on the keyboard for live translation, language swapping, Insert, Replace and Copy.",13f,muted))
            action(this,"Choose and download translation packs"){startDownload()}
            downloadMessage=text("",13f,accent);addView(downloadMessage)
            downloadProgress=ProgressBar(this@MainActivity,null,
                android.R.attr.progressBarStyleHorizontal).apply{max=100;visibility=View.GONE}
            addView(downloadProgress)
            addView(text("Target for optional Translate on Send",14f,ink,true))
            addView(text("Choose a target explicitly. The Send key stays normal "+
                "until you also enable Translate on Send. Old saved settings do "+
                "not automatically enable translation.",12f,muted))
            listOf("English","اردو","سنڌي").forEachIndexed{index,label->
                action(this,label){
                    prefs.edit().putInt("autoTarget",index)
                        .putBoolean("autoTargetConfigured",true).apply()
                    Toast.makeText(this@MainActivity,"Target: "+label,Toast.LENGTH_SHORT).show()
                }
            }
        }
        addBlock {
            addView(text("03   OPTIONAL CLOUD TRANSLATION",14f,accent,true))
            addView(text("Google's offline ML Kit engine needs no account. Google Cloud is a separate paid service: signing in to a Google account alone cannot enable it. Cloud use requires a billing-enabled Google Cloud project and authorized access. The optional API key below is kept for advanced users.",13f,muted))
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
            option(this,"Translate automatically when pressing Send (opt-in)",
                "translateOnSendExplicit",false)
            addView(text("Requires a target language selected above. The previous "+
                "Translate on Send preference is ignored for safety.",12f,muted))
            option(this,"Use Enter to send when chat app has no Send action",
                "sendViaEnterFallback",false)
            addView(text("Direct Send requires an app-supported keyboard Send action. "+
                "Enter fallback works only when the app sends on Enter; otherwise it "+
                "might add a new line.",12f,muted))
        }
        showTransfer()
    }
    private fun privacy(){
        heading("Clipboard & Security","Privacy and local data")
        addBlock {
            option(this,"Clipboard history — retain every observed copy","history")
            option(this,"Also save copied images to encrypted vault","historyImages")
            option(this,"Auto-save new gallery screenshots (requires full Photos access)",
                "autoScreenshotArchive",false)
            addView(text("Every observed text copy is retained (including repeats "+
                "and multi-item copies), with no age or item-count expiration. "+
                "Saved images are also retained when image history is enabled; "+
                "storage capacity and Android background clipboard limits still "+
                "apply. Passwords marked sensitive are excluded.",12f,muted))
            addView(text("Before replacing a preview app, export a portable "+
                "password-encrypted full backup of text and screenshots from "+
                "Clipboard → menu → Back up everything. Restore it in the "+
                "newly installed package. Android cannot transfer private "+
                "storage automatically between different package IDs.",12f,muted))
            option(this,"High contrast labels","highContrast")
            option(this,"Private performance metrics","performanceMetrics")
            action(this,"Import my saved text history into predictions",
                "Explicit opt-in · safe natural-language clips only") {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Learn from saved clipboard text?")
                    .setMessage("KeyPop can learn short word sequences from up "+
                        "to 2,000 existing text clips. Numbers, URLs, emails and "+
                        "long or suspicious clips are excluded. It does not "+
                        "store full sentences in its prediction database. "+
                        "This action never uploads your data.")
                    .setNegativeButton("Cancel",null)
                    .setPositiveButton("Learn privately"){_,_->
                        Thread {
                            val result=runCatching {
                                val model=KeyPopPersonalLanguageModel(this@MainActivity)
                                val db=ClipStore(this@MainActivity)
                                var offset=0;var patterns=0
                                while(offset<2000) {
                                    val batch=db.page(100,offset)
                                    if(batch.isEmpty())break
                                    for(clip in batch)patterns+=
                                        model.learnFromSavedClip(
                                            KeyPopTextLanguage.detect(clip.text,0),clip.text)
                                    offset+=batch.size
                                }
                                patterns
                            }
                            runOnUiThread{
                                Toast.makeText(this@MainActivity,
                                    result.fold(
                                        {"Learned $it sentence associations locally"},
                                        {"Import unavailable: "+(it.localizedMessage?:"error")}
                                    ),Toast.LENGTH_LONG).show()
                            }
                        }.start()
                    }.show()
            }
            action(this,"Erase my learned sentence patterns",
                "Keep text clips and custom dictionary intact") {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Erase personalized predictions?")
                    .setMessage("Remove learned word sequences only. Clipboard "+
                        "history, screenshots and manual dictionary words remain.")
                    .setNegativeButton("Cancel",null)
                    .setPositiveButton("Erase"){_,_->
                        Thread {
                            val result=runCatching{
                                KeyPopPersonalLanguageModel(this@MainActivity).clear()
                            }
                            runOnUiThread{
                                Toast.makeText(this@MainActivity,
                                    if(result.isSuccess)"Sentence patterns erased"
                                    else "Could not erase learned patterns",
                                    Toast.LENGTH_LONG).show()
                            }
                        }.start()
                    }.show()
            }
            action(this,"Manage saved passwords"){
                startActivity(Intent(this@MainActivity,KeyPopVaultActivity::class.java))
            }
            action(this,"Manage clipboard"){
                startActivity(Intent(this@MainActivity,KeyPopClipboardActivity::class.java))
            }
            action(this,"Browse saved screenshots"){
                startActivity(Intent(this@MainActivity,KeyPopScreenshotHistoryActivity::class.java))
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
            action(this,"Performance Diagnostics"){
                startActivity(Intent(this@MainActivity,KeyPopDiagnosticsActivity::class.java))
            }
        }
    }
}
