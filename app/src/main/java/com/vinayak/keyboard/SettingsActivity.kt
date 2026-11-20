package com.vinayak.keyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.io.File

object VoiceBridge {
    var listener: ((String) -> Unit)? = null
    fun deliver(t: String) { listener?.invoke(t) }
}

class VoiceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val prefs = Prefs(this)
            val language = prefs.str("voice_lang", "en-IN")
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to type")
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
    private lateinit var previewBox: FrameLayout
    private lateinit var themeGrid: GridLayout
    private lateinit var col: LinearLayout
    private val swatchRows = HashMap<String, View>()
    private var d = 1f
    private fun px(v: Int) = (v * d).toInt()

    private val noop = object : KeyboardView.Callback {
        override fun onKey(text: String) {}
        override fun onDelete() {}
        override fun onEnter() {}
        override fun onGlobe(long: Boolean) {}
        override fun onCursor(steps: Int) {}
        override fun onSwipe(path: List<PointF>) {}
        override fun onFeedback() {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        d = resources.displayMetrics.density
        val uiTheme = Theme.resolve(this, prefs)
        col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(28), px(20), px(36))
            setBackgroundColor(if (uiTheme.dark) 0xFF10141C.toInt() else 0xFFF4F6FA.toInt())
        }
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(if (uiTheme.dark) 0xFF10141C.toInt() else 0xFFF4F6FA.toInt())
            clipToPadding = false
            addView(col)
        })

        col.addView(TextView(this).apply {
            text = "V Keyboard"
            textSize = 30f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            setTextColor(if (uiTheme.dark) 0xFFF5F7FC.toInt() else 0xFF172033.toInt())
            setPadding(0, px(4), 0, px(2))
        })
        col.addView(TextView(this).apply {
            text = "Your keyboard, your style."
            textSize = 14f
            setTextColor(if (uiTheme.dark) 0xFFADB8C9.toInt() else 0xFF68758A.toInt())
            setPadding(0, 0, 0, px(18))
        })
        status = TextView(this).apply { textSize = 14f; setPadding(0, px(6), 0, px(10)) }
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

        // ---- live preview
        section("Live preview")
        previewBox = FrameLayout(this)
        col.addView(previewBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        refreshPreview()

        // ---- themes
        section("Theme")
        themeGrid = GridLayout(this).apply { columnCount = 3 }
        col.addView(themeGrid)
        buildThemeGrid()

        section("Custom theme colours (tap to edit)")
        colorRow("Background", "c_bg", 0xFFF4F0F1.toInt())
        colorRow("Keys", "c_key", 0xFFF7F5F6.toInt())
        colorRow("Text and icons", "c_text", 0xFF000000.toInt())
        colorRow("Accent", "c_accent", 0xFF4272C4.toInt())

        // ---- appearance
        section("Appearance")
        seek("Key height", "keyscale", 70, 130, 100, "%")
        seek("Key corner radius", "keyradius", 0, 45, 20, "")
        seek("Text size", "fontscale", 80, 130, 100, "%")
        sw("Key borders", "keyborder", false)
        sw("Bottom bar (mic and hide-keyboard button)", "bottombar", true)
        col.addView(Button(this).apply {
            text = "Choose background photo"
            setOnClickListener {
                @Suppress("DEPRECATION")
                startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), 11)
            }
        })
        col.addView(Button(this).apply {
            text = "Remove background photo"
            setOnClickListener {
                File(filesDir, "bg.jpg").delete()
                prefs.put("bgimg", "")
                refreshPreview()
            }
        })
        seek("Photo dimming", "bgdim", 0, 80, 35, "%")

        // ---- voice typing
        section("Voice typing")
        col.addView(TextView(this).apply {
            text = "Choose the language used when you tap the microphone on the keyboard."
            textSize = 13f
        })
        val voiceLanguages = listOf(
            "English (India)" to "en-IN",
            "English (United States)" to "en-US",
            "Hindi" to "hi-IN",
            "Malayalam" to "ml-IN",
            "Kannada" to "kn-IN",
            "Tamil" to "ta-IN"
        )
        val voiceSpinner = Spinner(this)
        voiceSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            voiceLanguages.map { it.first }
        )
        voiceSpinner.setSelection(voiceLanguages.indexOfFirst { it.second == prefs.str("voice_lang", "en-IN") }.coerceAtLeast(0))
        voiceSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.put("voice_lang", voiceLanguages[position].second)
            }
        }
        col.addView(voiceSpinner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(52)))

        // ---- typing
        section("Typing")
        sw("Word suggestions", "suggest", true)
        sw("Auto-correct", "autocorrect", true)
        sw("Auto-capitalise", "autocap", true)
        sw("Swipe typing", "swipe", true)
        sw("Double space inserts full stop", "dblspace", true)
        sw("Number row", "numrow", true)

        section("Feedback")
        sw("Vibrate on keypress", "haptic", true)
        sw("Sound on keypress", "sound", false)
        sw("Key preview popup", "preview", true)

        // ---- GIFs
        section("GIFs and stickers")
        col.addView(TextView(this).apply {
            text = "GIF and sticker search uses Giphy. Get a free API key at developers.giphy.com (Create an App, choose API), then paste it here."
            textSize = 13f
        })
        col.addView(EditText(this).apply {
            hint = "Giphy API key"
            setText(prefs.str("giphy", ""))
            isSingleLine = true
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { prefs.put("giphy", s?.toString()?.trim() ?: "") }
            })
        })

        section("Test it")
        col.addView(EditText(this).apply { hint = "Type here to test the keyboard" })
    }

    // ---------------------------------------------------------------- helpers
    private fun section(title: String) {
        val dark = Theme.resolve(this, prefs).dark
        col.addView(TextView(this).apply {
            text = title.uppercase()
            textSize = 11f
            letterSpacing = 0.10f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setPadding(px(2), px(26), 0, px(10))
            setTextColor(if (dark) 0xFF91BFFF.toInt() else 0xFF3867D6.toInt())
        })
    }

    private fun sw(label: String, key: String, def: Boolean) {
        col.addView(Switch(this).apply {
            text = label; textSize = 16f
            setPadding(0, px(8), 0, px(8))
            isChecked = prefs.bool(key, def)
            setOnCheckedChangeListener { _, v -> prefs.put(key, v); refreshPreview() }
        })
    }

    private fun seek(label: String, key: String, min: Int, max: Int, def: Int, unit: String) {
        val l = TextView(this).apply { textSize = 15f; setPadding(0, px(10), 0, 0) }
        fun set(v: Int) { l.text = "$label: $v$unit" }
        val cur = prefs.int(key, def)
        set(cur)
        col.addView(l)
        col.addView(SeekBar(this).apply {
            this.max = max - min
            progress = cur - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { set(p + min) }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) { prefs.put(key, progress + min); refreshPreview() }
            })
        })
    }

    private fun refreshPreview() {
        val t = Theme.resolve(this, prefs)
        previewBox.animate().cancel()
        previewBox.animate().alpha(0.72f).setDuration(90).withEndAction {
            previewBox.background = ThemeBgDrawable(t)
            previewBox.removeAllViews()
            previewBox.addView(KeyboardView(this, prefs, t, noop),
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            previewBox.animate().alpha(1f).setDuration(180).start()
        }.start()
    }

    private fun buildThemeGrid() {
        themeGrid.removeAllViews()
        val sysDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val cw = ((resources.displayMetrics.widthPixels - px(36)) / 3f)
        val u = cw / 330f
        val cur = prefs.int("theme", 0)
        val ui = Theme.preset(1)
        for (id in Theme.NAMES.indices) {
            val s = ThemeSwatch(this, Theme.preset(id, sysDark, prefs), Theme.NAMES[id], ui, u, id == cur)
            s.setOnClickListener { prefs.put("theme", id); buildThemeGrid(); refreshPreview() }
            val g = GridLayout.LayoutParams()
            g.width = cw.toInt(); g.height = (210f * u).toInt()
            themeGrid.addView(s, g)
        }
    }

    private fun colorRow(label: String, key: String, def: Int) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.setPadding(0, px(10), 0, px(10))
        row.addView(TextView(this).apply { text = label; textSize = 16f }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val chip = View(this)
        fun paint() {
            chip.background = GradientDrawable().apply {
                setColor(prefs.int(key, def)); cornerRadius = px(8).toFloat(); setStroke(px(1), 0xFF888888.toInt())
            }
        }
        paint()
        row.addView(chip, LinearLayout.LayoutParams(px(44), px(30)))
        row.setOnClickListener {
            pickColor(label, prefs.int(key, def)) { c ->
                prefs.put(key, c)
                prefs.put("theme", 9)
                paint(); buildThemeGrid(); refreshPreview()
            }
        }
        col.addView(row)
    }

    private fun pickColor(title: String, start: Int, onPick: (Int) -> Unit) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(22), px(14), px(22), 0) }
        val preview = View(this)
        val rgb = intArrayOf(start shr 16 and 255, start shr 8 and 255, start and 255)
        fun cur() = (0xFF shl 24) or (rgb[0] shl 16) or (rgb[1] shl 8) or rgb[2]
        fun paint() { preview.setBackgroundColor(cur()) }
        box.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(54)))
        for ((i, n) in listOf("Red", "Green", "Blue").withIndex()) {
            box.addView(TextView(this).apply { text = n; setPadding(0, px(8), 0, 0) })
            box.addView(SeekBar(this).apply {
                max = 255; progress = rgb[i]
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { rgb[i] = p; paint() }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            })
        }
        paint()
        AlertDialog.Builder(this).setTitle(title).setView(box)
            .setPositiveButton("OK") { _, _ -> onPick(cur()) }
            .setNegativeButton("Cancel", null).show()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 11 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return
            val f = File(filesDir, "bg.jpg")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            prefs.put("bgimg", f.absolutePath)
            refreshPreview()
            Toast.makeText(this, "Background set", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't load that photo", Toast.LENGTH_LONG).show()
        }
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
