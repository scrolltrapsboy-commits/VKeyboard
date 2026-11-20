package com.vinayak.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot

class KeyboardView(
    ctx: Context,
    private val prefs: Prefs,
    private val theme: Theme,
    private val cb: Callback
) : View(ctx) {

    interface Callback {
        fun onKey(text: String)
        fun onDelete()
        fun onEnter()
        fun onGlobe(long: Boolean)
        fun onCursor(steps: Int)
        fun onSwipe(path: List<PointF>)
        fun onFeedback()
    }

    var page = Layouts.LETTERS
        set(v) { field = v; relayout(); invalidate() }
    var shift = 0
        set(v) { field = v; invalidate() }
    var enterLabel = "icon"
        set(v) { field = v; invalidate() }

    private var rows: List<List<Key>> = emptyList()
    private var rects: List<List<RectF>> = emptyList()
    private val d = resources.displayMetrics.density
    private val h = Handler(Looper.getMainLooper())
    private var lastShiftTap = 0L

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val ip = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        strokeWidth = 1.9f * d
    }

    private fun dp(v: Float) = v * d

    private inner class PS(val id: Int, val startKey: Key?, var rect: RectF?, var row: Int, val sx: Float, val sy: Float) {
        var key: Key? = startKey
        val path = ArrayList<PointF>()
        var altChars: String? = null
        var altRect = RectF()
        var altSel = 0
        var swiping = false
        var cursorMode = false
        var lastX = sx
        var consumed = false
        var longRun: Runnable? = null
        var repRun: Runnable? = null
        var repCount = 0
    }

    private val states = HashMap<Int, PS>()

    init {
        relayout()
    }

    // ---------------------------------------------------------------- layout
    override fun onMeasure(w: Int, hh: Int) {
        val kh = dp(prefs.int("keyh", 52).toFloat() + 7f)
        setMeasuredDimension(MeasureSpec.getSize(w), (kh * 5 + dp(8f)).toInt())
    }

    override fun onSizeChanged(w: Int, hh: Int, ow: Int, oh: Int) {
        relayout()
    }

    private fun relayout() {
        rows = Layouts.build(page, prefs.bool("numrow", true))
        val w = width.toFloat()
        val hgt = height.toFloat()
        if (w == 0f || hgt == 0f) { rects = emptyList(); return }
        val padH = dp(3f)
        val padV = dp(4f)
        val rh = (hgt - padV * 2) / rows.size
        rects = rows.mapIndexed { i, r ->
            val total = r.sumOf { it.w.toDouble() }.toFloat()
            val unit = (w - padH * 2) / total
            var x = padH
            r.map { k ->
                val rc = RectF(x, padV + i * rh, x + k.w * unit, padV + (i + 1) * rh)
                x += k.w * unit
                rc
            }
        }
    }

    fun keyUnit(): Float = (width - dp(6f)) / 10f

    fun letterCenters(): Map<Char, PointF> {
        val m = HashMap<Char, PointF>()
        if (rects.size != rows.size) return m
        for (i in rows.indices) for (j in rows[i].indices) {
            val k = rows[i][j]
            if (k.type == KT.CHAR && k.label.length == 1 && k.label[0].isLetter()) {
                m[k.label[0].lowercaseChar()] = PointF(rects[i][j].centerX(), rects[i][j].centerY())
            }
        }
        return m
    }

    private fun hit(x: Float, y: Float): Triple<Key, RectF, Int>? {
        if (rows.isEmpty() || rects.size != rows.size) return null
        val rowIdx = rects.indices.firstOrNull { y < rects[it][0].bottom } ?: (rects.size - 1)
        val r = rects[rowIdx]
        val keys = rows[rowIdx]
        var best = -1
        for (j in keys.indices) {
            if (keys[j].type == KT.SPACER) continue
            if (x < r[j].right) { best = j; break }
        }
        if (best == -1) best = keys.indexOfLast { it.type != KT.SPACER }
        return Triple(keys[best], r[best], rowIdx)
    }

    // ---------------------------------------------------------------- drawing
    private fun display(k: Key): String =
        if (shift > 0 && k.label.length == 1 && k.label[0].isLetter()) k.label.uppercase() else k.label

    private fun drawText(c: Canvas, s: String, cx: Float, cy: Float, size: Float, color: Int, bold: Boolean = false) {
        tp.textSize = size
        tp.color = color
        tp.isFakeBoldText = bold
        val fm = tp.fontMetrics
        c.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2f, tp)
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(theme.bg)
        if (rects.size != rows.size) return
        val inset = dp(2.5f)
        val vin = dp(3.5f)
        for (i in rows.indices) for (j in rows[i].indices) {
            val k = rows[i][j]
            if (k.type == KT.SPACER) continue
            val cell = rects[i][j]
            val rr = RectF(cell.left + inset, cell.top + vin, cell.right - inset, cell.bottom - vin)
            val pressed = states.values.any { it.key === k && it.altChars == null && !it.swiping }
            fill.color = if (pressed) theme.keyPressed else theme.key
            c.drawRoundRect(rr, dp(9f), dp(9f), fill)
            drawContent(c, k, rr)
        }
        drawOverlays(c)
    }

    private fun drawContent(c: Canvas, k: Key, r: RectF) {
        val cx = r.centerX()
        val cy = r.centerY()
        val s = dp(10f)
        ip.color = theme.text
        ip.style = Paint.Style.STROKE
        when (k.type) {
            KT.CHAR -> {
                val big = k.label.length == 1 && (k.label[0].isLetter() || k.label[0].isDigit())
                drawText(c, display(k), cx, if (k.label == "," || k.label == ".") cy + dp(3f) else cy,
                    if (big) dp(21f) else dp(19f), theme.text)
            }
            KT.SPACE -> drawText(c, "English(India)", cx, cy, dp(14.5f), theme.text)
            KT.MODE -> drawText(c, k.label, cx, cy, dp(17f), theme.text)
            KT.SHIFT -> {
                val p = Path()
                p.moveTo(cx, cy - s * 1.0f); p.lineTo(cx + s * 1.0f, cy + s * 0.05f)
                p.lineTo(cx + s * 0.42f, cy + s * 0.05f); p.lineTo(cx + s * 0.42f, cy + s * 0.9f)
                p.lineTo(cx - s * 0.42f, cy + s * 0.9f); p.lineTo(cx - s * 0.42f, cy + s * 0.05f)
                p.lineTo(cx - s * 1.0f, cy + s * 0.05f); p.close()
                ip.style = if (shift > 0) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
                c.drawPath(p, ip)
                if (shift == 2) {
                    ip.style = Paint.Style.STROKE
                    c.drawLine(cx - s * 0.42f, cy + s * 1.25f, cx + s * 0.42f, cy + s * 1.25f, ip)
                }
            }
            KT.DEL -> {
                val p = Path()
                p.moveTo(cx - s * 1.2f, cy); p.lineTo(cx - s * 0.5f, cy - s * 0.8f)
                p.lineTo(cx + s * 1.2f, cy - s * 0.8f); p.lineTo(cx + s * 1.2f, cy + s * 0.8f)
                p.lineTo(cx - s * 0.5f, cy + s * 0.8f); p.close()
                c.drawPath(p, ip)
                val xx = cx + s * 0.3f
                c.drawLine(xx - s * 0.3f, cy - s * 0.3f, xx + s * 0.3f, cy + s * 0.3f, ip)
                c.drawLine(xx - s * 0.3f, cy + s * 0.3f, xx + s * 0.3f, cy - s * 0.3f, ip)
            }
            KT.ENTER -> when (enterLabel) {
                "icon" -> {
                    c.drawLine(cx + s * 0.9f, cy - s * 0.9f, cx + s * 0.9f, cy + s * 0.2f, ip)
                    c.drawLine(cx + s * 0.9f, cy + s * 0.2f, cx - s * 0.9f, cy + s * 0.2f, ip)
                    c.drawLine(cx - s * 0.9f, cy + s * 0.2f, cx - s * 0.4f, cy - s * 0.3f, ip)
                    c.drawLine(cx - s * 0.9f, cy + s * 0.2f, cx - s * 0.4f, cy + s * 0.7f, ip)
                }
                "search" -> {
                    ip.color = theme.accent
                    c.drawCircle(cx - s * 0.15f, cy - s * 0.15f, s * 0.7f, ip)
                    c.drawLine(cx + s * 0.35f, cy + s * 0.35f, cx + s * 0.95f, cy + s * 0.95f, ip)
                }
                else -> drawText(c, enterLabel, cx, cy, dp(16f), theme.accent, true)
            }
            KT.GLOBE -> {
                c.drawCircle(cx, cy, s * 0.95f, ip)
                c.drawOval(RectF(cx - s * 0.4f, cy - s * 0.95f, cx + s * 0.4f, cy + s * 0.95f), ip)
                c.drawLine(cx - s * 0.95f, cy, cx + s * 0.95f, cy, ip)
            }
            KT.SPACER -> {}
        }
    }

    private fun drawOverlays(c: Canvas) {
        val previewOn = prefs.bool("preview", true)
        for (ps in states.values) {
            // swipe trail
            if (ps.swiping && ps.path.size > 1) {
                val p = Path()
                p.moveTo(ps.path[0].x, ps.path[0].y)
                for (i in 1 until ps.path.size) p.lineTo(ps.path[i].x, ps.path[i].y)
                ip.style = Paint.Style.STROKE
                ip.color = theme.accent
                ip.alpha = 170
                ip.strokeWidth = dp(7f)
                c.drawPath(p, ip)
                ip.alpha = 255
                ip.strokeWidth = 1.9f * d
            }
            // key preview bubble
            val k = ps.key
            val rc = ps.rect
            if (previewOn && ps.altChars == null && !ps.swiping && !ps.cursorMode &&
                k != null && rc != null && k.type == KT.CHAR && ps.row > 0
            ) {
                val bw = rc.width() * 1.3f
                val bh = rc.height() * 1.15f
                val left = (rc.centerX() - bw / 2).coerceIn(0f, width - bw)
                val top = (rc.top - bh + dp(10f)).coerceAtLeast(0f)
                fill.color = theme.popup
                c.drawRoundRect(RectF(left, top, left + bw, top + bh), dp(12f), dp(12f), fill)
                drawText(c, display(k), left + bw / 2, top + bh / 2, dp(30f), theme.text)
            }
            // long-press alternates popup
            val alts = ps.altChars
            if (alts != null) {
                fill.color = theme.popup
                c.drawRoundRect(ps.altRect, dp(12f), dp(12f), fill)
                val iw = ps.altRect.width() / alts.length
                for (i in alts.indices) {
                    val ir = RectF(ps.altRect.left + i * iw, ps.altRect.top, ps.altRect.left + (i + 1) * iw, ps.altRect.bottom)
                    if (i == ps.altSel) {
                        fill.color = theme.accent
                        c.drawRoundRect(RectF(ir.left + dp(2f), ir.top + dp(4f), ir.right - dp(2f), ir.bottom - dp(4f)), dp(9f), dp(9f), fill)
                    }
                    drawText(c, alts[i].toString(), ir.centerX(), ir.centerY(), dp(24f),
                        if (i == ps.altSel) 0xFFFFFFFF.toInt() else theme.text)
                }
            }
        }
    }

    // ---------------------------------------------------------------- touch
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                down(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) move(e.getPointerId(i), e.getX(i), e.getY(i))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = e.actionIndex
                up(e.getPointerId(i), e.getX(i), e.getY(i), false)
            }
            MotionEvent.ACTION_CANCEL -> states.keys.toList().forEach { up(it, 0f, 0f, true) }
        }
        invalidate()
        return true
    }

    private fun down(id: Int, x: Float, y: Float) {
        val hit = hit(x, y)
        val ps = PS(id, hit?.first, hit?.second, hit?.third ?: -1, x, y)
        ps.path.add(PointF(x, y))
        states[id] = ps
        val k = hit?.first ?: return
        cb.onFeedback()
        when (k.type) {
            KT.DEL -> {
                cb.onDelete()
                ps.consumed = true
                startRepeat(ps)
            }
            KT.SHIFT, KT.MODE, KT.ENTER, KT.SPACE, KT.SPACER -> {}
            else -> if (k.alts.isNotEmpty() || k.type == KT.GLOBE) {
                val r = Runnable { onLong(ps) }
                ps.longRun = r
                h.postDelayed(r, 380)
            }
        }
    }

    private fun startRepeat(ps: PS) {
        val r = object : Runnable {
            override fun run() {
                if (states[ps.id] !== ps) return
                cb.onDelete()
                ps.repCount++
                h.postDelayed(this, if (ps.repCount > 12) 30L else 55L)
            }
        }
        ps.repRun = r
        h.postDelayed(r, 380)
    }

    private fun cancelLong(ps: PS) {
        ps.longRun?.let { h.removeCallbacks(it) }
        ps.longRun = null
    }

    private fun onLong(ps: PS) {
        if (states[ps.id] !== ps) return
        ps.longRun = null
        val k = ps.key ?: return
        if (k.type == KT.GLOBE) { ps.consumed = true; cb.onGlobe(true); return }
        if (k.alts.isEmpty()) return
        val alts = if (shift > 0) k.alts.map { it.uppercaseChar() }.joinToString("") else k.alts
        val rc = ps.rect ?: return
        ps.altChars = alts
        val iw = dp(40f)
        val total = iw * alts.length
        val left = (rc.centerX() - total / 2).coerceIn(dp(2f), (width - total - dp(2f)).coerceAtLeast(dp(2f)))
        val ih = dp(52f)
        val top = (rc.top - ih - dp(2f)).coerceAtLeast(0f)
        ps.altRect = RectF(left, top, left + total, top + ih)
        updateAltSel(ps, ps.lastX)
        cb.onFeedback()
        invalidate()
    }

    private fun updateAltSel(ps: PS, x: Float) {
        val alts = ps.altChars ?: return
        val iw = ps.altRect.width() / alts.length
        ps.altSel = ((x - ps.altRect.left) / iw).toInt().coerceIn(0, alts.length - 1)
    }

    private fun move(id: Int, x: Float, y: Float) {
        val ps = states[id] ?: return
        ps.lastX = if (ps.cursorMode) ps.lastX else x
        if (ps.altChars != null) { updateAltSel(ps, x); return }
        val k = ps.startKey ?: return
        ps.path.add(PointF(x, y))
        if (ps.cursorMode || (k.type == KT.SPACE && abs(x - ps.sx) > dp(16f))) {
            if (!ps.cursorMode) ps.lastX = ps.sx
            ps.cursorMode = true
            cancelLong(ps)
            val step = dp(14f)
            val n = ((x - ps.lastX) / step).toInt()
            if (n != 0) { cb.onCursor(n); ps.lastX += n * step }
            return
        }
        if (ps.swiping) return
        if (prefs.bool("swipe", true) && page == Layouts.LETTERS && states.size == 1 &&
            k.type == KT.CHAR && k.label.length == 1 && k.label[0].isLetter()
        ) {
            if (hypot(x - ps.sx, y - ps.sy) > dp(46f)) {
                ps.swiping = true
                cancelLong(ps)
                return
            }
        }
        val hit = hit(x, y)
        if (hit?.first !== ps.key) {
            ps.key = hit?.first
            ps.rect = hit?.second
            ps.row = hit?.third ?: -1
            cancelLong(ps)
        }
    }

    private fun up(id: Int, x: Float, y: Float, cancel: Boolean) {
        val ps = states.remove(id) ?: return
        cancelLong(ps)
        ps.repRun?.let { h.removeCallbacks(it) }
        if (cancel) return
        val alts = ps.altChars
        if (alts != null) {
            updateAltSel(ps, x)
            cb.onKey(alts[ps.altSel].toString())
            if (shift == 1) shift = 0
            return
        }
        if (ps.swiping) { if (ps.path.size > 3) cb.onSwipe(ps.path); return }
        if (ps.cursorMode || ps.consumed) return
        val k = hit(x, y)?.first ?: ps.key ?: return
        fire(k)
    }

    private fun fire(k: Key) {
        when (k.type) {
            KT.CHAR -> { cb.onKey(display(k)); if (shift == 1) shift = 0 }
            KT.SPACE -> cb.onKey(" ")
            KT.ENTER -> cb.onEnter()
            KT.GLOBE -> cb.onGlobe(false)
            KT.SHIFT -> {
                val now = System.currentTimeMillis()
                shift = if (now - lastShiftTap < 300 && shift != 2) 2 else if (shift == 0) 1 else 0
                lastShiftTap = now
            }
            KT.MODE -> page = k.target
            else -> {}
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        h.removeCallbacksAndMessages(null)
        states.clear()
    }
}
