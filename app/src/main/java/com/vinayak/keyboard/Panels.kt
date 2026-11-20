package com.vinayak.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

interface PanelHost {
    fun commitText(t: String)
    fun commitGif(item: GifItem)
    fun backToKeys()
    fun startSearch(target: Int)
    fun openSettings()
    fun toggleOneHanded()
    fun openPanel(name: String)
    fun rebuild()
    fun setKeyScale(v: Int)
    fun sendKey(code: Int, shift: Boolean)
    fun ctxMenu(id: Int)
}

private fun mkText(ctx: Context, s: String, sizePx: Float, color: Int, font: Boolean = true): TextView = TextView(ctx).apply {
    text = s
    setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx)
    if (font) typeface = Fonts.get(ctx)
    setTextColor(color)
    includeFontPadding = false
}

private fun roundBg(fill: Int, radius: Float, stroke: Int = 0, sw: Float = 0f): GradientDrawable =
    GradientDrawable().apply {
        setColor(fill)
        cornerRadius = radius
        if (sw > 0f) setStroke(sw.toInt().coerceAtLeast(1), stroke)
    }

private fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

private fun withAlpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a shl 24)

/** Samsung style panel header: keyboard button, divider, title, optional right-side icons. */
private fun header(ctx: Context, theme: Theme, u: Float, title: String, host: PanelHost, right: List<Pair<View, Int>> = emptyList()): LinearLayout {
    val h = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    val back = IconView(ctx, "keyboard", theme.text, u)
    back.setOnClickListener { host.backToKeys() }
    h.addView(back, lp((156f * u).toInt(), ViewGroup.LayoutParams.MATCH_PARENT))
    val div = View(ctx).apply { setBackgroundColor(theme.text) }
    h.addView(div, lp((2f * u).toInt().coerceAtLeast(1), (34f * u).toInt()))
    val t = mkText(ctx, title, 50f * u, theme.text)
    h.addView(t, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = (30f * u).toInt() })
    for ((v, w) in right) h.addView(v, lp((w * u).toInt(), ViewGroup.LayoutParams.MATCH_PARENT))
    return h
}

// ============================================================ Clipboard
class ClipboardPanel(
    ctx: Context, private val theme: Theme, private val prefs: Prefs,
    private val u: Float, private val availW: Int, private val host: PanelHost
) : LinearLayout(ctx) {
    private val grid = GridLayout(ctx).apply { columnCount = 3 }
    private val selected = HashSet<String>()
    private val pinIcon = IconView(ctx, "pin", theme.text, u)
    private val trashIcon = IconView(ctx, "trash", theme.text, u)
    private val empty = mkText(ctx, "Clipboard is empty", 46f * u, theme.dim)

    init {
        orientation = VERTICAL
        isClickable = true
        addView(header(ctx, theme, u, "Clipboard", host, listOf(pinIcon to 132, trashIcon to 132)),
            lp(ViewGroup.LayoutParams.MATCH_PARENT, (140f * u).toInt()))
        val frame = FrameLayout(ctx)
        val sv = ScrollView(ctx)
        grid.setPadding((9f * u).toInt(), 0, (9f * u).toInt(), (20f * u).toInt())
        sv.addView(grid)
        frame.addView(sv, FrameLayout.LayoutParams(-1, -1))
        frame.addView(empty, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        addView(frame, lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        pinIcon.setOnClickListener {
            selected.forEach { prefs.togglePin(it) }
            selected.clear(); refresh()
        }
        trashIcon.setOnClickListener {
            if (selected.isEmpty()) prefs.clearUnpinned() else selected.forEach { prefs.removeClip(it) }
            selected.clear(); refresh()
        }
    }

    fun refresh() {
        grid.removeAllViews()
        selected.retainAll(prefs.clips().toSet())
        val clips = prefs.clips()
        empty.visibility = if (clips.isEmpty()) View.VISIBLE else View.GONE
        val cw = ((availW - 18f * u) / 3f - 26f * u).toInt()
        val m = (13f * u).toInt()
        for (t in clips) {
            val sel = t in selected
            val pinned = prefs.isPinned(t)
            val card = mkText(context, t, 48f * u, theme.text).apply {
                maxLines = 5
                ellipsize = TextUtils.TruncateAt.END
                setLineSpacing(0f, 1.12f)
                setPadding((30f * u).toInt(), (24f * u).toInt(), (18f * u).toInt(), (16f * u).toInt())
                background = roundBg(if (sel) withAlpha(theme.accent, 0x33) else 0, 40f * u,
                    if (pinned || sel) theme.accent else theme.dim, 2.6f * u)
            }
            card.setOnClickListener {
                if (selected.isNotEmpty()) { if (!selected.remove(t)) selected.add(t); refresh() }
                else { host.commitText(t); host.backToKeys() }
            }
            card.setOnLongClickListener { if (!selected.remove(t)) selected.add(t); refresh(); true }
            val glp = GridLayout.LayoutParams()
            glp.width = cw
            glp.height = cw
            glp.setMargins(m, m, m, m)
            grid.addView(card, glp)
        }
        val on = selected.isNotEmpty()
        pinIcon.setIconColor(if (on) theme.accent else theme.text)
        trashIcon.setIconColor(if (on) theme.accent else theme.text)
    }
}

// ============================================================ Emoji / GIF / Sticker / Emoticons
class EmojiPanel(
    ctx: Context, private val theme: Theme, private val prefs: Prefs,
    private val u: Float, private val availW: Int, private val host: PanelHost
) : LinearLayout(ctx) {
    private var tab = 0
    private var cat = 'S'
    private var token = 0
    private var gifQuery: String? = null
    private val tabIcons = ArrayList<IconView>()
    private val catIcons = ArrayList<Pair<Char, IconView>>()
    private val catRow = LinearLayout(ctx).apply { orientation = HORIZONTAL }
    private val chipScroll = HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false }
    private val chipRow = LinearLayout(ctx).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val rowB = LinearLayout(ctx).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val emojiScroll = ScrollView(ctx)
    private val grid = GridLayout(ctx).apply { columnCount = 8 }
    private val gifScroll = ScrollView(ctx)
    private val gifCols = LinearLayout(ctx).apply { orientation = HORIZONTAL }
    private val emoticonScroll = ScrollView(ctx)
    private val message = mkText(ctx, "", 44f * u, theme.dim).apply { gravity = Gravity.CENTER }
    private val chips = listOf("Trending", "Reactions", "Happy", "Sad", "Love", "Funny", "Wow", "Yes", "No", "Thanks", "Bye", "Party", "OMG", "Cool")
    private val chipViews = ArrayList<TextView>()

    private val emoticons = listOf(
        ":-)", ":D", ":P", ";-)", ":-(", ":'(", ":O", "XD", "<3", "</3", ":*", "^_^", "-_-", "o_O", "T_T", ">_<",
        "¯\\_(ツ)_/¯", "(╯°□°)╯︵ ┻━┻", "┬─┬ ノ( ゜-゜ノ)", "ಠ_ಠ", "(づ｡◕‿‿◕｡)づ", "ʕ•ᴥ•ʔ", "(¬‿¬)", "(•‿•)",
        "( ͡° ͜ʖ ͡°)", "(☞ﾟヮﾟ)☞", "(⌐■_■)", "orz", "m(_ _)m", "(^o^)/", "(*^▽^*)", "(≧▽≦)"
    )

    init {
        orientation = VERTICAL
        isClickable = true

        // ---- row A: keyboard button + tabs
        val rowA = LinearLayout(ctx).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val kb = IconView(ctx, "keyboard", theme.text, u)
        kb.setOnClickListener { host.backToKeys() }
        rowA.addView(kb, lp((156f * u).toInt(), ViewGroup.LayoutParams.MATCH_PARENT))
        listOf("face", "gif", "sticker", "emoticon").forEachIndexed { i, kind ->
            val v = IconView(ctx, kind, theme.text, u)
            v.setOnClickListener { selectTab(i) }
            tabIcons.add(v)
            rowA.addView(v, lp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        rowA.addView(View(ctx), lp(0, 1, 1.4f))
        addView(rowA, lp(ViewGroup.LayoutParams.MATCH_PARENT, (140f * u).toInt()))

        // ---- row B: search + (categories | GIF chips)
        val search = IconView(ctx, "search", theme.text, u)
        search.setOnClickListener { host.startSearch(tab) }
        rowB.addView(search, lp((166f * u).toInt(), ViewGroup.LayoutParams.MATCH_PARENT))
        val div = View(ctx).apply { setBackgroundColor(theme.dim); alpha = 0.6f }
        rowB.addView(div, lp((2f * u).toInt().coerceAtLeast(1), (90f * u).toInt()))
        listOf('R' to "clock", 'S' to "emoji", 'A' to "dog", 'F' to "food", 'T' to "house",
            'C' to "ball", 'O' to "objects", 'Y' to "symbols", 'G' to "flag").forEach { (code, kind) ->
            val v = IconView(ctx, kind, theme.text, u)
            v.setOnClickListener { selectCat(code) }
            catIcons.add(code to v)
            catRow.addView(v, lp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        val rowBContent = FrameLayout(ctx)
        rowBContent.addView(catRow, FrameLayout.LayoutParams(-1, -1))
        chipScroll.addView(chipRow)
        rowBContent.addView(chipScroll, FrameLayout.LayoutParams(-1, -1))
        rowB.addView(rowBContent, lp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        addView(rowB, lp(ViewGroup.LayoutParams.MATCH_PARENT, (134f * u).toInt()))
        val line = View(ctx).apply { setBackgroundColor(theme.dim); alpha = 0.45f }
        addView(line, lp(ViewGroup.LayoutParams.MATCH_PARENT, (2f * u).toInt().coerceAtLeast(1)))

        // ---- GIF chips
        for (name in chips) {
            val chip = mkText(ctx, name, 40f * u, theme.text).apply {
                setPadding((30f * u).toInt(), (14f * u).toInt(), (30f * u).toInt(), (14f * u).toInt())
            }
            chip.setOnClickListener {
                gifQuery = if (name == "Trending") null else name
                loadGif()
            }
            chipViews.add(chip)
            chipRow.addView(chip, lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = (8f * u).toInt(); rightMargin = (8f * u).toInt()
            })
        }

        // ---- body
        val body = FrameLayout(ctx)
        grid.setPadding(0, (6f * u).toInt(), 0, (20f * u).toInt())
        emojiScroll.addView(grid)
        gifCols.setPadding((8f * u).toInt(), (8f * u).toInt(), (8f * u).toInt(), (20f * u).toInt())
        gifScroll.addView(gifCols)
        val eg = GridLayout(ctx).apply { columnCount = 3; setPadding((9f * u).toInt(), (10f * u).toInt(), (9f * u).toInt(), (20f * u).toInt()) }
        val ecw = ((availW - 18f * u) / 3f - 16f * u).toInt()
        for (e in emoticons) {
            val tvv = TextView(ctx).apply {
                text = e; setTextSize(TypedValue.COMPLEX_UNIT_PX, 44f * u); setTextColor(theme.text)
                gravity = Gravity.CENTER; maxLines = 2
                background = roundBg(theme.keyFill, 28f * u)
            }
            tvv.setOnClickListener { host.commitText("$e ") }
            val g = GridLayout.LayoutParams()
            g.width = ecw; g.height = ecw
            val mm = (8f * u).toInt(); g.setMargins(mm, mm, mm, mm)
            eg.addView(tvv, g)
        }
        emoticonScroll.addView(eg)
        body.addView(emojiScroll, FrameLayout.LayoutParams(-1, -1))
        body.addView(gifScroll, FrameLayout.LayoutParams(-1, -1))
        body.addView(emoticonScroll, FrameLayout.LayoutParams(-1, -1))
        body.addView(message, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        addView(body, lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        selectTab(0)
    }

    private fun hl() = if (theme.dark) withAlpha(theme.accent, 0x55) else 0xFFB5CBF5.toInt()

    fun selectTab(i: Int) {
        tab = i
        tabIcons.forEachIndexed { j, v -> v.highlight = if (j == i) hl() else 0 }
        rowB.visibility = if (i == 3) View.GONE else View.VISIBLE
        catRow.visibility = if (i == 0) View.VISIBLE else View.GONE
        chipScroll.visibility = if (i in 1..2) View.VISIBLE else View.GONE
        emojiScroll.visibility = if (i == 0) View.VISIBLE else View.GONE
        gifScroll.visibility = if (i in 1..2) View.VISIBLE else View.GONE
        emoticonScroll.visibility = if (i == 3) View.VISIBLE else View.GONE
        message.visibility = View.GONE
        when (i) {
            0 -> selectCat(cat)
            1, 2 -> { gifQuery = null; loadGif() }
        }
    }

    /** Called by the keyboard after an in-keyboard search finished. */
    fun runGifQuery(target: Int, q: String) {
        selectTab(target)
        gifQuery = q
        loadGif()
    }

    fun showEmojiTab() = selectTab(0)

    // ---------------- emoji
    private fun selectCat(c: Char) {
        cat = c
        for ((code, v) in catIcons) v.setIconColor(if (code == c) theme.accent else theme.text)
        val list = if (c == 'R') prefs.recentEmoji() else EmojiData.group(c).map { it.ch }
        token++
        grid.removeAllViews()
        emojiScroll.scrollTo(0, 0)
        if (list.isEmpty()) {
            message.visibility = View.VISIBLE
            message.setOnClickListener(null)
            if (c == 'R') message.text = "No recent emoji yet"
            else {
                message.text = "Loading…"
                val tk = token
                postDelayed({ if (tk == token && tab == 0) selectCat(cat) }, 400)
            }
            return
        }
        message.visibility = View.GONE
        addChunk(list, 0, token)
    }

    private fun addChunk(list: List<String>, from: Int, tk: Int) {
        if (tk != token) return
        val to = minOf(from + 120, list.size)
        for (i in from until to) grid.addView(cell(list[i]))
        if (to < list.size) post { addChunk(list, to, tk) }
    }

    private fun cell(ch: String): View {
        val t = TextView(context).apply {
            text = ch
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 62f * u)
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        val g = GridLayout.LayoutParams()
        g.width = availW / 8
        g.height = g.width
        t.layoutParams = g
        t.setOnClickListener { host.commitText(ch); prefs.addRecent(ch) }
        t.setOnLongClickListener { skinPopup(t, ch) }
        return t
    }

    private fun skinPopup(anchor: View, ch: String): Boolean {
        val vars = EmojiData.skinVariants(ch) ?: return false
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            background = roundBg(theme.popup, 30f * u)
            setPadding((10f * u).toInt(), (8f * u).toInt(), (10f * u).toInt(), (8f * u).toInt())
        }
        val pw = PopupWindow(row, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        pw.setBackgroundDrawable(ColorDrawable(0))
        pw.elevation = 14f
        for (v in listOf(ch) + vars) {
            val t = TextView(context).apply {
                text = v; gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_PX, 56f * u)
            }
            t.setOnClickListener { host.commitText(v); prefs.addRecent(v); pw.dismiss() }
            row.addView(t, lp((104f * u).toInt(), (104f * u).toInt()))
        }
        pw.showAsDropDown(anchor, 0, -(anchor.height + (130f * u).toInt()))
        return true
    }

    // ---------------- GIF / sticker
    private fun showMessage(text: String, onTap: (() -> Unit)?) {
        gifCols.removeAllViews()
        message.text = text
        message.visibility = View.VISIBLE
        message.setOnClickListener(if (onTap == null) null else View.OnClickListener { onTap() })
    }

    private fun loadGif() {
        token++
        val tk = token
        chipViews.forEachIndexed { i, v ->
            val on = (gifQuery == null && i == 0) || (gifQuery != null && chips[i] == gifQuery)
            v.background = roundBg(if (on) withAlpha(theme.accent, 0x33) else 0, 40f * u, if (on) theme.accent else theme.dim, 2.4f * u)
        }
        gifScroll.scrollTo(0, 0)
        val key = prefs.str("giphy", "").trim()
        if (key.isEmpty()) {
            showMessage("GIFs and stickers need a free Giphy API key.\nTap here to open Settings and add it.") { host.openSettings() }
            return
        }
        showMessage("Loading…", null)
        Giphy.fetch(key, tab == 2, gifQuery) { list, err ->
            if (tk != token) return@fetch
            if (list == null) {
                showMessage("Couldn't load ($err).\nTap to retry.") { loadGif() }
            } else if (list.isEmpty()) {
                showMessage("Nothing found.", null)
            } else {
                message.visibility = View.GONE
                buildMasonry(list)
            }
        }
    }

    private fun buildMasonry(list: List<GifItem>) {
        gifCols.removeAllViews()
        val cols = Array(3) { LinearLayout(context).apply { orientation = VERTICAL } }
        val heights = FloatArray(3)
        val colW = ((availW - 16f * u) / 3f).toInt()
        for (c in cols) gifCols.addView(c, lp(colW, ViewGroup.LayoutParams.WRAP_CONTENT))
        for (item in list) {
            var best = 0
            for (i in 1 until 3) if (heights[i] < heights[best]) best = i
            val m = (4f * u).toInt()
            val iw = colW - 2 * m
            val ih = (iw * item.ratio).toInt().coerceIn((60f * u).toInt(), (400f * u).toInt())
            heights[best] = heights[best] + ih + 2 * m
            val iv = ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_XY
                background = roundBg(withAlpha(theme.dim, 0x30), 16f * u)
                setOnClickListener { host.commitGif(item) }
            }
            ImageLoader.load(item.thumb, iv)
            cols[best].addView(iv, lp(iw, ih).apply { setMargins(m, m, m, m) })
        }
    }
}

// ============================================================ "More" grid
class MorePanel(
    ctx: Context, private val theme: Theme, private val prefs: Prefs,
    private val u: Float, private val availW: Int, private val host: PanelHost
) : FrameLayout(ctx) {
    private class Item(val id: String, val label: String, val icon: String, val key: String?)
    private val items = listOf(
        Item("onehand", "One-handed", "onehand", null),
        Item("size", "Keyboard size", "ksize", null),
        Item("edit", "Text editing", "edit", null),
        Item("theme", "Theme", "palette", null),
        Item("numrow", "Number row", "numrow", "numrow"),
        Item("swipe", "Swipe typing", "swipe", "swipe"),
        Item("autocorrect", "Auto-correct", "abc", "autocorrect"),
        Item("settings", "Settings", "gear", null)
    )
    private val icons = HashMap<String, IconView>()

    init {
        isClickable = true
        val grid = GridLayout(ctx).apply { columnCount = 4 }
        val cellW = (255f * u).toInt()
        val side = ((availW - 4 * cellW) / 2).coerceAtLeast(0)
        grid.setPadding(side, (70f * u).toInt(), side, 0)
        for (it in items) {
            val cell = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
            val ic = IconView(ctx, it.icon, theme.text, u)
            icons[it.id] = ic
            cell.addView(ic, lp((110f * u).toInt(), (110f * u).toInt()))
            val label = mkText(ctx, it.label, 38f * u, theme.text).apply {
                gravity = Gravity.CENTER; maxLines = 2
                setPadding((10f * u).toInt(), (10f * u).toInt(), (10f * u).toInt(), 0)
            }
            cell.addView(label, lp(cellW, ViewGroup.LayoutParams.WRAP_CONTENT))
            cell.setOnClickListener { _ -> act(it) }
            val g = GridLayout.LayoutParams()
            g.width = cellW; g.height = (330f * u).toInt()
            grid.addView(cell, g)
        }
        addView(grid, LayoutParams(-1, -2))
        refresh()
    }

    private fun act(it: Item) {
        when (it.id) {
            "onehand" -> host.toggleOneHanded()
            "size", "edit", "theme" -> host.openPanel(it.id)
            "settings" -> host.openSettings()
            else -> {
                val k = it.key ?: return
                prefs.put(k, !prefs.bool(k, true))
                refresh()
                if (it.id == "numrow") host.rebuild()
            }
        }
    }

    fun refresh() {
        for (it in items) {
            val k = it.key ?: continue
            icons[it.id]?.setIconColor(if (prefs.bool(k, true)) theme.accent else theme.text)
        }
    }
}

// ============================================================ Keyboard size
class SizePanel(
    ctx: Context, private val theme: Theme, private val prefs: Prefs,
    private val u: Float, private val host: PanelHost
) : LinearLayout(ctx) {
    init {
        orientation = VERTICAL
        isClickable = true
        addView(header(ctx, theme, u, "Keyboard size", host), lp(ViewGroup.LayoutParams.MATCH_PARENT, (140f * u).toInt()))
        val label = mkText(ctx, "", 46f * u, theme.text)
        val bar = SeekBar(ctx).apply { max = 60 }
        fun upd(v: Int) { label.text = "Key height: $v%" }
        val cur = prefs.int("keyscale", 100)
        bar.progress = cur - 70
        upd(cur)
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { upd(p + 70); if (fromUser) host.setKeyScale(p + 70) }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        val reset = mkText(ctx, "Reset to default", 44f * u, theme.accent).apply {
            setPadding(0, (30f * u).toInt(), 0, 0)
            setOnClickListener { bar.progress = 30; upd(100); host.setKeyScale(100) }
        }
        val box = LinearLayout(ctx).apply { orientation = VERTICAL; setPadding((50f * u).toInt(), (70f * u).toInt(), (50f * u).toInt(), 0) }
        box.addView(label)
        box.addView(bar, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (40f * u).toInt() })
        box.addView(reset)
        addView(box, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
}

// ============================================================ Theme picker
internal class ThemeSwatch(ctx: Context, private val t: Theme, private val name: String, private val panel: Theme, private val u: Float, private val selected: Boolean) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Fonts.get(ctx) }
    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val boxH = 150f * u
        val box = RectF(8f * u, 6f * u, w - 8f * u, boxH)
        p.style = Paint.Style.FILL; p.color = t.bg
        c.drawRoundRect(box, 26f * u, 26f * u, p)
        p.color = t.key
        val kw = (box.width() - 50f * u) / 4f
        for (r in 0 until 2) for (i in 0 until 4) {
            val l = box.left + 12f * u + i * (kw + 8f * u)
            val tt = box.top + 14f * u + r * 44f * u
            c.drawRoundRect(RectF(l, tt, l + kw, tt + 34f * u), 8f * u, 8f * u, p)
        }
        p.color = t.accent
        c.drawCircle(box.right - 26f * u, box.bottom - 24f * u, 11f * u, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = (if (selected) 6f else 2.2f) * u
        p.color = if (selected) panel.accent else panel.dim
        c.drawRoundRect(box, 26f * u, 26f * u, p)
        tp.color = panel.text
        tp.textSize = 36f * u
        c.drawText(name, w / 2, boxH + 46f * u, tp)
    }
}

class ThemePanel(
    ctx: Context, private val theme: Theme, private val prefs: Prefs,
    private val u: Float, private val availW: Int, private val host: PanelHost
) : LinearLayout(ctx) {
    init {
        orientation = VERTICAL
        isClickable = true
        val gear = IconView(ctx, "gear", theme.text, u)
        gear.setOnClickListener { host.openSettings() }
        addView(header(ctx, theme, u, "Theme", host, listOf(gear to 132)), lp(ViewGroup.LayoutParams.MATCH_PARENT, (140f * u).toInt()))
        val sysDark = theme.dark
        val grid = GridLayout(ctx).apply { columnCount = 3; setPadding((20f * u).toInt(), (6f * u).toInt(), (20f * u).toInt(), (20f * u).toInt()) }
        val cw = ((availW - 40f * u) / 3f).toInt()
        val cur = prefs.int("theme", 0)
        for (id in Theme.NAMES.indices) {
            val sw = ThemeSwatch(ctx, Theme.preset(id, sysDark, prefs), Theme.NAMES[id], theme, u, id == cur)
            sw.setOnClickListener { prefs.put("theme", id); host.rebuild() }
            val g = GridLayout.LayoutParams()
            g.width = cw; g.height = (210f * u).toInt()
            grid.addView(sw, g)
        }
        val sv = ScrollView(ctx)
        sv.addView(grid)
        addView(sv, lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }
}

// ============================================================ Text editing
class EditPanel(
    ctx: Context, private val theme: Theme, private val u: Float, private val host: PanelHost
) : LinearLayout(ctx) {
    private var selecting = false
    private lateinit var selectBtn: TextView

    private fun btn(label: String, w: Float, h: Float, onClick: () -> Unit): TextView =
        mkText(context, label, 46f * u, theme.text).apply {
            gravity = Gravity.CENTER
            background = roundBg(theme.keyFill, 26f * u)
            setOnClickListener { onClick() }
            layoutParams = GridLayout.LayoutParams().apply {
                width = (w * u).toInt(); height = (h * u).toInt()
                val m = (7f * u).toInt(); setMargins(m, m, m, m)
            }
        }

    init {
        orientation = VERTICAL
        isClickable = true
        addView(header(ctx, theme, u, "Text editing", host), lp(ViewGroup.LayoutParams.MATCH_PARENT, (140f * u).toInt()))
        val body = LinearLayout(ctx).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        body.setPadding((30f * u).toInt(), (30f * u).toInt(), (30f * u).toInt(), 0)

        val pad = GridLayout(ctx).apply { columnCount = 3; rowCount = 3 }
        fun place(v: TextView, r: Int, c: Int) {
            val g = v.layoutParams as GridLayout.LayoutParams
            g.rowSpec = GridLayout.spec(r); g.columnSpec = GridLayout.spec(c)
            pad.addView(v, g)
        }
        place(btn("↑", 150f, 140f) { host.sendKey(KeyEvent.KEYCODE_DPAD_UP, selecting) }, 0, 1)
        place(btn("←", 150f, 140f) { host.sendKey(KeyEvent.KEYCODE_DPAD_LEFT, selecting) }, 1, 0)
        selectBtn = btn("Select", 150f, 140f) { selecting = !selecting; paintSelect() }
        selectBtn.setTextSize(TypedValue.COMPLEX_UNIT_PX, 34f * u)
        place(selectBtn, 1, 1)
        place(btn("→", 150f, 140f) { host.sendKey(KeyEvent.KEYCODE_DPAD_RIGHT, selecting) }, 1, 2)
        place(btn("↓", 150f, 140f) { host.sendKey(KeyEvent.KEYCODE_DPAD_DOWN, selecting) }, 2, 1)
        body.addView(pad, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val right = GridLayout(ctx).apply { columnCount = 2 }
        fun add(v: TextView) { right.addView(v) }
        add(btn("Select all", 240f, 140f) { host.ctxMenu(android.R.id.selectAll) })
        add(btn("Cut", 240f, 140f) { host.ctxMenu(android.R.id.cut) })
        add(btn("Copy", 240f, 140f) { host.ctxMenu(android.R.id.copy) })
        add(btn("Paste", 240f, 140f) { host.ctxMenu(android.R.id.paste) })
        add(btn("Home", 240f, 140f) { host.sendKey(KeyEvent.KEYCODE_MOVE_HOME, selecting) })
        add(btn("End", 240f, 140f) { host.sendKey(KeyEvent.KEYCODE_MOVE_END, selecting) })
        body.addView(right, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.15f))
        addView(body, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        paintSelect()
    }

    private fun paintSelect() {
        selectBtn.background = roundBg(if (selecting) theme.accent else theme.keyFill, 26f * u)
        selectBtn.setTextColor(if (selecting) 0xFFFFFFFF.toInt() else theme.text)
    }
}
