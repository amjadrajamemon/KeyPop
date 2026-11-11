package com.aetherkeys.keyboard

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.LinearLayout

/**
 * DEBUGGABLE builds only. Records the *attached IME view* geometry, selected
 * toolbar relationship and current visible prediction text. Unlike
 * UIAutomator dumps, this does not depend on an unstable accessibility root.
 * CI reads the values using adb run-as; no typed content is uploaded by
 * release APKs. The probe file is absent in production-signed builds.
 */
object KeyPopVisualProbe {
    fun capture(context:Context,row:LinearLayout,toolbar:LinearLayout?,
                typed:String) {
        if(context.applicationInfo.flags and
            ApplicationInfo.FLAG_DEBUGGABLE==0)return
        row.postDelayed({
            if(row.parent==null)return@postDelayed
            val bounds=Rect()
            val visible=row.visibility==View.VISIBLE && row.isShown &&
                row.getGlobalVisibleRect(bounds) && bounds.width()>0 &&
                bounds.height()>0
            val displayed=(0 until row.childCount).mapNotNull{index ->
                (row.getChildAt(index) as? Button)?.takeIf {
                    it.visibility==View.VISIBLE && it.text.isNotBlank()
                }?.text?.toString()
            }
            val relative=if(toolbar!=null && row.parent===toolbar.parent) {
                val parent=row.parent as? LinearLayout
                val rowIndex=parent?.indexOfChild(row)?:-1
                val toolbarIndex=parent?.indexOfChild(toolbar)?:-1
                if(rowIndex>=0 && toolbarIndex>=0) {
                    if(rowIndex<toolbarIndex)"above" else "below"
                }else "unknown"
            }else "unknown"
            context.getSharedPreferences("keypop_visual_probe",
                Context.MODE_PRIVATE).edit()
                .putBoolean("active_visible",visible)
                .putInt("visible_chips",displayed.size)
                .putString("labels",displayed.joinToString("|"))
                .putString("typed",typed.take(64))
                .putString("placement",relative)
                .putInt("row_height_px",bounds.height())
                .putLong("updated_uptime",SystemClock.uptimeMillis())
                .apply()
        },160L)
    }
}
