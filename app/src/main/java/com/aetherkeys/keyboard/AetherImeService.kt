package com.aetherkeys.keyboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class AetherImeService : InputMethodService() {
    private val prefs by lazy { getSharedPreferences("keypop",Context.MODE_PRIVATE) }
    private val clipStore by lazy { ClipStore(this) }
    private val lexicon by lazy { KeyPopLexicon(this) }
    private var suggestionBar:LinearLayout?=null
    private val keyViews=ArrayList<Pair<Button,String>>()
    private val clipboard by lazy { getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    private val languages=listOf("ENGLISH","اردو","سنڌي")
    private var language=0
    private var shift=false
    private var symbols=false
    private var clipsOpen=false
    private var translatorOpen=false
    private var translateSource=""
    private var translateResult=""
    private var translationStatus=""
    private var translateTarget=0
    private val codes=listOf("en","ur","sd")
    private val speechReceiver=object:BroadcastReceiver() {
        override fun onReceive(context:Context,intent:Intent) {
            if(intent.action==KeyPopVoiceActivity.RESULT_ACTION) {
                val recognized=intent.getStringExtra("text").orEmpty()
                if(recognized.isNotBlank() && !sensitive(currentInputEditorInfo))
                    currentInputConnection?.commitText(recognized+" ",1)
            }
        }
    }
    private var emojiOpen=false
    private var pinnedOnly=false
    private var cursorTools=false
    private var extended=false
    private var capsLock=false
    private var lastShiftPress=0L
    private var current:LinearLayout?=null
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

    override fun onCreate() {
        super.onCreate()
        val filter=IntentFilter(KeyPopVoiceActivity.RESULT_ACTION)
        if(Build.VERSION.SDK_INT>=33)
            registerReceiver(speechReceiver,filter,Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(speechReceiver,filter)
        clipboard.addPrimaryClipChangedListener {
            if (!sensitive(currentInputEditorInfo) && prefs.getBoolean("history",true)) {
                try {
                    val clip=clipboard.primaryClip
                    if(clip!=null && clip.itemCount>0 && clip.description.hasMimeType("text/plain")) {
                        if(Build.VERSION.SDK_INT>=33 &&
                            clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE")==true)
                            return@addPrimaryClipChangedListener
                        clip.getItemAt(0).text?.toString()?.let { clipStore.save(it) }
                    }
                } catch (_:Exception) { /* Never break typing on inaccessible clipboard. */ }
            }
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(speechReceiver) } catch(_:Exception) {}
        super.onDestroy()
    }

    override fun onCreateInputView():View {
        language=prefs.getInt("language",0).coerceIn(0,2)
        current=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        draw()
        return current!!
    }
    override fun onUpdateSelection(oldSelStart:Int,oldSelEnd:Int,newSelStart:Int,newSelEnd:Int,candidatesStart:Int,candidatesEnd:Int) {
        super.onUpdateSelection(oldSelStart,oldSelEnd,newSelStart,newSelEnd,candidatesStart,candidatesEnd)
        refreshSuggestions()
    }

    override fun onStartInputView(info:EditorInfo?,restarting:Boolean) {
        super.onStartInputView(info,restarting)
        clipsOpen=false;emojiOpen=false;cursorTools=false;translatorOpen=false
        shift=false;capsLock=false
        draw()
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
        val bg=if(heavy) gradient(s.accent,s.surface,round,s.accent)
            else gradient(s.key,s.surface,round,if(s.outline) s.accent else null)
        return Button(this).apply {
            text=label
            textSize=if(label.length>3) 12f else prefs.getInt("font",17).coerceIn(12,24).toFloat()
            isAllCaps=false
            setTextColor(if(heavy) Color.WHITE else s.ink)
            typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
            gravity=Gravity.CENTER
            background=bg
            minWidth=0; minimumWidth=0; minHeight=0; minimumHeight=0
            setPadding(0,0,0,0)
            elevation=d(if(s.shape==2) 0 else 2).toFloat()
            setOnClickListener { tapFeedback(this); action() }
        }
    }
    private fun chip(label:String,weight:Float=1f,onTap:()->Unit):Button {
        val s=palette()
        return Button(this).apply {
            text=label; textSize=11f; isAllCaps=false
            setTextColor(s.ink); minWidth=0; minimumWidth=0; minHeight=0; minimumHeight=0
            background=gradient(s.surface,s.background,11,if(s.outline) s.accent else null)
            setPadding(0,0,0,0)
            setOnClickListener { tapFeedback(this); onTap() }
        }
    }
    private fun draw() {
        val root=current ?: return
        root.removeAllViews()
        keyViews.clear()
        val s=palette()
        root.background=KeyPopBackdropDrawable(KeyPopSkins.index(this),s)
        root.setPadding(d(3),d(5),d(3),d(6))
        val top=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        fun topButton(value:String,weight:Float=1f,callback:()->Unit) {
            top.addView(chip(value,weight,callback),LinearLayout.LayoutParams(0,d(36),weight).apply {
                leftMargin=d(2); rightMargin=d(2)
            })
        }
        val secure=sensitive(currentInputEditorInfo)
        val isNumeric=numeric(currentInputEditorInfo)
        topButton("✦  "+languages[language],1.5f) { cycleLanguage() }
        if(secure) topButton("Paste",.75f) {
            val ok=currentInputConnection?.performContextMenuAction(android.R.id.paste) ?: false
            if(!ok) Toast.makeText(this,"This field does not accept keyboard paste",Toast.LENGTH_SHORT).show()
        }
        if(!secure) topButton("▤ Clips",1f) { clipsOpen=!clipsOpen;emojiOpen=false;draw() }
        topButton(if(symbols) "ABC" else "?#@",.75f) {
            symbols=!symbols;extended=false;clipsOpen=false;emojiOpen=false;draw()
        }
        topButton(if(extended) "1/2" else "2/2",.53f) { extended=!extended;draw() }
        if(!secure && !isNumeric) {
            topButton("☺",.50f) { emojiOpen=!emojiOpen;clipsOpen=false;translatorOpen=false;draw() }
            topButton("⇄",.55f) {
                translatorOpen=!translatorOpen;emojiOpen=false;clipsOpen=false
                if(translatorOpen) {
                    translateSource=currentInputConnection?.getSelectedText(0)?.toString()
                        ?: currentInputConnection?.getTextBeforeCursor(1000,0)?.toString().orEmpty()
                    translateResult="";translationStatus=""
                    translateTarget=(language+1)%3
                }
                draw()
            }
            topButton("🎙",.52f) {
                val lang=when(language){1->"ur-PK";2->"sd-PK";else->"en-US"}
                startActivity(Intent(this,KeyPopVoiceActivity::class.java)
                    .putExtra("lang",lang).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        topButton("↤↦",.65f) { cursorTools=!cursorTools;draw() }
        topButton(when(prefs.getInt("handMode",0)) {1->"◧";2->"◨";else->"↔"},.55f) {
            val next=(prefs.getInt("handMode",0)+1)%3
            prefs.edit().putInt("handMode",next).apply();draw()
        }
        topButton("◈",.55f) { prefs.edit().putInt("theme",(KeyPopSkins.index(this)+1)%15).apply();draw() }
        topButton("⚙",.55f) {
            startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        root.addView(top)
        suggestionBar=null
        if(clipsOpen && !secure) { drawClips(root); return }
        if(translatorOpen && !secure && !isNumeric) showTranslator(root)
        if(emojiOpen && !secure && !isNumeric) {
            root.addView(TextView(this).apply {
                text="EMOJI  •  TAP TO INSERT";setTextColor(palette().accent);textSize=11f
                setPadding(d(8),d(9),0,d(9))
            })
            emojiRows.forEach { addRow(root,it,48) }
            return
        }
        if(!secure && !isNumeric && !symbols && prefs.getBoolean("suggestions",true) && !translatorOpen && !emojiOpen) {
            val bar=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
            root.addView(bar,LinearLayout.LayoutParams(-1,d(35)))
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
                 else if(language==0) "✧  KEYPOP  •  TYPE YOUR WAY"
                 else "✧  KEYPOP  •  "+languages[language]
            textSize=9.5f; letterSpacing=0.07f
            setTextColor(s.accent)
            gravity=Gravity.CENTER_VERTICAL
            setPadding(d(10),0,0,0)
        }
        root.addView(title,LinearLayout.LayoutParams(-1,d(25)))
        val rows=if(isNumeric) listOf(listOf("1","2","3"),listOf("4","5","6"),
            listOf("7","8","9"),listOf(".","0","⌫"))
            else if(symbols) { if(extended) extraSymbols else symbolRows }
            else when(language) {
                1-> if(extended) urduExtended else urdu
                2-> if(extended) sindhiExtended else sindhi
                else->english
            }
        val keyHeight=prefs.getInt("height",51).coerceIn(36,90)
        if(!isNumeric && !symbols && prefs.getBoolean("numberRow",true))
            addRow(root, listOf("1","2","3","4","5","6","7","8","9","0"),keyHeight-10)
        rows.forEachIndexed { i, row ->
            val keys=if(i==rows.lastIndex && !symbols && !isNumeric) listOf("⇧")+row+listOf("⌫") else row
            addRow(root,keys,keyHeight)
        }
        if(isNumeric) addRow(root,listOf("-","@","↵"),keyHeight, true)
        else addRow(root,listOf(if(symbols) "ABC" else "?123", "🌐", ",","SPACE",".","↵","⌫"),keyHeight, true)
    }
    private fun addRow(parent:LinearLayout,labels:List<String>,height:Int,footer:Boolean=false) {
        val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER }
        val gap=prefs.getInt("spacing",4).coerceIn(1,10)
        when(prefs.getInt("handMode",0)) {
            1 -> row.setPadding(0,0,d(72),0)
            2 -> row.setPadding(d(72),0,0,0)
        }
        val split=prefs.getBoolean("splitKeyboard",false) && resources.configuration.smallestScreenWidthDp>=600
        labels.forEachIndexed { index,label ->
            if(split && index==labels.size/2) row.addView(View(this),
                LinearLayout.LayoutParams(d(28),d(height)))
            val show=when {
                label=="SPACE" -> if(language==0) "English" else languages[language]
                shift && language==0 && label.length==1 && label[0].isLetter() -> label.uppercase(Locale.ROOT)
                else -> label
            }
            val weight=when(label) {
                "SPACE" -> 4.3f
                "?123","ABC" -> 1.4f
                "🌐" -> 1.2f
                else -> 1f
            }
            val heavy=label in listOf("↵","⇧") || (label=="SPACE" && palette().shape==3)
            val superscripts=listOf("¹","²","³","⁴","⁵","⁶","⁷","⁸","⁹","⁰")
            val hintIndex="qwertyuiop".indexOf(label)
            val caption=if(!symbols && language==0 && hintIndex>=0 && prefs.getBoolean("hints",true))
                show+"\n"+superscripts[hintIndex] else show
            val button=makeKey(caption,heavy,weight) { press(label) }
            if(label=="⌫" && prefs.getBoolean("wordDelete",true)) button.setOnLongClickListener {
                tapFeedback(button)
                val before=currentInputConnection?.getTextBeforeCursor(200,0)?.toString().orEmpty()
                val found=Regex("[\\p{L}\\p{N}]+\\s*$").find(before)
                val count=if(found!=null) before.length-found.range.first else 1
                currentInputConnection?.deleteSurroundingText(count,0)
                true
            } else {
                val extras=alternates(label)
                if(extras.isNotEmpty()) button.setOnLongClickListener {
                    tapFeedback(button)
                    if(extras.size==1) currentInputConnection?.commitText(extras.first(),1)
                    else {
                        val popup=android.widget.PopupMenu(this,button)
                        for(extra in extras) popup.menu.add(extra).setOnMenuItemClickListener {
                            currentInputConnection?.commitText(extra,1);refreshSuggestions();true
                        }
                        popup.show()
                    }
                    true
                }
            }
            keyViews.add(button to label)
            if(language==0 && !symbols && !numeric(currentInputEditorInfo) &&
                prefs.getBoolean("glide",false) && label.length==1 && label[0].isLetter()) {
                installGlide(button,label)
            }
            row.addView(button,LinearLayout.LayoutParams(0,d(height),weight).apply {
                leftMargin=d(gap/2); rightMargin=d(gap/2); topMargin=d(2); bottomMargin=d(2)
            })
        }
        parent.addView(row)
    }
    private fun installGlide(button:Button,label:String) {
        var startX=0f;var startY=0f
        var started=0L;var sliding=false
        val trail=ArrayList<String>()
        button.setOnTouchListener { _,event ->
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX=event.rawX;startY=event.rawY;started=SystemClock.uptimeMillis()
                    sliding=false;trail.clear();trail.add(label);true
                }
                MotionEvent.ACTION_MOVE -> {
                    if(kotlin.math.abs(event.rawX-startX)+kotlin.math.abs(event.rawY-startY)>d(18))
                        sliding=true
                    if(sliding) {
                        val box=Rect()
                        for((key,letter) in keyViews) {
                            if(letter.length==1 && letter[0].isLetter()
                                && key.getGlobalVisibleRect(box)
                                && box.contains(event.rawX.toInt(),event.rawY.toInt())) {
                                if(trail.lastOrNull()!=letter)trail.add(letter)
                                break
                            }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    tapFeedback(button)
                    if(sliding && trail.size>=3) {
                        val guess=lexicon.glide(0,trail)
                        if(guess!=null){
                            currentInputConnection?.commitText(guess+" ",1)
                            lexicon.learn(0,guess);refreshSuggestions()
                        } else press(label)
                    } else if(!sliding && SystemClock.uptimeMillis()-started>500
                        && alternates(label).isNotEmpty()) {
                        val options=alternates(label)
                        val menu=android.widget.PopupMenu(this,button)
                        for(option in options)menu.menu.add(option).setOnMenuItemClickListener {
                            currentInputConnection?.commitText(option,1);true
                        }
                        menu.show()
                    } else press(label)
                    true
                }
                MotionEvent.ACTION_CANCEL ->true
                else->false
            }
        }
    }
    private fun alternates(label:String):List<String> {
        val numeric="qwertyuiop".indexOf(label)
        val accents=mapOf(
            "a" to listOf("@","á","à","â","ä"),
            "e" to listOf("é","è","ê","ë"),
            "i" to listOf("í","ì","î","ï"),
            "o" to listOf("ó","ò","ô","ö"),
            "u" to listOf("ú","ù","û","ü"),
            "s" to listOf("$","ß"),"d" to listOf("#"),"f" to listOf("%"),
            "n" to listOf("ñ"),"c" to listOf("ç"),
            "." to listOf("…"),"," to listOf(";"))
        val scripts=mapOf(
            "ا" to listOf("آ","أ","إ"),"ت" to listOf("ٹ","ٿ","ٽ","ٺ"),
            "د" to listOf("ڈ","ڌ","ڏ","ڊ","ڍ"),"ر" to listOf("ڑ","ڙ"),
            "ن" to listOf("ں","ڻ"),"ک" to listOf("ڪ"),"ڪ" to listOf("ک"),
            "ب" to listOf("ٻ","ڀ"),"ج" to listOf("ڄ","جھ","ڃ"),
            "چ" to listOf("ڇ"),"ف" to listOf("ڦ"),"گ" to listOf("ڳ","گھ"),
            "و" to listOf("ؤ"),"ہ" to listOf("ھ"),"ه" to listOf("ھ"),
            "ی" to listOf("ئ","ے"),"ي" to listOf("ئ"))
        if(numeric>=0 && language==0 && prefs.getBoolean("hints",true)) {
            val digit=if(numeric==9)"0" else (numeric+1).toString()
            return listOf(digit)+accents.getOrDefault(label,emptyList())
        }
        return if(language==0)accents.getOrDefault(label,emptyList())
        else scripts.getOrDefault(label,emptyList())
    }
    private fun refreshSuggestions() {
        val bar=suggestionBar ?:return
        val input=currentInputConnection ?:return
        if(sensitive(currentInputEditorInfo)||numeric(currentInputEditorInfo))return
        val before=input.getTextBeforeCursor(48,0)?.toString().orEmpty()
        val word=Regex("[\\p{L}]+$").find(before)?.value.orEmpty()
        bar.removeAllViews()
        val values=lexicon.suggestions(language,word)
        if(values.isEmpty()) {
            bar.addView(chip("✦ Local suggestions") {},
                LinearLayout.LayoutParams(-1,d(32)))
            return
        }
        for(candidate in values) {
            bar.addView(chip(candidate) {
                val current=input.getTextBeforeCursor(48,0)?.toString().orEmpty()
                val actual=Regex("[\\p{L}]+$").find(current)?.value
                if(actual!=null) {
                    input.beginBatchEdit()
                    input.deleteSurroundingText(actual.length,0)
                    input.commitText(candidate+" ",1)
                    input.endBatchEdit()
                    lexicon.learn(language,candidate)
                    refreshSuggestions()
                }
            },LinearLayout.LayoutParams(0,d(32),1f).apply {
                leftMargin=d(2);rightMargin=d(2)
            })
        }
    }
    private fun press(value:String) {
        when(value) {
            "⌫" -> currentInputConnection?.deleteSurroundingTextInCodePoints(1,0)
            "⇧" -> {
                if(language==0) {
                    val now=System.currentTimeMillis()
                    if(now-lastShiftPress<420) { capsLock=!capsLock;shift=capsLock }
                    else if(capsLock) {capsLock=false;shift=false}
                    else shift=!shift
                    lastShiftPress=now
                }
                draw()
            }
            "SPACE" -> {
                if(!sensitive(currentInputEditorInfo)) {
                    if(prefs.getBoolean("doubleSpace",true)) {
                        val last=currentInputConnection?.getTextBeforeCursor(2,0)?.toString().orEmpty()
                        if(last.length==2 && last.last()==' ' && last.first().isLetter()) {
                            currentInputConnection?.deleteSurroundingText(1,0)
                            currentInputConnection?.commitText(". ",1)
                            refreshSuggestions()
                            return
                        }
                    }
                    val before=currentInputConnection?.getTextBeforeCursor(48,0)?.toString().orEmpty()
                    val word=Regex("[\\p{L}]+$").find(before)?.value.orEmpty()
                    if(word.isNotBlank()) {
                        val corrected=if(prefs.getBoolean("autoCorrect",false))
                            lexicon.correction(language,word) else null
                        if(corrected!=null) {
                            currentInputConnection?.deleteSurroundingText(word.length,0)
                            currentInputConnection?.commitText(corrected,1)
                            lexicon.learn(language,corrected)
                        } else lexicon.learn(language,word)
                    }
                }
                currentInputConnection?.commitText(" ",1)
                refreshSuggestions()
            }
            "🌐" -> cycleLanguage()
            "?123" -> { symbols=true;draw() }
            "ABC" -> { symbols=false;draw() }
            "↵" -> {
                val action=(currentInputEditorInfo?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
                if(action!=EditorInfo.IME_ACTION_NONE && action!=EditorInfo.IME_ACTION_UNSPECIFIED)
                    currentInputConnection?.performEditorAction(action)
                else currentInputConnection?.commitText("\n",1)
            }
            else -> {
                val prior=currentInputConnection?.getTextBeforeCursor(120,0)?.toString().orEmpty()
                val sentenceStart=prior.isBlank() || prior.trimEnd().lastOrNull() in listOf('.','!','?','\n')
                val capital=shift || (language==0 && prefs.getBoolean("autoCap",true) && sentenceStart)
                currentInputConnection?.commitText(
                    if(capital && language==0) value.uppercase(Locale.ROOT) else value,1)
                if(shift && language==0 && !capsLock) { shift=false;draw() }
                else refreshSuggestions()
            }
        }
    }
    private fun cycleLanguage() {
        language=(language+1)%3
        prefs.edit().putInt("language",language).apply()
        shift=false;capsLock=false;symbols=false;clipsOpen=false;emojiOpen=false;extended=false;draw()
    }
    private fun showTranslator(root:LinearLayout) {
        val style=palette()
        val panel=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(d(9),d(8),d(9),d(5))
            background=gradient(style.surface,style.background,12)
        }
        val original=TextView(this).apply {
            text="SOURCE · "+languages[language]+"\n"+
                (if(translateSource.isBlank()) "Select text or type a message first" else translateSource.take(180))
            textSize=11f;setTextColor(style.ink)
        }
        panel.addView(original)
        val resultView=TextView(this).apply {
            text=if(translateResult.isBlank()) translationStatus else translateResult
            textSize=13f;setTextColor(style.ink)
            setPadding(0,d(6),0,d(6))
        }
        val controls=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        fun small(label:String,weight:Float=1f,onTap:()->Unit) {
            controls.addView(chip(label,onTap=onTap),
                LinearLayout.LayoutParams(0,d(38),weight).apply { rightMargin=d(3) })
        }
        small("To "+languages[translateTarget]) {
            translateTarget=(translateTarget+1)%3;translateResult="";translationStatus=""
            draw()
        }
        small("Translate",1.3f) {
            if(translateSource.isBlank()) {
                translationStatus="Select or type a message first";resultView.text=translationStatus
            } else {
                translationStatus="Translating… (first use may download a language model)"
                resultView.text=translationStatus
                KeyPopTranslator.translate(codes[language],codes[translateTarget],translateSource,
                    prefs.getBoolean("remoteTranslate",false),prefs.getString("translateEndpoint","").orEmpty()) { outcome ->
                    outcome.onSuccess { translateResult=it;resultView.text=it;draw() }
                        .onFailure { translationStatus=it.message ?: "Translation unavailable";resultView.text=translationStatus }
                }
            }
        }
        small("×") {translatorOpen=false;draw()}
        panel.addView(controls)
        panel.addView(resultView)
        if(translateResult.isNotBlank()) {
            val actions=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL}
            fun translatedAction(label:String,fn:()->Unit) {
                actions.addView(chip(label,onTap=fn),LinearLayout.LayoutParams(0,d(38),1f).apply{rightMargin=d(3)})
            }
            translatedAction("Copy") {
                clipboard.setPrimaryClip(ClipData.newPlainText("KeyPop translation",translateResult))
            }
            fun insert():Boolean {
                val input=currentInputConnection ?: return false
                val selected=input.getSelectedText(0)
                input.beginBatchEdit()
                if(selected.isNullOrEmpty()) {
                    val previous=input.getTextBeforeCursor(translateSource.length,0)?.toString().orEmpty()
                    if(previous==translateSource) input.deleteSurroundingText(translateSource.length,0)
                }
                val ok=input.commitText(translateResult,1)
                input.endBatchEdit()
                return ok
            }
            translatedAction("Insert") { if(insert()){translatorOpen=false;draw()} }
            translatedAction("Send") {
                if(insert()) {
                    translatorOpen=false;draw()
                    val action=(currentInputEditorInfo?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
                    if(action==EditorInfo.IME_ACTION_SEND) {
                        currentInputConnection?.performEditorAction(action)
                    } else Toast.makeText(this,"Inserted. Use the app's Send button.",Toast.LENGTH_SHORT).show()
                }
            }
            translatedAction("Share") {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type="text/plain";putExtra(Intent.EXTRA_TEXT,translateResult)
                },"Share translation").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            panel.addView(actions)
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
        val listed=clipStore.search("",pinnedOnly)
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
        for(image in savedImages.take(25)) {
            val line=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
            val label=(if(image.pinned)"📌 " else "")+"📷 Screenshot · "+image.mime
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
