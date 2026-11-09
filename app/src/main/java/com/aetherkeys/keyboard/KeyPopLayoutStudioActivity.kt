package com.aetherkeys.keyboard

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import android.view.View

/** Tap two keys to swap; long press any character to remap it. */
class KeyPopLayoutStudioActivity:Activity() {
    private var lang=0
    private var chosen:Pair<Int,Int>?=null
    private lateinit var root:LinearLayout
    private val ink=Color.WHITE
    private val dim=0xFFA9B8D8.toInt()
    private val accent=0xFF82E2FD.toInt()
    private val ground=0xFF0C1428.toInt()
    private fun dp(x:Int)=(x*resources.displayMetrics.density).toInt()
    private fun bg(c:Int,round:Int=14,stroke:Int?=null)=GradientDrawable().apply {
        setColor(c);cornerRadius=dp(round).toFloat()
        if(stroke!=null)setStroke(dp(1),stroke)
    }
    private fun text(label:String,size:Float=14f,color:Int=ink)=TextView(this).apply{
        text=label;textSize=size;setTextColor(color)
        setPadding(dp(4),dp(6),dp(4),dp(7))
    }
    private fun b(label:String,selected:Boolean=false,onClick:()->Unit)=Button(this).apply {
        text=label;isAllCaps=false;textSize=if(label.length>3)12f else 15f
        setTextColor(if(selected)ground else ink)
        minWidth=0;minimumWidth=0;minHeight=0;minimumHeight=0
        background=bg(if(selected)accent else 0xFF26334F.toInt(),11,0xFF425273.toInt())
        setPadding(dp(2),0,dp(2),0)
        setOnClickListener{onClick()}
    }
    private fun add(child:View,below:Int=12) {
        root.addView(child,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(below)})
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=ground;window.navigationBarColor=ground
        val scroll=ScrollView(this).apply{setBackgroundColor(ground)}
        root=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(22),dp(16),dp(30))
        }
        scroll.addView(root);setContentView(scroll);render()
    }
    private fun render() {
        root.removeAllViews()
        add(text("KEYPOP  /  LAYOUT LAB",12f,accent),5)
        add(text("Build your own layout",26f),6)
        add(text("Choose a layout, swap keys, or long-press a key to edit its character. Every change is saved locally.",13f,dim),18)
        add(text("LANGUAGE",12f,accent),5)
        val languages=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        KeyPopLayouts.languages.forEachIndexed { i,name->
            languages.addView(b(name,lang==i){lang=i;chosen=null;render()},
                LinearLayout.LayoutParams(0,dp(43),1f).apply{rightMargin=dp(5)})
        }
        add(languages,17)
        if(lang==0) {
            add(text("BASE ARRANGEMENT",12f,accent),5)
            val scroll=HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false}
            val strip=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
            KeyPopLayouts.names.forEachIndexed { i,name->
                strip.addView(b(name,KeyPopLayouts.variant(this)==i) {
                    if(KeyPopLayouts.variant(this)!=i)
                        AlertDialog.Builder(this).setTitle("Change English layout?")
                            .setMessage("This resets English key swaps. Urdu and Sindhi are unaffected.")
                            .setNegativeButton("Cancel",null)
                            .setPositiveButton("Change"){_,_->
                                KeyPopLayouts.choose(this,i);chosen=null;render()
                            }.show()
                },LinearLayout.LayoutParams(dp(104),dp(44)).apply{rightMargin=dp(7)})
            }
            scroll.addView(strip);add(scroll,18)
        }
        val label=chosen?.let {
            "Selected: "+KeyPopLayouts.active(this,lang)[it.first][it.second]+
                " · width "+KeyPopKeyGeometry.width(this,lang,it.first,it.second)+
                "% · tap another key to swap"
        }?:"Tap a key to select, then another to swap. Hold to edit a character."
        add(text(label,13f,dim),12)
        // A selected key changes width directly in its existing row while the
        // slider remains attached. No redraw/scroll reset during dragging.
        var selectedRow:LinearLayout?=null
        chosen?.let { (selectedR,selectedC)->
            val currentWidth=KeyPopKeyGeometry.width(this,lang,selectedR,selectedC)
            val widthLabel=text("Key width: $currentWidth%",13f,accent)
            add(widthLabel,2)
            val slider=SeekBar(this).apply {
                max=175-65
                progress=currentWidth-65
                contentDescription="Selected key width"
                setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar:SeekBar?,progress:Int,byUser:Boolean) {
                        if(!byUser)return
                        val width=progress+65
                        KeyPopKeyGeometry.setWidth(this@KeyPopLayoutStudioActivity,
                            lang,selectedR,selectedC,width)
                        widthLabel.text="Key width: $width%"
                        val row=selectedRow ?:return
                        val tile=row.getChildAt(selectedC)?:return
                        val params=tile.layoutParams as? LinearLayout.LayoutParams ?:return
                        params.weight=width/100f
                        tile.layoutParams=params
                        row.requestLayout()
                    }
                    override fun onStartTrackingTouch(bar:SeekBar?){}
                    override fun onStopTrackingTouch(bar:SeekBar?){}
                })
            }
            add(slider,12)
        }
        val board=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL;setPadding(dp(5),dp(12),dp(5),dp(10))
            background=bg(0xFF18213B.toInt(),18,0xFF394968.toInt())
        }
        KeyPopLayouts.active(this,lang).forEachIndexed{r,line->
            val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
            if(chosen?.first==r)selectedRow=row
            line.forEachIndexed{c,key->
                val tile=b(key,chosen==Pair(r,c)) {
                    val before=chosen
                    if(before==null)chosen=Pair(r,c)
                    else {
                        if(before!=Pair(r,c))KeyPopLayouts.swap(this,lang,before.first,before.second,r,c)
                        chosen=null
                    }
                    render()
                }.apply {
                    textSize=if(key.length>2)11f else 17f
                    contentDescription="Key "+key+", row "+(r+1)+", column "+(c+1)+
                        ". Long press to edit."
                    setOnLongClickListener{edit(r,c,key);true}
                }
                val weight=KeyPopKeyGeometry.width(this,lang,r,c)/100f
                row.addView(tile,LinearLayout.LayoutParams(0,dp(52),weight).apply{
                    leftMargin=dp(1);rightMargin=dp(1);bottomMargin=dp(4)
                })
            }
            board.addView(row)
        }
        add(board,16)
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        row.addView(b("Edit selected key") {
            val item=chosen
            if(item!=null)edit(item.first,item.second,
                KeyPopLayouts.active(this,lang)[item.first][item.second])
            else Toast.makeText(this,"Select a key first",Toast.LENGTH_SHORT).show()
        },LinearLayout.LayoutParams(0,dp(46),1f).apply{rightMargin=dp(5)})
        row.addView(b("Reset this language") {
            AlertDialog.Builder(this).setTitle("Reset custom layout?")
                .setMessage("Restore original characters for "+KeyPopLayouts.languages[lang]+"?")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Reset"){_,_->KeyPopLayouts.reset(this,lang)
                    KeyPopKeyGeometry.reset(this,lang)
                    chosen=null;render()}
                .show()
        },LinearLayout.LayoutParams(0,dp(46),1f))
        add(row,13)

        add(b("Done"){finish()})
    }
    private fun edit(r:Int,c:Int,old:String) {
        val editor=EditText(this).apply {
            setSingleLine(true);setText(old);setSelection(text.length)
        }
        AlertDialog.Builder(this).setTitle("Customize key")
            .setMessage("Enter 1–4 visible characters.")
            .setView(editor).setNegativeButton("Cancel",null)
            .setPositiveButton("Apply"){_,_->
                if(KeyPopLayouts.replace(this,lang,r,c,editor.text.toString())) {
                    chosen=null;render()
                } else Toast.makeText(this,"Invalid key (1–4 characters)",Toast.LENGTH_SHORT).show()
            }.show()
    }
}
