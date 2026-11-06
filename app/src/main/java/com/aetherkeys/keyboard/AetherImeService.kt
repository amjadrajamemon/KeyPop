package com.aetherkeys.keyboard

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class AetherImeService : InputMethodService() {
    private val prefs by lazy { getSharedPreferences("keypop", Context.MODE_PRIVATE) }
    private val clipStore by lazy { ClipStore(this) }
    private val clipboard by lazy { getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    private var language = 0
    private var shift = false
    private var clipsOpen = false
    private val languages = listOf("EN", "اردو", "سنڌي")
    private val english = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    private val urdu = listOf("قwertyuiop", "ضصثقفغعهخح", "شسیبلاتنمک", "ظطزرذدپو")
    private val sindhi = listOf("قںڳٻڀٺٿٽپڄ", "حخدڌڏڊڙزسش", "صضطظعغفقڪک", "گههئوئيیبت")
    private var current: LinearLayout? = null

    override fun onCreate() {
        super.onCreate()
        clipboard.addPrimaryClipChangedListener {
            val info = currentInputEditorInfo
            val type = info?.inputType
            val klass = type?.and(InputType.TYPE_MASK_CLASS)
            val variation = type?.and(InputType.TYPE_MASK_VARIATION)
            val sensitive = info == null || klass == InputType.TYPE_NULL ||
                ((info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0) ||
                (klass == InputType.TYPE_CLASS_TEXT &&
                    (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                     variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                     variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
                (klass == InputType.TYPE_CLASS_NUMBER &&
                    variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
            if (!sensitive && prefs.getBoolean("history", true)) {
                try {
                    val clip = clipboard.primaryClip
                    if (clip != null && clip.itemCount > 0 && clip.description.hasMimeType("text/plain")) {
                        if (Build.VERSION.SDK_INT >= 33 && clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true) return@addPrimaryClipChangedListener
                        clip.getItemAt(0).text?.toString()?.let { clipStore.save(it) }
                    }
                } catch (_: Exception) { }
            }
        }
    }

    override fun onCreateInputView(): View {
        language = prefs.getInt("language", 0).coerceIn(0, 2)
        val view = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        current = view
        draw()
        return view
    }

    private fun colorTheme(): IntArray {
        val i = prefs.getInt("theme", 0).coerceIn(0, 14)
        val backgrounds = intArrayOf(0xFF080D23.toInt(), 0xFF091A2E.toInt(), 0xFF1F0837.toInt(), 0xFF211B35.toInt(), 0xFF1B281F.toInt(), 0xFF122625.toInt(), 0xFF211323.toInt(), 0xFF191919.toInt(), 0xFFE8EBF3.toInt(), 0xFF000000.toInt(), 0xFFF0F3F8.toInt(), 0xFF151B28.toInt(), 0xFF202329.toInt(), 0xFF272B32.toInt(), 0xFF1C2330.toInt())
        val keys = intArrayOf(0xFF27365F.toInt(), 0xFF125077.toInt(), 0xFF51346E.toInt(), 0xFF4A3A6B.toInt(), 0xFF38563C.toInt(), 0xFF255B58.toInt(), 0xFF57334C.toInt(), 0xFF424242.toInt(), 0xFFFFFFFF.toInt(), 0xFF212121.toInt(), 0xFFFFFFFF.toInt(), 0xFF3C475A.toInt(), 0xFF454B53.toInt(), 0xFF575F69.toInt(), 0xFF435471.toInt())
        val ink = if (i == 8 || i == 10) Color.BLACK else Color.WHITE
        return intArrayOf(backgrounds[i], keys[i], ink)
    }

    private fun draw() {
        val root = current ?: return
        root.removeAllViews()
        val c = colorTheme()
        root.setBackgroundColor(c[0])
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun action(label: String, callback: () -> Unit) {
            toolbar.addView(key(label, c, 40) { callback() }, LinearLayout.LayoutParams(0, dp(40), 1f))
        }
        action(languages[language]) { language = (language + 1) % 3; prefs.edit().putInt("language", language).apply(); clipsOpen=false; draw() }
        action("⌗") { clipsOpen = !clipsOpen; draw() }
        action("🎨") { prefs.edit().putInt("theme", (prefs.getInt("theme",0) + 1) % 15).apply(); draw() }
        action("⚙") { startActivity(android.content.Intent(this, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        root.addView(toolbar)
        if (clipsOpen) { showClips(root, c); return }
        val rows = when (language) { 1 -> urdu; 2 -> sindhi; else -> english }
        val keyHeight = prefs.getInt("height", 51).coerceIn(36, 90)
        if (prefs.getBoolean("numberRow", true)) addRow(root, "1234567890".map { it.toString() }, c, keyHeight)
        for ((idx, line) in rows.withIndex()) {
            val letters = line.map { it.toString() }
            if (idx == rows.lastIndex && language == 0) {
                addRow(root, listOf("⇧") + letters + listOf("⌫"), c, keyHeight)
            } else addRow(root, letters, c, keyHeight)
        }
        addRow(root, listOf("?123", "🌐", ",", "space", ".", "↵", "⌫"), c, keyHeight)
    }

    private fun addRow(parent: LinearLayout, labels: List<String>, c: IntArray, height: Int) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (label in labels) {
            val b = key(if (shift && label.length == 1) label.uppercase() else label, c, height) { press(label) }
            b.setOnLongClickListener {
                val alts = mapOf("a" to "@", "s" to "$", "d" to "#", "f" to "%", "g" to "&",
                    "q" to "1", "w" to "2", "e" to "3", "r" to "4", "t" to "5",
                    "y" to "6", "u" to "7", "i" to "8", "o" to "9", "p" to "0", "." to "…")
                val alt = alts[label] ?: return@setOnLongClickListener false
                currentInputConnection?.commitText(alt, 1)
                true
            }
            row.addView(b, LinearLayout.LayoutParams(0, dp(height), if (label == "space") 4f else if (label == "?123") 1.45f else 1f))
        }
        parent.addView(row)
    }

    private fun press(value: String) {
        when (value) {
            "⌫" -> currentInputConnection?.deleteSurroundingText(1, 0)
            "⇧" -> { shift = !shift; draw() }
            "space" -> currentInputConnection?.commitText(" ", 1)
            "↵" -> {
                val opts = currentInputEditorInfo?.imeOptions ?: 0
                val a = opts and EditorInfo.IME_MASK_ACTION
                if (a != EditorInfo.IME_ACTION_NONE && a != EditorInfo.IME_ACTION_UNSPECIFIED)
                    currentInputConnection?.performEditorAction(a)
                else currentInputConnection?.commitText("\n", 1)
            }
            "🌐" -> { language = (language + 1) % 3; prefs.edit().putInt("language", language).apply(); draw() }
            "?123" -> Toast.makeText(this, "Use the number row or long-press keys", Toast.LENGTH_SHORT).show()
            else -> currentInputConnection?.commitText(if (shift) value.uppercase() else value, 1)
        }
    }

    private fun key(label: String, c: IntArray, h: Int, fn: () -> Unit): Button {
        val bg = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c[1], c[0])).apply {
            cornerRadius = dp(if (prefs.getInt("theme", 0) % 5 == 0) 20 else 9).toFloat()
            setStroke(dp(1), c[1])
        }
        return Button(this).apply {
            text = label
            textSize = if (label == "space") 13f else 16f
            isAllCaps = false
            setTextColor(c[2])
            background = bg
            gravity = Gravity.CENTER
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            setPadding(0,0,0,0)
            setOnClickListener { fn() }
        }
    }

    private fun showClips(root: LinearLayout, c: IntArray) {
        val caption = TextView(this).apply { text = "Clipboard · encrypted on device · long-press to pin"; setTextColor(c[2]); setPadding(dp(10),dp(8),0,dp(8)) }
        root.addView(caption)
        val scroll = ScrollView(this)
        val items = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (clip in clipStore.list().take(30)) {
            val title = if (clip.pinned) "📌 " else ""
            val b = key(title + clip.text.take(72), c, 56) { currentInputConnection?.commitText(clip.text, 1); clipsOpen=false; draw() }
            b.setOnLongClickListener { clipStore.pin(clip.text, !clip.pinned); draw(); true }
            items.addView(b)
        }
        scroll.addView(items)
        root.addView(scroll, LinearLayout.LayoutParams(-1, dp(210)))
        root.addView(key("Clear history", c, 38) { clipStore.clear(); draw() })
        root.addView(key("Back to keyboard", c, 38) { clipsOpen=false; draw() })
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
