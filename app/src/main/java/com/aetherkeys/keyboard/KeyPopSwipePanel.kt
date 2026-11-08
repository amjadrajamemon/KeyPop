package com.aetherkeys.keyboard

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.LinearLayout
import kotlin.math.abs

/** Simple touch-only page navigator. Buttons and arrows still work, but a
 * deliberate horizontal swipe anywhere over the tool tiles changes pages. */
object KeyPopPagerSwipeRules {
    fun direction(deltaX:Float,deltaY:Float,threshold:Float):Int {
        if(abs(deltaX)<threshold || abs(deltaX)<abs(deltaY)*1.3f)return 0
        return if(deltaX<0f)1 else -1
    }
}
class KeyPopSwipePanel(context:Context,private val onPageChange:(Int)->Unit):
    LinearLayout(context) {
    private val slop=ViewConfiguration.get(context).scaledTouchSlop
    private val threshold=maxOf(48f*resources.displayMetrics.density,slop*2f)
    private var initialX=0f
    private var initialY=0f
    private var swiping=false
    override fun onInterceptTouchEvent(event:MotionEvent):Boolean {
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX=event.x;initialY=event.y;swiping=false
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx=event.x-initialX;val dy=event.y-initialY
                if(abs(dx)>slop && abs(dx)>abs(dy)*1.3f) {
                    swiping=true
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL,MotionEvent.ACTION_UP ->swiping=false
        }
        return super.onInterceptTouchEvent(event)
    }
    override fun onTouchEvent(event:MotionEvent):Boolean {
        when(event.actionMasked) {
            MotionEvent.ACTION_MOVE ->return swiping
            MotionEvent.ACTION_UP -> {
                if(swiping) {
                    val direction=KeyPopPagerSwipeRules.direction(
                        event.x-initialX,event.y-initialY,threshold)
                    swiping=false
                    if(direction!=0)onPageChange(direction)
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> {swiping=false;return true}
        }
        return super.onTouchEvent(event)
    }
}
