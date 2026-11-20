package com.vinayak.keyboard

enum class KT { CHAR, SHIFT, DEL, MODE, ENTER, SPACE, GLOBE, SPACER }

data class Key(
    val label: String,
    val type: KT = KT.CHAR,
    val w: Float = 1f,
    val alts: String = "",
    val target: Int = 0
)

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

    private fun c(s: String) = Key(s, KT.CHAR, 1f, Alts.map[s] ?: "")
    private fun row(s: String): List<Key> = s.map { c(it.toString()) }
    private fun del() = Key("DEL", KT.DEL, 1.5f)
    private fun bottom(label: String, target: Int) = listOf(
        Key(label, KT.MODE, 1.5f, "", target),
        Key("GLOBE", KT.GLOBE),
        c(","),
        Key(" ", KT.SPACE, 4f),
        c("."),
        Key("ENTER", KT.ENTER, 1.5f)
    )

    fun build(page: Int, numRow: Boolean): List<List<Key>> {
        val nums = row("1234567890")
        return when (page) {
            SYM1 -> listOf(
                nums,
                row("+×÷=/_<>[]"),
                row("!@#₹%^&*()"),
                listOf(Key("1/2", KT.MODE, 1.5f, "", SYM2)) + row("-'\":;,?") + del(),
                bottom("ABC", LETTERS)
            )
            SYM2 -> listOf(
                nums,
                row("`~\\|{}€£¥$"),
                row("°•○●□■♠♥◇♣"),
                listOf(Key("2/2", KT.MODE, 1.5f, "", SYM1)) + row("☆▪¤《》¡¿") + del(),
                bottom("ABC", LETTERS)
            )
            else -> {
                val top = "qwertyuiop".mapIndexed { i, ch ->
                    val digit = if (i == 9) "0" else (i + 1).toString()
                    val extra = Alts.map[ch.toString()] ?: ""
                    if (numRow) c(ch.toString()) else Key(ch.toString(), KT.CHAR, 1f, digit + extra)
                }
                val rows = ArrayList<List<Key>>()
                if (numRow) rows.add(nums)
                rows.add(top)
                rows.add(listOf(Key("", KT.SPACER, 0.5f)) + row("asdfghjkl") + Key("", KT.SPACER, 0.5f))
                rows.add(listOf(Key("SHIFT", KT.SHIFT, 1.5f)) + row("zxcvbnm") + del())
                rows.add(bottom("!#1", SYM1))
                rows
            }
        }
    }
}
