package com.aetherkeys.keyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("keypop", MODE_PRIVATE)
        val shell = ScrollView(this)
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(25), dp(22), dp(25))
        }
        fun label(s: String) { view.addView(TextView(this).apply { text=s; textSize=18f; setPadding(0,dp(9),0,dp(9)) }) }
        fun button(s: String, f: () -> Unit) {
            view.addView(Button(this).apply { text=s; setOnClickListener { f() } })
        }
        label("KeyPop · AetherKeys — Android keyboard alpha")
        label("English · اردو · سنڌي")
        button("Enable keyboard in Android settings") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        button("Select KeyPop keyboard") { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() }
        label("15 built-in visual profiles")
        val names = listOf("Nebula Glass","Ocean Flux","Violet Matrix","Aurora","Forest Pulse","Mint Orbit","Rose Nebula","Carbon","Cloud White","AMOLED Black","Minimal Light","Familiar Google-style","Familiar Swift-style","Familiar Fleksy-style","Familiar Typewise-style","Familiar Samsung-style")
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        spinner.setSelection(prefs.getInt("theme", 0).coerceIn(0,14))
        view.addView(spinner)
        button("Save selected profile") { prefs.edit().putInt("theme", spinner.selectedItemPosition).apply() }
        val number = CheckBox(this).apply {
            text="Show dedicated number row"
            isChecked=prefs.getBoolean("numberRow", true)
            setOnCheckedChangeListener { _, v -> prefs.edit().putBoolean("numberRow", v).apply() }
        }
        view.addView(number)
        val history = CheckBox(this).apply {
            text="Store clipboard text history (encrypted locally)"
            isChecked=prefs.getBoolean("history", true)
            setOnCheckedChangeListener { _, v -> prefs.edit().putBoolean("history", v).apply() }
        }
        view.addView(history)
        label("Key height")
        val size = SeekBar(this).apply {
            max=54
            progress=(prefs.getInt("height", 51)-36).coerceIn(0,54)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) {
                    if (user) prefs.edit().putInt("height", p+36).apply()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        view.addView(size)
        label("First GitHub build: core typing, styles, clipboard and sizing. Translation, Autofill, voice and image sharing from the source ZIP are not included in this smoke-build subset.")
        shell.addView(view)
        setContentView(shell)
    }
    private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()
}
