package com.aetherkeys.keyboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.content.Intent
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.text.InputType
import android.view.MotionEvent
import android.graphics.Rect
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputContentInfo
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.HorizontalScrollView
import android.widget.TextView
import android.widget.SeekBar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.Toast
import java.util.Locale
import java.util.concurrent.Executors

class AetherImeService : InputMethodService() {
    private val prefs by lazy { getSharedPreferences("keypop",Context.MODE_PRIVATE) }
    private val clipStore by lazy { ClipStore(this) }
    private val lexicon by lazy { KeyPopLexicon(this) }
    private var suggestionBar:LinearLayout?=null
    private var materialTopBar:LinearLayout?=null
    private var lastSuggestionContext:String?=null
    private val repeatHandler=Handler(Looper.getMainLooper())
    private var repeatBackspace:Runnable?=null
    private var didRepeatBackspace=false
    private val keyViews=ArrayList<Pair<Button,String>>()
    private val imagePreviewExecutor=Executors.newFixedThreadPool(2)
    @Volatile private var recentScreenshotUri:Uri?=null
    @Volatile private var screenshotMime="image/png"
    private var screenshotRefreshToken=0
    private val clipboard by lazy { getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    private val languages=listOf("ENGLISH","اردو","سنڌي")
    private var language=0
    private var shift=false
    private var symbols=false
    private var clipsOpen=false
    private var toolsOpen=false
    private var toolsPage=0
    private var resizeOpen=false
    private val renderedRows=ArrayList<Pair<LinearLayout,Int>>()
    private var navSafeBottomPx=0
    private var translatorOpen=false
    private var writingOpen=false
    private var writingSource=""
    private var writingPreview=""
    private var writingMode="Formal"
    private var translateSource=""
    private var translateSourceChoice=-1
    private var translateResult=""
    private var translationStatus=""
    private var translateTarget=0
    private var googleEasyMode=false
    private var liveTranslationEnabled=true
    private var liveRevision=0L
    private val liveHandler=Handler(Looper.getMainLooper())
    private var liveSourceView:TextView?=null
    private var liveResultView:TextView?=null
    private val codes=listOf("en","ur","sd")
    private var voiceTargetPackage=""
    private var pendingSpeech:String?=null
    private var pendingSpeechSend=false
    private val speechReceiver=object:BroadcastReceiver() {
        override fun onReceive(context:Context,intent:Intent) {
            if(intent.action==KeyPopVoiceActivity.RESULT_ACTION) {
                val recognized=intent.getStringExtra("text").orEmpty()
                val requestSend=intent.getBooleanExtra("send",false)
                if(recognized.isNotBlank()) {
                    val target=currentInputEditorInfo?.packageName.orEmpty()
                    if(!sensitive(currentInputEditorInfo) && target==voiceTargetPackage) {
                        val success=currentInputConnection?.commitText(recognized,1) ?: false
                        if(success && requestSend) {
                            val action=(currentInputEditorInfo?.imeOptions ?:0) and
                                EditorInfo.IME_MASK_ACTION
                            if(action==EditorInfo.IME_ACTION_SEND)
                                currentInputConnection?.performEditorAction(action)
                        }
                        if(!success) {
                            pendingSpeech=recognized
                            pendingSpeechSend=requestSend
                        }
                    }else {
                        pendingSpeech=recognized
                        pendingSpeechSend=requestSend
                    }
                }
            }
        }
    }
    private var emojiCategory="Faces"
    private var emojiSearch=false
    private var emojiQuery=""
    private var emojiOpen=false
    private var pinnedOnly=false
    private var cursorTools=false
    private var extended=false
    private var capsLock=false
    private var lastShiftPress=0L
    private var current:LinearLayout?=null
    private var floatingShell:FrameLayout?=null
    private var characterStrip:KeyPopCharacterStrip?=null
    private var liveBackground:KeyPopBackdropDrawable?=null
    private var themeAnimator:android.animation.ValueAnimator?=null
    private var lastThemeFrame=0L
    private val emojiRows=listOf(
        listOf("😀","😁","😂","😍","🥰","😊","😎","😭"),
        listOf("❤️","👍","👏","🙏","🔥","✨","🎉","✅"),
        listOf("💯","💪","🤔","😅","😇","🥹","👋","⭐"))
    private fun sensitive(info:EditorInfo?):Boolean {
        if(info==null) return true
        val klass=info.inputType and InputType.TYPE_MASK_CLASS
        val variation=info.inputType and InputType.TYPE_MASK_VARIATION
        return klass==InputType.TYPE_NULL ||
            (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)!=0 ||
            (klass==InputType.TYPE_CLASS_TEXT &&
                (variation==InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                 variation==InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                 variation==InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
            (klass==InputType.TYPE_CLASS_NUMBER && variation==InputType.TYPE_NUMBER_VARIATION_PASSWORD)
    }
    private fun numeric(info:EditorInfo?):Boolean {
        val klass=(info?.inputType ?: 0) and InputType.TYPE_MASK_CLASS
        return klass==InputType.TYPE_CLASS_NUMBER || klass==InputType.TYPE_CLASS_PHONE
    }
    private val english=listOf(listOf("q","w","e","r","t","y","u","i","o","p"),
        listOf("a","s","d","f","g","h","j","k","l"),
        listOf("z","x","c","v","b","n","m"))
    private val urdu=listOf(listOf("ق","و","ع","ر","ت","ے","ء","ی","ہ","پ"),
        listOf("ا","س","د","ف","گ","ح","ج","ک","ل"),
        listOf("ز","ش","چ","ط","ب","ن","م"))
    private val sindhi=listOf(listOf("ق","و","ع","ر","ت","ي","ء","ڀ","ه","پ"),
        listOf("ا","س","د","ف","ڳ","ح","ج","ڪ","ل"),
        listOf("ز","ش","چ","ٽ","ٻ","ن","م"))
    private val symbolRows=listOf(listOf("1","2","3","4","5","6","7","8","9","0"),
        listOf("@","#","$","%","&","-","+","(",")","/"),
        listOf("*","\"","'",";",":","!","?","_","="))
    private val extraSymbols=listOf(listOf("~","€","£","¥","•","√","π","÷","×","∆"),
        listOf("{","}","[","]","<",">","©","®","™","✓"),
        listOf("،","؛","؟","«","»","–","—","…","°"))
    private val urduExtended=listOf(
        listOf("آ","أ","إ","ث","ٹ","ڈ","ڑ","ژ","ذ","ظ"),
        listOf("ض","غ","خ","ھ","ؤ","ئ","ة","ۃ","ً","ٰ"),
        listOf("ِ","ُ","َ","ْ","ّ","،","؟","؛","ء","ٓ"))
    private val sindhiExtended=listOf(
        listOf("آ","ٻ","ڀ","ٿ","ٽ","ٺ","ڄ","جھ","ڃ","ڇ"),
        listOf("ڌ","ڏ","ڊ","ڍ","ڙ","ژ","ڦ","ک","ڳ","گھ"),
        listOf("ڻ","ؤ","ھ","ء","ئ","و","ي","ه","ا","ب"))

    private val clipListener=ClipboardManager.OnPrimaryClipChangedListener {
        if (!sensitive(currentInputEditorInfo) && prefs.getBoolean("history",false)) {
            try {
                val clip=clipboard.primaryClip
                if(clip!=null && clip.itemCount>0 && clip.description.hasMimeType("text/plain")) {
                    val markedSensitive=Build.VERSION.SDK_INT>=33 &&
                        clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE")==true
                    if(!markedSensitive)
                        clip.getItemAt(0).text?.toString()?.let { clipStore.save(it) }
                }
            } catch (_:Exception) { /* Never break typing on inaccessible clipboard. */ }
        }
        if(!sensitive(currentInputEditorInfo))refreshRecentScreenshotAsync()
    }

    override fun onCreate() {
        super.onCreate()
        window.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        window.window?.navigationBarColor=0xFF101827.toInt()
        if(Build.VERSION.SDK_INT>=29)window.window?.isNavigationBarContrastEnforced=true
        val filter=IntentFilter(KeyPopVoiceActivity.RESULT_ACTION)
        androidx.core.content.ContextCompat.registerReceiver(
            this,speechReceiver,filter,androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        clipboard.addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        stopBackspaceRepeating()
        stopLiveTranslation()
        screenshotRefreshToken++
        themeAnimator?.cancel();themeAnimator=null;liveBackground=null
        try { unregisterReceiver(speechReceiver) } catch(_:Exception) {}
        try { clipboard.removePrimaryClipChangedListener(clipListener) } catch(_:Exception) {}
        imagePreviewExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onCreateInputView():View {
        // A teal Gboard theme must not silently keep the unrelated Custom geometry.
        // Keep explicit non-Gboard presets; migrate legacy teal theme once.
        if(!prefs.getBoolean("gboardLayoutMigration20261107",false)) {
            val oldPreset=prefs.getInt("keyboardPreset",KeyboardPresetCatalog.CUSTOM)
            if(!prefs.contains("keyboardPreset") ||
                (oldPreset==KeyboardPresetCatalog.CUSTOM &&
                    prefs.getInt("theme",0)==10))
                KeyboardPresetCatalog.apply(this,KeyboardPresetCatalog.MATERIAL)
            prefs.edit().putBoolean("gboardLayoutMigration20261107",true).apply()
        }
        language=prefs.getInt("language",0).coerceIn(0,2)
        floatingShell=FrameLayout(this).apply{
            setBackgroundColor(Color.TRANSPARENT)
            // The navigation bar is a system-owned area. Keep the last key above it.
            setPadding(0,0,0,d(12))
        }
        current=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        val shell=floatingShell!!
        characterStrip=KeyPopCharacterStrip(this,shell)
        shell.addView(current,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM))
        ViewCompat.setOnApplyWindowInsetsListener(shell) { view,insets ->
            val bottom=insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val safe=KeyboardSizingRules.safeBottom(bottom,d(12))
            if(navSafeBottomPx!=safe) {
                navSafeBottomPx=safe
                view.setPadding(0,0,0,safe)
                applyFloatingLayout()
            }
            insets
        }
        ViewCompat.requestApplyInsets(shell)
        draw()
        return shell
    }
    override fun onUpdateSelection(oldSelStart:Int,oldSelEnd:Int,newSelStart:Int,newSelEnd:Int,candidatesStart:Int,candidatesEnd:Int) {
        super.onUpdateSelection(oldSelStart,oldSelEnd,newSelStart,newSelEnd,candidatesStart,candidatesEnd)
        refreshSuggestions()
        scheduleLiveTranslation()
    }

    override fun onFinishInputView(finishingInput:Boolean) {
        stopBackspaceRepeating()
        stopLiveTranslation()
        themeAnimator?.pause()
        KeyPopDiagnostics.flush(this)
        super.onFinishInputView(finishingInput)
    }

    override fun onStartInputView(info:EditorInfo?,restarting:Boolean) {
        val viewStart=SystemClock.elapsedRealtimeNanos()
        super.onStartInputView(info,restarting)
        stopLiveTranslation()
        stopBackspaceRepeating()
        clipsOpen=false;emojiOpen=false;cursorTools=false;translatorOpen=false;writingOpen=false
        googleEasyMode=false
        toolsOpen=false;resizeOpen=false
        shift=false;capsLock=false
        val pending=pendingSpeech
        if(pending!=null && !sensitive(info) && info?.packageName==voiceTargetPackage) {
            val inserted=currentInputConnection?.commitText(pending,1) ?: false
            if(inserted){
                pendingSpeech=null
                if(pendingSpeechSend) {
                    val action=(info?.imeOptions ?:0) and EditorInfo.IME_MASK_ACTION
                    if(action==EditorInfo.IME_ACTION_SEND)
                        currentInputConnection?.performEditorAction(action)
                }
                pendingSpeechSend=false
            }
        } else if(pending!=null && info?.packageName!=voiceTargetPackage) {
            pendingSpeech=null;pendingSpeechSend=false
        }
        draw()
        refreshRecentScreenshotAsync()
        KeyPopDiagnostics.observeKeyboardBuild(this,SystemClock.elapsedRealtimeNanos()-viewStart)
    }

    private fun d(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun gradient(a:Int,b:Int,r:Int,stroke:Int?=null):GradientDrawable=
        GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(a,b)).apply {
            cornerRadius=d(r).toFloat()
            if(stroke!=null) setStroke(d(1),stroke)
        }
    private fun palette()=KeyPopSkins.active(this)
    private fun tapFeedback(v:View) {
        if(prefs.getBoolean("haptics",false)) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if(prefs.getBoolean("sound",false))
            (getSystemService(Context.AUDIO_SERVICE) as AudioManager).playSoundEffect(AudioManager.FX_KEY_CLICK)
    }
    private fun makeKey(label:String,heavy:Boolean=false,weight:Float=1f,action:()->Unit):Button {
        val s=palette()
        val round=KeyPopSkins.radius(s,prefs.getInt("round",13)).toInt().coerceIn(1,40)
        val bg=KeyPopGlass.key(this,s,heavy)
        return KeyPopHintKey(this).apply {
            text=label
            textSize=if(label.length>3) 12f else prefs.getInt("font",17).coerceIn(12,24).toFloat()
            isAllCaps=false
            setTextColor(if(heavy) Color.WHITE else s.ink)
            typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
            gravity=Gravity.CENTER
            background=android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf((s.accent and 0x00FFFFFF) or (0x44 shl 24)),
                bg,null)
            minWidth=0; minimumWidth=0; minHeight=0; minimumHeight=0
            setPadding(0,0,0,0)
            elevation=d(if(KeyPopSkins.index(this@AetherImeService)==10)1 else 3).toFloat()
            setOnClickListener { tapFeedback(this); action() }
        }
    }
    private fun chip(label:String,weight:Float=1f,onTap:()->Unit):Button {
        val s=palette()
        return Button(this).apply {
            text=label; textSize=11f; isAllCaps=false
            setTextColor(s.ink); minWidth=0; minimumWidth=0; minHeight=0; minimumHeight=0
            background=KeyPopGlass.panel(this@AetherImeService,s)
            setPadding(0,0,0,0)
            setOnClickListener { tapFeedback(this); onTap() }
        }
    }
    private fun applyFloatingLayout() {
        val root=current ?:return
        val shell=floatingShell ?:return
        val floating=prefs.getBoolean("floating",false)
        val screenWidth=resources.displayMetrics.widthPixels
        val percent=prefs.getInt("keyboardWidthPercent",100).coerceIn(65,100)
        val width=(screenWidth*percent/100f).toInt().coerceAtLeast(d(220))
            .coerceAtMost(screenWidth)
        val x=if(floating)prefs.getInt("floatX",0).coerceIn(-d(75),d(75)) else 0
        val left=((screenWidth-width)/2+x).coerceIn(0,(screenWidth-width).coerceAtLeast(0))
        val lift=prefs.getInt("bottomLift",0).coerceIn(0,96)
        val params=(root.layoutParams as? FrameLayout.LayoutParams)
            ?:FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM)
        params.width=if(percent>=100 && !floating)-1 else width
        params.leftMargin=if(percent>=100 && !floating)0 else left
        params.bottomMargin=d(lift+if(floating)prefs.getInt("floatY",16).coerceIn(0,90) else 0)
        params.gravity=Gravity.BOTTOM or Gravity.LEFT
        shell.minimumHeight=if(floating)minOf(d(480),
            (resources.displayMetrics.heightPixels*.7f).toInt()) else 0
        root.elevation=if(floating)d(14).toFloat() else 0f
        root.layoutParams=params
        shell.requestLayout()
    }
    private fun refreshRowHeights() {
        val height=prefs.getInt("height",51).coerceIn(36,90)
        renderedRows.forEach { (row,offset)->
            val px=d((height+offset).coerceAtLeast(30))
            for(i in 0 until row.childCount) {
                val v=row.getChildAt(i)
                if(v !is Button)continue
                val layout=v.layoutParams
                if(layout.height!=px) {layout.height=px;v.layoutParams=layout}
            }
            row.requestLayout()
        }
        current?.requestLayout()
        floatingShell?.requestLayout()
    }
    /** In-place key styling updates while the slider remains attached to its view. */
    private fun refreshKeyAppearance() {
        val skin=palette()
        val radius=KeyPopSkins.radius(skin,prefs.getInt("round",13)).toInt().coerceIn(1,40)
        val font=prefs.getInt("font",17).coerceIn(12,24).toFloat()
        val space=d(prefs.getInt("spacing",4).coerceIn(1,10)/2)
        keyViews.forEach { (key,label)->
            val heavy=label=="↵" || label=="⇧" ||
                (label=="SPACE" && skin.shape==3)
            val bg=KeyPopGlass.key(this,skin,heavy)
            key.background=android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(
                    (skin.accent and 0x00FFFFFF) or (0x55 shl 24)),bg,null)
            key.textSize=if(key.text.toString().length>3)12f else font
            val layout=key.layoutParams as? LinearLayout.LayoutParams
            if(layout!=null) {
                layout.leftMargin=space;layout.rightMargin=space
                key.layoutParams=layout
            }
        }
        current?.requestLayout()
    }
    /** In-place resizing avoids detaching the gesture target during a drag. */
    private fun showResizePanel(root:LinearLayout) {
        val skin=palette()
        val panel=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(d(8),d(8),d(8),d(8))
            background=gradient(skin.surface,skin.background,12,skin.accent)
        }
        val title=TextView(this).apply{
            text="SIZE & POSITION  ·  Drag the handle or use sliders"
            textSize=12f;setTextColor(skin.ink)
        }
        panel.addView(title)
        val handle=chip("↕  DRAG TO RESIZE KEY HEIGHT") {}
        handle.contentDescription="Hold and drag vertically to resize the keyboard"
        var downY=0f
        var startHeight=51
        handle.setOnTouchListener { _,event ->
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN ->{
                    downY=event.rawY
                    startHeight=prefs.getInt("height",51).coerceIn(36,90)
                    true
                }
                MotionEvent.ACTION_MOVE ->{
                    val next=(startHeight+(downY-event.rawY)/resources.displayMetrics.density/3f)
                        .toInt().coerceIn(36,90)
                    if(prefs.getInt("height",51)!=next){
                        prefs.edit().putInt("height",next).apply()
                        refreshRowHeights()
                    }
                    true
                }
                MotionEvent.ACTION_UP ->{draw();true}
                MotionEvent.ACTION_CANCEL ->true
                else ->false
            }
        }
        panel.addView(handle,LinearLayout.LayoutParams(-1,d(45)).apply{topMargin=d(6)})
        if(prefs.getBoolean("floating",false)) {
            val move=chip("✥  DRAG TO MOVE FLOATING KEYBOARD") {}
            move.contentDescription="Drag to reposition the floating keyboard"
            var firstX=0f;var firstY=0f
            var initialX=0;var initialY=0
            move.setOnTouchListener { _,event ->
                when(event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        firstX=event.rawX;firstY=event.rawY
                        initialX=prefs.getInt("floatX",0)
                        initialY=prefs.getInt("floatY",16)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx=(event.rawX-firstX).toInt()
                        val dy=(event.rawY-firstY)/resources.displayMetrics.density
                        prefs.edit().putInt("floatX",(initialX+dx).coerceIn(-d(75),d(75)))
                            .putInt("floatY",(initialY-dy.toInt()).coerceIn(0,90)).apply()
                        applyFloatingLayout()
                        true
                    }
                    MotionEvent.ACTION_UP ->true
                    MotionEvent.ACTION_CANCEL ->true
                    else ->false
                }
            }
            panel.addView(move,LinearLayout.LayoutParams(-1,d(42)).apply{topMargin=d(4)})
        }
        fun adjust(name:String,pref:String,min:Int,max:Int,initial:Int,onUpdate:()->Unit) {
            val caption=TextView(this).apply{
                text=name+"  ·  "+initial
                textSize=12f;setTextColor(skin.ink)
            }
            panel.addView(caption)
            panel.addView(SeekBar(this).apply {
                this.max=max-min
                progress=(prefs.getInt(pref,initial).coerceIn(min,max)-min)
                setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s:SeekBar?,p:Int,fromUser:Boolean){
                        if(fromUser){
                            prefs.edit().putInt(pref,p+min).apply()
                            caption.text=name+"  ·  "+(p+min)
                            onUpdate()
                        }
                    }
                    override fun onStartTrackingTouch(s:SeekBar?){}
                    override fun onStopTrackingTouch(s:SeekBar?){draw()}
                })
            })
        }
        adjust("Key height (dp)","height",36,90,51){refreshRowHeights()}
        adjust("Keyboard width (%)","keyboardWidthPercent",65,100,100){applyFloatingLayout()}
        adjust("Key spacing (dp)","spacing",1,10,4){refreshKeyAppearance()}
        adjust("Key label size (sp)","font",12,24,17){refreshKeyAppearance()}
        adjust("Key corners (dp)","round",3,40,13){refreshKeyAppearance()}
        adjust("Above navigation bar (dp)","bottomLift",0,96,0){applyFloatingLayout()}
        val numberSwitch=chip(if(prefs.getBoolean("numberRow",true))
            "1 2 3  ·  Number row: ON  (tap to hide)"
            else "1 2 3  ·  Number row: OFF  (tap to show)") {
            prefs.edit().putBoolean("numberRow",!prefs.getBoolean("numberRow",true)).apply()
            draw()
        }
        numberSwitch.contentDescription="Toggle dedicated number row"
        panel.addView(numberSwitch,LinearLayout.LayoutParams(-1,d(42)))
        if(prefs.getBoolean("floating",false)){
            adjust("Floating left/right","floatX",-d(75),d(75),0){applyFloatingLayout()}
            adjust("Floating lift (dp)","floatY",0,90,16){applyFloatingLayout()}
        }
        val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        fun action(label:String,weight:Float,fn:()->Unit){
            actions.addView(chip(label,onTap=fn),LinearLayout.LayoutParams(0,d(40),weight).apply{
                rightMargin=d(4)
            })
        }
        action("Reset",1f){
            prefs.edit().putInt("height",51).putInt("keyboardWidthPercent",100)
                .putInt("bottomLift",0).putInt("spacing",4).putInt("font",17)
                .putInt("round",13).putBoolean("numberRow",true)
                .putBoolean("floating",false).apply()
            resizeOpen=false;draw()
        }
        action("Floating: "+(if(prefs.getBoolean("floating",false))"On" else "Off"),1.3f){
            val activate=!prefs.getBoolean("floating",false)
            val e=prefs.edit().putBoolean("floating",activate)
            if(activate && prefs.getInt("keyboardWidthPercent",100)==100)
                e.putInt("keyboardWidthPercent",85)
            e.apply()
            draw()
        }
        action("✓ Done",1f){resizeOpen=false;draw()}
        panel.addView(actions)
        root.addView(panel)
    }
    private fun updateThemeAnimation() {
        val enabled=prefs.getBoolean("animatedThemes",false) &&
            android.animation.ValueAnimator.areAnimatorsEnabled()
        if(!enabled) {
            themeAnimator?.cancel();themeAnimator=null
            liveBackground?.phase=0f
            return
        }
        if(themeAnimator==null) {
            themeAnimator=android.animation.ValueAnimator.ofFloat(0f,1f).apply {
                duration=8000L
                repeatCount=android.animation.ValueAnimator.INFINITE
                repeatMode=android.animation.ValueAnimator.RESTART
                addUpdateListener {
                    val time=SystemClock.uptimeMillis()
                    if(time-lastThemeFrame>=85L) {
                        lastThemeFrame=time
                        liveBackground?.phase=it.animatedValue as Float
                    }
                }
                start()
            }
        } else if(themeAnimator?.isPaused==true)themeAnimator?.resume()
    }

    /** Refresh clipboard-image and opt-in gallery screenshot offer only when the
     * keyboard opens or the clipboard changes, never on each typed character. */
    private fun refreshRecentScreenshotAsync() {
        if(sensitive(currentInputEditorInfo)) {
            recentScreenshotUri=null
            return
        }
        val token=++screenshotRefreshToken
        imagePreviewExecutor.execute {
            var uri:Uri?=null
            var mime="image/png"
            try {
                val clip=clipboard.primaryClip
                val markedSensitive=Build.VERSION.SDK_INT>=33 &&
                    clip?.description?.extras?.getBoolean("android.content.extra.IS_SENSITIVE")==true
                if(clip!=null && clip.itemCount>0 &&
                    clip.description.hasMimeType("image/*") && !markedSensitive) {
                    uri=clip.getItemAt(0).uri
                    if(uri!=null)mime=contentResolver.getType(uri)?:"image/png"
                }
                if(uri==null && prefs.getBoolean("autoScreenshotSuggestions",false)) {
                    uri=KeyPopRecentScreenshots.latest(this)
                    if(uri!=null)mime=contentResolver.getType(uri)?:"image/png"
                }
            } catch(_:Exception){ /* Inaccessible clipboard or media is not an IME failure. */ }
            val found=uri
            Handler(Looper.getMainLooper()).post {
                if(token==screenshotRefreshToken && !sensitive(currentInputEditorInfo) &&
                    current!=null) {
                    val changed=recentScreenshotUri!=found
                    recentScreenshotUri=found
                    screenshotMime=mime
                    if(changed)draw()
                }
            }
        }
    }
    private fun insertRecentScreenshot(uri:Uri,mime:String) {
        if(sensitive(currentInputEditorInfo))return
        val info=InputContentInfo(uri,ClipDescription("Recent screenshot",arrayOf(mime)),null)
        val inserted=runCatching {
            currentInputConnection?.commitContent(info,
                InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,null)==true
        }.getOrDefault(false)
        if(inserted)return
        try{
            val intent=Intent(Intent.ACTION_SEND).apply{
                type=mime
                putExtra(Intent.EXTRA_STREAM,uri)
                clipData=ClipData.newUri(contentResolver,"Recent screenshot",uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(Intent.createChooser(intent,"Share screenshot")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }catch(_:Exception){
            Toast.makeText(this,"This app cannot receive screenshot images",Toast.LENGTH_SHORT).show()
        }
    }
    private fun showScreenshotThumbnail(button:Button,uri:Uri) {
        imagePreviewExecutor.execute {
            val bitmap=runCatching {
                contentResolver.openInputStream(uri)?.use{
                    BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{
                        inSampleSize=8
                        inPreferredConfig=android.graphics.Bitmap.Config.RGB_565
                    })
                }
            }.getOrNull()?:return@execute
            Handler(Looper.getMainLooper()).post {
                if(recentScreenshotUri==uri && button.isAttachedToWindow){
                    val icon=BitmapDrawable(resources,bitmap)
                    icon.setBounds(0,0,d(30),d(30))
                    button.setCompoundDrawables(icon,null,null,null)
                    button.text=""
                }else bitmap.recycle()
            }
        }
    }

    private fun draw() {
        val root=current ?: return
        characterStrip?.dismiss()
        root.removeAllViews()
        suggestionBar=null
        materialTopBar=null
        lastSuggestionContext=null
        keyViews.clear()
        renderedRows.clear()
        val s=palette()
        val keyboardBackdrop=KeyPopBackdropDrawable(KeyPopSkins.index(this),s)
        liveBackground=keyboardBackdrop
        root.background=if(KeyPopSkins.index(this)==10)KeyPopGlass.background(this,s)
            else keyboardBackdrop
        updateThemeAnimation()
        root.setPadding(d(3),d(5),d(3),d(6))
        applyFloatingLayout()
        val preset=KeyboardPresetCatalog.selected(this)
        val secure=sensitive(currentInputEditorInfo)
        val isNumeric=numeric(currentInputEditorInfo)
        val materialToolbar=preset.id==KeyboardPresetCatalog.MATERIAL && !secure && !isNumeric
        if(materialToolbar) {
            val top=LinearLayout(this).apply {
                orientation=LinearLayout.HORIZONTAL
                gravity=Gravity.CENTER_VERTICAL
                setPadding(d(3),d(1),d(3),d(1))
            }
            fun iconAction(glyph:String,description:String,fn:()->Unit) {
                val button=chip(glyph,onTap=fn)
                button.contentDescription=description
                button.textSize=if(glyph=="GIF")13f else 21f
                top.addView(button,LinearLayout.LayoutParams(0,d(40),1f).apply {
                    leftMargin=d(2);rightMargin=d(2)
                })
            }
            iconAction("▦","Open feature grid and resize tools"){
                toolsOpen=!toolsOpen;if(toolsOpen)toolsPage=0;resizeOpen=false;draw()
            }
            iconAction("▤","Open clipboard history"){
                clipsOpen=!clipsOpen;emojiOpen=false;toolsOpen=false;resizeOpen=false;draw()
            }
            iconAction("GIF","Open GIF and sticker shelf"){
                startActivity(Intent(this,KeyPopMediaStudioActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            iconAction("⚙","Open organized keyboard settings"){
                startActivity(Intent(this,MainActivity::class.java)
                    .putExtra("openSection","settingsHome")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            iconAction("⇄","Translate text"){
                translatorOpen=!translatorOpen;writingOpen=false;emojiOpen=false
                clipsOpen=false;toolsOpen=false;resizeOpen=false
                if(translatorOpen) {
                    translateSource=editorTranslationText()
                    translateSourceChoice=-1
                    translateResult="";translationStatus=""
                    googleEasyMode=false
                    val detected=KeyPopTextLanguage.detect(translateSource,language)
                    translateTarget=prefs.getInt("autoTarget",if(detected==2)0 else 2).coerceIn(0,2)
                    if(translateTarget==detected)translateTarget=if(detected==2)0 else 2
                }
                draw()
                if(translatorOpen) scheduleLiveTranslation(80)
            }
            val screenshot=recentScreenshotUri
            if(screenshot!=null && prefs.getBoolean("screenshotClipOffer",true)) {
                val screenshotButton=chip("📷",onTap={
                    insertRecentScreenshot(screenshot,screenshotMime)
                }).apply{
                    contentDescription="Recent screenshot. Tap to paste or share"
                    textSize=18f
                }
                top.addView(screenshotButton,LinearLayout.LayoutParams(0,d(40),1f).apply{
                    leftMargin=d(2);rightMargin=d(2)
                })
                showScreenshotThumbnail(screenshotButton,screenshot)
            } else iconAction("◈","Preview theme gallery without changing your active theme"){
                startActivity(Intent(this,MainActivity::class.java)
                    .putExtra("openSection","themes")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            iconAction("🎙","Open voice translation"){
                voiceTargetPackage=currentInputEditorInfo?.packageName.orEmpty()
                pendingSpeech=null;pendingSpeechSend=false
                val code=when(language){1->"ur-PK";2->"sd-PK";else->"en-US"}
                startActivity(Intent(this,KeyPopVoiceActivity::class.java)
                    .putExtra("lang",code)
                    .putExtra("target",prefs.getInt("autoTarget",1))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            root.addView(top,LinearLayout.LayoutParams(-1,d(43)))
            materialTopBar=top
        } else {
        val toolbar=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
            setPadding(d(2),0,d(2),0)
        }
        fun shortcut(label:String,weight:Float,fn:()->Unit) {
            val control=chip(label,onTap=fn)
            control.contentDescription=label
            control.textSize=11f
            toolbar.addView(control,LinearLayout.LayoutParams(0,d(42),weight).apply {
                leftMargin=d(2);rightMargin=d(2)
            })
        }
        shortcut(if(toolsOpen)"Close Menu" else "☷  Tools",1.12f) {
            toolsOpen=!toolsOpen;if(toolsOpen)toolsPage=0;resizeOpen=false;draw()
        }
        shortcut(if(resizeOpen)"✓  Done" else "↕  Resize",1f) {
            resizeOpen=!resizeOpen;toolsOpen=false;draw()
        }
        shortcut(if(secure)"Paste" else "▤  Clips",.9f) {
            if(secure)currentInputConnection?.performContextMenuAction(android.R.id.paste)
            else {clipsOpen=!clipsOpen;emojiOpen=false;toolsOpen=false;resizeOpen=false;draw()}
        }
        shortcut("☺  Emoji",.9f) {
            if(!secure && !isNumeric){
                emojiOpen=!emojiOpen;clipsOpen=false;toolsOpen=false
                translatorOpen=false;writingOpen=false;resizeOpen=false;draw()
            }
        }
        shortcut("⚙ Settings",1.1f) {
            startActivity(Intent(this,MainActivity::class.java)
                .putExtra("openSection","settingsHome")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        root.addView(toolbar,LinearLayout.LayoutParams(-1,d(44)))
        }

        if(toolsOpen) {
            val panel=LinearLayout(this).apply {
                orientation=LinearLayout.VERTICAL
                setPadding(d(8),d(8),d(8),d(8))
                background=KeyPopGlass.background(this@AetherImeService,s)
            }
            panel.addView(TextView(this).apply {
                text=listOf("EVERYDAY TOOLS","EDIT & CONTROL","ADVANCED TOOLS")[toolsPage]
                textSize=11f
                setTextColor(s.ink)
                setPadding(d(8),d(3),0,d(7))
            })
            fun grid(actions:List<Pair<String,()->Unit>>) {
                actions.chunked(2).forEach { pair->
                    val row=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL}
                    pair.forEach { (title,action)->
                        val tile=chip(title,onTap=action).apply {
                            contentDescription=title
                            textSize=15f
                        }
                        row.addView(tile,LinearLayout.LayoutParams(0,d(55),1f).apply {
                            leftMargin=d(4)
                            rightMargin=d(4)
                            topMargin=d(4)
                            bottomMargin=d(4)
                        })
                    }
                    if(pair.size==1)row.addView(View(this),
                        LinearLayout.LayoutParams(0,d(55),1f))
                    panel.addView(row)
                }
            }
            when(toolsPage) {
                0 -> grid(listOf(
                    "☺  Emoji" to {
                        toolsOpen=false
                        if(!secure && !isNumeric) {
                            emojiOpen=true;clipsOpen=false;writingOpen=false;translatorOpen=false
                        }
                        draw()
                    },
                    "▣  Autofill" to {
                        toolsOpen=false
                        startActivity(Intent(this,MainActivity::class.java)
                            .putExtra("openSection","security")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        draw()
                    },
                    "▤  Clipboard" to {
                        toolsOpen=false
                        if(!secure) {
                            clipsOpen=true;emojiOpen=false;writingOpen=false;translatorOpen=false
                        }
                        draw()
                    },
                    "⬚  Floating" to {
                        prefs.edit().putBoolean("floating",!prefs.getBoolean("floating",false)).apply()
                        toolsOpen=false;draw()
                    }
                ))
                1 -> grid(listOf(
                    "↔  One-handed" to {
                        prefs.edit().putInt("handMode",(prefs.getInt("handMode",0)+1)%3).apply()
                        toolsOpen=false;draw()
                    },
                    "↕  Text editing" to {
                        cursorTools=true;toolsOpen=false;draw()
                    },
                    "⤴  Share KeyPop" to {
                        val share=Intent(Intent.ACTION_SEND).apply {
                            type="text/plain"
                            putExtra(Intent.EXTRA_TEXT,
                                "KeyPop Keyboard: https://github.com/amjadrajamemon/KeyPop")
                        }
                        startActivity(Intent.createChooser(share,"Share KeyPop")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                    "⬚  Resize" to {resizeOpen=true;toolsOpen=false;draw()},
                    "↶  Undo" to {
                        val input=currentInputConnection
                        val time=SystemClock.uptimeMillis()
                        val down=KeyEvent(time,time,KeyEvent.ACTION_DOWN,
                            KeyEvent.KEYCODE_Z,0,KeyEvent.META_CTRL_ON)
                        val accepted=input?.sendKeyEvent(down)?:false
                        input?.sendKeyEvent(KeyEvent.changeAction(down,KeyEvent.ACTION_UP))
                        if(!accepted)Toast.makeText(this,
                            "Undo is not available in this text field",Toast.LENGTH_SHORT).show()
                        toolsOpen=false;draw()
                    },
                    "🌐  Next language" to {toolsOpen=false;cycleLanguage()}
                ))
                else -> grid(listOf(
                    "✦  Writing" to {
                        toolsOpen=false
                        if(!secure&&!isNumeric) {
                            writingOpen=true;translatorOpen=false;emojiOpen=false;clipsOpen=false
                            writingSource=currentInputConnection?.getSelectedText(0)?.toString()
                                ?:currentInputConnection?.getTextBeforeCursor(800,0)?.toString().orEmpty()
                            writingPreview="";writingMode="Formal"
                        }
                        draw()
                    },
                    "⇄  Translate" to {
                        toolsOpen=false
                        if(!secure&&!isNumeric) {
                            translatorOpen=true;writingOpen=false;emojiOpen=false;clipsOpen=false
                            translateSource=editorTranslationText()
                            translateSourceChoice=-1
                            translateResult="";translationStatus=""
                            googleEasyMode=false
                            val detected=KeyPopTextLanguage.detect(translateSource,language)
                            translateTarget=prefs.getInt("autoTarget",if(detected==2)0 else 2).coerceIn(0,2)
                            if(translateTarget==detected)translateTarget=if(detected==2)0 else 2
                        }
                        draw()
                        if(translatorOpen) scheduleLiveTranslation(80)
                    },
                    "🎙 Voice" to {
                        voiceTargetPackage=currentInputEditorInfo?.packageName.orEmpty()
                        pendingSpeech=null;pendingSpeechSend=false
                        val code=when(language){1->"ur-PK";2->"sd-PK";else->"en-US"}
                        startActivity(Intent(this,KeyPopVoiceActivity::class.java)
                            .putExtra("lang",code)
                            .putExtra("target",prefs.getInt("autoTarget",if(language==0)1 else 0))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                    "◈  Themes" to {
                        startActivity(Intent(this,MainActivity::class.java)
                            .putExtra("openSection","themes")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                    "▤  GIF & Media" to {
                        startActivity(Intent(this,KeyPopMediaStudioActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                    "⌨  Layouts" to {
                        startActivity(Intent(this,MainActivity::class.java)
                            .putExtra("openSection","layouts")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                    "⇄  Symbols" to {
                        symbols=!symbols;extended=false;toolsOpen=false;draw()
                    },
                    "✳  Extra keys" to {
                        extended=!extended;toolsOpen=false;draw()
                    }
                ))
            }
            panel.addView(View(this),LinearLayout.LayoutParams(-1,0,1f))
            val pagination=LinearLayout(this).apply {
                orientation=LinearLayout.HORIZONTAL
                gravity=Gravity.CENTER
            }
            pagination.addView(chip("‹") {
                toolsPage=(toolsPage+2)%3;draw()
            }.apply {contentDescription="Previous tool page"},
                LinearLayout.LayoutParams(d(56),d(37)))
            pagination.addView(chip((0..2).joinToString("  "){
                if(it==toolsPage)"●" else "○"
            }) {toolsPage=(toolsPage+1)%3;draw()
            }.apply {contentDescription="Tool page "+(toolsPage+1)+" of 3"},
                LinearLayout.LayoutParams(d(112),d(37)))
            pagination.addView(chip("›") {
                toolsPage=(toolsPage+1)%3;draw()
            }.apply {contentDescription="Next tool page"},
                LinearLayout.LayoutParams(d(56),d(37)))
            panel.addView(pagination)
            root.addView(panel,LinearLayout.LayoutParams(-1,
                d(if(toolsPage==0)276 else if(toolsPage==1)300 else 345)))
            // Gboard-style tools replace the letter area rather than stack above it.
            return
        }
        if(resizeOpen)showResizePanel(root)
        suggestionBar=null
        if(clipsOpen && !secure) { drawClips(root); return }
        if(writingOpen && !secure && !isNumeric) showWritingPanel(root)
        if(translatorOpen && !secure && !isNumeric) showTranslator(root)
        if(emojiOpen && !secure && !isNumeric) {
            showEmojiPanel(root)
            if(!emojiSearch)return
        }
        if(!secure && !isNumeric && !symbols && prefs.getBoolean("suggestions",true) && !emojiOpen) {
            val bar=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
            root.addView(bar,LinearLayout.LayoutParams(-1,d(if(materialToolbar)43 else 35)))
            suggestionBar=bar
            refreshSuggestions()
        }
        if(cursorTools) {
            val tools=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
            fun editChip(label:String,action:()->Unit) {
                tools.addView(chip(label) { action() }, LinearLayout.LayoutParams(0,d(36),1f).apply {
                    leftMargin=d(2);rightMargin=d(2)
                })
            }
            fun arrow(code:Int) {
                currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,code))
                currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP,code))
            }
            editChip("←") { arrow(KeyEvent.KEYCODE_DPAD_LEFT) }
            editChip("→") { arrow(KeyEvent.KEYCODE_DPAD_RIGHT) }
            editChip("Select all") { currentInputConnection?.performContextMenuAction(android.R.id.selectAll) }
            if(!secure) {
                editChip("Copy") { currentInputConnection?.performContextMenuAction(android.R.id.copy) }
                editChip("Paste") { currentInputConnection?.performContextMenuAction(android.R.id.paste) }
            }
            root.addView(tools)
        }
        val title=TextView(this).apply {
            text=if(symbols) "SYMBOLS   •   TAP ABC TO RETURN"
                 else "✧  "+preset.name.uppercase()+"  •  "+languages[language]
            textSize=9.5f; letterSpacing=0.07f
            setTextColor(s.accent)
            gravity=Gravity.CENTER_VERTICAL
            setPadding(d(10),0,0,0)
        }
        if(preset.id!=KeyboardPresetCatalog.GESTURE &&
            preset.id!=KeyboardPresetCatalog.MATERIAL && !resizeOpen)
            root.addView(title,LinearLayout.LayoutParams(-1,d(20)))
        val rows=if(isNumeric) listOf(listOf("1","2","3"),listOf("4","5","6"),
            listOf("7","8","9"),listOf(".","0","⌫"))
            else if(emojiSearch)KeyPopLayouts.active(this,0)
            else if(symbols) { if(extended) extraSymbols else symbolRows }
            else if(language!=0 && prefs.getBoolean("romanMode",false))
                KeyPopLayouts.active(this,0)
            else if(extended && language==1)urduExtended
            else if(extended && language==2)sindhiExtended
            else KeyPopLayouts.active(this,language)
        val keyHeight=prefs.getInt("height",51).coerceIn(36,90)
        // Dedicated 0–9 row exactly once, above the alphabetic rows.
        if(KeyPopRowParity.hasNumberRow(prefs.getBoolean("numberRow",true),symbols,numeric(currentInputEditorInfo)))
            addRow(root, KeyPopRowParity.digits,keyHeight-5)
        rows.forEachIndexed { i, row ->
            val keys=when {
                i==rows.lastIndex && !symbols && !isNumeric -> listOf("⇧")+row+listOf("⌫")
                i==rows.lastIndex && symbols -> row+listOf("⌫")
                else -> row
            }
            val customRow=if(!isNumeric && !symbols && !extended && !emojiSearch)i else -1
            val customLanguage=if(language!=0 && prefs.getBoolean("romanMode",false))0 else language
            addRow(root,keys,keyHeight,layoutRow=customRow,layoutLanguage=customLanguage)
        }
        if(isNumeric) addRow(root,listOf("-","@","↵"),keyHeight, true)
        else addRow(root,KeyboardPresetCatalog.foot(preset.id,symbols),keyHeight,true)
    }
    private fun addRow(parent:LinearLayout,labels:List<String>,height:Int,footer:Boolean=false,
                       layoutRow:Int=-1,layoutLanguage:Int=language) {
        val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER }
        val preset=KeyboardPresetCatalog.selected(this)
        val gap=prefs.getInt("spacing",4).coerceIn(1,10)
        val hand=prefs.getInt("handMode",0)
        val offset=if(preset.id==KeyboardPresetCatalog.FACETED && layoutRow>=0 && layoutRow%2==1)d(14) else 0
        when(hand) {
            1 -> row.setPadding(offset,0,d(72),0)
            2 -> row.setPadding(d(72),0,offset,0)
            else -> if(offset>0) row.setPadding(offset,0,0,0)
        }
        val split=prefs.getBoolean("splitKeyboard",false) && resources.configuration.smallestScreenWidthDp>=600
        labels.forEachIndexed { index,label ->
            if(split && index==labels.size/2) row.addView(View(this),
                LinearLayout.LayoutParams(d(28),d(height)))
            val show=when {
                label=="SPACE" -> if(language==0) "English" else languages[language]
                label=="☺," -> "☺"
                label=="↵" && prefs.getBoolean("autoTranslate",false) &&
                    !sensitive(currentInputEditorInfo) -> "⇄ Send"
                shift && (language==0 || prefs.getBoolean("romanMode",false)) &&
                    label.length==1 && label[0].isLetter() -> label.uppercase(Locale.ROOT)
                else -> label
            }
            val weight=KeyboardPresetCatalog.keyWeight(preset.id,label)
            val heavy=label in listOf("↵","⇧") || (label=="SPACE" && palette().shape==3)
            val altLanguage=if(language!=0 && prefs.getBoolean("romanMode",false))0 else language
            val material=preset.id==KeyboardPresetCatalog.MATERIAL && !symbols
            val variants=KeyPopRowParity.alternates(
                KeyPopMaterialHints.candidates(label,altLanguage,material),
                label,KeyPopRowParity.hasNumberRow(
                    prefs.getBoolean("numberRow",true),symbols,numeric(currentInputEditorInfo)))
            val actualHint=if(prefs.getBoolean("hints",true))variants.firstOrNull() else null
            // The digit row is a single large digit per key: alternate numbers
            // remain accessible on hold, never as a second visible caption.
            val letterHint=label.length==1 && label[0].isLetter() &&
                actualHint!=null && actualHint!=label
            val caption=if(!material && !symbols && letterHint)
                show+"\n"+actualHint else show
            val button=makeKey(caption,heavy,weight) { press(label) }
            if(material && label.length==1 && label[0].isLetter() &&
                actualHint!=null && actualHint!=label && button is KeyPopHintKey) {
                button.hintText=actualHint
                button.hintColor=palette().ink
            }
            if(label=="☺,")button.setOnLongClickListener{
                currentInputConnection?.commitText(",",1)
                refreshSuggestions()
                true
            }
            if(label=="⌫")installRepeatingBackspace(button)
            val glidable=!symbols && language==0 && label.length==1 &&
                label[0].isLetter() && prefs.getBoolean("glide",false)
            if(variants.isNotEmpty() || glidable)
                installCharacterGesture(button,label,variants,glidable)
            button.contentDescription=when(label) {
                "⌫" -> "Backspace. Hold to delete faster, release to stop"
                "⇧" -> "Shift. Double tap to lock uppercase"
                "SPACE" -> "Spacebar. Hold to switch languages or slide to move cursor"
                "↵" -> "Enter or app action"
                "🌐" -> "Switch typing language"
                "?123" -> "Switch to symbols and numbers"
                "ABC" -> "Switch to letters"
                else -> if(label.length==1) "Type "+label else label
            }
            button.isFocusable=true
            if(label=="SPACE" && prefs.getBoolean("spaceCursor",true))
                installSpaceGestures(button)
            keyViews.add(button to label)
            val indexInLayout=if(layoutRow==2 && label !in setOf("⇧","⌫"))index-1
                else if(label in setOf("⇧","⌫"))-1 else index
            val widthFactor=if(layoutRow>=0 && indexInLayout>=0)
                KeyPopKeyGeometry.width(this,layoutLanguage,layoutRow,indexInLayout)/100f else 1f
            row.addView(button,LinearLayout.LayoutParams(0,d(height),weight*widthFactor).apply {
                leftMargin=d(gap/2); rightMargin=d(gap/2); topMargin=d(2); bottomMargin=d(2)
            })
        }
        parent.addView(row)
        renderedRows.add(row to (height-prefs.getInt("height",51).coerceIn(36,90)))
    }
    /** Slide on Space to move the caret; long press for an explicit language menu. */
    /** Gboard-like deletion: tap removes one character, hold repeats and accelerates.
     * Releasing/cancelling stops immediately, including in translation mode. */
    private fun stopBackspaceRepeating() {
        repeatBackspace?.let{repeatHandler.removeCallbacks(it)}
        repeatBackspace=null
        didRepeatBackspace=false
    }
    private fun installRepeatingBackspace(button:Button) {
        button.setOnTouchListener { _,event->
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    stopBackspaceRepeating()
                    button.isPressed=true
                    val ticker=object:Runnable {
                        var repeats=0
                        override fun run() {
                            if(!button.isAttachedToWindow)return
                            didRepeatBackspace=true
                            currentInputConnection?.deleteSurroundingTextInCodePoints(1,0)
                            repeats++
                            // Avoid inflating suggestion views for every deleted character.
                            if(repeats%5==0)refreshSuggestions()
                            repeatHandler.postDelayed(this,when {
                                repeats>34 -> 25L
                                repeats>14 -> 40L
                                else -> 75L
                            })
                        }
                    }
                    repeatBackspace=ticker
                    repeatHandler.postDelayed(ticker,330L)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    button.isPressed=false
                    val repeated=didRepeatBackspace
                    stopBackspaceRepeating()
                    if(!repeated)press("⌫")
                    else {refreshSuggestions();scheduleLiveTranslation(270)}
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    button.isPressed=false
                    stopBackspaceRepeating()
                    true
                }
                else->true
            }
        }
    }

    private fun installSpaceGestures(button:Button) {
        var firstX=0f
        var lastX=0f
        var downTime=0L
        var moved=false
        fun languageMenu() {
            val menu=android.widget.PopupMenu(this,button)
            languages.forEachIndexed{index,label->
                menu.menu.add(label).setOnMenuItemClickListener {
                    language=index
                    prefs.edit().putInt("language",index).apply()
                    shift=false;capsLock=false;symbols=false;extended=false;draw()
                    true
                }
            }
            menu.show()
        }
        button.setOnTouchListener { _,event ->
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    firstX=event.rawX;lastX=event.rawX
                    downTime=SystemClock.uptimeMillis()
                    moved=false;true
                }
                MotionEvent.ACTION_MOVE -> {
                    if(kotlin.math.abs(event.rawX-firstX)>d(14))moved=true
                    if(moved) {
                        val difference=event.rawX-lastX
                        if(kotlin.math.abs(difference)>=d(15)) {
                            val code=if(difference>0)KeyEvent.KEYCODE_DPAD_RIGHT
                                else KeyEvent.KEYCODE_DPAD_LEFT
                            val times=(kotlin.math.abs(difference)/d(15)).toInt().coerceIn(1,8)
                            repeat(times) {
                                currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,code))
                                currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP,code))
                            }
                            lastX=event.rawX
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if(!moved && SystemClock.uptimeMillis()-downTime>=500) languageMenu()
                    else if(!moved)press("SPACE")
                    true
                }
                MotionEvent.ACTION_CANCEL ->true
                else->false
            }
        }
    }

    /** Shows alternates while held and permits sliding without releasing the key. */
    private fun installCharacterGesture(button:Button,label:String,variants:List<String>,
                                        glideEnabled:Boolean) {
        var downX=0f;var downY=0f
        var canceled=false;var popup=false;var gliding=false
        val trail=ArrayList<String>()
        val open=Runnable {
            if(!canceled && !gliding && variants.isNotEmpty() && button.isAttachedToWindow) {
                characterStrip?.show(button,variants,palette().accent,palette().ink,palette().surface)
                popup=characterStrip?.isShowing==true
                if(popup)tapFeedback(button)
            }
        }
        button.setOnTouchListener { _,event->
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    canceled=false;popup=false;gliding=false
                    downX=event.rawX;downY=event.rawY
                    trail.clear();trail.add(label)
                    button.isPressed=true
                    button.postDelayed(open,390L)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if(popup) characterStrip?.slide(event.rawX)
                    else {
                        val delta=kotlin.math.abs(event.rawX-downX)+
                            kotlin.math.abs(event.rawY-downY)
                        if(delta>d(17)) {
                            button.removeCallbacks(open)
                            if(glideEnabled)gliding=true
                        }
                        if(gliding) {
                            val rect=Rect()
                            for((key,letter) in keyViews) {
                                if(letter.length==1 && letter[0].isLetter()
                                    && key.getGlobalVisibleRect(rect)
                                    && rect.contains(event.rawX.toInt(),event.rawY.toInt())) {
                                    if(trail.lastOrNull()!=letter)trail.add(letter)
                                    break
                                }
                            }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    canceled=true;button.isPressed=false
                    button.removeCallbacks(open)
                    val selected=if(popup)characterStrip?.chosen() else null
                    characterStrip?.dismiss()
                    if(selected!=null) {
                        currentInputConnection?.commitText(selected,1)
                        refreshSuggestions()
                    } else if(gliding && trail.size>=3) {
                        val suggested=lexicon.glide(0,trail)
                        if(suggested!=null) {
                            currentInputConnection?.commitText(suggested+" ",1)
                            lexicon.learn(0,suggested);refreshSuggestions()
                        } else press(label)
                    } else press(label)
                    popup=false
                    true
                }
                MotionEvent.ACTION_CANCEL ->{
                    canceled=true;button.isPressed=false
                    button.removeCallbacks(open);characterStrip?.dismiss()
                    popup=false;true
                }
                else ->false
            }
        }
    }
    private fun translateBeforeSend() {
        val input=currentInputConnection ?:return
        val source=input.getTextBeforeCursor(1500,0)?.toString().orEmpty()
        if(source.isBlank())return
        val target=prefs.getInt("autoTarget",if(language==0)1 else 0).coerceIn(0,2)
        val detected=KeyPopTextLanguage.detect(source,language)
        val sourceLanguage=codes[detected]
        val targetLanguage=codes[target]
        if(sourceLanguage==targetLanguage) {
            Toast.makeText(this,"Select a different translation target in Settings",
                Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this,"Translating message…",Toast.LENGTH_SHORT).show()
        KeyPopTranslator.translate(this,sourceLanguage,targetLanguage,source,
            prefs.getBoolean("remoteTranslate",false),prefs.getString("translateEndpoint","").orEmpty()) { result ->
            result.onSuccess { translated ->
                val current=currentInputConnection ?:return@onSuccess
                if(sensitive(currentInputEditorInfo))return@onSuccess
                val stillCurrent=current.getTextBeforeCursor(source.length,0)?.toString().orEmpty()
                if(stillCurrent!=source) {
                    Toast.makeText(this,"Message changed; translation not inserted",Toast.LENGTH_SHORT).show()
                    return@onSuccess
                }
                current.beginBatchEdit()
                current.deleteSurroundingText(source.length,0)
                current.commitText(translated,1)
                current.endBatchEdit()
                val action=(currentInputEditorInfo?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
                if(action==EditorInfo.IME_ACTION_SEND) current.performEditorAction(action)
                else Toast.makeText(this,"Translated and inserted. Tap Send in the app.",Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(this,it.message ?: "Translation unavailable; original text retained",Toast.LENGTH_LONG).show()
            }
        }
    }
    private fun showEmojiPanel(root:LinearLayout) {
        val skin=palette()
        val top=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        top.addView(chip(if(emojiSearch)"🔎 "+emojiQuery+"_" else "🔎 Search emoji") {
            emojiSearch=!emojiSearch
            if(!emojiSearch)emojiQuery=""
            draw()
        },LinearLayout.LayoutParams(0,d(37),2f))
        top.addView(chip("× Close") {
            emojiOpen=false;emojiSearch=false;emojiQuery="";draw()
        },LinearLayout.LayoutParams(0,d(37),1f))
        root.addView(top)
        val tabs=HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false}
        val strip=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        KeyPopEmoji.categories.forEach { cat ->
            strip.addView(chip(if(cat==emojiCategory)"● "+cat else cat) {
                emojiCategory=cat;draw()
            },LinearLayout.LayoutParams(d(if(cat.length>8)99 else 80),d(34)).apply{rightMargin=d(3)})
        }
        tabs.addView(strip);root.addView(tabs)
        val matches=if(emojiSearch && emojiQuery.isNotBlank())KeyPopEmoji.search(emojiQuery)
            else KeyPopEmoji.inCategory(this,emojiCategory,"")
        val list=matches.take(if(emojiSearch)16 else 64)
        val grid=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        if(list.isEmpty())grid.addView(TextView(this).apply{
            text="No matching emoji. Try another word."
            setTextColor(skin.ink);setPadding(d(9),d(13),d(9),d(13))
        })
        list.chunked(8).forEach { chunk->
            val line=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
            chunk.forEach { entry->
                val button=chip(entry.symbol) {
                    if(!sensitive(currentInputEditorInfo)) {
                        currentInputConnection?.commitText(entry.symbol,1)
                        KeyPopEmoji.record(this,entry.symbol)
                    }
                }.apply {
                    textSize=21f;contentDescription=entry.keywords
                    setOnLongClickListener {
                        KeyPopEmoji.toggleFavorite(this@AetherImeService,entry.symbol)
                        Toast.makeText(this@AetherImeService,"Favorite updated",Toast.LENGTH_SHORT).show()
                        draw();true
                    }
                }
                line.addView(button,LinearLayout.LayoutParams(0,d(43),1f).apply{
                    leftMargin=d(1);rightMargin=d(1);bottomMargin=d(2)
                })
            }
            repeat(8-chunk.size){line.addView(View(this),LinearLayout.LayoutParams(0,d(43),1f))}
            grid.addView(line)
        }
        root.addView(ScrollView(this).apply{addView(grid)},
            LinearLayout.LayoutParams(-1,d(if(emojiSearch)93 else 176)))
        if(emojiSearch)root.addView(TextView(this).apply{
            text="Type emoji name below. Enter exits search."
            textSize=10f;setTextColor(skin.ink);setPadding(d(5),d(3),d(5),d(3))
        })
    }

    /** One live prediction surface, sharing the Gboard-style toolbar slot. */
    private fun refreshSuggestions() {
        val bar=suggestionBar ?:return
        val input=currentInputConnection ?:return
        if(sensitive(currentInputEditorInfo) || numeric(currentInputEditorInfo)) {
            bar.removeAllViews()
            bar.visibility=View.GONE
            materialTopBar?.visibility=View.VISIBLE
            return
        }
        val before=input.getTextBeforeCursor(140,0)?.toString().orEmpty()
        val word=Regex("[\\p{L}]+$").find(before)?.value.orEmpty()
        val allowNext=prefs.getBoolean("predictNext",true)
        val showPredictions=word.isNotEmpty() || (allowNext && before.isNotBlank())
        val key="$language|$translatorOpen|$before|$allowNext"
        if(materialTopBar!=null)
            materialTopBar?.visibility=if(showPredictions)View.GONE else View.VISIBLE
        bar.visibility=if(showPredictions)View.VISIBLE else View.GONE
        if(lastSuggestionContext==key)return
        lastSuggestionContext=key
        if(!showPredictions) {bar.removeAllViews();return}
        val predLang=when {
            translatorOpen && translateSourceChoice in 0..2 -> translateSourceChoice
            translatorOpen && word.isNotBlank() -> KeyPopTextLanguage.detect(word,language)
            language!=0 && prefs.getBoolean("romanMode",false) -> 0
            else -> language
        }
        val without=before.removeSuffix(word).trimEnd()
        val previous=Regex("[\\p{L}]+$").find(without)?.value.orEmpty()
        val values=if(word.isEmpty())lexicon.nextWords(predLang,previous)
            else lexicon.suggestions(predLang,word,
                limit=if(prefs.getBoolean("aggressivePredictions",true))8 else 3,
                previous=previous)
        val spelling=if(word.length>=4 && prefs.getBoolean("grammarHints",true))
            lexicon.correction(predLang,word) else null
        val grammar=if(word.isNotEmpty() && prefs.getBoolean("grammarHints",true))
            KeyPopGrammarRules.suggest(before,predLang) else null
        bar.removeAllViews()
        fun chipSuggestion(label:String,action:()->Unit) {
            bar.addView(chip(label,onTap=action),
                LinearLayout.LayoutParams(0,d(if(materialTopBar!=null)41 else 34),1f).apply{
                    leftMargin=d(2);rightMargin=d(2)
                })
        }
        fun completeWord(choice:String) {
            val connection=currentInputConnection ?:return
            val actual=Regex("[\\p{L}]+$").find(
                connection.getTextBeforeCursor(140,0)?.toString().orEmpty())?.value.orEmpty()
            if(actual!=word)return
            connection.beginBatchEdit()
            if(actual.isNotEmpty())connection.deleteSurroundingText(actual.length,0)
            connection.commitText(choice+" ",1)
            connection.endBatchEdit()
            lexicon.learn(predLang,choice)
            refreshSuggestions()
        }
        if(word.isEmpty()) {
            values.distinct().take(3).forEach{candidate->
                chipSuggestion(candidate) {completeWord(candidate)}
            }
            return
        }
        val alternatives=(listOfNotNull(spelling)+values)
            .filterNot{it.equals(word,ignoreCase=true)}
            .distinct().take(2)
        if(grammar!=null) {
            chipSuggestion("✓ "+grammar.label) {
                val connection=currentInputConnection
                val ending=connection?.getTextBeforeCursor(grammar.remove,0)?.toString().orEmpty()
                if(ending.length==grammar.remove && before.endsWith(ending)) {
                    connection?.beginBatchEdit()
                    connection?.deleteSurroundingText(grammar.remove,0)
                    connection?.commitText(grammar.replacement,1)
                    connection?.endBatchEdit()
                    refreshSuggestions()
                }
            }
        } else if(alternatives.isNotEmpty()) {
            val first=alternatives.first()
            chipSuggestion(first) {completeWord(first)}
        }
        // The exact current token stays tappable in the central slot.
        chipSuggestion(word) {completeWord(word)}
        val last=if(grammar!=null) alternatives.firstOrNull()
            else alternatives.getOrNull(1)
        if(last!=null)chipSuggestion(last) {completeWord(last)}
    }
    /** Re-label only changed Latin keys instead of rebuilding the full IME. */
    private fun updateShiftKeys() {
        if(language!=0 && !prefs.getBoolean("romanMode",false))return
        for((key,raw) in keyViews) {
            if(raw.length!=1 || !raw[0].isLetter())continue
            val old=key.text.toString()
            val hint=if(old.contains('\n'))"\n"+old.substringAfter('\n') else ""
            key.text=(if(shift||capsLock)raw.uppercase(Locale.ROOT)
                else raw.lowercase(Locale.ROOT))+hint
        }
    }
    private fun press(value:String) {
        if(emojiOpen && emojiSearch) {
            when(value) {
                "⌫" -> emojiQuery=emojiQuery.dropLast(1)
                "SPACE" -> emojiQuery+=" "
                "↵","?123","ABC" -> {emojiSearch=false;emojiQuery=""}
                "⇧" -> {shift=!shift}
                "🌐" -> {emojiSearch=false;emojiQuery="";cycleLanguage();return}
                else -> if(value.length==1 && value[0].isLetterOrDigit())
                    emojiQuery+=value.lowercase(Locale.ROOT)
            }
            draw();return
        }
        when(value) {
            "⌫" -> currentInputConnection?.deleteSurroundingTextInCodePoints(1,0)
            "⇧" -> {
                if(language==0 || (prefs.getBoolean("romanMode",false) && language!=0)) {
                    val now=System.currentTimeMillis()
                    if(now-lastShiftPress<420) { capsLock=!capsLock;shift=capsLock }
                    else if(capsLock) {capsLock=false;shift=false}
                    else shift=!shift
                    lastShiftPress=now
                }
                updateShiftKeys()
            }
            "SPACE" -> {
                if(!sensitive(currentInputEditorInfo)) {
                    if(language!=0 && prefs.getBoolean("romanMode",false)) {
                        val roman=currentInputConnection?.getTextBeforeCursor(60,0)?.toString()
                            .orEmpty()
                        val word=Regex("[A-Za-z]+$").find(roman)?.value
                        if(word!=null) {
                            val converted=KeyPopRoman.convert(language,word)
                            if(converted!=word) {
                                currentInputConnection?.beginBatchEdit()
                                currentInputConnection?.deleteSurroundingText(word.length,0)
                                currentInputConnection?.commitText(converted,1)
                                currentInputConnection?.endBatchEdit()
                            }
                        }
                    }
                    if(KeyPopTypingRules.autoPunctuationEnabled(
                            prefs.getBoolean("autoPunctuate",true),
                            currentInputEditorInfo?.inputType?:0) &&
                        prefs.getBoolean("doubleSpace",true)) {
                        val last=currentInputConnection?.getTextBeforeCursor(2,0)?.toString().orEmpty()
                        if(last.length==2 && last.last()==' ' && last.first().isLetter()) {
                            currentInputConnection?.deleteSurroundingText(1,0)
                            currentInputConnection?.commitText(". ",1)
                            refreshSuggestions()
                            scheduleLiveTranslation(260)
                            return
                        }
                    }
                    val before=currentInputConnection?.getTextBeforeCursor(48,0)?.toString().orEmpty()
                    val word=Regex("[\\p{L}]+$").find(before)?.value.orEmpty()
                    if(word.isNotBlank()) {
                        val predLang=if(language!=0 &&
                            prefs.getBoolean("romanMode",false))0 else language
                        val inputType=currentInputEditorInfo?.inputType?:0
                        val corrected=if(KeyPopTypingRules.autocorrectEnabled(
                                prefs.getBoolean("autoCorrect",true),inputType))
                            lexicon.correction(predLang,word) else null
                        // Grammar hints are suggestions, not forced edits. Optional
                        // auto-grammar has its own setting and respects autocorrect off.
                        val grammar=if(prefs.getBoolean("autoGrammarApply",false) &&
                            KeyPopTypingRules.autocorrectEnabled(
                                prefs.getBoolean("autoCorrect",true),inputType) &&
                            prefs.getBoolean("grammarHints",true))
                            KeyPopGrammarRules.suggest(before,predLang) else null
                        if(grammar!=null && grammar.remove<=before.length) {
                            currentInputConnection?.deleteSurroundingText(grammar.remove,0)
                            currentInputConnection?.commitText(grammar.replacement,1)
                            lexicon.learn(predLang,grammar.replacement.substringAfterLast(' '))
                        } else if(corrected!=null) {
                            currentInputConnection?.deleteSurroundingText(word.length,0)
                            val final=KeyPopTypingRules.preservesCase(word,corrected)
                            currentInputConnection?.commitText(final,1)
                            lexicon.learn(predLang,final)
                        } else lexicon.learn(predLang,word)
                    }
                }
                currentInputConnection?.commitText(" ",1)
                refreshSuggestions()
            }
            "🌐" -> cycleLanguage()
            "☺,","☺" ->{
                if(!sensitive(currentInputEditorInfo)){
                    emojiOpen=true;clipsOpen=false;translatorOpen=false
                    draw()
                } else currentInputConnection?.commitText(",",1)
            }
            "?123" -> { symbols=true;draw() }
            "ABC" -> { symbols=false;draw() }
            "↵" -> {
                if(prefs.getBoolean("autoTranslate",false) &&
                    !sensitive(currentInputEditorInfo) && !numeric(currentInputEditorInfo)) {
                    translateBeforeSend()
                    return
                }
                val action=(currentInputEditorInfo?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
                if(action!=EditorInfo.IME_ACTION_NONE && action!=EditorInfo.IME_ACTION_UNSPECIFIED)
                    currentInputConnection?.performEditorAction(action)
                else currentInputConnection?.commitText("\n",1)
            }
            else -> {
                val prior=currentInputConnection?.getTextBeforeCursor(120,0)?.toString().orEmpty()
                val sentenceStart=prior.isBlank() || prior.trimEnd().lastOrNull() in listOf('.','!','?','\n')
                val latinMode=language==0 || prefs.getBoolean("romanMode",false)
                val capital=shift || (latinMode && prefs.getBoolean("autoCap",true) && sentenceStart)
                val variation=(currentInputEditorInfo?.inputType ?: 0) and
                    InputType.TYPE_MASK_VARIATION
                val noAutoSpace=variation in setOf(InputType.TYPE_TEXT_VARIATION_URI,
                    InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                    InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)
                val punct=value in setOf(",",".","!","?","،","؟","۔")
                val doAutoPunct=punct && !noAutoSpace &&
                    KeyPopTypingRules.autoPunctuationEnabled(
                        prefs.getBoolean("autoPunctuate",true),
                        currentInputEditorInfo?.inputType?:0) &&
                    prior.trimEnd().lastOrNull()?.isLetterOrDigit()==true
                if(doAutoPunct && prior.endsWith(" "))
                    currentInputConnection?.deleteSurroundingText(1,0)
                currentInputConnection?.commitText(
                    (if(capital && latinMode)value.uppercase(Locale.ROOT) else value)+
                        (if(doAutoPunct)" " else ""),1)
                if(shift && latinMode && !capsLock) {
                    shift=false
                    updateShiftKeys()
                }
                refreshSuggestions()
            }
        }
        scheduleLiveTranslation()
    }
    private fun cycleLanguage() {
        language=(language+1)%3
        prefs.edit().putInt("language",language).apply()
        shift=false;capsLock=false;symbols=false;clipsOpen=false;emojiOpen=false;extended=false;draw()
    }
    private fun showWritingPanel(root:LinearLayout) {
        val skin=palette()
        val panel=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(d(8),d(7),d(8),d(7))
            background=gradient(skin.surface,skin.background,12)
        }
        panel.addView(TextView(this).apply{
            text="PRIVATE WRITING TOOLS  ·  Offline · Rule-based"
            textSize=11f;setTextColor(skin.accent)
        })
        panel.addView(TextView(this).apply{
            text=if(writingSource.isBlank())"Select or type text in the app first."
                else "Original: "+writingSource.take(140)
            textSize=12f;setTextColor(skin.ink)
            setPadding(0,d(4),0,d(5))
        })
        val modes=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        listOf("Formal","Polite","Concise","Punctuate").forEach { mode->
            modes.addView(chip(if(writingMode==mode)"✓ "+mode else mode) {
                writingMode=mode
                writingPreview=KeyPopWritingTools.rewrite(writingSource,mode,language)
                draw()
            },LinearLayout.LayoutParams(0,d(37),1f).apply{rightMargin=d(3)})
        }
        panel.addView(modes)
        if(writingPreview.isBlank() && writingSource.isNotBlank())
            writingPreview=KeyPopWritingTools.rewrite(writingSource,writingMode,language)
        panel.addView(TextView(this).apply{
            text=if(writingPreview.isBlank())"No preview yet" else writingPreview.take(300)
            textSize=13f;setTextColor(skin.ink);setPadding(d(3),d(7),d(3),d(8))
        })
        val buttons=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        fun action(label:String,fn:()->Unit) {
            buttons.addView(chip(label,onTap=fn),
                LinearLayout.LayoutParams(0,d(36),1f).apply{rightMargin=d(3)})
        }
        action("Replace") {
            val input=currentInputConnection
            if(input!=null && writingPreview.isNotBlank() && !sensitive(currentInputEditorInfo)) {
                val selected=input.getSelectedText(0)?.toString()
                val before=input.getTextBeforeCursor(writingSource.length,0)?.toString()
                if(selected==writingSource || before==writingSource) {
                    input.beginBatchEdit()
                    if(selected!=writingSource)input.deleteSurroundingText(writingSource.length,0)
                    input.commitText(writingPreview,1)
                    input.endBatchEdit()
                    writingOpen=false;draw()
                } else Toast.makeText(this,"Text changed: reopen writing tools",Toast.LENGTH_SHORT).show()
            }
        }
        action("Copy") {
            if(writingPreview.isNotBlank())
                clipboard.setPrimaryClip(ClipData.newPlainText("KeyPop rewrite",writingPreview))
        }
        action("Close") {writingOpen=false;draw()}
        panel.addView(buttons)
        if(prefs.getBoolean("smartReplies",true)) {
            panel.addView(TextView(this).apply{
                text="QUICK REPLIES · Tap to insert"
                textSize=10f;setTextColor(skin.accent)
                setPadding(0,d(7),0,d(3))
            })
            val replies=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
            KeyPopTypingIntelligence.contextualReplies(language,writingSource).take(3).forEach { reply ->
                replies.addView(chip(reply.take(24)) {
                    if(!sensitive(currentInputEditorInfo)) {
                        currentInputConnection?.commitText(reply,1)
                        writingOpen=false;draw()
                    }
                },LinearLayout.LayoutParams(0,d(43),1f).apply{rightMargin=d(3)})
            }
            panel.addView(replies)
        }
        root.addView(panel)
    }

    /** Text belongs to the active editor, not KeyPop's Settings Activity. */
    private fun editorTranslationText():String {
        val input=currentInputConnection?:return ""
        return (input.getSelectedText(0)?.toString()?.takeIf{it.isNotBlank()}
            ?:input.getTextBeforeCursor(2400,0)?.toString().orEmpty()).takeLast(1200)
    }

    private fun stopLiveTranslation() {
        liveRevision++
        liveHandler.removeCallbacksAndMessages(null)
        liveSourceView=null;liveResultView=null
    }

    /** Debounce both physical editor updates and KeyPop's own key presses. */
    private fun scheduleLiveTranslation(waitMs:Long=300L,manual:Boolean=false) {
        if(!translatorOpen || (!liveTranslationEnabled && !manual) ||
            sensitive(currentInputEditorInfo) || numeric(currentInputEditorInfo))return
        liveRevision++
        val revision=liveRevision
        liveHandler.removeCallbacksAndMessages(null)
        val debounce=if(googleEasyMode && !manual)maxOf(waitMs,1100L) else waitMs
        liveHandler.postDelayed({
            if(revision!=liveRevision || !translatorOpen ||
                (!liveTranslationEnabled && !manual))return@postDelayed
            val text=editorTranslationText()
            translateSource=text
            liveSourceView?.text=if(text.isBlank())"Type in the active app…"
                else text.takeLast(180)
            translateResult=""
            if(text.isBlank()) {
                translationStatus="Translation appears as you type."
                liveResultView?.text=translationStatus
                return@postDelayed
            }
            val source=if(translateSourceChoice<0)
                KeyPopTextLanguage.detect(text,language) else translateSourceChoice
            if(source==translateTarget) {
                translationStatus="Choose a different target language."
                liveResultView?.text=translationStatus
                return@postDelayed
            }
            if(googleEasyMode && (!KeyPopCloudTranslation.configured(this) ||
                !prefs.getBoolean("googleCloudConsent",false))) {
                translationStatus="Google Cloud requires an API key and consent in Settings."
                liveResultView?.text=translationStatus
                return@postDelayed
            }
            val requestedGoogleMode=googleEasyMode
            translationStatus=if(requestedGoogleMode)"Google Cloud translating…"
                else "Translating offline…"
            liveResultView?.text=translationStatus
            val callback:(Result<String>)->Unit={outcome->
                if(revision==liveRevision && translatorOpen &&
                    googleEasyMode==requestedGoogleMode && editorTranslationText()==text) {
                    outcome.onSuccess{
                        translateResult=it
                        translationStatus=""
                        // Preserve all key and prediction views; no full redraw.
                        liveResultView?.text=it
                    }.onFailure{
                        translationStatus=it.localizedMessage?:"Translation unavailable"
                        liveResultView?.text=translationStatus
                    }
                }
            }
            if(requestedGoogleMode)
                KeyPopCloudTranslation.translate(this,codes[source],codes[translateTarget],text,callback)
            else
                KeyPopTranslator.translate(this,codes[source],codes[translateTarget],text,
                    false,"",callback)
        },debounce)
    }

    private fun showTranslator(root:LinearLayout) {
        val style=palette()
        val panel=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(d(9),d(8),d(9),d(7))
            background=gradient(style.surface,style.background,12)
        }
        val title=TextView(this).apply{
            text="EASY TRANSLATE  ·  LIVE"
            textSize=11f;setTextColor(style.accent)
        }
        panel.addView(title)
        val controls=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        fun small(label:String,weight:Float=1f,onTap:()->Unit){
            controls.addView(chip(label,onTap=onTap).apply {
                textSize=11f;maxLines=1
            },LinearLayout.LayoutParams(0,d(38),weight).apply{rightMargin=d(3)})
        }
        val detected=KeyPopTextLanguage.detect(translateSource,language)
        val from=if(translateSourceChoice<0)detected else translateSourceChoice
        small(if(translateSourceChoice<0)"From: Auto" else "From: "+languages[from],1.15f){
            translateSourceChoice=if(translateSourceChoice<0)
                KeyPopEasyTranslateDirections.nextSource(-1,translateTarget)
            else {
                val next=KeyPopEasyTranslateDirections.nextSource(
                    translateSourceChoice,translateTarget)
                if(next<=translateSourceChoice)-1 else next
            }
            translateResult="";draw();scheduleLiveTranslation(50)
        }
        small("⇄",0.38f){
            val oldFrom=from
            translateSourceChoice=translateTarget
            translateTarget=oldFrom
            translateResult="";draw();scheduleLiveTranslation(50)
        }
        small("To: "+languages[translateTarget],1.15f){
            translateTarget=KeyPopEasyTranslateDirections.nextTarget(translateTarget,from)
            translateResult="";draw();scheduleLiveTranslation(50)
        }
        small("×",0.38f){translatorOpen=false;stopLiveTranslation();draw()}
        panel.addView(controls)
        val modes=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        fun mode(label:String,weight:Float=1f,onTap:()->Unit) {
            modes.addView(chip(label,onTap=onTap).apply{textSize=11f;maxLines=1},
                LinearLayout.LayoutParams(0,d(34),weight).apply{rightMargin=d(3)})
        }
        mode(if(!googleEasyMode)"✓ KeyPop offline" else "KeyPop offline"){
            googleEasyMode=false;translateResult=""
            draw();scheduleLiveTranslation(50)
        }
        mode(if(googleEasyMode)"✓ Google Cloud" else "Google Cloud"){
            googleEasyMode=true;translateResult=""
            draw();scheduleLiveTranslation(50)
        }
        mode(if(liveTranslationEnabled)"● Live" else "○ Live",0.7f) {
            liveTranslationEnabled=!liveTranslationEnabled
            if(!liveTranslationEnabled)stopLiveTranslation()
            draw()
            if(liveTranslationEnabled)scheduleLiveTranslation(60)
        }
        panel.addView(modes)
        val sourceCard=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(d(9),d(7),d(9),d(7))
            background=gradient(style.background,style.surface,9)
        }
        sourceCard.addView(TextView(this).apply{
            text="SOURCE · "+languages[from]
            textSize=10f;setTextColor(style.accent)
        })
        val inputText=TextView(this).apply {
            text=if(translateSource.isBlank())"Type in the active app…"
                else translateSource.takeLast(180)
            textSize=12f;setTextColor(style.ink);maxLines=3
        }
        liveSourceView=inputText
        sourceCard.addView(inputText)
        panel.addView(sourceCard)
        val resultCard=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(d(9),d(7),d(9),d(7))
            background=gradient(style.surface,style.background,9)
        }
        resultCard.addView(TextView(this).apply{
            text="TRANSLATION · "+languages[translateTarget]
            textSize=10f;setTextColor(style.accent)
        })
        val resultView=TextView(this).apply {
            text=if(translateResult.isBlank())
                translationStatus.ifBlank{"Live preview appears here"} else translateResult
            textSize=13f;setTextColor(style.ink);maxLines=4
        }
        liveResultView=resultView
        resultCard.addView(resultView)
        panel.addView(resultCard)
        val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        fun action(label:String,fn:()->Unit) {
            actions.addView(chip(label,onTap=fn).apply{textSize=11f},
                LinearLayout.LayoutParams(0,d(36),1f).apply{rightMargin=d(3)})
        }
        fun replaceInEditor(replace:Boolean):Boolean {
            if(translateResult.isBlank())return false
            val input=currentInputConnection?:return false
            if(replace) {
                val selected=input.getSelectedText(0)?.toString()
                val before=input.getTextBeforeCursor(translateSource.length,0)?.toString()
                if(selected!=translateSource && before!=translateSource) {
                    translationStatus="Editor text changed. Recheck live preview."
                    translateResult="";draw();scheduleLiveTranslation(50)
                    return false
                }
                input.beginBatchEdit()
                if(selected!=translateSource)input.deleteSurroundingText(translateSource.length,0)
                val ok=input.commitText(translateResult,1)
                input.endBatchEdit()
                return ok
            }
            return input.commitText(translateResult,1)
        }
        action("Insert"){if(replaceInEditor(false)){translatorOpen=false;stopLiveTranslation();draw()}}
        action("Replace"){if(replaceInEditor(true)){translatorOpen=false;stopLiveTranslation();draw()}}
        action("Copy"){
            if(translateResult.isNotBlank()) {
                clipboard.setPrimaryClip(ClipData.newPlainText("KeyPop translation",translateResult))
                Toast.makeText(this,"Translation copied",Toast.LENGTH_SHORT).show()
            }
        }
        action(if(liveTranslationEnabled)"Refresh" else "Translate"){
            scheduleLiveTranslation(0,manual=true)
        }
        panel.addView(actions)
        if(googleEasyMode && (!KeyPopCloudTranslation.configured(this) ||
            !prefs.getBoolean("googleCloudConsent",false))) {
            panel.addView(chip("Set up Google Cloud API key ↗"){
                startActivity(Intent(this,MainActivity::class.java)
                    .putExtra("openSection","translation").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },LinearLayout.LayoutParams(-1,d(36)))
        }
        root.addView(panel)
    }
    private fun drawClips(root:LinearLayout) {
        val s=palette()
        val title=TextView(this).apply {
            text="ENCRYPTED CLIPBOARD  ·  NEWEST FIRST  ·  HOLD TO PIN"
            textSize=10f;setTextColor(s.ink);setPadding(d(9),d(10),0,d(9))
        }
        root.addView(title)
        val options=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        options.addView(chip(if(pinnedOnly) "📌 Pinned" else "All clips") {
            pinnedOnly=!pinnedOnly;draw()
        },LinearLayout.LayoutParams(0,d(35),1f))
        options.addView(chip("Search / manage") {
            startActivity(Intent(this,MainActivity::class.java)
                .putExtra("openClips",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        },LinearLayout.LayoutParams(0,d(35),1.7f))
        root.addView(options)
        val items=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        val listed=try{clipStore.search("",pinnedOnly)}catch(_:Exception){
            root.addView(TextView(this).apply{
                text="Clipboard could not be decrypted. Data was not erased."
                setTextColor(s.ink);setPadding(d(10),d(10),d(10),d(10))
            });return
        }
        if(listed.isEmpty()) items.addView(TextView(this).apply {
            text="Nothing copied yet. Your saved text will appear here."
            setTextColor(s.ink);setPadding(d(9),d(20),d(9),d(20))
        })
        listed.take(60).forEach { item ->
            val button=makeKey((if(item.pinned) "📌 " else "")+item.text.take(65)) {
                currentInputConnection?.commitText(item.text,1);clipsOpen=false;draw()
            }
            button.setOnLongClickListener { clipStore.pin(item.text,!item.pinned);draw();true }
            items.addView(button,LinearLayout.LayoutParams(-1,d(46)).apply{bottomMargin=d(5)})
        }
        val savedImages=try{KeyPopImageStore(this).list()}catch(_:Exception){emptyList()}
        val images=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        for(image in savedImages.take(20)) {
            val line=LinearLayout(this).apply{
                orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
            }
            val preview=android.widget.ImageView(this).apply{
                contentDescription="Saved screenshot image preview"
                scaleType=android.widget.ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(0xFF26344B.toInt())
                setPadding(d(1),d(1),d(1),d(1))
            }
            line.addView(preview,LinearLayout.LayoutParams(d(49),d(42)))
            imagePreviewExecutor.submit {
                val bitmap=runCatching{KeyPopImageStore(this).thumbnail(image,110)}.getOrNull()
                if(bitmap!=null)android.os.Handler(android.os.Looper.getMainLooper()).post {
                    if(preview.isAttachedToWindow)preview.setImageBitmap(bitmap)
                    else bitmap.recycle()
                }
            }
            val label=(if(image.pinned)"📌 " else "")+
                (if(image.mime=="image/gif")"🎞 " else "📷 ")+image.label.take(45)
            line.addView(chip(label,2f) {
                val uri=KeyPopImageStore(this).uriForShare(image)
                if(uri==null){Toast.makeText(this,"Image unavailable",Toast.LENGTH_SHORT).show()}
                else {
                    val input=currentInputConnection
                    val content=InputContentInfo(uri,ClipDescription("KeyPop image",arrayOf(image.mime)),null)
                    val inserted=try{
                        input?.commitContent(content,InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,null)
                    }catch(_:Exception){false}
                    if(inserted==true){clipsOpen=false;draw()}
                    else {
                        clipboard.setPrimaryClip(ClipData.newUri(contentResolver,"KeyPop image",uri))
                        val share=Intent(Intent.ACTION_SEND).apply {
                            type=image.mime;putExtra(Intent.EXTRA_STREAM,uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(share,"Share saved image")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            },LinearLayout.LayoutParams(0,d(40),2f))
            line.addView(chip(if(image.pinned)"Unpin" else "Pin") {
                KeyPopImageStore(this).pin(image.id);draw()
            },LinearLayout.LayoutParams(0,d(40),1f))
            line.addView(chip("Delete") {
                KeyPopImageStore(this).delete(image.id);draw()
            },LinearLayout.LayoutParams(0,d(40),1f))
            images.addView(line)
        }
        items.addView(images)
        root.addView(ScrollView(this).apply { addView(items) },LinearLayout.LayoutParams(-1,d(215)))
        root.addView(chip("GIF & Sticker Shelf · Manage / Search") {
            startActivity(Intent(this,KeyPopMediaStudioActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        },LinearLayout.LayoutParams(-1,d(37)))
        root.addView(chip("＋ Import screenshot / image") {
            startActivity(Intent(this,KeyPopImageImportActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        },LinearLayout.LayoutParams(-1,d(37)))
        val actions=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        val back=makeKey("←  Keyboard") { clipsOpen=false;draw() }
        val erase=makeKey("Hold: clear unpinned") {
            Toast.makeText(this,"Hold to remove unpinned clips",Toast.LENGTH_SHORT).show()
        }
        erase.setOnLongClickListener { clipStore.clearUnpinned();draw();true }
        actions.addView(back,LinearLayout.LayoutParams(0,d(43),1f).apply{rightMargin=d(4)})
        actions.addView(erase,LinearLayout.LayoutParams(0,d(43),1f).apply{leftMargin=d(4)})
        root.addView(actions)
    }
}
