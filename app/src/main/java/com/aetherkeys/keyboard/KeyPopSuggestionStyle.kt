package com.aetherkeys.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout

/**
 * One source of truth for the suggestion strip and its Settings live preview.
 * User colors are local SharedPreferences; defaults inherit the active theme.
 * A preference change is reflected on the next keyboard opening and instantly
 * within the live editor, with no layout rebuild while a key is being pressed.
 */
object KeyPopSuggestionStyle {
    const val ABOVE_TOOLBAR=0
    const val BELOW_TOOLBAR=1
    val paletteNames=listOf("Follow keyboard theme","Light contrast","AMOLED black",
        "Ocean teal","Royal purple","Sunset","Custom colors")
    private val paletteBackground=intArrayOf(0,0xFFF1F6FB.toInt(),0xFF0C0F13.toInt(),
        0xFF073F47.toInt(),0xFF30254E.toInt(),0xFF5D2634.toInt(),0)
    private val paletteText=intArrayOf(0,0xFF1A2E3D.toInt(),Color.WHITE,
        Color.WHITE,Color.WHITE,Color.WHITE,0)
    private val paletteChip=intArrayOf(0,0xFFE2EDF5.toInt(),0xFF263440.toInt(),
        0xFF166A72.toInt(),0xFF514078.toInt(),0xFF824258.toInt(),0)

    fun position(c:Context):Int=prefs(c).getInt("suggestionPosition",ABOVE_TOOLBAR)
        .coerceIn(ABOVE_TOOLBAR,BELOW_TOOLBAR)
    fun fontSize(c:Context):Int=prefs(c).getInt("suggestionFont",17).coerceIn(12,32)
    fun heightDp(c:Context):Int=prefs(c).getInt("suggestionHeight",45).coerceIn(38,78)
    fun gapDp(c:Context):Int=prefs(c).getInt("suggestionGap",4).coerceIn(0,16)
    fun radiusDp(c:Context):Int=prefs(c).getInt("suggestionCorner",12).coerceIn(0,28)
    fun opacity(c:Context):Int=prefs(c).getInt("suggestionOpacity",100).coerceIn(45,100)
    fun bold(c:Context):Boolean=prefs(c).getBoolean("suggestionBold",false)
    fun palette(c:Context):Int=prefs(c).getInt("suggestionPalette",0)
        .coerceIn(0,paletteNames.lastIndex)
    private fun prefs(c:Context)=c.getSharedPreferences("keypop",Context.MODE_PRIVATE)
    private fun dp(c:Context,v:Int)=(v*c.resources.displayMetrics.density).toInt()
    private fun withOpacity(color:Int,alpha:Int):Int =
        Color.argb((alpha*255/100).coerceIn(0,255),
            Color.red(color),Color.green(color),Color.blue(color))

    fun backgroundColor(c:Context):Int {
        val p=palette(c)
        val color=when(p) {
            0->KeyPopSkins.active(c).surface
            6->prefs(c).getInt("suggestionBackgroundColor",0xFF213746.toInt())
            else->paletteBackground[p]
        }
        return withOpacity(color,opacity(c))
    }
    fun chipColor(c:Context):Int {
        val p=palette(c)
        return when(p) {
            0->KeyPopSkins.active(c).key
            6->prefs(c).getInt("suggestionChipColor",0xFF38566B.toInt())
            else->paletteChip[p]
        }
    }
    fun textColor(c:Context):Int {
        val p=palette(c)
        return when(p) {
            0->KeyPopSkins.active(c).ink
            6->prefs(c).getInt("suggestionTextColor",Color.WHITE)
            else->paletteText[p]
        }
    }
    private fun panel(c:Context,color:Int,radius:Int):GradientDrawable=
        GradientDrawable().apply {
            setColor(color)
            cornerRadius=dp(c,radius).toFloat()
        }

    fun applyRow(c:Context,row:LinearLayout) {
        row.orientation=LinearLayout.HORIZONTAL
        row.gravity=Gravity.CENTER_VERTICAL
        row.visibility=android.view.View.VISIBLE
        row.background=panel(c,backgroundColor(c),radiusDp(c))
        row.contentDescription="KeyPop word prediction and suggestion row"
        row.setPadding(dp(c,2),dp(c,1),dp(c,2),dp(c,1))
        for(i in 0 until row.childCount) {
            val button=row.getChildAt(i) as? Button ?:continue
            applyChip(c,button)
        }
    }

    fun applyChip(c:Context,button:Button) {
        button.textSize=fontSize(c).toFloat()
        button.setTextColor(textColor(c))
        button.typeface=Typeface.create("sans-serif-medium",
            if(bold(c))Typeface.BOLD else Typeface.NORMAL)
        button.isAllCaps=false
        button.gravity=Gravity.CENTER
        button.setSingleLine(true)
        button.ellipsize=android.text.TextUtils.TruncateAt.END
        button.minHeight=0
        button.minimumHeight=0
        button.minWidth=0
        button.minimumWidth=0
        button.setPadding(dp(c,3),0,dp(c,3),0)
        button.background=panel(c,chipColor(c),radiusDp(c))
        val margin=dp(c,gapDp(c))/2
        val params=button.layoutParams as? LinearLayout.LayoutParams
        if(params!=null) {
            params.height=dp(c,heightDp(c)-2)
            params.leftMargin=margin
            params.rightMargin=margin
            button.layoutParams=params
        }
    }
    fun addChip(c:Context,row:LinearLayout,button:Button) {
        val margin=dp(c,gapDp(c))/2
        row.addView(button,LinearLayout.LayoutParams(0,dp(c,heightDp(c)-2),1f).apply {
            leftMargin=margin;rightMargin=margin
        })
        applyChip(c,button)
    }
}
