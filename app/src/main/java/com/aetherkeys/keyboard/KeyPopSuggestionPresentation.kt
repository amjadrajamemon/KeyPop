package com.aetherkeys.keyboard

import android.view.View
import android.widget.Button
import android.widget.LinearLayout

/** A visible, accessible prediction row must never turn into three
 * INVISIBLE buttons merely because an editor is empty or the language
 * model is warming up. Applied to the actual IME views, not a mock. */
object KeyPopSuggestionPresentation {
    const val WAITING_TEXT="Type to see suggestions"
    const val CONTINUE_TEXT="Continue typing"

    fun waiting(bar:LinearLayout,hasContext:Boolean=false) {
        bar.visibility=View.VISIBLE
        val middle=bar.childCount/2
        for(i in 0 until bar.childCount){
            val chip=bar.getChildAt(i) as? Button ?:continue
            chip.visibility=if(i==middle)View.VISIBLE else View.INVISIBLE
            chip.text=if(i==middle) {
                if(hasContext)CONTINUE_TEXT else WAITING_TEXT
            }else ""
            chip.isEnabled=false
            chip.contentDescription=if(i==middle)chip.text.toString()
                else "No prediction available"
        }
        bar.requestLayout()
    }

    fun composing(bar:LinearLayout,typed:String) {
        if(typed.isBlank()){waiting(bar);return}
        bar.visibility=View.VISIBLE
        val middle=bar.childCount/2
        for(i in 0 until bar.childCount){
            val chip=bar.getChildAt(i) as? Button ?:continue
            chip.visibility=if(i==middle)View.VISIBLE else View.INVISIBLE
            chip.text=if(i==middle)typed else ""
            chip.isEnabled=false
            chip.contentDescription=if(i==middle)"Typed word: "+typed
                else "Prediction pending"
        }
        bar.requestLayout()
    }

    fun markAvailable(button:Button,label:String) {
        button.visibility=View.VISIBLE
        button.isEnabled=true
        button.contentDescription="Suggestion: "+label
    }
}
