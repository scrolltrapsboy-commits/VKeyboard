package com.vinayak.keyboard

import android.content.ClipboardManager
import android.content.Intent
import android.graphics.PointF
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class KeyboardService : InputMethodService(), KeyboardView.Callback {

    private lateinit var prefs: Prefs
    private lateinit var dict: Dictionary
    private lateinit var theme: Theme
    private lateinit var clipboard: ClipboardManager
    private val h = Handler(Looper.getMainLooper())

    private var kv: KeyboardView? = null
    private var topBar: FrameLayout? = null
    private var toolbar: LinearLayout? = null
    private var suggestionBar: LinearLayout? = null
    private var moreBar: LinearLayout? = null
    private var emojiPanel: EmojiPanel? = null
    private var clipPanel: ClipPanel? = null
    private val chips = ArrayList<TextView>()

    private var builtVer = -1
    private var moreOpen = false
    private var panelOpen = false
    private var lastInfo: EditorInfo? = null

    private var lastFix: Pair<String, String>? = null
    private var swipeWord: String? = null
    private var swipeCap = false
    private var swipeSlots: List<String>? = null
    private var slots: List<String> = listOf("", "", "")

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        try {
            val t = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
            if (!t.isNullOrBlank()) prefs.addClip(t)
        } catch (_: Exception) {
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        dict = Dictionary(this, prefs)
        dict.load()
        clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.addPrimaryClipChangedListener(clipListener)
        VoiceBridge.listener = { text ->
            h.postDelayed({ currentInputConnection?.commitText("$text ", 1) }, 600)
        }
    }

    override fun onDestroy() {
        clipboard.removePrimaryClipChangedListener(clipListener)
        VoiceBridge.listener = null
        super.onDestroy()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View = buildRoot()

    // ------------------------------------------------------------------ UI
    private fun buildRoot(): View {
        theme = Theme.resolve(this, prefs)
        builtVer = prefs.ver
        val d = resources.displayMetrics.density
        val dm = resources.displayMetrics
        val oh = prefs.int("onehand", 0)
        val availW = if (oh == 0) dm.widthPixels else (dm.widthPixels * 0.8f).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.bg)
            when (oh) {
                1 -> setPadding(0, 0, (dm.widthPixels * 0.2f).toInt(), 0)
                2 -> setPadding((dm.widthPixels * 0.2f).toInt(), 0, 0, 0)
            }
        }

        // ---- toolbar (shown when not typing)
        val tb = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fun tool(kind: String, action: () -> Unit) {
            val v = IconView(this, kind, theme.text)
            v.setOnClickListener { v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); action() }
            tb.addView(v, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }
        tool("emoji") { openPanel(emojiPanel) }
        tool("clip") { clipPanel?.refresh(); openPanel(clipPanel) }
        tool("mic") { startVoice() }
        tool("hand") { cycleOneHanded() }
        tool("gear") {
            startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        tool("more") { moreOpen = true; updateTopBar() }
        toolbar = tb

        // ---- suggestion strip
        val sb = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        chips.clear()
        for (i in 0 until 3) {
            val t = TextView(this).apply {
                textSize = 17f; setTextColor(theme.text); gravity = Gravity.CENTER
                maxLines = 1
                if (i == 1) setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            t.setOnClickListener { pickSuggestion(slots[i]) }
            chips.add(t)
            sb.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            if (i < 2) {
                val div = View(this).apply { setBackgroundColor(theme.dim); alpha = 0.35f }
                sb.addView(div, LinearLayout.LayoutParams((1 * d).toInt(), (22 * d).toInt()))
            }
        }
        sb.visibility = View.GONE
        suggestionBar = sb

        // ---- edit tools
        val mb = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fun edit(label: String, action: () -> Unit) {
            val t = TextView(this).apply {
                text = label; textSize = 14f; setTextColor(theme.text); gravity = Gravity.CENTER
            }
            t.setOnClickListener { action() }
            mb.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }
        edit("Select all") { currentInputConnection?.performContextMenuAction(android.R.id.selectAll) }
        edit("Cut") { currentInputConnection?.performContextMenuAction(android.R.id.cut) }
        edit("Copy") { currentInputConnection?.performContextMenuAction(android.R.id.copy) }
        edit("Paste") { currentInputConnection?.performContextMenuAction(android.R.id.paste) }
        edit("<") { sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT) }
        edit(">") { sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT) }
        edit("Close") { moreOpen = false; updateTopBar() }
        mb.visibility = View.GONE
        moreBar = mb

        val top = FrameLayout(this)
        top.addView(tb, FrameLayout.LayoutParams(-1, -1))
        top.addView(sb, FrameLayout.LayoutParams(-1, -1))
        top.addView(mb, FrameLayout.LayoutParams(-1, -1))
        topBar = top
        root.addView(top, LinearLayout.LayoutParams(-1, (46 * d).toInt()))

        // ---- body: keyboard + panels
        val keyboard = KeyboardView(this, prefs, theme, this)
        kv = keyboard
        val body = FrameLayout(this)
        body.addView(keyboard, FrameLayout.LayoutParams(-1, -2))

        val ep = EmojiPanel(this, theme, availW,
            onEmoji = { currentInputConnection?.commitText(it, 1) },
            onBack = { closePanels() },
            onDel = { onDelete() })
        ep.visibility = View.GONE
        emojiPanel = ep
        body.addView(ep, FrameLayout.LayoutParams(-1, -1))

        val cp = ClipPanel(this, theme, prefs,
            onPaste = { currentInputConnection?.commitText(it, 1) },
            onBack = { closePanels() })
        cp.visibility = View.GONE
        clipPanel = cp
        body.addView(cp, FrameLayout.LayoutParams(-1, -1))

        root.addView(body, LinearLayout.LayoutParams(-1, -2))
        panelOpen = false
        return root
    }

    private fun openPanel(p: View?) {
        if (p == null) return
        emojiPanel?.visibility = View.GONE
        clipPanel?.visibility = View.GONE
        p.visibility = View.VISIBLE
        kv?.visibility = View.INVISIBLE
        topBar?.visibility = View.GONE
        panelOpen = true
    }

    private fun closePanels() {
        emojiPanel?.visibility = View.GONE
        clipPanel?.visibility = View.GONE
        kv?.visibility = View.VISIBLE
        topBar?.visibility = View.VISIBLE
        panelOpen = false
        updateTopBar()
    }

    private fun updateTopBar() {
        if (panelOpen) return
        val showSug = slots.any { it.isNotBlank() }
        toolbar?.visibility = if (!showSug && !moreOpen) View.VISIBLE else View.GONE
        moreBar?.visibility = if (moreOpen && !showSug) View.VISIBLE else View.GONE
        suggestionBar?.visibility = if (showSug) View.VISIBLE else View.GONE
        for (i in 0 until 3) chips[i].text = slots[i]
    }

    private fun cycleOneHanded() {
        prefs.put("onehand", (prefs.int("onehand", 0) + 1) % 3)
        setInputView(buildRoot())
        initView(lastInfo)
    }

    private fun initView(info: EditorInfo?) {
        val v = kv ?: return
        if (info != null) {
            val cls = info.inputType and InputType.TYPE_MASK_CLASS
            v.page = if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE) Layouts.SYM1 else Layouts.LETTERS
            v.enterLabel = enterLabelFor(info)
        }
        scheduleRefresh()
    }

    private fun enterLabelFor(info: EditorInfo): String {
        if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return "icon"
        if (info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0) return "icon"
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEARCH -> "search"
            EditorInfo.IME_ACTION_GO -> "Go"
            EditorInfo.IME_ACTION_SEND -> "Send"
            EditorInfo.IME_ACTION_NEXT -> "Next"
            EditorInfo.IME_ACTION_DONE -> "Done"
            else -> "icon"
        }
    }

    // ------------------------------------------------------------------ lifecycle
    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lastInfo = info
        if (builtVer != prefs.ver || kv == null) setInputView(buildRoot())
        swipeWord = null; swipeSlots = null; lastFix = null
        slots = listOf("", "", "")
        moreOpen = false
        closePanels()
        initView(info)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        closePanels()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        scheduleRefresh()
    }

    private val refreshRun = Runnable { refresh() }
    private fun scheduleRefresh() {
        h.removeCallbacks(refreshRun)
        h.post(refreshRun)
    }

    // ------------------------------------------------------------------ state refresh
    private fun isSensitive(): Boolean {
        val t = currentInputEditorInfo?.inputType ?: 0
        val cls = t and InputType.TYPE_MASK_CLASS
        val v = t and InputType.TYPE_MASK_VARIATION
        if (cls != InputType.TYPE_CLASS_TEXT) return true
        return v == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            v == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            v == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            v == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
            v == InputType.TYPE_TEXT_VARIATION_URI ||
            (t and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0
    }

    private fun trailingWord(s: String): String {
        var i = s.length
        while (i > 0 && (s[i - 1].isLetter() || (s[i - 1] == '\'' && i > 1 && s[i - 2].isLetter()))) i--
        return s.substring(i)
    }

    private fun refresh() {
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo
        val v = kv ?: return
        val before = ic.getTextBeforeCursor(60, 0)?.toString() ?: ""

        if (prefs.bool("autocap", true) && v.shift != 2 && v.page == Layouts.LETTERS && ei != null) {
            v.shift = if (ic.getCursorCapsMode(ei.inputType) != 0) 1 else 0
        }

        val sw = swipeWord
        if (sw != null && !before.endsWith("$sw ")) { swipeWord = null; swipeSlots = null }

        val word = trailingWord(before)
        if (word.isNotEmpty()) { swipeWord = null; swipeSlots = null }

        slots = if (!prefs.bool("suggest", true) || isSensitive()) {
            listOf("", "", "")
        } else if (swipeSlots != null) {
            swipeSlots!!
        } else if (word.isNotEmpty()) {
            val comps = dict.suggest(word, 2).filter { !it.equals(word, true) }
            val center = comps.getOrNull(0) ?: dict.correct(word) ?: ""
            listOf(word, center, comps.getOrNull(1) ?: "")
        } else listOf("", "", "")
        updateTopBar()
    }

    // ------------------------------------------------------------------ typing
    private fun matchCase(typed: String, cand: String): String = when {
        typed.length > 1 && typed.none { it.isLowerCase() } -> cand.uppercase()
        typed.isNotEmpty() && typed[0].isUpperCase() -> cand.replaceFirstChar { it.uppercase() }
        else -> cand
    }

    override fun onKey(text: String) {
        val ic = currentInputConnection ?: return
        if (text == " ") { handleSpace(ic); scheduleRefresh(); return }
        lastFix = null
        ic.commitText(text, 1)
        scheduleRefresh()
    }

    private fun handleSpace(ic: android.view.inputmethod.InputConnection) {
        val before = ic.getTextBeforeCursor(40, 0)?.toString() ?: ""
        val word = trailingWord(before)
        if (prefs.bool("autocorrect", true) && !isSensitive() && word.isNotEmpty()) {
            if (word == "i") {
                ic.deleteSurroundingText(1, 0); ic.commitText("I ", 1); lastFix = null; return
            }
            val fix = dict.correct(word)
            if (fix != null) {
                val out = matchCase(word, fix)
                ic.beginBatchEdit()
                ic.deleteSurroundingText(word.length, 0)
                ic.commitText("$out ", 1)
                ic.endBatchEdit()
                lastFix = Pair(word, out)
                return
            }
        }
        lastFix = null
        if (prefs.bool("dblspace", true) && before.length >= 2 && before.endsWith(" ") &&
            before[before.length - 2].isLetterOrDigit()
        ) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
        } else ic.commitText(" ", 1)
    }

    override fun onDelete() {
        val ic = currentInputConnection ?: return
        val fix = lastFix
        if (fix != null) {
            val b = ic.getTextBeforeCursor(fix.second.length + 1, 0)?.toString()
            if (b == fix.second + " ") {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(fix.second.length + 1, 0)
                ic.commitText(fix.first, 1)
                ic.endBatchEdit()
                prefs.learn(fix.first)
                lastFix = null
                scheduleRefresh()
                return
            }
        }
        lastFix = null
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        scheduleRefresh()
    }

    override fun onEnter() {
        val ei = currentInputEditorInfo
        val ic = currentInputConnection ?: return
        lastFix = null
        if (ei == null) { sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER); return }
        val action = ei.imeOptions and EditorInfo.IME_MASK_ACTION
        val multi = ei.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
        if (multi || ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0 ||
            action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED
        ) sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        else ic.performEditorAction(action)
    }

    override fun onGlobe(long: Boolean) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        if (long) { imm.showInputMethodPicker(); return }
        if (Build.VERSION.SDK_INT >= 28) {
            if (!switchToNextInputMethod(false)) imm.showInputMethodPicker()
        } else imm.showInputMethodPicker()
    }

    override fun onCursor(steps: Int) {
        val code = if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        repeat(abs(steps)) { sendDownUpKeyEvents(code) }
    }

    override fun onFeedback() {
        if (prefs.bool("haptic", true)) {
            @Suppress("DEPRECATION")
            kv?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
        }
        if (prefs.bool("sound", false)) {
            (getSystemService(AUDIO_SERVICE) as AudioManager).playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD)
        }
    }

    // ------------------------------------------------------------------ swipe typing
    override fun onSwipe(path: List<PointF>) {
        val v = kv ?: return
        val c = dict.swipe(path, v.letterCenters(), v.keyUnit())
        if (c.isEmpty()) return
        val ic = currentInputConnection ?: return
        val cap = v.shift > 0
        val before = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""
        val prefix = if (before.isNotEmpty() && !before.last().isWhitespace() && before.last() !in "([{\"'") " " else ""
        val word = if (cap) c[0].replaceFirstChar { it.uppercase() } else c[0]
        ic.commitText("$prefix$word ", 1)
        if (v.shift == 1) v.shift = 0
        lastFix = null
        swipeWord = word
        swipeCap = cap
        swipeSlots = listOf(c.getOrNull(1) ?: "", c[0], c.getOrNull(2) ?: "")
        slots = swipeSlots!!
        updateTopBar()
        scheduleRefresh()
    }

    private fun pickSuggestion(s: String) {
        if (s.isBlank()) return
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(60, 0)?.toString() ?: ""
        val sw = swipeWord
        if (sw != null && before.endsWith("$sw ")) {
            val w = if (swipeCap) s.replaceFirstChar { it.uppercase() } else s
            ic.deleteSurroundingText(sw.length + 1, 0)
            ic.commitText("$w ", 1)
            swipeWord = w
            scheduleRefresh()
            return
        }
        val word = trailingWord(before)
        if (word.isEmpty()) return
        if (s == word) prefs.learn(word)
        ic.beginBatchEdit()
        ic.deleteSurroundingText(word.length, 0)
        ic.commitText((if (s == word) s else matchCase(word, s)) + " ", 1)
        ic.endBatchEdit()
        lastFix = null
        scheduleRefresh()
    }

    // ------------------------------------------------------------------ voice
    private fun startVoice() {
        try {
            startActivity(Intent(this, VoiceActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(this, "Voice input unavailable", Toast.LENGTH_SHORT).show()
        }
    }
}
