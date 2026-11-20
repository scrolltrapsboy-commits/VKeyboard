package com.vinayak.keyboard

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.PointF
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class KeyboardService : InputMethodService(), KeyboardView.Callback, PanelHost {

    private lateinit var prefs: Prefs
    private lateinit var dict: Dictionary
    private lateinit var theme: Theme
    private lateinit var clipboard: ClipboardManager
    private val h = Handler(Looper.getMainLooper())

    private var kv: KeyboardView? = null
    private var column: LinearLayout? = null
    private var toolbar: LinearLayout? = null
    private var suggestionBar: LinearLayout? = null
    private var pasteBar: LinearLayout? = null
    private var searchBar: LinearLayout? = null
    private var searchQuery: TextView? = null
    private var searchResults: LinearLayout? = null
    private var bottomBar: LinearLayout? = null
    private var morePanel: MorePanel? = null
    private var sizePanel: SizePanel? = null
    private var themePanel: ThemePanel? = null
    private var editPanel: EditPanel? = null
    private var clipPanel: ClipboardPanel? = null
    private var emojiPanel: EmojiPanel? = null
    private var fullShown: View? = null
    private var topContainer: View? = null
    private var overlayShown: View? = null
    private val chips = ArrayList<TextView>()
    private var uScale = 1f

    private var builtVer = -1
    private var lastInfo: EditorInfo? = null
    private var searchTarget = -1
    private var searchText = ""

    private var pasteText: String? = null
    private var dismissedTs = 0L
    private var lastFix: Pair<String, String>? = null
    private var swipeWord: String? = null
    private var swipeCap = false
    private var swipeSlots: List<String>? = null
    private var slots: List<String> = listOf("", "", "")

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        try {
            val t = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
            if (!t.isNullOrBlank()) {
                prefs.addClip(t)
                pasteText = t
                h.post { updateTopBar() }
            }
        } catch (_: Exception) {
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        dict = Dictionary(this, prefs)
        dict.load()
        EmojiData.load(this)
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
        val dm = resources.displayMetrics
        val oh = prefs.int("onehand", 0)
        val availW = if (oh == 0) dm.widthPixels else (dm.widthPixels * 0.8f).toInt()
        val u = availW / 1080f
        uScale = u
        val font = Fonts.get(this)
        val mm = LinearLayout.LayoutParams.MATCH_PARENT

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ThemeBgDrawable(theme)
            when (oh) {
                1 -> setPadding(0, 0, (dm.widthPixels * 0.2f).toInt(), 0)
                2 -> setPadding((dm.widthPixels * 0.2f).toInt(), 0, 0, 0)
            }
        }
        val main = FrameLayout(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column = col

        // ---- toolbar
        val tb = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fun tool(kind: String, action: () -> Unit) {
            val slot = FrameLayout(this)
            val side = (136f * u).toInt()
            val v = IconView(this, kind, theme.text, u)
            v.setOnClickListener { v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); action() }
            slot.addView(v, FrameLayout.LayoutParams(side, side, Gravity.CENTER))
            tb.addView(slot, LinearLayout.LayoutParams(0, mm, 1f))
        }
        tool("emoji") { emojiPanel?.showEmojiTab(); showFull(emojiPanel) }
        tool("clip") { clipPanel?.refresh(); showFull(clipPanel) }
        tool("gear") { openSettings() }
        tool("hand") { toggleOneHanded() }
        tool("more") {
            if (overlayShown === morePanel) backToKeys() else { morePanel?.refresh(); showOverlay(morePanel) }
        }
        toolbar = tb

        // ---- suggestion strip
        val sb = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        chips.clear()
        for (i in 0 until 3) {
            val t = TextView(this).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_PX, 50f * u)
                typeface = font
                setTextColor(theme.text); gravity = Gravity.CENTER; maxLines = 1
            }
            t.setOnClickListener { pickSuggestion(slots[i]) }
            chips.add(t)
            sb.addView(t, LinearLayout.LayoutParams(0, mm, 1f))
            if (i < 2) {
                val div = View(this).apply { setBackgroundColor(theme.dim); alpha = 0.4f }
                sb.addView(div, LinearLayout.LayoutParams(maxOf(1, (2f * u).toInt()), (56f * u).toInt()))
            }
        }
        sb.visibility = View.GONE
        suggestionBar = sb

        // ---- "Paste" chip
        val pb = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val chip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        chip.addView(IconView(this, "clip", theme.text, u), LinearLayout.LayoutParams((90f * u).toInt(), mm))
        chip.addView(TextView(this).apply {
            text = "Paste"; setTextSize(TypedValue.COMPLEX_UNIT_PX, 50f * u); typeface = font; setTextColor(theme.text)
        })
        chip.setOnClickListener {
            pasteText?.let { t -> currentInputConnection?.commitText(t, 1) }
            pasteText = null
            updateTopBar()
        }
        pb.addView(chip, LinearLayout.LayoutParams(0, mm, 1f))
        val closeV = IconView(this, "close", theme.text, u)
        closeV.setOnClickListener {
            dismissedTs = clipboard.primaryClipDescription?.timestamp ?: 0L
            pasteText = null
            updateTopBar()
        }
        pb.addView(closeV, LinearLayout.LayoutParams((130f * u).toInt(), mm))
        pb.visibility = View.GONE
        pasteBar = pb

        // ---- in-keyboard search bar (emoji / GIF search)
        val sbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        sbar.addView(IconView(this, "search", theme.text, u), LinearLayout.LayoutParams((110f * u).toInt(), mm))
        val q = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 46f * u); typeface = font
            setTextColor(theme.text); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
        }
        searchQuery = q
        sbar.addView(q, LinearLayout.LayoutParams((300f * u).toInt(), mm))
        val hs = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val results = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        searchResults = results
        hs.addView(results)
        sbar.addView(hs, LinearLayout.LayoutParams(0, mm, 1f))
        val x = IconView(this, "close", theme.text, u)
        x.setOnClickListener { exitSearch(true) }
        sbar.addView(x, LinearLayout.LayoutParams((130f * u).toInt(), mm))
        sbar.visibility = View.GONE
        searchBar = sbar

        val top = FrameLayout(this)
        top.addView(tb, FrameLayout.LayoutParams(-1, -1))
        top.addView(sb, FrameLayout.LayoutParams(-1, -1))
        top.addView(pb, FrameLayout.LayoutParams(-1, -1))
        top.addView(sbar, FrameLayout.LayoutParams(-1, -1))
        topContainer = top
        col.addView(top, LinearLayout.LayoutParams(mm, (136f * u).toInt()))

        // ---- keys + overlay panels that live under the toolbar
        val keyboard = KeyboardView(this, prefs, theme, this)
        kv = keyboard
        val kvFrame = FrameLayout(this)
        kvFrame.addView(keyboard, FrameLayout.LayoutParams(-1, -1))
        val mp = MorePanel(this, theme, prefs, u, availW, this).also { it.visibility = View.GONE }
        val sp = SizePanel(this, theme, prefs, u, this).also { it.visibility = View.GONE }
        val tp = ThemePanel(this, theme, prefs, u, availW, this).also { it.visibility = View.GONE }
        val ep2 = EditPanel(this, theme, u, this).also { it.visibility = View.GONE }
        morePanel = mp; sizePanel = sp; themePanel = tp; editPanel = ep2
        for (p in listOf<View>(mp, sp, tp, ep2)) kvFrame.addView(p, FrameLayout.LayoutParams(-1, -1))

        // Keep the keyboard/panel viewport at the exact measured keyboard height.
        // A WRAP_CONTENT FrameLayout with MATCH_PARENT panel children can otherwise
        // expand to the IME window height and make emoji/clipboard feel full-screen.
        val keyboardHeight = (Layouts.REF_H * u * prefs.int("keyscale", 100) / 100f).toInt()
        col.addView(kvFrame, LinearLayout.LayoutParams(mm, keyboardHeight))
        main.addView(col, FrameLayout.LayoutParams(-1, -2))

        // ---- full-height panels (cover toolbar + keys)
        val cp = ClipboardPanel(this, theme, prefs, u, availW, this).also { it.visibility = View.GONE }
        val ep = EmojiPanel(this, theme, prefs, u, availW, this).also { it.visibility = View.GONE }
        clipPanel = cp; emojiPanel = ep
        kvFrame.addView(cp, FrameLayout.LayoutParams(-1, -1))
        kvFrame.addView(ep, FrameLayout.LayoutParams(-1, -1))
        root.addView(main, LinearLayout.LayoutParams(mm, LinearLayout.LayoutParams.WRAP_CONTENT))

        // ---- bottom strip (mic + hide keyboard)
        val bb = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val mic = IconView(this, "mic", theme.dim, u)
        mic.setOnClickListener { startVoice() }
        bb.addView(mic, LinearLayout.LayoutParams((117f * u).toInt(), mm))
        bb.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        val chev = IconView(this, "chev", theme.dim, u)
        chev.setOnClickListener { requestHideSelf(0) }
        bb.addView(chev, LinearLayout.LayoutParams((117f * u).toInt(), mm))
        bb.visibility = if (prefs.bool("bottombar", true)) View.VISIBLE else View.GONE
        bottomBar = bb
        root.addView(bb, LinearLayout.LayoutParams(mm, (104f * u).toInt()))

        fullShown = null
        overlayShown = null
        return root
    }

    // ------------------------------------------------------------------ panels
    private fun hideAll() {
        for (v in listOf<View?>(morePanel, sizePanel, themePanel, editPanel, clipPanel, emojiPanel)) v?.visibility = View.GONE
        column?.visibility = View.VISIBLE
        topContainer?.visibility = View.VISIBLE
        kv?.visibility = View.VISIBLE
        fullShown = null
        overlayShown = null
    }

    private fun showFull(p: View?) {
        if (p == null) return
        if (searchTarget >= 0) { searchTarget = -1; searchText = "" }
        hideAll()
        p.visibility = View.VISIBLE
        topContainer?.visibility = View.GONE
        kv?.visibility = View.GONE
        fullShown = p
    }

    private fun showOverlay(p: View?) {
        if (p == null) return
        hideAll()
        p.visibility = View.VISIBLE
        kv?.visibility = View.INVISIBLE
        overlayShown = p
        updateTopBar()
    }

    private fun updateTopBar() {
        val searching = searchTarget >= 0
        val overlay = overlayShown != null
        val showSug = !searching && !overlay && slots.any { it.isNotBlank() }
        val showPaste = !searching && !overlay && pasteText != null && !showSug
        toolbar?.visibility = if (!searching && !showSug && !showPaste) View.VISIBLE else View.GONE
        suggestionBar?.visibility = if (showSug) View.VISIBLE else View.GONE
        pasteBar?.visibility = if (showPaste) View.VISIBLE else View.GONE
        searchBar?.visibility = if (searching) View.VISIBLE else View.GONE
        for (i in 0 until 3) chips[i].text = slots[i]
    }

    private fun checkClip() {
        pasteText = try {
            val desc = clipboard.primaryClipDescription
            if (desc != null && clipboard.hasPrimaryClip() && desc.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) &&
                desc.timestamp != dismissedTs && System.currentTimeMillis() - desc.timestamp < 90_000
            ) clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.takeIf { it.isNotBlank() }
            else null
        } catch (_: Exception) { null }
    }

    // ------------------------------------------------------------------ PanelHost
    override fun commitText(t: String) { currentInputConnection?.commitText(t, 1) }

    override fun commitGif(item: GifItem) {
        val info = currentInputEditorInfo
        val supported = try {
            info?.contentMimeTypes?.any { ClipDescription.compareMimeTypes("image/gif", it) } == true
        } catch (e: Throwable) { false }
        val link = (if (item.page.isBlank()) item.gif else item.page) + " "
        if (!supported) {
            currentInputConnection?.commitText(link, 1)
            Toast.makeText(this, "This app can't take GIFs, sent the link instead", Toast.LENGTH_SHORT).show()
            return
        }
        Giphy.download(this, item) { uri ->
            val ic = currentInputConnection
            if (uri == null || ic == null) {
                ic?.commitText(link, 1)
            } else {
                val ci = InputContentInfo(uri, ClipDescription("GIF", arrayOf("image/gif")),
                    if (item.page.isBlank()) null else Uri.parse(item.page))
                val ok = ic.commitContent(ci, InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null)
                if (!ok) ic.commitText(link, 1)
            }
        }
    }

    override fun backToKeys() {
        if (searchTarget >= 0) exitSearch(false)
        hideAll()
        updateTopBar()
    }

    override fun startSearch(target: Int) {
        searchTarget = target
        searchText = ""
        hideAll()
        kv?.enterLabel = "search"
        updateSearch()
        updateTopBar()
    }

    private fun exitSearch(reopen: Boolean) {
        val target = searchTarget
        searchTarget = -1
        searchText = ""
        kv?.enterLabel = lastInfo?.let { enterLabelFor(it) } ?: "icon"
        if (reopen) {
            showFull(emojiPanel)
            emojiPanel?.selectTab(target.coerceAtLeast(0))
        }
        updateTopBar()
    }

    private fun updateSearch() {
        val q = searchQuery ?: return
        q.text = if (searchText.isEmpty()) (if (searchTarget == 0) "Search emoji" else "Search GIPHY") else searchText
        q.alpha = if (searchText.isEmpty()) 0.5f else 1f
        val res = searchResults ?: return
        res.removeAllViews()
        if (searchTarget != 0 || searchText.isEmpty()) return
        for (e in EmojiData.search(searchText)) {
            val t = TextView(this).apply {
                text = e.ch; gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_PX, 62f * uScale)
            }
            t.setOnClickListener { commitText(e.ch); prefs.addRecent(e.ch) }
            res.addView(t, LinearLayout.LayoutParams((110f * uScale).toInt(), LinearLayout.LayoutParams.MATCH_PARENT))
        }
    }

    override fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun toggleOneHanded() {
        prefs.put("onehand", (prefs.int("onehand", 0) + 1) % 3)
        rebuild()
    }

    override fun openPanel(name: String) {
        when (name) {
            "size" -> showOverlay(sizePanel)
            "theme" -> showOverlay(themePanel)
            "edit" -> showOverlay(editPanel)
        }
    }

    override fun rebuild() {
        setInputView(buildRoot())
        initView(lastInfo)
    }

    override fun setKeyScale(v: Int) {
        prefs.putQuiet("keyscale", v)
        kv?.requestLayout()
        kv?.invalidate()
    }

    override fun sendKey(code: Int, shift: Boolean) {
        val ic = currentInputConnection ?: return
        val now = SystemClock.uptimeMillis()
        val meta = if (shift) KeyEvent.META_SHIFT_ON else 0
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, meta))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, meta))
    }

    override fun ctxMenu(id: Int) { currentInputConnection?.performContextMenuAction(id) }

    // ------------------------------------------------------------------ lifecycle
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

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lastInfo = info
        if (builtVer != prefs.ver || kv == null) setInputView(buildRoot())
        swipeWord = null; swipeSlots = null; lastFix = null
        slots = listOf("", "", "")
        searchTarget = -1; searchText = ""
        checkClip()
        hideAll()
        initView(info)
        updateTopBar()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        searchTarget = -1
        hideAll()
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
        if (searchTarget >= 0) return
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
        if (searchTarget >= 0) {
            if (text != " " || searchText.isNotEmpty()) searchText += text.lowercase()
            updateSearch()
            return
        }
        val ic = currentInputConnection ?: return
        if (text == " ") { handleSpace(ic); scheduleRefresh(); return }
        lastFix = null
        ic.commitText(text, 1)
        scheduleRefresh()
    }

    private fun handleSpace(ic: InputConnection) {
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
        if (searchTarget >= 0) {
            if (searchText.isNotEmpty()) searchText = searchText.dropLast(1)
            updateSearch()
            return
        }
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
        if (searchTarget >= 0) {
            val t = searchTarget
            val q = searchText
            if (t >= 1) {
                exitSearch(true)
                emojiPanel?.runGifQuery(t, q)
            }
            return
        }
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
        if (searchTarget >= 0) {
            searchText += c[0]
            updateSearch()
            return
        }
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
