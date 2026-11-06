package com.aetherkeys.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.min

/**
 * IME-window-owned long-press strip. Unlike PopupMenu it stays visible while
 * the original pointer is held and can be selected by sliding, then releasing.
 * No overlay permissions, activities, or background services are required.
 */
class KeyPopCharacterStrip(private val context:Context,private val host:FrameLayout) {
    private var strip:LinearLayout?=null
    private var choices=emptyList<String>()
    private val cells=ArrayList<TextView>()
    private var selected=0
    private var leftOnScreen=0
    private var stripWidth=0
    private var accent=Color.CYAN
    private var foreground=Color.WHITE
    private fun dp(n:Int)=(n*context.resources.displayMetrics.density).toInt()
    private fun rounded(color:Int,radius:Int)=GradientDrawable().apply{
        setColor(color);cornerRadius=dp(radius).toFloat()
    }
    val isShowing:Boolean get()=strip!=null
    fun show(anchor:View,alternates:List<String>,accentColor:Int,ink:Int,background:Int) {
        dismiss()
        if(alternates.isEmpty())return
        choices=alternates.take(12)
        accent=accentColor;foreground=ink;selected=0
        val h=dp(52)
        val hostWidth=host.width.takeIf{it>0}?:context.resources.displayMetrics.widthPixels
        stripWidth=min(hostWidth-dp(8),dp(48)*choices.size).coerceAtLeast(dp(48))
        val root=LinearLayout(context).apply{
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(4),dp(4),dp(4),dp(4))
            background=rounded(background,12)
            elevation=dp(12).toFloat()
            isClickable=false;isFocusable=false
        }
        cells.clear()
        choices.forEach{label->
            val cell=TextView(context).apply {
                text=label;textSize=20f;setTextColor(foreground)
                gravity=Gravity.CENTER
                includeFontPadding=false
                contentDescription="Alternate "+label
                isClickable=false;isFocusable=false
            }
            root.addView(cell,LinearLayout.LayoutParams(0,-1,1f))
            cells.add(cell)
        }
        val parentPos=IntArray(2);host.getLocationOnScreen(parentPos)
        val keyPos=IntArray(2);anchor.getLocationOnScreen(keyPos)
        val localX=(keyPos[0]+anchor.width/2-stripWidth/2-parentPos[0])
            .coerceIn(0,(hostWidth-stripWidth).coerceAtLeast(0))
        val localTop=(keyPos[1]-parentPos[1]-h-dp(5))
            .coerceIn(0,(host.height-h).coerceAtLeast(0))
        val params=FrameLayout.LayoutParams(stripWidth,h,Gravity.TOP or Gravity.START).apply{
            leftMargin=localX;topMargin=localTop
        }
        strip=root
        host.addView(root,params)
        leftOnScreen=parentPos[0]+localX
        highlight()
    }
    fun slide(rawX:Float) {
        if(!isShowing||choices.isEmpty())return
        val step=stripWidth.toFloat()/choices.size
        val index=((rawX-leftOnScreen)/step).toInt().coerceIn(0,choices.lastIndex)
        if(index!=selected){selected=index;highlight()}
    }
    private fun highlight(){
        for((i,cell) in cells.withIndex()){
            val active=i==selected
            cell.background=rounded(if(active)accent else Color.TRANSPARENT,9)
            cell.setTextColor(if(active) Color.BLACK else foreground)
        }
    }
    fun chosen():String? =if(isShowing)choices.getOrNull(selected) else null
    fun dismiss(){
        strip?.let{view->
            (view.parent as? FrameLayout)?.removeView(view)
        }
        strip=null;choices=emptyList();cells.clear()
    }
}
