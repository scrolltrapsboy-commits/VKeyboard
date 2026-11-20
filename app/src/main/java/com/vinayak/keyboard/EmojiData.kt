package com.vinayak.keyboard

import android.content.Context
import android.graphics.Paint

/** Full Unicode emoji set (Google/Noto glyphs on your phone), filtered to what this phone can draw. */
object EmojiData {
    class E(val ch: String, val cat: Char, val tags: String)

    @Volatile var ready = false
    private var loading = false
    private var all: List<E> = emptyList()
    private val groups = HashMap<Char, List<E>>()
    private val paint = Paint()
    private val MODS = listOf("\uD83C\uDFFB", "\uD83C\uDFFC", "\uD83C\uDFFD", "\uD83C\uDFFE", "\uD83C\uDFFF")

    fun load(ctx: Context) {
        if (ready || loading) return
        loading = true
        Thread {
            try {
                val list = ArrayList<E>()
                ctx.assets.open("emoji.txt").bufferedReader().useLines { seq ->
                    seq.forEach { line ->
                        val p = line.split("\t")
                        if (p.size >= 2 && p[0].isNotEmpty() && paint.hasGlyph(p[1])) {
                            list.add(E(p[1], p[0][0], if (p.size > 4) p[4].lowercase() else ""))
                        }
                    }
                }
                val g = HashMap<Char, List<E>>()
                for (c in "SPAFTCOYG") g[c] = list.filter { it.cat == c }
                g['S'] = (g['S'] ?: emptyList()) + (g['P'] ?: emptyList())   // smileys + people share one tab
                all = list
                groups.putAll(g)
                ready = true
            } catch (_: Exception) {
            }
            loading = false
        }.start()
    }

    fun group(cat: Char): List<E> = groups[cat] ?: emptyList()

    fun search(q: String): List<E> {
        val words = q.trim().lowercase().split(" ").filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return all.filter { e -> words.all { w -> e.tags.contains(w) } }.take(60)
    }

    /** Five skin-tone versions if this phone supports them for [ch], otherwise null. */
    fun skinVariants(ch: String): List<String>? {
        if (ch.contains('\u200D')) return null
        val base = ch.replace("\uFE0F", "")
        val v = MODS.map { base + it }
        return if (v.all { paint.hasGlyph(it) } && !paint.hasGlyph(base + MODS[0] + MODS[0])) v else null
    }
}
