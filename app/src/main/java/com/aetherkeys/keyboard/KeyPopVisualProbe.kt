package com.aetherkeys.keyboard

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout

/** Checks attached keyboard views only for KeyPop's dedicated debug test fields. */
object KeyPopVisualProbe {
    const val FIELD_PREFIX="com.aetherkeys.keyboard.predictionSmoke:"

    fun capture(context:Context,info:EditorInfo?,row:LinearLayout,
                toolbar:LinearLayout?,typed:String) {
        if(info==null || context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE==0 ||
            info.packageName!=context.packageName)return
        val marker=info.privateImeOptions ?:return
        if(!marker.startsWith(FIELD_PREFIX))return
        row.postDelayed({
            if(!row.isAttachedToWindow || row.parent==null)return@postDelayed
            val bounds=Rect()
            val visible=row.isShown && row.getGlobalVisibleRect(bounds) && !bounds.isEmpty
            val chips=if(visible)(0 until row.childCount).mapNotNull { index ->
                (row.getChildAt(index) as? Button)?.takeIf {
                    it.isShown && it.text.isNotBlank() && it.getGlobalVisibleRect(Rect())
                }
            } else emptyList()
            val alternatives=chips.filter {
                it.isEnabled && it.contentDescription?.startsWith("Suggestion: ")==true &&
                    !it.text.toString().equals(typed,ignoreCase=true)
            }
            val choice=alternatives.firstOrNull {
                it.text.startsWith("improvem",ignoreCase=true)
            }
            val point=IntArray(2)
            choice?.getLocationOnScreen(point)
            val parent=row.parent as? LinearLayout
            val placement=if(parent!=null && toolbar?.parent===parent) {
                if(parent.indexOfChild(row)<parent.indexOfChild(toolbar))"above" else "below"
            } else ""
            context.getSharedPreferences("keypop_visual_probe",Context.MODE_PRIVATE).edit()
                .putString("field",marker.removePrefix(FIELD_PREFIX))
                .putBoolean("active_visible",visible)
                .putInt("visible_chips",chips.size)
                .putString("suggestions",alternatives.joinToString("|"){it.text.toString()})
                .putString("typed",typed.take(64))
                .putString("placement",placement)
                .putInt("row_height_px",bounds.height())
                .putInt("choice_x",if(choice==null)0 else point[0]+choice.width/2)
                .putInt("choice_y",if(choice==null)0 else point[1]+choice.height/2)
                .putLong("updated_uptime",SystemClock.uptimeMillis())
                .apply()
        },120L)
    }
}
