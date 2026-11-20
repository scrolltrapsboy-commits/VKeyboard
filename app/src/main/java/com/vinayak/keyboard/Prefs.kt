package com.vinayak.keyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import org.json.JSONArray
import java.io.File

class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("kb_prefs", Context.MODE_PRIVATE)

    fun bool(k: String, d: Boolean): Boolean = sp.getBoolean(k, d)
    fun int(k: String, d: Int): Int = sp.getInt(k, d)
    fun str(k: String, d: String): String = sp.getString(k, d) ?: d
    val ver: Int get() = sp.getInt("ver", 0)

    fun put(k: String, v: Boolean) { sp.edit().putBoolean(k, v).putInt("ver", ver + 1).apply() }
    fun put(k: String, v: Int) { sp.edit().putInt(k, v).putInt("ver", ver + 1).apply() }
    fun put(k: String, v: String) { sp.edit().putString(k, v).putInt("ver", ver + 1).apply() }
    /** Saves without telling the keyboard to rebuild (used for live sliders). */
    fun putQuiet(k: String, v: Int) { sp.edit().putInt(k, v).apply() }

    // ---- learned words
    fun learned(): Set<String> = sp.getStringSet("learned", emptySet()) ?: emptySet()
    fun learn(w: String) {
        val s = HashSet(learned())
        s.add(w.lowercase())
        sp.edit().putStringSet("learned", s).apply()
    }

    // ---- clipboard history (pinned items stay, others are capped)
    private fun readList(k: String): List<String> {
        val a = JSONArray(sp.getString(k, "[]") ?: "[]")
        return List(a.length()) { a.getString(it) }
    }
    private fun writeList(k: String, l: List<String>) {
        sp.edit().putString(k, JSONArray(l).toString()).apply()
    }
    fun pins(): List<String> = readList("pins")
    fun clips(): List<String> = pins() + readList("clips").filter { it !in pins() }
    fun isPinned(t: String) = t in pins()
    fun addClip(t: String) {
        if (isPinned(t)) return
        val l = readList("clips").toMutableList()
        l.remove(t); l.add(0, t)
        writeList("clips", l.take(30))
    }
    fun togglePin(t: String) {
        val p = pins().toMutableList()
        if (p.contains(t)) {
            p.remove(t)
            val l = readList("clips").toMutableList(); l.add(0, t); writeList("clips", l.take(30))
        } else {
            p.add(0, t)
            writeList("clips", readList("clips").filter { it != t })
        }
        writeList("pins", p)
    }
    fun removeClip(t: String) {
        writeList("clips", readList("clips").filter { it != t })
        writeList("pins", pins().filter { it != t })
    }
    fun clearUnpinned() = writeList("clips", emptyList())

    // ---- recent emoji
    fun recentEmoji(): List<String> = readList("recent_emoji")
    fun addRecent(e: String) {
        val l = readList("recent_emoji").toMutableList()
        l.remove(e); l.add(0, e)
        writeList("recent_emoji", l.take(40))
    }
}

class Theme(
    val bg: Int, val key: Int, val keyPressed: Int, val text: Int,
    val dim: Int, val accent: Int, val popup: Int, val dark: Boolean
) {
    var bgImage: Bitmap? = null
    var dimAlpha = 90

    val keyFill: Int get() = if (bgImage != null) (key and 0x00FFFFFF) or (0xB8 shl 24) else key

    companion object {
        val NAMES = listOf("System", "Light", "Dark", "Black", "Ocean", "Rose", "Forest", "Lavender", "Sunset", "Custom", "Minimal Light", "Glassy", "AMOLED", "Pastel Gradient")

        private fun mix(a: Int, b: Int, f: Float): Int {
            fun ch(x: Int, y: Int) = (x + (y - x) * f).toInt().coerceIn(0, 255)
            val ar = a shr 16 and 255; val ag = a shr 8 and 255; val ab = a and 255
            val br = b shr 16 and 255; val bg = b shr 8 and 255; val bb = b and 255
            return (0xFF shl 24) or (ch(ar, br) shl 16) or (ch(ag, bg) shl 8) or ch(ab, bb)
        }

        private fun lum(c: Int): Float =
            (0.299f * (c shr 16 and 255) + 0.587f * (c shr 8 and 255) + 0.114f * (c and 255)) / 255f

        fun preset(id: Int, sysDark: Boolean = false, p: Prefs? = null): Theme = when (id) {
            1 -> Theme(0xFFF4F0F1.toInt(), 0xFFF7F5F6.toInt(), 0xFFE5E0E1.toInt(), 0xFF000000.toInt(), 0xFF8E8A8B.toInt(), 0xFF4272C4.toInt(), 0xFFFFFFFF.toInt(), false)
            2 -> Theme(0xFF262425.toInt(), 0xFF353334.toInt(), 0xFF4B4849.toInt(), 0xFFF2F2F2.toInt(), 0xFFA09C9D.toInt(), 0xFF8AB4F8.toInt(), 0xFF3F3D3E.toInt(), true)
            3 -> Theme(0xFF000000.toInt(), 0xFF1B1B1B.toInt(), 0xFF383838.toInt(), 0xFFFFFFFF.toInt(), 0xFF8E8E8E.toInt(), 0xFF8AB4F8.toInt(), 0xFF2A2A2A.toInt(), true)
            4 -> Theme(0xFFDCEBFA.toInt(), 0xFFF3F9FF.toInt(), 0xFFBBD6F2.toInt(), 0xFF0B2A4A.toInt(), 0xFF5B7FA6.toInt(), 0xFF1E6FD9.toInt(), 0xFFFFFFFF.toInt(), false)
            5 -> Theme(0xFFF9E3EA.toInt(), 0xFFFFF4F7.toInt(), 0xFFF0C2D0.toInt(), 0xFF4A1426.toInt(), 0xFFA5667A.toInt(), 0xFFD81B60.toInt(), 0xFFFFFFFF.toInt(), false)
            6 -> Theme(0xFF1E2B24.toInt(), 0xFF2B3D33.toInt(), 0xFF3E5648.toInt(), 0xFFE6F2EA.toInt(), 0xFF8FB09C.toInt(), 0xFF6FD39A.toInt(), 0xFF33493C.toInt(), true)
            7 -> Theme(0xFFE8E1F5.toInt(), 0xFFF6F2FD.toInt(), 0xFFCFC2EA.toInt(), 0xFF2A1B4D.toInt(), 0xFF7F6FA8.toInt(), 0xFF6A3FD6.toInt(), 0xFFFFFFFF.toInt(), false)
            8 -> Theme(0xFF2D1B2E.toInt(), 0xFF47304A.toInt(), 0xFF62456A.toInt(), 0xFFFFE9D6.toInt(), 0xFFC49A8A.toInt(), 0xFFFF8A4C.toInt(), 0xFF55385A.toInt(), true)
            10 -> Theme(0xFFF8F9FC.toInt(), 0xFFFFFFFF.toInt(), 0xFFE9EDF4.toInt(), 0xFF202633.toInt(), 0xFF788397.toInt(), 0xFF3867D6.toInt(), 0xFFFFFFFF.toInt(), false)
            11 -> Theme(0xFF18202D.toInt(), 0xBFFFFFFF.toInt(), 0xD9FFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFFE0E8F5.toInt(), 0xFF8ED8FF.toInt(), 0xE62B394D.toInt(), true)
            12 -> Theme(0xFF000000.toInt(), 0xFF111111.toInt(), 0xFF292929.toInt(), 0xFFFFFFFF.toInt(), 0xFF999999.toInt(), 0xFF00D7A0.toInt(), 0xFF171717.toInt(), true)
            13 -> Theme(0xFFF5EFFF.toInt(), 0xFFFFF8FE.toInt(), 0xFFF1DFFF.toInt(), 0xFF49375D.toInt(), 0xFF9A82AD.toInt(), 0xFFB46BCE.toInt(), 0xFFFFF8FE.toInt(), false)
            9 -> {
                val bg = p?.int("c_bg", 0xFFF4F0F1.toInt()) ?: 0xFFF4F0F1.toInt()
                val key = p?.int("c_key", 0xFFF7F5F6.toInt()) ?: 0xFFF7F5F6.toInt()
                val tx = p?.int("c_text", 0xFF000000.toInt()) ?: 0xFF000000.toInt()
                val ac = p?.int("c_accent", 0xFF4272C4.toInt()) ?: 0xFF4272C4.toInt()
                val dark = lum(bg) < 0.5f
                Theme(bg, key, mix(key, if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt(), 0.12f), tx, mix(tx, bg, 0.5f), ac,
                    mix(key, if (dark) 0xFFFFFFFF.toInt() else 0xFFFFFFFF.toInt(), 0.2f), dark)
            }
            else -> preset(if (sysDark) 2 else 1)
        }

        fun resolve(c: Context, p: Prefs): Theme {
            val sysDark = (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val t = preset(p.int("theme", 0), sysDark, p)
            val path = p.str("bgimg", "")
            if (path.isNotEmpty() && File(path).exists()) {
                try {
                    t.bgImage = BitmapFactory.decodeFile(path)
                    t.dimAlpha = (p.int("bgdim", 35) * 255 / 100).coerceIn(0, 255)
                } catch (_: Throwable) {
                }
            }
            return t
        }
    }
}

/** Draws the keyboard background: plain colour, or a cropped photo with a dim layer. */
class ThemeBgDrawable(private val t: Theme) : Drawable() {
    private val p = Paint(Paint.FILTER_BITMAP_FLAG)
    override fun draw(c: Canvas) {
        val b = bounds
        val bmp = t.bgImage
        if (bmp == null || b.width() == 0 || b.height() == 0) {
            when (t.bg) {
                0xFFF5EFFF.toInt() -> {
                    p.shader = LinearGradient(0f, 0f, b.width().toFloat(), b.height().toFloat(),
                        intArrayOf(0xFFF5EFFF.toInt(), 0xFFE8F4FF.toInt(), 0xFFFFEAF4.toInt()),
                        null, Shader.TileMode.CLAMP)
                    c.drawRect(b, p); p.shader = null
                }
                0xFF18202D.toInt() -> {
                    p.shader = LinearGradient(0f, 0f, b.width().toFloat(), b.height().toFloat(),
                        0xFF26364B.toInt(), 0xFF111923.toInt(), Shader.TileMode.CLAMP)
                    c.drawRect(b, p); p.shader = null
                }
                else -> c.drawColor(t.bg)
            }
            return
        }
        val scale = maxOf(b.width() / bmp.width.toFloat(), b.height() / bmp.height.toFloat())
        val sw = b.width() / scale
        val sh = b.height() / scale
        val sl = (bmp.width - sw) / 2f
        val st = (bmp.height - sh) / 2f
        c.drawBitmap(bmp, Rect(sl.toInt(), st.toInt(), (sl + sw).toInt(), (st + sh).toInt()), b, p)
        c.drawColor(t.dimAlpha shl 24)
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}

object Fonts {
    private var tf: Typeface? = null
    fun get(c: Context): Typeface {
        tf?.let { return it }
        val t = try {
            Typeface.createFromAsset(c.assets, "Inter-Regular.otf")
        } catch (e: Exception) {
            Typeface.DEFAULT
        }
        tf = t
        return t
    }
}
