package com.aetherkeys.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout

/** Tap any real key in the proportional preview. The preview is remeasured
 * whenever a width or height changes, without leaving the editor. */
class KeyPopLiveKeyEditor(context:Context,
    private val selected:(Int,Int,String)->Unit):LinearLayout(context) {
    private val preferences=context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
    private val density=resources.displayMetrics.density
    private fun dp(x:Int)=(x*density).toInt()
    var activeLanguage:Int=0
    var selectedRow:Int=-1
    var selectedColumn:Int=-1
    var selectedToken:String="SPACE"
    init { orientation=VERTICAL;setPadding(dp(3),dp(5),dp(3),dp(5));refresh() }

    fun refresh() {
        removeAllViews()
        val skin=KeyboardPresetCatalog.selected(context)
        val height=preferences.getInt("height",51).coerceIn(36,90)
        val widthPercent=preferences.getInt("keyboardWidthPercent",100).coerceIn(65,100)
        val screenDp=(resources.displayMetrics.widthPixels/density).toInt()
        val side=(((100-widthPercent)*screenDp)/200).coerceAtLeast(0)
        setPadding(dp(side+3),dp(5),dp(side+3),dp(5))
        if(preferences.getBoolean("numberRow",true))
            appendRow((1..9).map(Int::toString)+listOf("0"),height-5,-1,skin.id)
        val rows=KeyPopLayouts.active(context,activeLanguage)
        rows.forEachIndexed {rowIndex,words ->
            val labels=if(rowIndex==rows.lastIndex)
                listOf("⇧")+words+listOf("⌫") else words
            appendRow(labels,height,rowIndex,skin.id)
        }
        appendRow(KeyPopKeysets.footer(KeyPopKeysets.selectedId(context),false),
            height,-1,skin.id)
    }
    private fun appendRow(labels:List<String>,height:Int,row:Int,preset:Int) {
        val container=LinearLayout(context).apply {
            orientation=HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
        }
        val spacing=preferences.getInt("spacing",4).coerceIn(1,10)
        val rounding=preferences.getInt("round",13).coerceIn(3,40)
        val font=preferences.getInt("font",19).coerceIn(12,32)
        labels.forEachIndexed {index,token ->
            val column=if(row==2 && token !in setOf("⇧","⌫"))index-1
                else if(row<0 || token in setOf("⇧","⌫"))-1 else index
            val factor=KeyPopKeyGeometry.widthPercent(context,activeLanguage,
                row,column,token)/100f
            val bonus=KeyboardPresetCatalog.controlHeightBonus(token)+
                if(token=="SPACE")12 else 0
            val keyHeight=((height+bonus)*KeyPopKeyGeometry.heightPercent(
                context,activeLanguage,row,column,token)/100f)
                .toInt().coerceIn(26,155)
            val isActive=row==selectedRow && column==selectedColumn &&
                token==selectedToken
            val button=Button(context).apply {
                text=if(token=="SPACE")"Space" else token
                textSize=if(token=="SPACE")(font*.6f) else (font*.7f)
                isAllCaps=false;minWidth=0;minimumWidth=0
                minHeight=0;minimumHeight=0
                setPadding(0,0,0,0)
                setTextColor(if(isActive)Color.BLACK else Color.WHITE)
                background=GradientDrawable().apply {
                    setColor(if(isActive)0xFF7AE2D1.toInt()
                        else if(token=="SPACE")0xFF385975.toInt()
                        else 0xFF263A51.toInt())
                    cornerRadius=dp(rounding).toFloat()
                    setStroke(dp(1),0xFF658195.toInt())
                }
                contentDescription="Select "+token+" for individual width and height"
                setOnClickListener {
                    selectedRow=row;selectedColumn=column;selectedToken=token
                    selected(row,column,token)
                    refresh()
                }
            }
            val weight=KeyboardPresetCatalog.keyWeight(preset,token)*factor
            container.addView(button,LayoutParams(0,dp(keyHeight),weight).apply{
                leftMargin=dp((spacing/2).coerceAtLeast(1))
                rightMargin=dp((spacing/2).coerceAtLeast(1))
                bottomMargin=dp(2)
            })
        }
        addView(container,LayoutParams(-1,-2))
    }
}
