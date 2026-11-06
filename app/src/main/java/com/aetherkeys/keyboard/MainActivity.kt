package com.aetherkeys.keyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {
    private val ink = 0xFFF8F9FF.toInt()
    private val muted = 0xFF9CA8C5.toInt()
    private val edge = 0xFF33405C.toInt()
    private val accent:Int get()=KeyPopSkins.active(this).accent
    private val pageBg:Int get() {
        val active=KeyPopSkins.active(this).background
        return KeyPopGlass.blend(active,Color.BLACK,
            if(Color.luminance(active)>0.32)0.74f else 0.18f)
    }
    private val prefs by lazy { getSharedPreferences("keypop", MODE_PRIVATE) }
    private val livePreviews=ArrayList<KeyboardPreview>()
    private var keyboardDefaultStatus:TextView?=null
    private fun updateDefaultKeyboardStatus() {
        val selected=runCatching {
            Settings.Secure.getString(contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty()
        }.getOrDefault("")
        val matches=selected.contains(packageName) &&
            selected.contains("AetherImeService")
        keyboardDefaultStatus?.text=if(matches)
            "✓ KeyPop is the default keyboard. Android normally restores it after reboot and unlock."
        else
            "Not selected as default. Select KeyPop once in the Android keyboard chooser."
        keyboardDefaultStatus?.setTextColor(
            if(matches)0xFF89EBB5.toInt() else 0xFFFFC97A.toInt())
    }
    override fun onResume() {
        super.onResume()
        updateDefaultKeyboardStatus()
    }
    private fun d(v:Int)=(v*resources.displayMetrics.density).toInt()

    private fun background(color:Int, radius:Int=18, border:Int?=null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius=d(radius).toFloat()
            if (border!=null) setStroke(d(1),border)
        }
    private fun gradient(a:Int,b:Int,r:Int=22): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(a,b)).apply {
            cornerRadius=d(r).toFloat()
            setStroke(d(1),edge)
        }
    private fun text(value:String,size:Float=14f,color:Int=ink,bold:Boolean=false):TextView =
        TextView(this).apply {
            this.text=value; textSize=size; setTextColor(color)
            if (bold) typeface=Typeface.create("sans-serif-medium",Typeface.BOLD)
            gravity=Gravity.CENTER_VERTICAL
            includeFontPadding=true
        }
    private fun column(pad:Int=0):LinearLayout=LinearLayout(this).apply {
        orientation=LinearLayout.VERTICAL; setPadding(d(pad),d(pad),d(pad),d(pad))
    }
    private fun gap(height:Int=12):View=View(this).apply { minimumHeight=d(height) }
    private fun card():LinearLayout=column(16).apply {
        val bg=KeyPopGlass.blend(pageBg,KeyPopSkins.active(this@MainActivity).surface,0.55f)
        background=gradient(KeyPopGlass.blend(bg,Color.WHITE,0.08f),bg)
    }
    private fun addCard(parent:LinearLayout, child:View) {
        parent.addView(child,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=d(16) })
    }
    private fun action(value:String, main:Boolean=false, onTap:()->Unit):TextView =
        text(value,14f,if(main) 0xFF10132B.toInt() else ink,true).apply {
            gravity=Gravity.CENTER
            minHeight=d(47)
            background=if(main)
                gradient(KeyPopGlass.blend(accent,Color.WHITE,0.21f),accent,14)
                else background(KeyPopGlass.blend(pageBg,KeyPopSkins.active(this@MainActivity).surface,0.8f),14,edge)
            setOnClickListener { onTap() }
        }

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=pageBg
        window.navigationBarColor=pageBg
        val shell=ScrollView(this).apply {
            isFillViewport=true
            setBackgroundColor(pageBg)
            isVerticalScrollBarEnabled=false
        }
        val root=column(18)
        shell.addView(root)

        // Header and prominent app identity.
        val brand=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        brand.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_keypop_logo)
            scaleType=ImageView.ScaleType.FIT_CENTER
        },LinearLayout.LayoutParams(d(62),d(62)))
        val label=column().apply { setPadding(d(12),0,0,0) }
        label.addView(text("KeyPop",29f,ink,true))
        label.addView(text("MAKE EVERY KEY YOURS",11f,0xFF87DDFB.toInt(),true))
        brand.addView(label)
        root.addView(brand)
        root.addView(gap(7))
        root.addView(text("Your keyboard. Your universe.",23f,ink,true))
        root.addView(text("Customize the entire typing experience with live visual controls, 15 profiles and three built-in languages.",13f,muted))
        root.addView(gap(10))

        lateinit var studio:LinearLayout
        lateinit var presetsCard:LinearLayout
        lateinit var controls:LinearLayout
        lateinit var translate:LinearLayout
        lateinit var vaultCard:LinearLayout
        lateinit var clipsCard:LinearLayout
        lateinit var onboarding:LinearLayout
        lateinit var modelCard:LinearLayout
        lateinit var tryCard:LinearLayout
        val dashboard=card()
        val backBar=card()
        val sectionTitle=text("SETTINGS",14f,ink,true)
        val back=text("‹  All Settings",15f,ink,true).apply{
            minHeight=d(36)
            setOnClickListener{navigateHome()}
        }
        backBar.addView(back)
        backBar.addView(sectionTitle)
        backBar.visibility=View.GONE
        dashboard.addView(text("KEYPOP  /  SETTINGS HOME",12f,accent,true))
        dashboard.addView(gap(3))
        dashboard.addView(text("Everything in its place",21f,ink,true))
        dashboard.addView(text("Select a category. Browsing never changes your theme, layout or dimensions.",12f,muted))
        dashboard.addView(text("Active style: "+KeyPopSkins.active(this).name,12f,accent,true))
        dashboard.addView(gap(10))
        fun navigateHome() {
            dashboard.visibility=View.VISIBLE
            backBar.visibility=View.GONE
            listOf(onboarding,modelCard,studio,presetsCard,controls,translate,vaultCard,clipsCard,tryCard)
                .forEach{it.visibility=View.GONE}
            shell.post{shell.scrollTo(0,0)}
        }
        fun navigate(target:LinearLayout,heading:String) {
            dashboard.visibility=View.GONE
            backBar.visibility=View.VISIBLE
            sectionTitle.text="SETTINGS  /  "+heading
            listOf(onboarding,modelCard,studio,presetsCard,controls,translate,vaultCard,clipsCard,tryCard)
                .forEach{it.visibility=if(it===target) View.VISIBLE else View.GONE}
            shell.post{shell.scrollTo(0,0)}
        }
        fun navRow(left:String,leftView:()->LinearLayout,right:String,rightView:()->LinearLayout) {
            val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
            row.addView(action(left){navigate(leftView(),left)},LinearLayout.LayoutParams(0,d(47),1f).apply{rightMargin=d(8)})
            row.addView(action(right){navigate(rightView(),right)},LinearLayout.LayoutParams(0,d(47),1f))
            dashboard.addView(row,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(8)})
        }
        navRow("Keyboard Setup",{onboarding},"Offline Packs",{modelCard})
        navRow("Themes",{studio},"Layouts",{presetsCard})
        navRow("Typing & Size",{controls},"Translation",{translate})
        navRow("Clipboard",{clipsCard},"Security",{vaultCard})
        navRow("Try Keyboard",{tryCard},"Language Packs",{modelCard})
        root.addView(dashboard)
        root.addView(gap(7))
        root.addView(backBar)
        root.addView(gap(7))

        onboarding=card()
        onboarding.addView(text("01  ·  GET STARTED",12f,0xFF83CFF4.toInt(),true))
        onboarding.addView(gap(7))
        keyboardDefaultStatus=text("Checking default keyboard…",13f,muted)
        onboarding.addView(keyboardDefaultStatus)
        onboarding.addView(text("After restarting the phone, KeyPop automatically appears on text fields if it remains the Android default. It cannot force itself over the lock screen or home screen.",12f,muted))
        onboarding.addView(gap(10))
        onboarding.addView(action("Enable KeyPop in Android settings",true) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        onboarding.addView(gap(8))
        onboarding.addView(action("Choose KeyPop as your keyboard") {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })
        onboarding.addView(gap(6))
        onboarding.addView(action("Refresh default keyboard status"){
            updateDefaultKeyboardStatus()
        })
        addCard(root,onboarding)
        updateDefaultKeyboardStatus()
        modelCard=card()
        modelCard.addView(text("OFFLINE LANGUAGE ENGINE",12f,accent,true))
        modelCard.addView(gap(5))
        modelCard.addView(text("Download translation models before typing",20f,ink,true))
        modelCard.addView(text("First-launch consent and a Language Pack Manager are available. English/Urdu ML Kit translation is downloaded on request; Android manages voice models.",12f,muted))
        modelCard.addView(gap(9))
        modelCard.addView(action("Manage text and voice packs",true) {
            startActivity(Intent(this,KeyPopModelManagerActivity::class.java))
        })
        addCard(root,modelCard)

        studio=card()
        studio.addView(text("THEME STUDIO",12f,0xFF83CFF4.toInt(),true))
        studio.addView(gap(5))
        studio.addView(text("15 styles including Gboard / SwiftKey / Samsung inspired",20f,ink,true))
        studio.addView(text("Browse without changing your active keyboard. Select a preview, then tap Apply Theme.",12f,muted))
        studio.addView(gap(14))
        val activeLabel=text("",14f,ink,true)
        studio.addView(activeLabel)
        studio.addView(gap(8))
        val mainPreview=KeyboardPreview(this,prefs.getInt("theme",0))
        livePreviews.add(mainPreview)
        studio.addView(mainPreview,LinearLayout.LayoutParams(-1,d(136)))
        studio.addView(gap(10))
        val gallery=HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false }
        val strip=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        val tiles=ArrayList<LinearLayout>()
        var pendingTheme=prefs.getInt("theme",0).coerceIn(0,14)
        fun choose(index:Int) {
            pendingTheme=index
            mainPreview.selection=index
            livePreviews.forEach{it.invalidate()}
            activeLabel.text="PREVIEW  ·  "+KeyPopSkins.all[index].name+
                "  ·  "+KeyPopSkins.all[index].subtitle
            tiles.forEachIndexed { i, tile ->
                tile.background=background(if(i==index) 0xFF3E416B.toInt() else 0xFF1D2943.toInt(),
                    16,if (i==index) accent else edge)
            }
        }
        KeyPopSkins.all.forEachIndexed { index, skin ->
            val tile=column(8)
            tile.addView(text(if(index<10) "PREMIUM "+(index+1) else "CLASSIC "+(index-9),10f,muted,true))
            tile.addView(text(skin.name,14f,ink,true))
            val tilePreview=KeyboardPreview(this,index)
            livePreviews.add(tilePreview)
            tile.addView(tilePreview,LinearLayout.LayoutParams(-1,d(126)))
            tile.addView(text(skin.subtitle,11f,muted))
            tile.setOnClickListener { choose(index) }
            strip.addView(tile,LinearLayout.LayoutParams(d(222),-2).apply { rightMargin=d(10) })
            tiles.add(tile)
        }
        gallery.addView(strip)
        studio.addView(action("✓ Apply selected theme",true) {
            prefs.edit().putInt("theme",pendingTheme).apply()
            android.widget.Toast.makeText(this,"Theme applied: "+KeyPopSkins.all[pendingTheme].name,
                android.widget.Toast.LENGTH_SHORT).show()
            livePreviews.forEach{it.invalidate()}
        })
        studio.addView(gap(12))
        studio.addView(gallery)
        studio.addView(gap(12))
        studio.addView(text("10 original premium skins  ·  5 familiar-style alternatives",11f,muted))
        studio.addView(gap(12))
        studio.addView(text("MAKE IT YOURS · KEY LIGHT ACCENT",11f,0xFF83CFF4.toInt(),true))
        studio.addView(gap(6))
        val accents=listOf(0xFF8C82FF.toInt(),0xFF00DBF3.toInt(),
            0xFFFF71D6.toInt(),0xFFB5F980.toInt(),0xFFFFCB69.toInt(),
            0xFFFF7066.toInt(),0xFF80A8FF.toInt(),0xFFF3F3FB.toInt())
        val swatches=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL}
        for(color in accents) {
            val swatch=text("●",27f,color,true).apply {
                gravity=Gravity.CENTER
                background=background(0xFF27324B.toInt(),12,edge)
                setOnClickListener {
                    prefs.edit().putInt("customAccent",color).apply()
                    livePreviews.forEach{it.invalidate()}
                }
            }
            swatches.addView(swatch,LinearLayout.LayoutParams(0,d(43),1f).apply{rightMargin=d(5)})
        }
        studio.addView(swatches)
        studio.addView(gap(6))
        studio.addView(action("Reset accent to theme default") {
            prefs.edit().remove("customAccent").apply()
            livePreviews.forEach{it.invalidate()}
        })
        addCard(root,studio)
        choose(prefs.getInt("theme",0).coerceIn(0,14))

        presetsCard=card()
        presetsCard.addView(text("FAMILIAR KEYBOARD LAYOUTS",12f,0xFF83CFF4.toInt(),true))
        presetsCard.addView(gap(5))
        presetsCard.addView(text("Gboard · SwiftKey · Samsung · KeyPop",22f,ink,true))
        presetsCard.addView(text("Tap any preview below, then Apply. The selected layout changes key sizes, punctuation and numbers, not just the color.",12f,0xFF83CFF4.toInt()))
        presetsCard.addView(text("Original layouts informed by Gboard, Microsoft SwiftKey, Samsung Keyboard, Fleksy and Typewise ergonomics. No copied visual assets or endorsements. Browsing is safe; only Apply changes your keyboard.",12f,muted))
        presetsCard.addView(gap(12))
        val presetStatus=text("",14f,ink,true)
        presetsCard.addView(presetStatus)
        presetsCard.addView(gap(8))
        val presetPreviewSlot=FrameLayout(this)
        presetsCard.addView(presetPreviewSlot,LinearLayout.LayoutParams(-1,d(140)))
        val activePreset=KeyboardPresetCatalog.selected(this).id
        var chosenPreset=activePreset
        val presetTiles=ArrayList<LinearLayout>()
        fun previewPreset(id:Int) {
            chosenPreset=id
            val candidate=KeyboardPresetCatalog.byId(id) ?:return
            presetPreviewSlot.removeAllViews()
            presetPreviewSlot.addView(KeyboardPresetPreview(this,candidate),
                FrameLayout.LayoutParams(-1,d(140)))
            presetStatus.text="PREVIEW  ·  "+candidate.name+
                "  ("+candidate.reference+")"
            presetTiles.forEachIndexed{index,tile->
                tile.background=background(if(index==id)0xFF3E416B.toInt() else 0xFF1D2943.toInt(),
                    16,if(index==id)accent else edge)
            }
        }
        presetsCard.addView(gap(10))
        presetsCard.addView(action("✓ Apply selected keyboard layout",true) {
            val selected=KeyboardPresetCatalog.byId(chosenPreset)
            if(KeyboardPresetCatalog.apply(this,chosenPreset) && selected!=null){
                android.widget.Toast.makeText(this,
                    selected.name+" applied. Reopen the keyboard to see all changes.",
                    android.widget.Toast.LENGTH_LONG).show()
                livePreviews.forEach{it.invalidate()}
            }
        })
        presetsCard.addView(gap(10))
        val presetGallery=HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false}
        val presetStrip=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        KeyboardPresetCatalog.all.forEach{candidate->
            val tile=column(9)
            tile.addView(text(candidate.name,15f,ink,true))
            tile.addView(KeyboardPresetPreview(this,candidate),
                LinearLayout.LayoutParams(-1,d(140)))
            tile.addView(text(candidate.description,12f,muted))
            tile.addView(text(candidate.reference,11f,0xFF83CFF4.toInt()))
            tile.setOnClickListener{previewPreset(candidate.id)}
            presetStrip.addView(tile,LinearLayout.LayoutParams(d(248),-2).apply{
                rightMargin=d(10)
            })
            presetTiles.add(tile)
        }
        presetGallery.addView(presetStrip)
        presetsCard.addView(presetGallery)
        addCard(root,presetsCard)
        previewPreset(activePreset)

        controls=card()
        controls.addView(text("KEYBOARD CUSTOMIZER",12f,0xFF83CFF4.toInt(),true))
        controls.addView(gap(4))
        controls.addView(text("Fine-tune every key",22f,ink,true))
        controls.addView(gap(9))
        controls.addView(action("⇄  Design Backup — import/export your custom theme") {
            startActivity(Intent(this,KeyPopProfileActivity::class.java))
        })
        controls.addView(gap(7))
        controls.addView(action("✎ Layout Lab — move, swap and remap keys",true) {
            startActivity(Intent(this,KeyPopLayoutStudioActivity::class.java))
        })
        controls.addView(gap(7))
        controls.addView(action("✦ Personal Dictionary — add and manage words") {
            startActivity(Intent(this,KeyPopDictionaryActivity::class.java))
        })
        controls.addView(gap(8))
        controls.addView(text("Adjust live keyboard behavior. Changes apply the next time the keyboard is opened or refreshed.",12f,muted))
        controls.addView(gap(12))
        controls.addView(text("For instant drag-to-resize, open the keyboard and tap Resize in its toolbar.",12f,0xFF83CFF4.toInt()))
        slider(controls,"Key height", "height", 36,90,51,"dp")
        slider(controls,"Keyboard width", "keyboardWidthPercent", 65,100,100,"%")
        slider(controls,"Lift above Android navigation", "bottomLift", 0,96,0,"dp")
        slider(controls,"Key corner curve", "round", 3,26,13,"dp")
        slider(controls,"Key spacing", "spacing", 1,10,4,"dp")
        slider(controls,"Key label size", "font", 12,24,17,"sp")
        toggle(controls,"Always show number row","Quick access to 0–9 above the letters","numberRow",true)
        toggle(controls,"Roman Urdu / Sindhi typing","Use English letters; convert common words into native script on Space","romanMode",false)
        toggle(controls,"Predictive word suggestions","Local suggestions learn words only outside sensitive fields","suggestions",true)
        toggle(controls,"Autocorrection","Optional basic autocorrect based on the local dictionary","autoCorrect",false)
        toggle(controls,"Visible grammar and spelling fixes","Show tappable offline correction options while typing","grammarHints",true)
        toggle(controls,"Double-space punctuation","Two spaces insert a period and one space","doubleSpace",true)
        toggle(controls,"Auto capitalize sentences","Capitalize the first English letter of a sentence","autoCap",true)
        toggle(controls,"Tablet split typing","Extra central gap in wide-screen layouts","splitKeyboard",false)
        toggle(controls,"Floating keyboard","Drag the ⠿ handle to reposition the compact keyboard","floating",false)
        toggle(controls,"Glide English words","Slide between letters to insert common words","glide",false)
        toggle(controls,"Show alternate-character hints","Symbols appear in the hold-and-slide popup even when hints are hidden","hints",true)
        toggle(controls,"Haptic feedback","Small vibration when a key is pressed","haptics",false)
        toggle(controls,"Keyboard sound","Subtle key click sound","sound",false)
        toggle(controls,"Clipboard history (opt in)","Encrypted local history with manual deletion and pinning","history",false)
        toggle(controls,"Long-press Backspace deletes a word","Hold delete to remove the preceding word","wordDelete",true)
        toggle(controls,"Spacebar cursor gestures","Drag on Space to move the cursor; hold to select a language","spaceCursor",true)
        toggle(controls,"High-contrast key labels","Force black or white glyphs and outlined keys for readability","highContrast",false)
        toggle(controls,"Subtle animated themes","Animate selected theme effects at reduced frame rate; respects Android reduced-motion settings","animatedThemes",false)
        toggle(controls,"Private performance diagnostics","Measure keyboard view construction only; never capture typed content","performanceMetrics",false)
        controls.addView(gap(7))
        controls.addView(action("Performance Lab — timing measurements and export") {
            startActivity(Intent(this,KeyPopDiagnosticsActivity::class.java))
        })
        addCard(root,controls)

        translate=card()
        translate.addView(text("TRANSLATION ENGINE",12f,0xFF83CFF4.toInt(),true))
        translate.addView(gap(6))
        translate.addView(text("Write, speak, translate and send",21f,ink,true))
        translate.addView(gap(8))
        translate.addView(action("🎙 Open built-in Voice Translate",true){
            startActivity(Intent(this,KeyPopVoiceActivity::class.java)
                .putExtra("lang","en-US")
                .putExtra("target",prefs.getInt("autoTarget",1)))
        })
        translate.addView(gap(7))
        translate.addView(text("Common English/Urdu/Sindhi phrases are built into KeyPop and work offline. Other English/Urdu text uses a downloaded on-device ML Kit model. Unrestricted Sindhi still needs a separately configured provider; remote sending is off by default.",12f,muted))
        toggle(translate,"Auto Translate on Send","Press the keyboard Send key to translate and insert automatically","autoTranslate",false)
        translate.addView(gap(5))
        val targetLabels=listOf("English","اردو","سنڌي")
        val targetTitle=text("",13f,muted,true)
        translate.addView(targetTitle)
        fun showTarget(){targetTitle.text="Automatic translation target: "+targetLabels[prefs.getInt("autoTarget",1).coerceIn(0,2)]}
        showTarget()
        val targetRow=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL}
        targetLabels.forEachIndexed{index,label->
            targetRow.addView(action(label) {
                prefs.edit().putInt("autoTarget",index).apply()
                showTarget()
            },LinearLayout.LayoutParams(0,d(42),1f).apply{rightMargin=d(5)})
        }
        translate.addView(targetRow)
        translate.addView(gap(8))
        toggle(translate,"Allow remote Sindhi translation","Off by default; the configured provider receives messages you submit","remoteTranslate",false)
        val endpoint=EditText(this).apply {
            hint="Optional HTTPS translation endpoint"
            inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true);setTextColor(ink);setHintTextColor(muted);textSize=13f
            background=background(0xFF27324B.toInt(),12,edge)
            setPadding(d(12),d(8),d(12),d(8))
            setText(prefs.getString("translateEndpoint","").orEmpty())
        }
        translate.addView(endpoint,LinearLayout.LayoutParams(-1,d(46)))
        translate.addView(gap(8))
        translate.addView(action("Save translation provider") {
            val value=endpoint.text.toString().trim()
            if(value.isEmpty() || value.startsWith("https://")) {
                prefs.edit().putString("translateEndpoint",value).apply()
                android.widget.Toast.makeText(this,"Provider saved",android.widget.Toast.LENGTH_SHORT).show()
            } else android.widget.Toast.makeText(this,"HTTPS is required",android.widget.Toast.LENGTH_SHORT).show()
        })
        addCard(root,translate)

        vaultCard=card()
        vaultCard.addView(text("LOGIN VAULT",12f,0xFF83CFF4.toInt(),true))
        vaultCard.addView(gap(5))
        vaultCard.addView(text("Secure sign-ins",21f,ink,true))
        vaultCard.addView(text("Encrypted account storage with device authentication. Autofill is available where Android and the destination app support it; restricted banking fields cannot be overridden.",12f,muted))
        vaultCard.addView(gap(12))
        vaultCard.addView(action("Manage saved usernames & passwords",true) {
            startActivity(Intent(this,KeyPopVaultActivity::class.java))
        })
        vaultCard.addView(gap(8))
        vaultCard.addView(action("Choose KeyPop as Android Autofill service") {
            if(android.os.Build.VERSION.SDK_INT>=26) {
                val request=Intent("android.settings.REQUEST_SET_AUTOFILL_SERVICE")
                    .setData(android.net.Uri.parse("package:"+packageName))
                try {startActivity(request)}
                catch(_:Exception){startActivity(Intent(Settings.ACTION_SETTINGS))}
            }
        })
        addCard(root,vaultCard)

        clipsCard=card()
        clipsCard.addView(text("CLIPBOARD STUDIO",12f,0xFF83CFF4.toInt(),true))
        clipsCard.addView(gap(4))
        clipsCard.addView(text("Search, pin and manage clips",21f,ink,true))
        clipsCard.addView(gap(8))
        clipsCard.addView(action("GIF & Sticker Shelf — import, search, rename and share",true) {
            startActivity(Intent(this,KeyPopMediaStudioActivity::class.java))
        })
        clipsCard.addView(text("Text is encrypted locally and remains until removed. Password-field clipboard collection is disabled.",12f,muted))
        clipsCard.addView(gap(10))
        val search=EditText(this).apply {
            hint="Search saved text…";setSingleLine(true)
            setTextColor(ink);setHintTextColor(muted);textSize=15f
            background=background(0xFF27324B.toInt(),12,edge)
            setPadding(d(12),d(9),d(12),d(9))
        }
        clipsCard.addView(search)
        clipsCard.addView(gap(8))
        val results=column()
        clipsCard.addView(results)
        val clipStore=ClipStore(this)
        fun renderClips() {
            results.removeAllViews()
            val items=try{clipStore.search(search.text.toString())}catch(_:Exception) {
                results.addView(text("Encrypted clipboard could not be opened. Saved data was retained.",13f,0xFFFF8888.toInt()))
                return
            }
            results.addView(text("Saved clips: "+items.size+"  ·  newest first",11f,muted))
            items.take(40).forEach { item ->
                val line=column(8).apply { background=background(0xFF25314C.toInt(),12,edge) }
                line.addView(text((if(item.pinned) "📌  " else "")+item.text.take(180),13f,ink))
                val actions=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
                actions.addView(action(if(item.pinned) "Unpin" else "Pin") {
                    clipStore.pin(item.text,!item.pinned);renderClips()
                },LinearLayout.LayoutParams(0,d(38),1f).apply{rightMargin=d(5)})
                actions.addView(action("Delete") {
                    clipStore.delete(item.text);renderClips()
                },LinearLayout.LayoutParams(0,d(38),1f))
                line.addView(actions)
                results.addView(line,LinearLayout.LayoutParams(-1,-2).apply{topMargin=d(7)})
            }
        }
        search.addTextChangedListener(object:android.text.TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){renderClips()}
            override fun afterTextChanged(s:android.text.Editable?){}
        })
        clipsCard.addView(gap(8))
        clipsCard.addView(action("Remove all unpinned clips") {
            android.app.AlertDialog.Builder(this)
                .setTitle("Clear unpinned clipboard?")
                .setMessage("Pinned clips will stay saved. Deleted clips cannot be restored.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Delete") { _,_ -> clipStore.clearUnpinned();renderClips() }
                .show()
        })
        addCard(root,clipsCard)
        renderClips()
        if(intent.getBooleanExtra("openClips",false)) shell.post {
            shell.smoothScrollTo(0,clipsCard.top)
            search.requestFocus()
        }

        tryCard=card()
        tryCard.addView(text("TEST THE EXPERIENCE",12f,0xFF83CFF4.toInt(),true))
        tryCard.addView(gap(5))
        tryCard.addView(text("Try English • اردو • سنڌي",19f,ink,true))
        tryCard.addView(text("Tap below to open your chosen keyboard and see changes in action.",12f,muted))
        tryCard.addView(gap(10))
        val field=EditText(this).apply {
            hint="Start typing here…"
            setHintTextColor(0xFF8495B3.toInt())
            setTextColor(ink); textSize=17f
            background=background(0xFF27324B.toInt(),13,edge)
            setPadding(d(14),d(11),d(14),d(11))
            minHeight=d(54)
        }
        tryCard.addView(field)
        addCard(root,tryCard)
        val foot=text("KEYPOP  •  PERSONALIZATION ALPHA  •  v0.4.0\nBenchmarks and device validation remain required for parity.",11f,muted)
        foot.gravity=Gravity.CENTER
        root.addView(foot)
        root.addView(gap(15))
        setContentView(shell)
        when(intent.getStringExtra("openSection")){
            "themes" ->navigate(studio,"Themes")
            "layouts" ->navigate(presetsCard,"Layouts")
            "settings" ->navigate(controls,"Typing & Size")
            "translation" ->navigate(translate,"Translation")
            "languagePacks" ->navigate(modelCard,"Offline Packs")
            else ->navigateHome()
        }
        if(!prefs.getBoolean("firstLaunchPacksV09",false)){
            prefs.edit().putBoolean("firstLaunchPacksV09",true).apply()
            shell.post {
                startActivity(Intent(this,KeyPopModelManagerActivity::class.java)
                    .putExtra("firstRun",true))
            }
        }
    }

    private fun slider(parent:LinearLayout,title:String,key:String,low:Int,high:Int,default:Int,unit:String) {
        val row=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        row.addView(text(title,14f,ink,true),LinearLayout.LayoutParams(0,-2,1f))
        val count=text("",13f,0xFF9DDAFD.toInt(),true)
        row.addView(count)
        parent.addView(row)
        val bar=SeekBar(this).apply {
            max=high-low
            progress=(prefs.getInt(key,default)-low).coerceIn(0,high-low)
            progressTintList=android.content.res.ColorStateList.valueOf(accent)
            thumbTintList=android.content.res.ColorStateList.valueOf(0xFF9CDFFA.toInt())
        }
        count.text=(bar.progress+low).toString()+unit
        bar.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s:SeekBar?, p:Int, user:Boolean) {
                count.text=(p+low).toString()+unit
                if(user) {
                    prefs.edit().putInt(key,p+low).apply()
                    livePreviews.forEach{it.invalidate()}
                }
            }
            override fun onStartTrackingTouch(s:SeekBar?) {}
            override fun onStopTrackingTouch(s:SeekBar?) {}
        })
        parent.addView(bar)
        parent.addView(gap(5))
    }

    private fun toggle(parent:LinearLayout,name:String,description:String,key:String,default:Boolean) {
        val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(0,d(9),0,d(9)) }
        val labels=column()
        labels.addView(text(name,14f,ink,true))
        labels.addView(text(description,11f,muted))
        row.addView(labels,LinearLayout.LayoutParams(0,-2,1f))
        row.addView(Switch(this).apply {
            isChecked=prefs.getBoolean(key,default)
            thumbTintList=android.content.res.ColorStateList.valueOf(0xFFA3E7FE.toInt())
            setOnCheckedChangeListener { _, checked->prefs.edit().putBoolean(key,checked).apply() }
        })
        parent.addView(row)
    }
}
