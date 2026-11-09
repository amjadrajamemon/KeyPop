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
    fun clear() {before=null;expectedSelection=null}
    fun valid():Boolean=before!=null
    fun text():String?=before
    fun seed(value:String,selection:Int?=null) {
        before=value.takeLast(capacity.coerceAtLeast(32))
        expectedSelection=selection?.takeIf{it>=0}
    }
    /** Called BEFORE invoking commitText(), because some editors call back
     * synchronously from commitText(). On failure the caller must clear(). */
    fun committed(value:String) {
        val old=before?:return
        before=(old+value).takeLast(capacity)
        expectedSelection=expectedSelection?.plus(value.length)
    }
    fun deletedCharacters(count:Int) {
        val old=before?:return
        before=old.dropLast(count.coerceAtLeast(0))
        expectedSelection=expectedSelection?.minus(count)?.coerceAtLeast(0)
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
        val value=before?:return false
        if(expectedSelection!=null && start!=expectedSelection) {
            clear()
            return false
        }
        expectedSelection=start
        return true
    }
}
