package com.vinayak.keyboard

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

object VoiceBridge {
    var listener: ((String) -> Unit)? = null
    fun deliver(t: String) { listener?.invoke(t) }
}

class VoiceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            @Suppress("DEPRECATION")
            startActivityForResult(i, 1)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No speech recognizer installed", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { VoiceBridge.deliver(it) }
        }
        finish()
    }
}

class SettingsActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val d = resources.displayMetrics.density
        fun px(v: Int) = (v * d).toInt()

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(40), px(20), px(28))
        }
        setContentView(ScrollView(this).apply { addView(col) })

        col.addView(TextView(this).apply { text = "V Keyboard"; textSize = 28f })
        status = TextView(this).apply { textSize = 14f; setPadding(0, px(8), 0, px(12)) }
        col.addView(status)

        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        col.addView(Button(this).apply {
            text = "1. Enable V Keyboard"
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        })
        col.addView(Button(this).apply {
            text = "2. Switch to V Keyboard"
            setOnClickListener { imm.showInputMethodPicker() }
        })

        fun sw(label: String, key: String, def: Boolean) {
            col.addView(Switch(this).apply {
                text = label; textSize = 16f
                setPadding(0, px(10), 0, px(10))
                isChecked = prefs.bool(key, def)
                setOnCheckedChangeListener { _, v -> prefs.put(key, v) }
            })
        }
        col.addView(TextView(this).apply { text = "Typing"; textSize = 13f; setPadding(0, px(18), 0, 0) })
        sw("Word suggestions", "suggest", true)
        sw("Auto-correct", "autocorrect", true)
        sw("Auto-capitalise", "autocap", true)
        sw("Swipe typing", "swipe", true)
        sw("Double space inserts full stop", "dblspace", true)
        sw("Number row", "numrow", true)
        col.addView(TextView(this).apply { text = "Feedback"; textSize = 13f; setPadding(0, px(18), 0, 0) })
        sw("Vibrate on keypress", "haptic", true)
        sw("Sound on keypress", "sound", false)
        sw("Key preview popup", "preview", true)

        col.addView(TextView(this).apply { text = "Appearance"; textSize = 13f; setPadding(0, px(18), 0, 0) })
        val themeNames = listOf("Follow system", "Light", "Dark")
        col.addView(Button(this).apply {
            text = "Theme: ${themeNames[prefs.int("theme", 0)]}"
            setOnClickListener {
                val n = (prefs.int("theme", 0) + 1) % 3
                prefs.put("theme", n)
                text = "Theme: ${themeNames[n]}"
            }
        })
        val hLabel = TextView(this).apply { textSize = 16f; setPadding(0, px(12), 0, 0) }
        fun setH(v: Int) { hLabel.text = "Key height: ${v}dp" }
        setH(prefs.int("keyh", 52))
        col.addView(hLabel)
        col.addView(SeekBar(this).apply {
            max = 32
            progress = prefs.int("keyh", 52) - 40
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { setH(p + 40) }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) { prefs.put("keyh", progress + 40) }
            })
        })

        col.addView(TextView(this).apply { text = "Test it"; textSize = 13f; setPadding(0, px(22), 0, 0) })
        col.addView(EditText(this).apply { hint = "Type here to test the keyboard" })
    }

    override fun onResume() {
        super.onResume()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val cur = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.startsWith(packageName) == true
        status.text = when {
            cur -> "Status: V Keyboard is active."
            enabled -> "Status: enabled. Now tap step 2 to select it."
            else -> "Status: not enabled yet. Tap step 1."
        }
    }
}
