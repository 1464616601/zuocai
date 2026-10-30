package com.jiangjinhao.zuocai.game

import android.graphics.Canvas

/**
 * 俄罗斯方块。10 列 18 行，带落点投影。
 */
class TetrisGame : EventGame() {
    override val title = "俄罗斯方块"
    override val hint = "▲旋转 · ▼加速 · B 直接落底"

    override var score = 0
    override var over = false
    override var label = ""

    private val cols = 10
    private val rows = 18
    private var cell = 18f

    private val shapes = arrayOf(
        arrayOf(intArrayOf(1, 1, 1, 1)),
        arrayOf(intArrayOf(1, 1), intArrayOf(1, 1)),
        arrayOf(intArrayOf(0, 1, 0), intArrayOf(1, 1, 1)),
        arrayOf(intArrayOf(1, 0, 0), intArrayOf(1, 1, 1)),
        arrayOf(intArrayOf(0, 0, 1), intArrayOf(1, 1, 1)),
        arrayOf(intArrayOf(0, 1, 1), intArrayOf(1, 1, 0)),
        arrayOf(intArrayOf(1, 1, 0), intArrayOf(0, 1, 1))
    )
    private val colors = intArrayOf(
        GameStyle.BRAND, GameStyle.WARN, 0xFF8B5CF6.toInt(), 0xFF3B82F6.toInt(),
        GameStyle.BRAND2, GameStyle.GOOD, 0xFFE9536F.toInt()
    )

    private var board = Array(rows) { arrayOfNulls<Int>(cols) }
    private var cur: Piece? = null
    private var nextIdx = 0
    private var dropAcc = 0f
    private var dropMs = 620f
    private var lines = 0

    private class Piece(
        var cells: Array<IntArray>,
        val color: Int,
        var x: Int,
        var y: Int
    )

    init {
        reset()
    }

    override fun reset() {
        board = Array(rows) { arrayOfNulls<Int>(cols) }
        nextIdx = G.rnd(shapes.size)
        cur = newPiece()
        dropAcc = 0f
        dropMs = 620f
        lines = 0
        score = 0
        over = false
        label = ""
    }

    private fun newPiece(): Piece {
        val idx = nextIdx
        nextIdx = G.rnd(shapes.size)
        val shape = Array(shapes[idx].size) { r -> shapes[idx][r].copyOf() }
        return Piece(shape, colors[idx], (cols - shape[0].size) / 2, 0)
    }

    override fun resize(w: Int, h: Int) {
        cell = minOf(w.toFloat() / cols, (h - 10f) / rows)
    }

    private fun collide(cells: Array<IntArray>, px: Int, py: Int): Boolean {
        for (y in cells.indices) {
            for (x in cells[y].indices) {
                if (cells[y][x] == 0) continue
                val bx = px + x
                val by = py + y
                if (bx < 0 || bx >= cols || by >= rows) return true
                if (by >= 0 && board[by][bx] != null) return true
            }
        }
        return false
    }

    private fun rotate(cells: Array<IntArray>): Array<IntArray> {
        val h = cells.size
        val w = cells[0].size
        val out = Array(w) { IntArray(h) }
        for (y in 0 until h) {
            for (x in 0 until w) {
                out[x][h - 1 - y] = cells[y][x]
            }
        }
        return out
    }

    private fun lock() {
        val p = cur ?: return
        p.cells.forEachIndexed { y, row ->
            row.forEachIndexed { x, v ->
                if (v != 0 && p.y + y >= 0) board[p.y + y][p.x + x] = p.color
            }
        }

        var cleared = 0
        var y = rows - 1
        while (y >= 0) {
            if (board[y].all { it != null }) {
                for (k in y downTo 1) board[k] = board[k - 1].copyOf()
                board[0] = arrayOfNulls(cols)
                cleared++
                y++
            }
            y--
        }
        if (cleared > 0) {
            lines += cleared
            score += when (cleared) {
                1 -> 100
                2 -> 300
                3 -> 500
                else -> 800
            }
            dropMs = maxOf(150f, 620f - lines * 22f)
            emit(Game.EV_LINE)
        }

        cur = newPiece()
        val np = cur!!
        if (collide(np.cells, np.x, np.y)) {
            over = true
            emit(Game.EV_OVER)
        }
    }

    override fun input(action: String) {
        if (over) return
        val p = cur ?: return
        when (action) {
            Game.LEFT -> if (!collide(p.cells, p.x - 1, p.y)) p.x -= 1
            Game.RIGHT -> if (!collide(p.cells, p.x + 1, p.y)) p.x += 1
            Game.UP, Game.A -> {
                val r = rotate(p.cells)
                for (dx in intArrayOf(0, -1, 1, -2, 2)) {
                    if (!collide(r, p.x + dx, p.y)) {
                        p.cells = r
                        p.x += dx
                        break
                    }
                }
            }
            Game.DOWN -> {
                if (!collide(p.cells, p.x, p.y + 1)) {
                    p.y += 1
                    score += 1
                } else {
                    lock()
                }
            }
            Game.B -> {
                while (!collide(p.cells, p.x, p.y + 1)) {
                    p.y += 1
                    score += 2
                }
                emit(Game.EV_DROP)
                lock()
            }
        }
    }

    override fun update(dtMs: Long) {
        if (over) return
        val p = cur ?: return
        dropAcc += dtMs
        while (dropAcc >= dropMs) {
            dropAcc -= dropMs
            if (!collide(p.cells, p.x, p.y + 1)) p.y += 1 else lock()
            if (over) return
        }
    }

    override fun draw(canvas: Canvas) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        canvas.drawColor(GameStyle.DARK_BG)

        val ox = ((w - cols * cell) / 2f).toInt().toFloat()
        val oy = ((h - rows * cell) / 2f).toInt().toFloat()

        // 棋盘底 + 发亮描边
        GameStyle.rr(canvas, ox - 5f, oy - 5f, cols * cell + 10f, rows * cell + 10f, 12f, GameStyle.DARK_PANEL)
        val frame = android.graphics.RectF(ox, oy, ox + cols * cell, oy + rows * cell)
        canvas.drawRect(frame, GameStyle.paint(GameStyle.DARK_GRID, strokeWidth = 2f))
        canvas.drawRect(frame, GameStyle.paint(GameStyle.LIGHT, 70, strokeWidth = 1.5f))

        val drawCell = { cx: Int, cy: Int, color: Int, alpha: Int ->
            GameStyle.rr(
                canvas, ox + cx * cell + 1.5f, oy + cy * cell + 1.5f,
                cell - 3f, cell - 3f, 4f, color, alpha
            )
        }

        board.forEachIndexed { y, row ->
            row.forEachIndexed { x, c -> if (c != null) drawCell(x, y, c, 255) }
        }

        val p = cur ?: return
        // 落点投影
        var ghostY = p.y
        while (!collide(p.cells, p.x, ghostY + 1)) ghostY++
        p.cells.forEachIndexed { y, row ->
            row.forEachIndexed { x, v ->
                if (v != 0) drawCell(p.x + x, ghostY + y, p.color, 42)
            }
        }
        // 当前方块
        p.cells.forEachIndexed { y, row ->
            row.forEachIndexed { x, v ->
                if (v != 0 && p.y + y >= 0) drawCell(p.x + x, p.y + y, p.color, 255)
            }
        }
    }
}
