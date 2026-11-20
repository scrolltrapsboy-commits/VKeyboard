package com.vinayak.keyboard

import android.content.Context
import android.content.res.Configuration
import org.json.JSONArray

class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("kb_prefs", Context.MODE_PRIVATE)

    fun bool(k: String, d: Boolean): Boolean = sp.getBoolean(k, d)
    fun int(k: String, d: Int): Int = sp.getInt(k, d)
    val ver: Int get() = sp.getInt("ver", 0)

    fun put(k: String, v: Boolean) { sp.edit().putBoolean(k, v).putInt("ver", ver + 1).apply() }
    fun put(k: String, v: Int) { sp.edit().putInt(k, v).putInt("ver", ver + 1).apply() }

    // Learned words (personal dictionary)
    fun learned(): Set<String> = sp.getStringSet("learned", emptySet()) ?: emptySet()
    fun learn(w: String) {
        val s = HashSet(learned())
        s.add(w.lowercase())
        sp.edit().putStringSet("learned", s).apply()
    }

    // Clipboard history
    fun clips(): List<String> {
        val a = JSONArray(sp.getString("clips", "[]") ?: "[]")
        return List(a.length()) { a.getString(it) }
    }
    private fun saveClips(l: List<String>) {
        sp.edit().putString("clips", JSONArray(l).toString()).apply()
    }
    fun addClip(t: String) {
        val l = clips().toMutableList()
        l.remove(t); l.add(0, t)
        saveClips(l.take(30))
    }
    fun removeClip(t: String) = saveClips(clips().filter { it != t })
    fun clearClips() = saveClips(emptyList())
}

class Theme(val dark: Boolean) {
    val bg = if (dark) 0xFF1F1F21.toInt() else 0xFFF3F0F0.toInt()
    val key = if (dark) 0xFF3A3A3D.toInt() else 0xFFFAF8F8.toInt()
    val keyPressed = if (dark) 0xFF55555A.toInt() else 0xFFDCD7D7.toInt()
    val text = if (dark) 0xFFEFEFEF.toInt() else 0xFF1B1B1B.toInt()
    val dim = if (dark) 0xFF9A9A9E.toInt() else 0xFF8A8585.toInt()
    val accent = if (dark) 0xFF8AB4F8.toInt() else 0xFF3E6FC9.toInt()
    val popup = if (dark) 0xFF2C2C2F.toInt() else 0xFFFFFFFF.toInt()

    companion object {
        fun resolve(c: Context, p: Prefs): Theme {
            val dark = when (p.int("theme", 0)) {
                1 -> false
                2 -> true
                else -> (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            }
            return Theme(dark)
        }
    }
}
