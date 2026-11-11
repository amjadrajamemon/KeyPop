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
    private val pageHistory=ArrayDeque<String>()
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
    private fun picker(parent:LinearLayout,label:String,selected:String,onTap:()->Unit):TextView {
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
        val valueText=text(selected,16f,ink,true)
        labels.addView(valueText)
        row.addView(labels,LinearLayout.LayoutParams(0,-2,1f))
        row.addView(text("⌄",20f,accent,true))
        parent.addView(row,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(9)})
        return valueText
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
        val caption=text("Tap any key below, then adjust its individual width or height. Changes appear live and remain saved.",13f,ink)
        shell.addView(caption)
        var language=0
        val languageSwitch=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL}
        lateinit var keyWidth:SeekBar
        lateinit var keyHeight:SeekBar
        lateinit var widthLabel:TextView
        lateinit var heightLabel:TextView
        lateinit var selectionLabel:TextView
        lateinit var board:KeyPopLiveKeyEditor
        fun updateSelection() {
            val token=board.selectedToken
            val row=board.selectedRow
            val col=board.selectedColumn
            val display=if(token=="SPACE")"Spacebar" else when(token) {
                "⇧"->"Shift / Case";"⌫"->"Backspace";"↵"->"Enter"
                ","->"Comma";"."->"Period";else->token
            }
            selectionLabel.text="SELECTED KEY: $display · "+KeyPopLayouts.languages[language]
            val width=KeyPopKeyGeometry.widthPercent(this,language,row,col,token)
            val height=KeyPopKeyGeometry.heightPercent(this,language,row,col,token)
            widthLabel.text="Selected key width: $width%"
            heightLabel.text="Selected key height: $height%"
            keyWidth.progress=width-65
            keyHeight.progress=height-70
        }
        board=KeyPopLiveKeyEditor(this){_,_,_->
            updateSelection()
        }
        KeyPopLayouts.languages.forEachIndexed {index,name->
            val option=android.widget.Button(this).apply{
                text=name;textSize=12f;isAllCaps=false
                setOnClickListener {
                    language=index
                    board.activeLanguage=index
                    board.selectedRow=-1;board.selectedColumn=-1
                    board.selectedToken="SPACE"
                    board.refresh()
                    // Refresh selected values after switching language.
                    val selectedWidth=KeyPopKeyGeometry.widthPercent(
                        this@MainActivity,index,-1,-1,"SPACE")
                    val selectedHeight=KeyPopKeyGeometry.heightPercent(
                        this@MainActivity,index,-1,-1,"SPACE")
                    selectionLabel.text="SELECTED KEY: Spacebar · "+name
                    widthLabel.text="Selected key width: $selectedWidth%"
                    heightLabel.text="Selected key height: $selectedHeight%"
                    keyWidth.progress=selectedWidth-65
                    keyHeight.progress=selectedHeight-70
                }
            }
            languageSwitch.addView(option,LinearLayout.LayoutParams(0,dp(38),1f))
        }
        shell.addView(languageSwitch)
        val previewArea=ScrollView(this).apply {
            isFillViewport=false
            addView(board)
        }
        shell.addView(previewArea,LinearLayout.LayoutParams(-1,dp(255)))
        val controls=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL;setPadding(0,dp(7),0,dp(8))
        }
        selectionLabel=text("SELECTED KEY: Spacebar · English",14f,accent,true)
        controls.addView(selectionLabel)
        widthLabel=text("Selected key width: 100%",14f,ink,true)
        controls.addView(widthLabel)
        keyWidth=SeekBar(this).apply{
            max=190-65;progress=35
            contentDescription="Adjust the selected key width in real time"
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
                override fun onProgressChanged(bar:SeekBar?,value:Int,user:Boolean){
                    if(!user)return
                    val target=board.selectedToken
                    val width=value+65
                    KeyPopKeyGeometry.setWidthPercent(this@MainActivity,
                        language,board.selectedRow,board.selectedColumn,target,width)
                    widthLabel.text="Selected key width: $width%"
                    board.refresh()
                }
                override fun onStartTrackingTouch(s:SeekBar?){}
                override fun onStopTrackingTouch(s:SeekBar?){}
            })
        }
        controls.addView(keyWidth)
        heightLabel=text("Selected key height: 100%",14f,ink,true)
        controls.addView(heightLabel)
        keyHeight=SeekBar(this).apply{
            max=180-70;progress=30
            contentDescription="Adjust the selected key height in real time"
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
                override fun onProgressChanged(bar:SeekBar?,value:Int,user:Boolean){
                    if(!user)return
                    val height=value+70
                    KeyPopKeyGeometry.setHeightPercent(this@MainActivity,
                        language,board.selectedRow,board.selectedColumn,
                        board.selectedToken,height)
                    heightLabel.text="Selected key height: $height%"
                    board.refresh()
                }
                override fun onStartTrackingTouch(s:SeekBar?){}
                override fun onStopTrackingTouch(s:SeekBar?){}
            })
        }
        controls.addView(keyHeight)
        updateSelection()
        controls.addView(text("WHOLE KEYBOARD · live settings",14f,accent,true))
        val general=mutableListOf<Triple<SeekBar,String,Int>>()
        fun sizeSlider(label:String,key:String,min:Int,max:Int,initial:Int,unit:String) {
            val title=text("$label: ${prefs.getInt(key,initial).coerceIn(min,max)}$unit",
                14f,ink,true)
            val slider=SeekBar(this).apply{
                this.max=max-min
                progress=prefs.getInt(key,initial).coerceIn(min,max)-min
                contentDescription=label
                setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
                    override fun onProgressChanged(s:SeekBar?,v:Int,user:Boolean){
                        title.text="$label: ${v+min}$unit"
                        if(user){prefs.edit().putInt(key,v+min).apply();board.refresh()}
                    }
                    override fun onStartTrackingTouch(s:SeekBar?){}
                    override fun onStopTrackingTouch(s:SeekBar?){}
                })
            }
            controls.addView(title);controls.addView(slider)
            general.add(Triple(slider,key,min))
        }
        sizeSlider("All keys · base height","height",36,90,51," dp")
        sizeSlider("Keyboard width","keyboardWidthPercent",65,100,100,"%")
        sizeSlider("Horizontal key spacing","spacing",1,10,4," dp")
        sizeSlider("Key text size","font",12,32,19," sp")
        sizeSlider("Corner rounding","round",3,40,13," dp")
        val numberRow=Switch(this).apply{
            text="Show number row";setTextColor(ink)
            isChecked=prefs.getBoolean("numberRow",true)
            setOnCheckedChangeListener{_,yes->
                prefs.edit().putBoolean("numberRow",yes).apply();board.refresh()
            }
        }
        controls.addView(numberRow)
        action(controls,"Reset all key dimensions to defaults"){
            KeyPopKeyGeometry.resetAllDimensions(this)
            prefs.edit().putInt("height",51).putInt("keyboardWidthPercent",100)
                .putInt("spacing",4).putInt("font",19).putInt("round",13).apply()
            val values=mapOf("height" to 51,"keyboardWidthPercent" to 100,
                "spacing" to 4,"font" to 19,"round" to 13)
            general.forEach{(bar,key,min)->bar.progress=values.getValue(key)-min}
            board.refresh()
            updateSelection()
        }
        val scroll=ScrollView(this).apply{isVerticalScrollBarEnabled=true;addView(controls)}
        val available=(resources.displayMetrics.heightPixels/resources.displayMetrics.density*
            .36f).toInt().coerceIn(220,360)
        shell.addView(scroll,LinearLayout.LayoutParams(-1,dp(available)))
        AlertDialog.Builder(this).setTitle("Live Key-by-Key Resizing")
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
        val target=section
        section="settingsHome"
        if(target!="settingsHome")pageHistory.addLast("settingsHome")
        navigate(target,false)
        if(intent.getBooleanExtra("openResizeDialog",false))
            content.post {showResizeDialog()}
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
    /** Android Back and our toolbar Back share the same one-level history. */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed(){
        backOneSection()
    }
    private fun backOneSection() {
        if(pageHistory.isNotEmpty())navigate(pageHistory.removeLast(),false)
        else if(section!="settingsHome")navigate("settingsHome",false)
        else finish()
    }
    override fun onNewIntent(intent:Intent){
        super.onNewIntent(intent)
        setIntent(intent)
        val target=intent.getStringExtra("openSection")?:"settingsHome"
        navigate(if(target=="settings")"typing" else target)
    }
    private fun navigate(destination:String,record:Boolean=true){
        val next=if(destination in setOf("themes","layouts","keysets"))"design" else destination
        if(record && next!=section)pageHistory.addLast(section)
        if(next=="settingsHome" && record)pageHistory.clear()
        section=next
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
            val previous=pageHistory.lastOrNull()?:"settingsHome"
            val parentLabel=when(previous) {
                "design"->"Keyboard Design";"typing"->"Typing & Size"
                "tools"->"Tools";"translation"->"Translation"
                "security"->"Clipboard & Security";else->"All Settings"
            }
            action(content,"‹  Back to $parentLabel"){backOneSection()}
            if(previous!="settingsHome")
                action(content,"All Settings"){navigate("settingsHome")}
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
    /** Keep both the dialog and settings preview attached while options
     * change. No return to Settings, no scroll reset, no stale captured layout. */
    private fun chooseLiveDesign(title:String,labels:List<String>,selected:Int,
                                 preview:KeyboardPresetPreview,value:TextView,
                                 apply:(Int)->Boolean) {
        val body=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(12),dp(10),dp(12),dp(12))
            // Android's default AlertDialog can be white even when this
            // screen is dark. White labels on the default white sheet were
            // selectable but invisible on the user's phone.
            background=shape(base,15,edge)
        }
        val hint=text("Choose an option and watch the keyboard preview change immediately.",13f,ink)
        body.addView(hint)
        val live=KeyboardPresetPreview(this,KeyboardPresetCatalog.selected(this),
            KeyPopKeysets.selectedId(this))
        body.addView(live,LinearLayout.LayoutParams(-1,dp(154)).apply {
            bottomMargin=dp(8)
        })
        val group=android.widget.RadioGroup(this).apply {
            orientation=android.widget.RadioGroup.VERTICAL
            background=shape(base,12)
        }
        labels.forEachIndexed {index,label->
            val radio=android.widget.RadioButton(this).apply {
                text=label
                textSize=15f
                KeyPopOptionStyle.decorate(this@MainActivity,this)
                id=android.view.View.generateViewId()
                isChecked=index==selected
                contentDescription="Choose "+label
                setOnClickListener {
                    if(apply(index)) {
                        value.text=label
                        preview.showLive()
                        live.showLive()
                    } else Toast.makeText(this@MainActivity,
                        "Unable to change "+title.lowercase(),Toast.LENGTH_SHORT).show()
                }
            }
            group.addView(radio,android.widget.RadioGroup.LayoutParams(-1,dp(53)).apply {
                bottomMargin=dp(5)
            })
        }
        val list=ScrollView(this).apply {
            isFillViewport=false
            background=shape(base,12)
            addView(group)
        }
        body.addView(list,LinearLayout.LayoutParams(-1,dp(232)))
        AlertDialog.Builder(this).setTitle(title+" · Live Preview")
            .setView(body).setPositiveButton("Done",null).show()
    }
    private fun design(){
        heading("Customize Keyboard","Change a theme, geometry, keyset or alphabet and see the update immediately.")
        val skin=KeyPopSkins.index(this)
        val preset=KeyboardPresetCatalog.selected(this)
        val keyset=KeyPopKeysets.selectedId(this)
        addBlock {
            val preview=KeyboardPresetPreview(this@MainActivity,preset,keyset)
            addView(preview,LinearLayout.LayoutParams(-1,dp(154)).apply{
                bottomMargin=dp(10)
            })
            addView(text("LIVE KEYBOARD PREVIEW · Select any option below to update it",
                12f,accent,true))
            lateinit var themeName:TextView
            themeName=picker(this,"Theme",KeyPopSkins.all[skin].name) {
                chooseLiveDesign("Theme",KeyPopSkins.all.map{it.name},
                    KeyPopSkins.index(this@MainActivity),preview,themeName){index->
                    prefs.edit().putInt("theme",index).remove("customAccent").apply()
                    true
                }
            }
            lateinit var layoutName:TextView
            layoutName=picker(this,"Layout",preset.name) {
                chooseLiveDesign("Keyboard geometry",KeyboardPresetCatalog.all.map{it.name},
                    KeyboardPresetCatalog.all.indexOfFirst{
                        it.id==KeyboardPresetCatalog.selected(this@MainActivity).id
                    }.coerceAtLeast(0),preview,layoutName){index->
                    KeyboardPresetCatalog.apply(this@MainActivity,
                        KeyboardPresetCatalog.all[index].id)
                }
            }
            lateinit var arrangementName:TextView
            arrangementName=picker(this,"English letter arrangement",
                KeyPopLayouts.names[KeyPopLayouts.variant(this@MainActivity)]) {
                chooseLiveDesign("English letter arrangement",KeyPopLayouts.names,
                    KeyPopLayouts.variant(this@MainActivity),preview,arrangementName){index->
                    KeyPopLayouts.choose(this@MainActivity,index)
                    true
                }
            }
            lateinit var keysName:TextView
            keysName=picker(this,"Keys & Symbols",
                KeyPopKeysets.all.first{it.id==keyset}.name) {
                chooseLiveDesign("Keys & Symbols",KeyPopKeysets.all.map{it.name},
                    KeyPopKeysets.all.indexOfFirst{
                        it.id==KeyPopKeysets.selectedId(this@MainActivity)
                    }.coerceAtLeast(0),preview,keysName){index->
                    KeyPopKeysets.apply(this@MainActivity,KeyPopKeysets.all[index].id)
                }
            }
            action(this,"Resize keys","Live individual key height, width, spacing and text size"){
                showResizeDialog()
            }
        }
    }
    /** Settings preview renders using exactly the same styling functions as
     * the active IME; every adjustment repaints while the dialog stays open. */
    private fun suggestionAppearanceEditor() {
        val column=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(13),dp(9),dp(13),dp(12))
            background=shape(base,14,edge)
        }
        val previewContainer=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(6),dp(6),dp(6),dp(8))
            background=shape(card,12,edge)
        }
        column.addView(text("LIVE PREVIEW · Placement relative to toolbar",13f,accent,true))
        column.addView(previewContainer,LinearLayout.LayoutParams(-1,dp(117)).apply {
            bottomMargin=dp(8)
        })
        fun updatePreview() {
            previewContainer.removeAllViews()
            val toolbar=TextView(this).apply {
                text="▦   Clipboard     ✦   Translate     ⚙   Settings"
                textSize=12f
                gravity=Gravity.CENTER
                setTextColor(ink)
                background=shape(0xFF334D60.toInt(),9)
            }
            val toolbarParams=LinearLayout.LayoutParams(-1,dp(41))
            val prediction=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
            for(word in listOf("improved","improvements","improving")) {
                val key=android.widget.Button(this).apply {
                    text=word;isAllCaps=false;setOnClickListener{}
                }
                KeyPopSuggestionStyle.addChip(this,prediction,key)
            }
            KeyPopSuggestionStyle.applyRow(this,prediction)
            val rowParams=LinearLayout.LayoutParams(-1,dp(
                KeyPopSuggestionStyle.heightDp(this))).apply{
                topMargin=dp(3)
            }
            if(KeyPopSuggestionStyle.position(this)==KeyPopSuggestionStyle.ABOVE_TOOLBAR) {
                previewContainer.addView(prediction,rowParams)
                previewContainer.addView(toolbar,toolbarParams)
            }else {
                previewContainer.addView(toolbar,toolbarParams)
                previewContainer.addView(prediction,rowParams)
            }
        }
        fun save(key:String,value:Int) {
            prefs.edit().putInt(key,value).apply()
            updatePreview()
        }
        fun colorEditor(title:String,prefKey:String,initial:Int) {
            val rgb=LinearLayout(this).apply{
                orientation=LinearLayout.VERTICAL
                setPadding(dp(12),dp(5),dp(12),dp(8))
                background=shape(base,12,edge)
            }
            val channels=intArrayOf(Color.red(initial),Color.green(initial),
                Color.blue(initial))
            val header=text("Color: #"+String.format("%06X",initial and 0xFFFFFF),
                15f,ink,true)
            rgb.addView(header)
            listOf("Red","Green","Blue").forEachIndexed {index,name->
                val label=text(name+": "+channels[index],13f,ink)
                rgb.addView(label)
                val slider=SeekBar(this).apply {
                    max=255;progress=channels[index]
                    contentDescription=name+" channel for "+title
                    setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
                        override fun onProgressChanged(bar:SeekBar?,n:Int,user:Boolean){
                            if(!user)return
                            channels[index]=n
                            label.text=name+": "+n
                            val color=Color.rgb(channels[0],channels[1],channels[2])
                            header.text="Color: #"+String.format("%06X",
                                color and 0xFFFFFF)
                            prefs.edit().putInt("suggestionPalette",6)
                                .putInt(prefKey,color).apply()
                            updatePreview()
                        }
                        override fun onStartTrackingTouch(bar:SeekBar?){}
                        override fun onStopTrackingTouch(bar:SeekBar?){}
                    })
                }
                rgb.addView(slider)
            }
            AlertDialog.Builder(this).setTitle(title)
                .setView(rgb).setPositiveButton("Done",null).show()
        }
        column.addView(text("Position",14f,ink,true))
        val placements=android.widget.RadioGroup(this).apply {
            orientation=android.widget.RadioGroup.HORIZONTAL
        }
        listOf("Above toolbar","Below toolbar").forEachIndexed{index,name->
            val radio=android.widget.RadioButton(this).apply {
                text=name;textSize=13f;setTextColor(ink)
                id=android.view.View.generateViewId()
                isChecked=KeyPopSuggestionStyle.position(this@MainActivity)==index
                setOnClickListener{save("suggestionPosition",index)}
            }
            placements.addView(radio,android.widget.RadioGroup.LayoutParams(
                0,dp(42)).apply{weight=1f})
        }
        column.addView(placements)
        column.addView(text("Color theme",14f,ink,true))
        val palettePanel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        KeyPopSuggestionStyle.paletteNames.forEachIndexed{index,name->
            val radio=android.widget.RadioButton(this).apply {
                text=name;textSize=14f
                KeyPopOptionStyle.decorate(this@MainActivity,this)
                isChecked=KeyPopSuggestionStyle.palette(this@MainActivity)==index
                setOnClickListener{save("suggestionPalette",index)}
            }
            palettePanel.addView(radio)
        }
        // Exclusive selection across all seven theme choices.
        val paletteGroup=android.widget.RadioGroup(this).apply {
            orientation=android.widget.RadioGroup.VERTICAL
        }
        while(palettePanel.childCount>0){
            val child=palettePanel.getChildAt(0)
            palettePanel.removeViewAt(0)
            child.id=android.view.View.generateViewId()
            paletteGroup.addView(child)
        }
        column.addView(paletteGroup)
        column.addView(text("Custom colors · Adjust RGB values live",14f,accent,true))
        action(column,"Background color","Choose an exact RGB color"){
            colorEditor("Suggestion background","suggestionBackgroundColor",
                prefs.getInt("suggestionBackgroundColor",0xFF213746.toInt()))
        }
        action(column,"Suggestion chip color","Color of each suggested word"){
            colorEditor("Suggestion chip","suggestionChipColor",
                prefs.getInt("suggestionChipColor",0xFF38566B.toInt()))
        }
        action(column,"Prediction text color","Text color independent of keyboard theme"){
            colorEditor("Prediction text","suggestionTextColor",
                prefs.getInt("suggestionTextColor",Color.WHITE))
        }
        fun slider(title:String,key:String,min:Int,max:Int,initial:Int,suffix:String) {
            val value=prefs.getInt(key,initial).coerceIn(min,max)
            val label=text("$title: $value$suffix",14f,ink,true)
            column.addView(label)
            column.addView(SeekBar(this).apply {
                this.max=max-min;progress=value-min
                contentDescription=title
                setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
                    override fun onProgressChanged(s:SeekBar?,n:Int,user:Boolean){
                        val current=n+min
                        label.text="$title: $current$suffix"
                        if(user)save(key,current)
                    }
                    override fun onStartTrackingTouch(s:SeekBar?){}
                    override fun onStopTrackingTouch(s:SeekBar?){}
                })
            })
        }
        slider("Prediction font size","suggestionFont",12,32,17," sp")
        slider("Prediction row height","suggestionHeight",38,78,45," dp")
        slider("Space between suggestions","suggestionGap",0,16,4," dp")
        slider("Suggestion corner rounding","suggestionCorner",0,28,12," dp")
        slider("Row background opacity","suggestionOpacity",45,100,100,"%")
        column.addView(Switch(this).apply {
            text="Bold suggestion text";setTextColor(ink)
            isChecked=KeyPopSuggestionStyle.bold(this@MainActivity)
            setOnCheckedChangeListener{_,checked->
                prefs.edit().putBoolean("suggestionBold",checked).apply()
                updatePreview()
            }
        })
        action(column,"Restore suggestion-row defaults"){
            prefs.edit()
                .remove("suggestionPosition").remove("suggestionPalette")
                .remove("suggestionBackgroundColor").remove("suggestionChipColor")
                .remove("suggestionTextColor").remove("suggestionFont")
                .remove("suggestionHeight").remove("suggestionGap")
                .remove("suggestionCorner").remove("suggestionOpacity")
                .remove("suggestionBold").apply()
            Toast.makeText(this,"Prediction row defaults restored",
                Toast.LENGTH_SHORT).show()
            updatePreview()
        }
        updatePreview()
        val scrolling=ScrollView(this).apply {
            isFillViewport=false
            addView(column)
        }
        AlertDialog.Builder(this).setTitle("Suggestion Row · Live Customization")
            .setView(scrolling).setPositiveButton("Done",null).show()
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
            range(this,"Suggestion font size","suggestionFont",12,32,17)
            range(this,"Suggestion strip height","suggestionHeight",38,78,45)
            action(this,"Customize suggestion row · Live preview",
                "Position, fonts, text/chip colors, background, opacity, height, spacing and corners") {
                suggestionAppearanceEditor()
            }
            addView(text("Set above or below the toolbar, preview changes instantly, and "+
                "retain your settings across English, Urdu and Sindhi keyboards.",12f,muted))
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
