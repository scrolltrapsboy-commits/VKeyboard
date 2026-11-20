package com.vinayak.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Line icons drawn to match the Samsung keyboard. [u] = pixels per reference pixel (screen width / 1080). */
class IconView(ctx: Context, private val kind: String, private var color: Int, private val u: Float) : View(ctx) {
    var highlight = 0
        set(v) { field = v; invalidate() }

    fun setIconColor(c: Int) { color = c; invalidate() }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4.4f * u
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val f = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val tx = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; typeface = Fonts.get(ctx); isFakeBoldText = true
    }

    override fun onDraw(c: Canvas) {
        p.color = color
        f.color = color
        tx.color = color
        val cx = width / 2f
        val cy = height / 2f
        if (highlight != 0) {
            f.color = highlight
            c.drawCircle(cx, cy, 48f * u, f)
            f.color = color
        }
        fun ln(x1: Float, y1: Float, x2: Float, y2: Float) = c.drawLine(cx + x1 * u, cy + y1 * u, cx + x2 * u, cy + y2 * u, p)
        fun rr(l: Float, t: Float, r: Float, b: Float, rad: Float) =
            c.drawRoundRect(RectF(cx + l * u, cy + t * u, cx + r * u, cy + b * u), rad * u, rad * u, p)
        fun circ(x: Float, y: Float, r: Float) = c.drawCircle(cx + x * u, cy + y * u, r * u, p)
        fun dot(x: Float, y: Float, r: Float) = c.drawCircle(cx + x * u, cy + y * u, r * u, f)
        fun label(s: String, size: Float, dy: Float) { tx.textSize = size * u; c.drawText(s, cx, cy + dy * u, tx) }

        when (kind) {
            "emoji" -> {
                circ(0f, 0f, 22.8f)
                dot(-7.7f, -5.8f, 2.8f); dot(7.7f, -5.8f, 2.8f)
                c.drawArc(RectF(cx - 11f * u, cy - 7f * u, cx + 11f * u, cy + 11f * u), 0f, 180f, false, p)
            }
            "clip" -> {
                rr(-19.8f, -22.8f, 19.8f, 22.8f, 9f)
                val t = Path()
                t.moveTo(cx - 9.5f * u, cy - 22.8f * u); t.lineTo(cx - 9.5f * u, cy - 14f * u)
                t.quadTo(cx - 9.5f * u, cy - 10.5f * u, cx - 6f * u, cy - 10.5f * u)
                t.lineTo(cx + 6f * u, cy - 10.5f * u)
                t.quadTo(cx + 9.5f * u, cy - 10.5f * u, cx + 9.5f * u, cy - 14f * u)
                t.lineTo(cx + 9.5f * u, cy - 22.8f * u)
                c.drawPath(t, p)
            }
            "gear" -> {
                val g = Path()
                for (i in 0 until 6) {
                    val a = i * 60.0 - 90.0
                    for ((da, r) in listOf(-23.0 to 17.5, -13.0 to 22.0, 13.0 to 22.0, 23.0 to 17.5)) {
                        val rad = (a + da) * PI / 180.0
                        val x = cx + (r * u * cos(rad)).toFloat()
                        val y = cy + (r * u * sin(rad)).toFloat()
                        if (i == 0 && da == -23.0) g.moveTo(x, y) else g.lineTo(x, y)
                    }
                }
                g.close()
                c.drawPath(g, p)
                circ(0f, 0f, 7.5f)
            }
            "more" -> for (dx in listOf(-17f, 0f, 17f)) dot(dx, 0f, 4.2f)
            "hand", "onehand" -> {
                rr(-17f, -24f, 17f, 24f, 5f)
                for (gx in listOf(2f, 7.5f, 13f)) for (gy in listOf(6f, 12f, 18f)) dot(gx, gy, 1.7f)
            }
            "mic" -> {
                rr(-8.5f, -26.5f, 8.5f, 2f, 8.5f)
                c.drawArc(RectF(cx - 19.5f * u, cy - 14f * u, cx + 19.5f * u, cy + 20f * u), 0f, 180f, false, p)
                ln(-12f, 27f, 12f, 27f)
            }
            "chev" -> { ln(-21f, -12.5f, 0f, 12.5f); ln(0f, 12.5f, 21f, -12.5f) }
            "close" -> { ln(-10f, -10f, 10f, 10f); ln(-10f, 10f, 10f, -10f) }
            "del" -> {
                val d = Path()
                d.moveTo(cx - 27f * u, cy); d.lineTo(cx - 12f * u, cy - 19.5f * u)
                d.lineTo(cx + 27f * u, cy - 19.5f * u); d.lineTo(cx + 27f * u, cy + 19.5f * u)
                d.lineTo(cx - 12f * u, cy + 19.5f * u); d.close()
                c.drawPath(d, p)
                ln(0f, -7.5f, 14f, 7.5f); ln(0f, 7.5f, 14f, -7.5f)
            }
            "keyboard" -> {
                rr(-26f, -20f, 26f, 20f, 8f)
                for (x in listOf(-16f, -8f, 0f, 8f, 16f)) { dot(x, -9f, 1.9f); dot(x, 0f, 1.9f) }
                ln(-9f, 10f, 9f, 10f)
            }
            "pin" -> {
                c.save(); c.rotate(40f, cx, cy)
                ln(-12f, -22f, 12f, -22f); ln(-7f, -22f, -7f, -3f); ln(7f, -22f, 7f, -3f)
                ln(-14f, -3f, 14f, -3f); ln(0f, -3f, 0f, 24f)
                c.restore()
            }
            "trash" -> {
                ln(-22f, -16f, 22f, -16f); rr(-7f, -24f, 7f, -16f, 3f)
                val b = Path()
                b.moveTo(cx - 17f * u, cy - 10f * u); b.lineTo(cx - 14f * u, cy + 24f * u)
                b.lineTo(cx + 14f * u, cy + 24f * u); b.lineTo(cx + 17f * u, cy - 10f * u)
                c.drawPath(b, p)
                ln(-5f, -2f, -5f, 16f); ln(5f, -2f, 5f, 16f)
            }
            "search" -> { circ(-5f, -5f, 17f); ln(7f, 7f, 23f, 23f) }
            "clock" -> { circ(0f, 0f, 23f); ln(0f, 0f, 0f, -13f); ln(0f, 0f, 11f, 6f) }
            "dog" -> {
                circ(0f, 3f, 17f); rr(-29f, -14f, -17f, 14f, 6f); rr(17f, -14f, 29f, 14f, 6f)
                dot(-6f, 0f, 2.4f); dot(6f, 0f, 2.4f); dot(0f, 9f, 3.2f)
            }
            "food" -> {
                ln(-14f, -24f, -14f, 24f); ln(-20f, -24f, -20f, -8f); ln(-8f, -24f, -8f, -8f); ln(-20f, -8f, -8f, -8f)
                ln(12f, -24f, 12f, 24f); ln(12f, -24f, 21f, -12f); ln(21f, -12f, 21f, 2f); ln(21f, 2f, 12f, 2f)
            }
            "house" -> {
                ln(-23f, 2f, 0f, -22f); ln(0f, -22f, 23f, 2f)
                ln(-16f, -4f, -16f, 22f); ln(-16f, 22f, 16f, 22f); ln(16f, 22f, 16f, -4f)
                ln(-6f, 22f, -6f, 8f); ln(-6f, 8f, 6f, 8f); ln(6f, 8f, 6f, 22f)
            }
            "ball" -> {
                circ(0f, 0f, 23f); ln(0f, -23f, 0f, 23f); ln(-23f, 0f, 23f, 0f)
                c.drawArc(RectF(cx - 37f * u, cy - 22f * u, cx - 9f * u, cy + 22f * u), -50f, 100f, false, p)
                c.drawArc(RectF(cx + 9f * u, cy - 22f * u, cx + 37f * u, cy + 22f * u), 130f, 100f, false, p)
            }
            "objects" -> { rr(-20f, -22f, 20f, 22f, 7f); ln(-7f, -22f, -7f, 22f) }
            "symbols" -> { rr(-22f, -22f, 22f, 22f, 7f); label("!?", 28f, 10f) }
            "flag" -> {
                ln(-12f, -24f, -12f, 24f)
                val g = Path()
                g.moveTo(cx - 12f * u, cy - 22f * u); g.lineTo(cx + 24f * u, cy - 15f * u); g.lineTo(cx - 12f * u, cy - 6f * u)
                c.drawPath(g, p)
            }
            "gif" -> { rr(-26f, -19f, 26f, 19f, 8f); label("GIF", 22f, 8f) }
            "sticker" -> { rr(-22f, -22f, 22f, 22f, 8f); ln(22f, 8f, 8f, 8f); ln(8f, 8f, 8f, 22f) }
            "face" -> {
                f.color = 0xFFFFC83D.toInt(); c.drawCircle(cx, cy, 24f * u, f)
                f.color = 0xFF4A3000.toInt(); p.color = 0xFF4A3000.toInt(); p.strokeWidth = 3.4f * u
                dot(-8f, -6f, 2.8f); dot(8f, -6f, 2.8f)
                c.drawArc(RectF(cx - 11f * u, cy - 6f * u, cx + 11f * u, cy + 12f * u), 10f, 160f, false, p)
            }
            "emoticon" -> {
                f.color = 0xFFFFC83D.toInt(); c.drawCircle(cx, cy, 24f * u, f)
                tx.color = 0xFF4A3000.toInt(); label(":-)", 22f, 8f)
            }
            "ksize" -> {
                rr(-20f, -20f, 20f, 20f, 7f)
                for ((x, y) in listOf(-20f to -20f, 20f to -20f, -20f to 20f, 20f to 20f, 0f to -20f, 0f to 20f)) dot(x, y, 3.6f)
            }
            "edit" -> {
                ln(-8f, -17f, 0f, -25f); ln(0f, -25f, 8f, -17f); ln(-8f, 17f, 0f, 25f); ln(0f, 25f, 8f, 17f)
                ln(-17f, -8f, -25f, 0f); ln(-25f, 0f, -17f, 8f); ln(17f, -8f, 25f, 0f); ln(25f, 0f, 17f, 8f)
            }
            "palette" -> {
                circ(0f, 0f, 23f)
                dot(-9f, -8f, 3.6f); dot(0f, -13f, 3.6f); dot(10f, -8f, 3.6f); dot(-12f, 4f, 3.6f); circ(8f, 10f, 5f)
            }
            "numrow" -> { rr(-25f, -17f, 25f, 17f, 7f); label("123", 20f, 7f) }
            "abc" -> { rr(-25f, -17f, 25f, 17f, 7f); label("Abc", 20f, 7f) }
            "swipe" -> {
                val s = Path()
                s.moveTo(cx - 22f * u, cy + 12f * u)
                s.cubicTo(cx - 12f * u, cy - 28f * u, cx + 6f * u, cy + 28f * u, cx + 22f * u, cy - 12f * u)
                c.drawPath(s, p)
                dot(-22f, 12f, 4f)
            }
        }
    }
}
