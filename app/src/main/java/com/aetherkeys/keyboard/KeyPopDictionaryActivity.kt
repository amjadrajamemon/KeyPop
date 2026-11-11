package com.aetherkeys.keyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*

/** Private multilingual personal dictionary management. */
class KeyPopDictionaryActivity:Activity() {
    private var language=0
    private val lexicon by lazy{KeyPopLexicon(this)}
    private val preferences by lazy{getSharedPreferences("keypop_lexicon",Context.MODE_PRIVATE)}
    private val ground=0xFF0B1429.toInt()
    private val accent=0xFF7DDBFF.toInt()
    private val white=Color.WHITE
    private val dim=0xFFA4B5CE.toInt()
    private lateinit var root:LinearLayout
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    private fun background(c:Int)=GradientDrawable().apply{
        setColor(c);cornerRadius=dp(13).toFloat()
        setStroke(dp(1),0xFF3C4C67.toInt())
    }
    private fun label(t:String,size:Float=14f,color:Int=white)=TextView(this).apply{
        text=t;textSize=size;setTextColor(color);setPadding(dp(4),dp(6),dp(4),dp(8))
    }
    private fun button(t:String,action:()->Unit)=Button(this).apply{
        text=t;isAllCaps=false;textSize=13f;setTextColor(white)
        minHeight=0;minimumHeight=0
        background=background(0xFF293757.toInt())
        setOnClickListener{action()}
    }
    private fun add(child:View,space:Int=10){
        root.addView(child,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(space)})
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=ground;window.navigationBarColor=ground
        val scroll=ScrollView(this).apply{setBackgroundColor(ground)}
        root=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL;setPadding(dp(17),dp(22),dp(17),dp(26))
        }
        scroll.addView(root);setContentView(scroll);render()
    }
    private fun render() {
        root.removeAllViews()
        add(label("KEYPOP  /  PERSONAL DICTIONARY",12f,accent),4)
        add(label("Words that matter to you",25f),4)
        add(label("Manage personal words and local learning. Learning follows the switch below; disable it at any time.",13f,dim),16)
        val options=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        KeyPopLayouts.languages.forEachIndexed{index,name->
            val control=button(name){language=index;render()}
            if(index==language)control.setTextColor(accent)
            options.addView(control,LinearLayout.LayoutParams(0,dp(42),1f).apply{
                rightMargin=dp(4)
            })
        }
        add(options,12)
        val toggle=Switch(this).apply{
            text="Learn new words automatically"
            setTextColor(white);textSize=14f
            isChecked=preferences.getBoolean("learningEnabled",true)
            setOnCheckedChangeListener {_,checked->
                preferences.edit().putBoolean("learningEnabled",checked).apply()
            }
        }
        add(toggle,15)
        add(label("ADD A WORD",12f,accent),2)
        val entry=EditText(this).apply{
            hint="Type a word for "+KeyPopLayouts.languages[language]
            textSize=15f;setTextColor(white);setHintTextColor(dim)
            setSingleLine(true);background=background(0xFF24304C.toInt())
            setPadding(dp(13),dp(8),dp(13),dp(8))
        }
        add(entry,7)
        add(button("＋ Save word") {
            val successful=lexicon.addManual(language,entry.text.toString())
            Toast.makeText(this,if(successful)"Word saved" else "Enter 2–32 letters",Toast.LENGTH_SHORT).show()
            if(successful){entry.text.clear();render()}
        },15)
        add(label("YOUR SAVED WORDS",12f,accent),4)
        val search=EditText(this).apply {
            hint="Search this dictionary…";setSingleLine(true)
            textSize=14f;setTextColor(white);setHintTextColor(dim)
            background=background(0xFF24304C.toInt())
            setPadding(dp(12),dp(7),dp(12),dp(7))
        }
        add(search,9)
        val results=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        add(results,12)
        fun showWords() {
            results.removeAllViews()
            val words=lexicon.learned(language)
                .filter{it.contains(search.text.toString(),ignoreCase=true)}
            results.addView(label(words.size.toString()+" personal word(s)",12f,dim))
            words.take(200).forEach { word->
                val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
                val wordLabel=label(word,15f).apply{setPadding(dp(8),dp(10),dp(3),dp(10))}
                row.addView(wordLabel,LinearLayout.LayoutParams(0,-2,1f))
                row.addView(button("Remove") {
                    lexicon.forget(language,word);showWords()
                },LinearLayout.LayoutParams(dp(93),dp(40)))
                results.addView(row)
            }
        }
        search.addTextChangedListener(object:TextWatcher{
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){showWords()}
            override fun afterTextChanged(s:Editable?){}
        })
        showWords()
        add(button("Clear all personal words for this language") {
            AlertDialog.Builder(this).setTitle("Clear personal dictionary?")
                .setMessage("The built-in dictionary is retained. Your saved words for "+
                    KeyPopLayouts.languages[language]+" will be removed.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Clear"){_,_->lexicon.clearLearned(language);showWords()}
                .show()
        },10)
        add(button("Done"){finish()})
    }
}
