package com.vinayak.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.CornerPathEffect
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

    private var pg: Page = Layouts.build(Layouts.LETTERS, true)
    private var rects: List<RectF> = emptyList()
    private var cells: List<RectF> = emptyList()
    private var sx = 1f
    private var sy = 1f
    private val d = resources.displayMetrics.density
    private val h = Handler(Looper.getMainLooper())
    private var lastShiftTap = 0L

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Fonts.get(ctx)
    }
    private val ip = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    private fun dp(v: Float) = v * d

    private inner class PS(val id: Int, val startIdx: Int, val sx0: Float, val sy0: Float) {
        var idx = startIdx
        val path = ArrayList<PointF>()
        var altChars: String? = null
        var altRect = RectF()
        var altSel = 0
        var swiping = false
        var cursorMode = false
        var lastX = sx0
        var consumed = false
        var longRun: Runnable? = null
        var repRun: Runnable? = null
        var repCount = 0
    }

    private val states = HashMap<Int, PS>()

    init { relayout() }

    // ---------------------------------------------------------------- layout
    private fun vScale() = prefs.int("keyscale", 100) / 100f

    override fun onMeasure(w: Int, hh: Int) {
        var width = MeasureSpec.getSize(w)
        if (width == 0) width = resources.displayMetrics.widthPixels
        setMeasuredDimension(width, (Layouts.REF_H * (width / 1080f) * vScale()).toInt())
    }

    override fun onSizeChanged(w: Int, hh: Int, ow: Int, oh: Int) { relayout() }

    private fun relayout() {
        pg = Layouts.build(page, prefs.bool("numrow", true))
        if (width == 0 || height == 0) { rects = emptyList(); cells = emptyList(); return }
        sx = width / 1080f
        sy = sx * vScale()
        val keys = pg.keys
        rects = keys.map { k ->
            RectF(k.l * sx, pg.tops[k.row] * sy, k.r * sx, (pg.tops[k.row] + pg.heights[k.row]) * sy)
        }
        val rowsN = pg.tops.size
        cells = keys.mapIndexed { i, k ->
            val r = rects[i]
            val prev = if (i > 0 && keys[i - 1].row == k.row) rects[i - 1] else null
            val next = if (i < keys.size - 1 && keys[i + 1].row == k.row) rects[i + 1] else null
            val left = if (prev == null) 0f else (prev.right + r.left) / 2f
            val right = if (next == null) width.toFloat() else (r.right + next.left) / 2f
            val top = if (k.row == 0) 0f else (pg.tops[k.row - 1] + pg.heights[k.row - 1] + pg.tops[k.row]) / 2f * sy
            val bottom = if (k.row == rowsN - 1) height.toFloat()
            else (pg.tops[k.row] + pg.heights[k.row] + pg.tops[k.row + 1]) / 2f * sy
            RectF(left, top, right, bottom)
        }
    }

    fun keyUnit(): Float = Layouts.PITCH * sx

    fun letterCenters(): Map<Char, PointF> {
        val m = HashMap<Char, PointF>()
        val keys = pg.keys
        if (rects.size != keys.size) return m
        for (i in keys.indices) {
            val k = keys[i]
            if (k.type == KT.CHAR && k.label.length == 1 && k.label[0].isLetter()) {
                m[k.label[0].lowercaseChar()] = PointF(rects[i].centerX(), rects[i].centerY())
            }
        }
        return m
    }

    private fun hitIdx(x: Float, y: Float): Int {
        if (cells.isEmpty()) return -1
        val cx = x.coerceIn(0f, width - 0.01f)
        val cy = y.coerceIn(0f, height - 0.01f)
        for (i in cells.indices) if (cells[i].contains(cx, cy)) return i
        return -1
    }

    // ---------------------------------------------------------------- drawing
    private fun display(k: Key): String =
        if (shift > 0 && k.label.length == 1 && k.label[0].isLetter()) k.label.uppercase() else k.label

    private fun text(c: Canvas, s: String, cx: Float, baseline: Float, size: Float, color: Int) {
        tp.textSize = size * (prefs.int("fontscale", 100) / 100f)
        tp.color = color
        c.drawText(s, cx, baseline, tp)
    }

    override fun onDraw(c: Canvas) {
        val keys = pg.keys
        if (rects.size != keys.size) return
        val radius = prefs.int("keyradius", 20) * sx
        val border = prefs.bool("keyborder", false)
        val last = pg.tops.size - 1
        for (i in keys.indices) {
            val k = keys[i]
            val rr = rects[i]
            val pressed = states.values.any { it.idx == i && it.altChars == null && !it.swiping }
            fill.color = if (pressed) theme.keyPressed else theme.keyFill
            c.drawRoundRect(rr, radius, radius, fill)
            if (border) {
                ip.style = Paint.Style.STROKE
                ip.strokeWidth = 2f * sx
                ip.color = theme.dim
                ip.alpha = 120
                ip.pathEffect = null
                c.drawRoundRect(rr, radius, radius, ip)
                ip.alpha = 255
            }
            drawContent(c, k, rr, k.row == last)
        }
        drawOverlays(c)
    }

    private fun drawContent(c: Canvas, k: Key, r: RectF, bottomRow: Boolean) {
        val cx = r.centerX()
        val cy = r.centerY()
        val u = sx
        ip.strokeWidth = 4.4f * u
        ip.color = theme.text
        ip.style = Paint.Style.STROKE
        ip.pathEffect = null
        when (k.type) {
            KT.CHAR -> {
                if (bottomRow && k.label == ".") {
                    fill.color = theme.text
                    c.drawCircle(cx, cy + 26f * u, 4.4f * u, fill)
                } else if (bottomRow && k.label == ",") {
                    val gx = cx - 3.5f * u
                    val gy = cy + 31f * u
                    val p = Path()
                    p.moveTo(gx + 0.5f * u, gy - 8.5f * u); p.lineTo(gx + 5.5f * u, gy - 8.5f * u)
                    p.lineTo(gx + 1f * u, gy + 8.5f * u); p.lineTo(gx - 4f * u, gy + 8.5f * u); p.close()
                    fill.color = theme.text
                    c.drawPath(p, fill)
                } else {
                    val letter = k.label.isNotEmpty() && k.label[0].isLetter()
                    text(c, display(k), cx, cy + 20.5f * u, (if (letter) 56.5f else 54f) * u, theme.text)
                }
            }
            KT.SPACE -> text(c, "English(India)", cx, cy + 13f * u, 33f * u, theme.text)
            KT.MODE -> text(c, k.label, cx, cy + 18.5f * u, 50f * u, theme.text)
            KT.SHIFT -> {
                val p = Path()
                p.moveTo(cx, cy - 24f * u); p.lineTo(cx + 23f * u, cy)
                p.lineTo(cx + 11f * u, cy); p.lineTo(cx + 11f * u, cy + 23f * u)
                p.lineTo(cx - 11f * u, cy + 23f * u); p.lineTo(cx - 11f * u, cy)
                p.lineTo(cx - 23f * u, cy); p.close()
                ip.pathEffect = CornerPathEffect(6f * u)
                ip.style = if (shift > 0) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
                c.drawPath(p, ip)
                ip.style = Paint.Style.STROKE
                ip.pathEffect = null
                if (shift == 2) c.drawLine(cx - 11f * u, cy + 33f * u, cx + 11f * u, cy + 33f * u, ip)
            }
            KT.DEL -> {
                val p = Path()
                p.moveTo(cx - 27f * u, cy); p.lineTo(cx - 12f * u, cy - 19.5f * u)
                p.lineTo(cx + 27f * u, cy - 19.5f * u); p.lineTo(cx + 27f * u, cy + 19.5f * u)
                p.lineTo(cx - 12f * u, cy + 19.5f * u); p.close()
                ip.pathEffect = CornerPathEffect(7f * u)
                c.drawPath(p, ip)
                ip.pathEffect = null
                c.drawLine(cx + 0f * u, cy - 7.5f * u, cx + 14f * u, cy + 7.5f * u, ip)
                c.drawLine(cx + 0f * u, cy + 7.5f * u, cx + 14f * u, cy - 7.5f * u, ip)
            }
            KT.ENTER -> when (enterLabel) {
                "icon" -> {
                    val p = Path()
                    p.moveTo(cx + 22f * u, cy - 22f * u); p.lineTo(cx + 22f * u, cy + 8f * u)
                    p.lineTo(cx - 22f * u, cy + 8f * u)
                    ip.pathEffect = CornerPathEffect(5f * u)
                    c.drawPath(p, ip)
                    ip.pathEffect = null
                    c.drawLine(cx - 6f * u, cy - 8f * u, cx - 22f * u, cy + 8f * u, ip)
                    c.drawLine(cx - 6f * u, cy + 24f * u, cx - 22f * u, cy + 8f * u, ip)
                }
                "search" -> {
                    ip.color = theme.accent
                    c.drawCircle(cx - 6f * u, cy - 6f * u, 17.5f * u, ip)
                    ip.strokeWidth = 3.6f * u
                    c.drawLine(cx + 6.5f * u, cy + 6.5f * u, cx + 23f * u, cy + 23f * u, ip)
                }
                else -> text(c, enterLabel, cx, cy + 16f * u, 46f * u, theme.accent)
            }
            KT.GLOBE -> {
                c.drawCircle(cx, cy, 23f * u, ip)
                c.drawOval(RectF(cx - 11f * u, cy - 23f * u, cx + 11f * u, cy + 23f * u), ip)
                c.drawLine(cx, cy - 23f * u, cx, cy + 23f * u, ip)
                c.drawLine(cx - 23f * u, cy, cx + 23f * u, cy, ip)
            }
        }
    }

    private fun drawOverlays(c: Canvas) {
        val previewOn = prefs.bool("preview", true)
        val keys = pg.keys
        for (ps in states.values) {
            if (ps.swiping && ps.path.size > 1) {
                val p = Path()
                p.moveTo(ps.path[0].x, ps.path[0].y)
                for (i in 1 until ps.path.size) p.lineTo(ps.path[i].x, ps.path[i].y)
                ip.style = Paint.Style.STROKE
                ip.color = theme.accent
                ip.alpha = 170
                ip.strokeWidth = 12f * sx
                ip.pathEffect = null
                c.drawPath(p, ip)
                ip.alpha = 255
            }
            val k = keys.getOrNull(ps.idx)
            val rc = rects.getOrNull(ps.idx)
            if (previewOn && ps.altChars == null && !ps.swiping && !ps.cursorMode &&
                k != null && rc != null && k.type == KT.CHAR && rc.top > rc.height() * 0.9f
            ) {
                val bw = rc.width() * 1.25f
                val bh = rc.height() * 1.05f
                val left = (rc.centerX() - bw / 2).coerceIn(0f, width - bw)
                val top = rc.top - bh + 14f * sx
                fill.color = 0x33000000
                c.drawRoundRect(RectF(left, top + 4f * sx, left + bw, top + bh + 4f * sx), 24f * sx, 24f * sx, fill)
                fill.color = theme.popup
                c.drawRoundRect(RectF(left, top, left + bw, top + bh), 24f * sx, 24f * sx, fill)
                text(c, display(k), left + bw / 2, top + bh / 2 + 25f * sx, 76f * sx, theme.text)
            }
            val alts = ps.altChars
            if (alts != null) {
                fill.color = 0x33000000
                val sh = RectF(ps.altRect); sh.offset(0f, 4f * sx)
                c.drawRoundRect(sh, 24f * sx, 24f * sx, fill)
                fill.color = theme.popup
                c.drawRoundRect(ps.altRect, 24f * sx, 24f * sx, fill)
                val iw = ps.altRect.width() / alts.length
                for (i in alts.indices) {
                    val ir = RectF(ps.altRect.left + i * iw, ps.altRect.top, ps.altRect.left + (i + 1) * iw, ps.altRect.bottom)
                    if (i == ps.altSel) {
                        fill.color = theme.accent
                        c.drawRoundRect(RectF(ir.left + 5f * sx, ir.top + 8f * sx, ir.right - 5f * sx, ir.bottom - 8f * sx), 18f * sx, 18f * sx, fill)
                    }
                    text(c, alts[i].toString(), ir.centerX(), ir.centerY() + 20f * sx, 56f * sx,
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
        val idx = hitIdx(x, y)
        val ps = PS(id, idx, x, y)
        ps.path.add(PointF(x, y))
        states[id] = ps
        val k = pg.keys.getOrNull(idx) ?: return
        cb.onFeedback()
        when (k.type) {
            KT.DEL -> {
                cb.onDelete()
                ps.consumed = true
                startRepeat(ps)
            }
            KT.SHIFT, KT.MODE, KT.ENTER, KT.SPACE -> {}
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
        val k = pg.keys.getOrNull(ps.idx) ?: return
        if (k.type == KT.GLOBE) { ps.consumed = true; cb.onGlobe(true); return }
        if (k.alts.isEmpty()) return
        val alts = if (shift > 0) k.alts.map { it.uppercaseChar() }.joinToString("") else k.alts
        val rc = rects.getOrNull(ps.idx) ?: return
        ps.altChars = alts
        val iw = 90f * sx
        val total = iw * alts.length
        val left = (rc.centerX() - total / 2).coerceIn(4f * sx, (width - total - 4f * sx).coerceAtLeast(4f * sx))
        val ih = 120f * sx
        val top = (rc.top - ih - 6f * sx).coerceAtLeast(0f)
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
        if (!ps.cursorMode) ps.lastX = x
        if (ps.altChars != null) { updateAltSel(ps, x); return }
        val k = pg.keys.getOrNull(ps.startIdx) ?: return
        ps.path.add(PointF(x, y))
        if (ps.cursorMode || (k.type == KT.SPACE && abs(x - ps.sx0) > dp(16f))) {
            if (!ps.cursorMode) ps.lastX = ps.sx0
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
            if (hypot(x - ps.sx0, y - ps.sy0) > dp(46f)) {
                ps.swiping = true
                cancelLong(ps)
                return
            }
        }
        val idx = hitIdx(x, y)
        if (idx != ps.idx) {
            ps.idx = idx
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
        val idx = hitIdx(x, y).let { if (it >= 0) it else ps.idx }
        val k = pg.keys.getOrNull(idx) ?: return
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
