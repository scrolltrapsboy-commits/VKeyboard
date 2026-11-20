package com.vinayak.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

private val SMILEYS = "😀 😃 😄 😁 😆 😅 😂 🤣 🥲 ☺️ 😊 😇 🙂 🙃 😉 😌 😍 🥰 😘 😗 😙 😚 😋 😛 😝 😜 🤪 🤨 🧐 🤓 😎 🥸 🤩 🥳 😏 😒 😞 😔 😟 😕 🙁 ☹️ 😣 😖 😫 😩 🥺 😢 😭 😤 😠 😡 🤬 🤯 😳 🥵 🥶 😱 😨 😰 😥 😓 🤗 🤔 🫡 🤭 🤫 🤥 😶 😐 😑 😬 🙄 😯 😦 😧 😮 😲 🥱 😴 🤤 😪 😵 🤐 🥴 🤢 🤮 🤧 😷 🤒 🤕 🤑 🤠 😈 👿 👹 👺 🤡 💩 👻 💀 ☠️ 👽 👾 🤖 🎃".split(" ")
private val GESTURES = "👍 👎 👊 ✊ 🤛 🤜 👏 🙌 👐 🤲 🤝 🙏 ✌️ 🤞 🤟 🤘 👌 🤌 🤏 👈 👉 👆 👇 ☝️ ✋ 🤚 🖐️ 🖖 👋 🤙 💪 🦾 🖕 ✍️ 🤳 💅 👀 👁️ 👅 👄 🧠 👶 👦 👧 🧑 👨 👩 🧔 👴 👵".split(" ")
private val SYMBOLS = "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟 ☮️ ✝️ ☪️ 🕉️ ☯️ ✨ ⭐ 🌟 💫 ⚡ 🔥 💥 💯 ✅ ❌ ❓ ❗ ‼️ ⚠️ 🚫 ♻️ 🔴 🟠 🟡 🟢 🔵 🟣 ⚫ ⚪ ▶️ ⏸️ ⏩ 🔁 ➕ ➖ ➗ ✖️ ™️ ©️ ®️".split(" ")
private val NATURE = "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🙈 🙉 🙊 🐔 🐧 🐦 🦆 🦅 🦉 🐺 🐴 🦄 🐝 🦋 🐌 🐞 🐢 🐍 🐙 🐬 🐳 🦈 🐘 🦒 🐒 🌸 🌹 🌻 🌲 🌴 🍀 🍁 🌍 🌙 ☀️ ⛅ 🌈 ❄️ 🍎 🍌 🍉 🍇 🍓 🥭 🍍 🥑 🌽 🍕 🍔 🍟 🌭 🌮 🍗 🍜 🍣 🍩 🍪 🎂 🍫 🍿 ☕ 🍵 🍺 🥤".split(" ")
private val OBJECTS = "⚽ 🏀 🏈 ⚾ 🎾 🏐 🏏 🏓 🎱 🥊 🏆 🥇 🎮 🎯 🎲 🎵 🎶 🎤 🎧 🎸 🎬 📱 💻 ⌨️ 🖥️ 📷 📚 ✏️ 📌 📎 🔑 🔒 💡 🔋 💰 💳 🎁 🎈 🎉 🎊 🚗 🚕 🚌 🏍️ 🚲 ✈️ 🚀 🚁 🚢 🏠 🏢 🏥 🏖️ ⛰️ 🗽 ⏰ ⌚ 🇮🇳".split(" ")

private fun label(ctx: Context, s: String, sp: Float, color: Int): TextView = TextView(ctx).apply {
    text = s; textSize = sp; setTextColor(color); gravity = Gravity.CENTER
}

class EmojiPanel(
    ctx: Context,
    private val theme: Theme,
    private val availW: Int,
    private val onEmoji: (String) -> Unit,
    onBack: () -> Unit,
    onDel: () -> Unit
) : LinearLayout(ctx) {
    private val d = resources.displayMetrics.density
    private val cats = listOf("😀" to SMILEYS, "👍" to GESTURES, "❤️" to SYMBOLS, "🐶" to NATURE, "⚽" to OBJECTS)
    private val tabs = ArrayList<TextView>()
    private val grid = GridLayout(ctx).apply { columnCount = 8 }
    private val scroll = ScrollView(ctx)

    init {
        orientation = VERTICAL
        setBackgroundColor(theme.bg)
        val head = LinearLayout(ctx).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val back = label(ctx, "ABC", 16f, theme.text)
        back.setOnClickListener { onBack() }
        head.addView(back, LayoutParams((60 * d).toInt(), (46 * d).toInt()))
        cats.forEachIndexed { i, cat ->
            val t = label(ctx, cat.first, 20f, theme.text)
            t.setOnClickListener { select(i) }
            tabs.add(t)
            head.addView(t, LayoutParams(0, (46 * d).toInt(), 1f))
        }
        val del = IconView(ctx, "del", theme.text)
        del.setOnClickListener { onDel() }
        head.addView(del, LayoutParams((60 * d).toInt(), (46 * d).toInt()))
        addView(head, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        scroll.addView(grid)
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        select(0)
    }

    private fun select(i: Int) {
        tabs.forEachIndexed { idx, t -> t.alpha = if (idx == i) 1f else 0.45f }
        grid.removeAllViews()
        for (e in cats[i].second) {
            val tv = label(context, e, 26f, theme.text)
            val lp = GridLayout.LayoutParams()
            lp.width = availW / 8
            lp.height = (46 * d).toInt()
            tv.layoutParams = lp
            tv.setOnClickListener { onEmoji(e) }
            grid.addView(tv)
        }
        scroll.scrollTo(0, 0)
    }
}

class ClipPanel(
    ctx: Context,
    private val theme: Theme,
    private val prefs: Prefs,
    private val onPaste: (String) -> Unit,
    onBack: () -> Unit
) : LinearLayout(ctx) {
    private val d = resources.displayMetrics.density
    private val list = LinearLayout(ctx).apply { orientation = VERTICAL }

    init {
        orientation = VERTICAL
        setBackgroundColor(theme.bg)
        val head = LinearLayout(ctx).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val back = label(ctx, "ABC", 16f, theme.text)
        back.setOnClickListener { onBack() }
        head.addView(back, LayoutParams((60 * d).toInt(), (46 * d).toInt()))
        head.addView(label(ctx, "Clipboard", 16f, theme.text), LayoutParams(0, (46 * d).toInt(), 1f))
        val clear = label(ctx, "Clear all", 14f, theme.accent)
        clear.setOnClickListener { prefs.clearClips(); refresh() }
        head.addView(clear, LayoutParams((90 * d).toInt(), (46 * d).toInt()))
        addView(head, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val sv = ScrollView(ctx)
        sv.addView(list)
        addView(sv, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun refresh() {
        list.removeAllViews()
        val clips = prefs.clips()
        if (clips.isEmpty()) {
            val t = label(context, "Copied text will appear here", 14f, theme.dim)
            t.setPadding(0, (30 * d).toInt(), 0, 0)
            list.addView(t)
            return
        }
        val hint = label(context, "Tap to paste • long-press to delete", 12f, theme.dim)
        list.addView(hint)
        for (t in clips) {
            val tv = TextView(context).apply {
                text = t; textSize = 15f; setTextColor(theme.text)
                maxLines = 3; ellipsize = TextUtils.TruncateAt.END
                setPadding((14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (10 * d).toInt())
                background = GradientDrawable().apply { setColor(theme.key); cornerRadius = 10 * d }
            }
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            lp.setMargins((8 * d).toInt(), (3 * d).toInt(), (8 * d).toInt(), (3 * d).toInt())
            tv.setOnClickListener { onPaste(t) }
            tv.setOnLongClickListener { prefs.removeClip(t); refresh(); true }
            list.addView(tv, lp)
        }
    }
}
