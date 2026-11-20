package com.vinayak.keyboard

enum class KT { CHAR, SHIFT, DEL, MODE, ENTER, SPACE, GLOBE }

/** l / r are horizontal edges in reference pixels (1080 px wide layout). */
data class Key(
    val label: String,
    val type: KT = KT.CHAR,
    val l: Float = 0f,
    val r: Float = 0f,
    val alts: String = "",
    val target: Int = 0,
    val row: Int = 0
)

/** tops / heights are in reference pixels, relative to the top of the key area. */
class Page(val keys: List<Key>, val tops: List<Float>, val heights: List<Float>)

object Alts {
    val map: Map<String, String> = mapOf(
        "a" to "àáâäæãåā", "c" to "çćč", "d" to "ð", "e" to "èéêëēėę", "i" to "ìíîïīį",
        "l" to "ł", "n" to "ñń", "o" to "òóôöõøœō", "s" to "ßśš", "u" to "ùúûüū",
        "y" to "ýÿ", "z" to "žźż",
        "," to "'\";:-", "." to "?!,:;'\"…",
        "-" to "–—·_", "'" to "‘’‚‹›", "\"" to "“”„«»", "?" to "¿‽", "!" to "¡‼",
        "$" to "¢£€¥₹", "₹" to "$€£¥¢", "%" to "‰", "=" to "≠≈≡", "+" to "±",
        "/" to "÷\\", "<" to "≤«‹", ">" to "≥»›", "*" to "†‡★", "&" to "§",
        "(" to "[{<", ")" to "]}>", "0" to "°∅", "1" to "¹½⅓¼", "2" to "²⅔", "3" to "³¾", "4" to "⁴"
    )
}

object Layouts {
    const val LETTERS = 0
    const val SYM1 = 1
    const val SYM2 = 2

    // Geometry measured from the Samsung reference (1080 px wide)
    const val PITCH = 105.1f
    const val KW = 90f
    const val REF_H = 736f

    private fun cKey(s: String, row: Int, l: Float, alts: String? = null) =
        Key(s, KT.CHAR, l, l + KW, alts ?: (Alts.map[s] ?: ""), 0, row)

    private fun grid(chars: String, row: Int, x0: Float): List<Key> =
        chars.mapIndexed { i, ch -> cKey(ch.toString(), row, x0 + i * PITCH) }

    private fun special(label: String, type: KT, l: Float, r: Float, row: Int, target: Int = 0) =
        Key(label, type, l, r, "", target, row)

    private fun bottom(row: Int, label: String, target: Int): List<Key> = listOf(
        special(label, KT.MODE, 22f, 150f, row, target),
        special("GLOBE", KT.GLOBE, 167f, 256f, row),
        cKey(",", row, 273f).copy(r = 361f),
        special(" ", KT.SPACE, 378f, 794f, row),
        cKey(".", row, 811f).copy(r = 899f),
        special("ENTER", KT.ENTER, 916f, 1058f, row)
    )

    private val TOPS5 = listOf(20f, 149f, 300f, 451f, 601f)
    private val HEIGHTS5 = listOf(96f, 115f, 115f, 115f, 115f)

    fun build(page: Int, numRow: Boolean): Page {
        val k = ArrayList<Key>()
        val nums = "1234567890"
        return when (page) {
            SYM1 -> {
                k += grid(nums, 0, 22f)
                k += grid("+×÷=/_<>[]", 1, 22f)
                k += grid("!@#₹%^&*()", 2, 22f)
                k += special("1/2", KT.MODE, 22f, 149f, 3, SYM2)
                k += grid("-'\":;,?", 3, 179.7f)
                k += special("DEL", KT.DEL, 931f, 1058f, 3)
                k += bottom(4, "ABC", LETTERS)
                Page(k, TOPS5, HEIGHTS5)
            }
            SYM2 -> {
                k += grid(nums, 0, 22f)
                k += grid("`~\\|{}€£¥$", 1, 22f)
                k += grid("°•○●□■♠♥◇♣", 2, 22f)
                k += special("2/2", KT.MODE, 22f, 149f, 3, SYM1)
                k += grid("☆▪¤《》¡¿", 3, 179.7f)
                k += special("DEL", KT.DEL, 931f, 1058f, 3)
                k += bottom(4, "ABC", LETTERS)
                Page(k, TOPS5, HEIGHTS5)
            }
            else -> {
                var r = 0
                if (numRow) { k += grid(nums, r, 22f); r++ }
                "qwertyuiop".forEachIndexed { i, ch ->
                    val digit = if (i == 9) "0" else (i + 1).toString()
                    val alts = if (numRow) null else digit + (Alts.map[ch.toString()] ?: "")
                    k += cKey(ch.toString(), r, 22f + i * PITCH, alts)
                }
                r++
                k += grid("asdfghjkl", r, 74.5f); r++
                k += special("SHIFT", KT.SHIFT, 22f, 149f, r)
                k += grid("zxcvbnm", r, 179.7f)
                k += special("DEL", KT.DEL, 931f, 1058f, r); r++
                k += bottom(r, "!#1", SYM1)
                if (numRow) Page(k, TOPS5, HEIGHTS5)
                else Page(k, listOf(38f, 208f, 378f, 548f), listOf(135f, 135f, 135f, 135f))
            }
        }
    }
}
