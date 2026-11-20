package com.vinayak.keyboard

import android.content.Context
import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln

class Dictionary(private val ctx: Context, private val prefs: Prefs) {
    private val words = ArrayList<String>()
    private val rank = HashMap<String, Int>()
    @Volatile
    var ready = false

    fun load() {
        Thread {
            try {
                val tmpW = ArrayList<String>()
                val tmpR = HashMap<String, Int>()
                ctx.assets.open("words.txt").bufferedReader().useLines { seq ->
                    seq.forEach { line ->
                        val t = line.trim().lowercase()
                        if (t.isNotEmpty() && !tmpR.containsKey(t)) { tmpR[t] = tmpW.size; tmpW.add(t) }
                    }
                }
                val extras = "i'm i'll i've i'd don't doesn't didn't can't won't isn't aren't wasn't weren't " +
                    "it's that's there's what's let's you're they're we're couldn't wouldn't shouldn't haven't hasn't"
                extras.split(" ").forEach { if (!tmpR.containsKey(it)) { tmpR[it] = tmpW.size; tmpW.add(it) } }
                words.addAll(tmpW)
                rank.putAll(tmpR)
                ready = true
            } catch (_: Exception) {
            }
        }.start()
    }

    fun known(w: String): Boolean {
        val l = w.lowercase()
        return rank.containsKey(l) || prefs.learned().contains(l)
    }

    fun suggest(prefix: String, n: Int): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val p = prefix.lowercase()
        val out = ArrayList<String>()
        for (w in prefs.learned().sorted()) if (w.length > p.length && w.startsWith(p) && out.size < n) out.add(w)
        if (ready) for (w in words) {
            if (out.size >= n) break
            if (w.length > p.length && w.startsWith(p) && w !in out) out.add(w)
        }
        return out
    }

    /** Most frequent dictionary word within one edit of [w], or null. */
    fun correct(w: String): String? {
        if (!ready) return null
        val lw = w.lowercase()
        if (known(lw) || lw.length < 2) return null
        for (cand in words) {
            if (abs(cand.length - lw.length) > 1) continue
            if (cand[0] != lw[0]) continue
            if (editLe1(lw, cand)) return cand
        }
        return null
    }

    private fun editLe1(a: String, b: String): Boolean {
        if (a == b) return true
        val la = a.length
        val lb = b.length
        if (abs(la - lb) > 1) return false
        if (la == lb) {
            var i = 0
            while (i < la && a[i] == b[i]) i++
            if (i == la) return true
            if (a.substring(i + 1) == b.substring(i + 1)) return true
            if (i + 1 < la && a[i] == b[i + 1] && a[i + 1] == b[i] && a.substring(i + 2) == b.substring(i + 2)) return true
            return false
        }
        val s = if (la < lb) a else b
        val l = if (la < lb) b else a
        var i = 0
        while (i < s.length && s[i] == l[i]) i++
        return s.substring(i) == l.substring(i + 1)
    }

    // ------------------------------------------------------------ swipe typing
    private fun pathLen(p: List<PointF>): Float {
        var t = 0f
        for (i in 1 until p.size) t += hypot(p[i].x - p[i - 1].x, p[i].y - p[i - 1].y)
        return t
    }

    private fun resample(p: List<PointF>, n: Int): List<PointF> {
        if (p.size < 2) return List(n) { p[0] }
        val dist = FloatArray(p.size)
        for (i in 1 until p.size) dist[i] = dist[i - 1] + hypot(p[i].x - p[i - 1].x, p[i].y - p[i - 1].y)
        val total = dist.last()
        if (total == 0f) return List(n) { p[0] }
        val out = ArrayList<PointF>(n)
        var j = 1
        for (i in 0 until n) {
            val t = total * i / (n - 1)
            while (j < p.size - 1 && dist[j] < t) j++
            val seg = dist[j] - dist[j - 1]
            val f = if (seg == 0f) 0f else (t - dist[j - 1]) / seg
            out.add(PointF(p[j - 1].x + (p[j].x - p[j - 1].x) * f, p[j - 1].y + (p[j].y - p[j - 1].y) * f))
        }
        return out
    }

    fun swipe(path: List<PointF>, centers: Map<Char, PointF>, unit: Float, n: Int = 3): List<String> {
        if (!ready || path.size < 2 || centers.isEmpty()) return emptyList()
        val userLen = pathLen(path)
        if (userLen < unit) return emptyList()
        val start = path.first()
        val end = path.last()
        val user = resample(path, 32)
        fun near(p: PointF, ch: Char): Boolean {
            val c = centers[ch] ?: return false
            return hypot(c.x - p.x, c.y - p.y) < unit * 1.6f
        }
        val scored = ArrayList<Pair<Float, String>>()
        for ((idx, w) in words.withIndex()) {
            if (w.length < 2) continue
            if (!near(start, w[0]) || !near(end, w.last())) continue
            val pts = ArrayList<PointF>()
            var ok = true
            var prev = '\u0000'
            for (ch in w) {
                if (ch == prev) continue
                val c = centers[ch]
                if (c == null) { ok = false; break }
                pts.add(c); prev = ch
            }
            if (!ok || pts.size < 2) continue
            val ideal = resample(pts, 32)
            val idealLen = pathLen(pts)
            var sum = 0f
            for (i in 0 until 32) sum += hypot(user[i].x - ideal[i].x, user[i].y - ideal[i].y)
            val cost = sum / 32f / unit + 0.3f * abs(ln(idealLen / userLen)) + idx * 0.00004f
            scored.add(cost to w)
        }
        return scored.sortedBy { it.first }.take(n).map { it.second }
    }
}
