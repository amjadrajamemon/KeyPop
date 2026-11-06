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
    private val accent = 0xFF8C82FF.toInt()
    private val prefs by lazy { getSharedPreferences("keypop", MODE_PRIVATE) }
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
        background=gradient(0xFF1A2440.toInt(),0xFF121A30.toInt())
    }
    private fun addCard(parent:LinearLayout, child:View) {
        parent.addView(child,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=d(16) })
    }
    private fun action(value:String, main:Boolean=false, onTap:()->Unit):TextView =
        text(value,14f,if(main) 0xFF10132B.toInt() else ink,true).apply {
            gravity=Gravity.CENTER
            minHeight=d(47)
            background=if(main) gradient(0xFFB6A7FF.toInt(),0xFF6DCFF8.toInt(),14)
                else background(0xFF26304B.toInt(),14,edge)
            setOnClickListener { onTap() }
        }

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=0xFF0A1124.toInt()
        window.navigationBarColor=0xFF0A1124.toInt()
        val shell=ScrollView(this).apply {
            isFillViewport=true
            setBackgroundColor(0xFF0A1124.toInt())
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

        val onboarding=card()
        onboarding.addView(text("01  ·  GET STARTED",12f,0xFF83CFF4.toInt(),true))
        onboarding.addView(gap(10))
        onboarding.addView(action("Enable KeyPop in Android settings",true) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        onboarding.addView(gap(8))
        onboarding.addView(action("Choose KeyPop as your keyboard") {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })
        addCard(root,onboarding)

        val studio=card()
        studio.addView(text("THEME STUDIO",12f,0xFF83CFF4.toInt(),true))
        studio.addView(gap(5))
        studio.addView(text("15 expressive profiles",22f,ink,true))
        studio.addView(text("Tap any design to apply immediately. The preview below updates without a separate Save button.",12f,muted))
        studio.addView(gap(14))
        val activeLabel=text("",14f,ink,true)
        studio.addView(activeLabel)
        studio.addView(gap(8))
        val mainPreview=KeyboardPreview(this,prefs.getInt("theme",0))
        studio.addView(mainPreview,LinearLayout.LayoutParams(-1,d(136)))
        studio.addView(gap(10))
        val gallery=HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false }
        val strip=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        val tiles=ArrayList<LinearLayout>()
        fun choose(index:Int) {
            prefs.edit().putInt("theme",index).apply()
            mainPreview.selection=index
            activeLabel.text=KeyPopSkins.all[index].name + "  ·  " + KeyPopSkins.all[index].subtitle
            tiles.forEachIndexed { i, tile ->
                tile.background=background(if(i==index) 0xFF3E416B.toInt() else 0xFF1D2943.toInt(),
                    16,if (i==index) accent else edge)
            }
        }
        KeyPopSkins.all.forEachIndexed { index, skin ->
            val tile=column(8)
            tile.addView(text(if(index<10) "PREMIUM "+(index+1) else "CLASSIC "+(index-9),10f,muted,true))
            tile.addView(text(skin.name,14f,ink,true))
            tile.addView(KeyboardPreview(this,index),LinearLayout.LayoutParams(-1,d(126)))
            tile.addView(text(skin.subtitle,11f,muted))
            tile.setOnClickListener { choose(index) }
            strip.addView(tile,LinearLayout.LayoutParams(d(222),-2).apply { rightMargin=d(10) })
            tiles.add(tile)
        }
        gallery.addView(strip)
        studio.addView(gallery)
        studio.addView(gap(12))
        studio.addView(text("10 original premium skins  ·  5 familiar-style alternatives",11f,muted))
        addCard(root,studio)
        choose(prefs.getInt("theme",0).coerceIn(0,14))

        val controls=card()
        controls.addView(text("KEYBOARD CUSTOMIZER",12f,0xFF83CFF4.toInt(),true))
        controls.addView(gap(4))
        controls.addView(text("Fine-tune every key",22f,ink,true))
        controls.addView(text("Adjust live keyboard behavior. Changes apply the next time the keyboard is opened or refreshed.",12f,muted))
        controls.addView(gap(12))
        slider(controls,"Key height", "height", 36,90,51,"dp")
        slider(controls,"Key corner curve", "round", 3,26,13,"dp")
        slider(controls,"Key spacing", "spacing", 1,10,4,"dp")
        slider(controls,"Key label size", "font", 12,24,17,"sp")
        toggle(controls,"Always show number row","Quick access to 0–9 above the letters","numberRow",true)
        toggle(controls,"Long-press number hints","Reveal additional characters by holding a key","hints",true)
        toggle(controls,"Haptic feedback","Small vibration when a key is pressed","haptics",false)
        toggle(controls,"Keyboard sound","Subtle key click sound","sound",false)
        toggle(controls,"Clipboard history","Encrypted local text history; clear it on demand","history",true)
        addCard(root,controls)

        val tryCard=card()
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
        val foot=text("KEYPOP  •  ANDROID ALPHA  •  v0.2.0\nNew studio preview ≠ complete Gboard feature parity.",11f,muted)
        foot.gravity=Gravity.CENTER
        root.addView(foot)
        root.addView(gap(15))
        setContentView(shell)
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
                if(user) prefs.edit().putInt(key,p+low).apply()
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
