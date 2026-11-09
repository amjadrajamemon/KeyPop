package com.aetherkeys.keyboard

/**
 * Short-lived, RAM-only editor mirror for the IME's own committed text.
 *
 * Android InputConnection.getTextBeforeCursor() can require cross-process
 * communication with the target app. Instead of fetching 120-140 characters
 * on EVERY tap, the keyboard seeds once and mirrors only what it inserted.
 * Any external caret movement or selection invalidates the mirror so the
 * next read falls back to the real editor. Nothing is persisted or logged.
 */
class KeyPopEditorContext(private val capacity:Int=256) {
    private var before:String?=null
    private var expectedSelection:Int?=null
    // Android may report selection acknowledgments after several quick keys.
    // Accept any still-pending local cursor position rather than clearing the
    // mirror on every delayed input-connection callback.
    private val pendingSelections=ArrayDeque<Int>()
    fun clear() {
        before=null
        expectedSelection=null
        pendingSelections.clear()
    }
    fun valid():Boolean=before!=null
    fun text():String?=before
    fun seed(value:String,selection:Int?=null) {
        before=value.takeLast(capacity.coerceAtLeast(32))
        expectedSelection=selection?.takeIf{it>=0}
        pendingSelections.clear()
    }
    /** Called BEFORE invoking commitText(), because some editors call back
     * synchronously from commitText(). On failure the caller must clear(). */
    fun committed(value:String) {
        val old=before?:return
        before=(old+value).takeLast(capacity)
        expectedSelection=expectedSelection?.plus(value.length)
        markPending()
    }
    fun deletedCharacters(count:Int) {
        val old=before?:return
        before=old.dropLast(count.coerceAtLeast(0))
        expectedSelection=expectedSelection?.minus(count)?.coerceAtLeast(0)
        markPending()
    }
    private fun markPending() {
        expectedSelection?.let{
            pendingSelections.addLast(it)
            if(pendingSelections.size>64)pendingSelections.removeFirst()
        }
    }
    fun deletedCodePoint() {
        val old=before?:return
        if(old.isEmpty())return
        val length=if(old.length>=2 && Character.isLowSurrogate(old.last()) &&
            Character.isHighSurrogate(old[old.lastIndex-1]))2 else 1
        deletedCharacters(length)
    }
    /**
     * True means the app acknowledged a selection that matches what our own
     * commit predicted. False requires an actual remote editor snapshot.
     */
    fun selectionChanged(start:Int,end:Int):Boolean {
        if(start<0||start!=end) {clear();return false}
        if(before==null)return false
        if(expectedSelection==null) {
            expectedSelection=start
            return true
        }
        if(start==expectedSelection){
            pendingSelections.clear()
            return true
        }
        if(start in pendingSelections) {
            while(pendingSelections.isNotEmpty()) {
                if(pendingSelections.removeFirst()==start)break
            }
            return true
        }
        clear()
        return false
    }
}
