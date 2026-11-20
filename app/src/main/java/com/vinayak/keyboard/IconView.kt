package com.vinayak.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class IconView(ctx: Context, private val kind: String, private val color: Int) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.9f * d
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(c: Canvas) {
        p.color = color
        p.style = Paint.Style.STROKE
        val cx = width / 2f
        val cy = height / 2f
        val s = min(width, height) * 0.27f
        when (kind) {
            "emoji" -> {
                c.drawCircle(cx, cy, s, p)
                p.style = Paint.Style.FILL
                c.drawCircle(cx - s * 0.38f, cy - s * 0.25f, d * 1.6f, p)
                c.drawCircle(cx + s * 0.38f, cy - s * 0.25f, d * 1.6f, p)
                p.style = Paint.Style.STROKE
                c.drawArc(RectF(cx - s * 0.55f, cy - s * 0.45f, cx + s * 0.55f, cy + s * 0.55f), 20f, 140f, false, p)
            }
            "clip" -> {
                c.drawRoundRect(RectF(cx - s * 0.75f, cy - s * 0.8f, cx + s * 0.75f, cy + s * 1.0f), d * 3, d * 3, p)
                c.drawRoundRect(RectF(cx - s * 0.38f, cy - s * 1.1f, cx + s * 0.38f, cy - s * 0.55f), d * 2, d * 2, p)
                c.drawLine(cx - s * 0.4f, cy, cx + s * 0.4f, cy, p)
                c.drawLine(cx - s * 0.4f, cy + s * 0.45f, cx + s * 0.2f, cy + s * 0.45f, p)
            }
            "mic" -> {
                c.drawRoundRect(RectF(cx - s * 0.32f, cy - s * 1.0f, cx + s * 0.32f, cy + s * 0.2f), s * 0.32f, s * 0.32f, p)
                c.drawArc(RectF(cx - s * 0.7f, cy - s * 0.55f, cx + s * 0.7f, cy + s * 0.6f), 0f, 180f, false, p)
                c.drawLine(cx, cy + s * 0.6f, cx, cy + s * 1.0f, p)
                c.drawLine(cx - s * 0.4f, cy + s * 1.0f, cx + s * 0.4f, cy + s * 1.0f, p)
            }
            "hand" -> {
                c.drawRoundRect(RectF(cx - s * 0.5f, cy - s * 1.0f, cx + s * 0.5f, cy + s * 1.0f), d * 3, d * 3, p)
                c.drawLine(cx - s * 0.2f, cy, cx + s * 0.25f, cy, p)
                c.drawLine(cx + s * 0.25f, cy, cx + s * 0.05f, cy - s * 0.2f, p)
                c.drawLine(cx + s * 0.25f, cy, cx + s * 0.05f, cy + s * 0.2f, p)
            }
            "gear" -> {
                c.drawCircle(cx, cy, s * 0.35f, p)
                c.drawCircle(cx, cy, s * 0.68f, p)
                for (i in 0 until 8) {
                    val a = i * PI / 4
                    c.drawLine(cx + (s * 0.68f * cos(a)).toFloat(), cy + (s * 0.68f * sin(a)).toFloat(),
                        cx + (s * 0.98f * cos(a)).toFloat(), cy + (s * 0.98f * sin(a)).toFloat(), p)
                }
            }
            "more" -> {
                p.style = Paint.Style.FILL
                for (dx in listOf(-0.7f, 0f, 0.7f)) c.drawCircle(cx + s * dx, cy, d * 2f, p)
            }
            "del" -> {
                val path = Path()
                path.moveTo(cx - s * 1.1f, cy); path.lineTo(cx - s * 0.45f, cy - s * 0.75f)
                path.lineTo(cx + s * 1.1f, cy - s * 0.75f); path.lineTo(cx + s * 1.1f, cy + s * 0.75f)
                path.lineTo(cx - s * 0.45f, cy + s * 0.75f); path.close()
                c.drawPath(path, p)
                val xx = cx + s * 0.3f
                c.drawLine(xx - s * 0.3f, cy - s * 0.3f, xx + s * 0.3f, cy + s * 0.3f, p)
                c.drawLine(xx - s * 0.3f, cy + s * 0.3f, xx + s * 0.3f, cy - s * 0.3f, p)
            }
        }
    }
}
