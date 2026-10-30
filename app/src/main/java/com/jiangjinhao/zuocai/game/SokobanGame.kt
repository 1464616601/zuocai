package com.jiangjinhao.zuocai.game

import android.graphics.Canvas

/**
 * 推箱子。三关，A 撤销，B 重来本关。
 *
 * 地图字符：`#` 墙、`$` 箱子、`.` 目标点、`*` 箱子已在目标点、`@` 玩家、空格 地板
 */
class SokobanGame : EventGame() {
    override val title = "推箱子"
    override val hint = "把木箱推到圆点上 · A 撤销 · B 重来"

    override var score = 0
    override var over = false
    override var label = ""

    private val levels = arrayOf(
        arrayOf(
            "########",
            "#  .   #",
            "#  \$   #",
            "#  @   #",
            "#      #",
            "########"
        ),
        arrayOf(
            "#########",
            "#   #   #",
            "# \$ # . #",
            "#  @    #",
            "# \$   . #",
            "#   #   #",
            "#########"
        ),
        arrayOf(
            "##########",
            "#  ..    #",
            "#  \$\$    #",
            "#   @    #",
            "#        #",
            "#   ##   #",
            "##########"
        )
    )

    private var cell = 30f
    private var map: Array<CharArray> = arrayOf()
    private var px = 0
    private var py = 0
    private var level = 0

    private class Snap(val m: Array<CharArray>, val x: Int, val y: Int)

    private val history = ArrayList<Snap>()

    init {
        reset()
    }

    override fun reset() {
        score = 0
        load(0)
    }

    private fun load(idx: Int) {
        level = idx
        val src = levels[idx]
        map = Array(src.size) { r -> src[r].toCharArray() }
        history.clear()
        outer@ for (y in map.indices) {
            for (x in map[y].indices) {
                if (map[y][x] == '@' || map[y][x] == '+') {
                    px = x
                    py = y
                    map[y][x] = if (map[y][x] == '+') '*' else ' '
                    break@outer
                }
            }
        }
        over = false
        label = "第 ${idx + 1} 关 · 共 ${levels.size} 关"
    }

    private fun snapshot() {
        history.add(Snap(Array(map.size) { map[it].copyOf() }, px, py))
        if (history.size > 60) history.removeAt(0)
    }

    private fun isDone(): Boolean = map.none { row -> row.any { it == '$' } }

    override fun resize(w: Int, h: Int) {
        val rows = if (map.isEmpty()) 6 else map.size
        val cols = if (map.isEmpty() || map[0].isEmpty()) 8 else map[0].size
        cell = minOf((w - 16f) / cols, (h - 16f) / rows)
    }

    override fun input(action: String) {
        when (action) {
            Game.UP -> walk(0, -1)
            Game.DOWN -> walk(0, 1)
            Game.LEFT -> walk(-1, 0)
            Game.RIGHT -> walk(1, 0)
            Game.B -> load(level)
            Game.A -> {
                if (history.isNotEmpty()) {
                    val s = history.removeAt(history.size - 1)
                    map = s.m
                    px = s.x
                    py = s.y
                }
            }
        }
    }

    private fun walk(dx: Int, dy: Int) {
        if (over) return
        val nx = px + dx
        val ny = py + dy
        if (ny < 0 || ny >= map.size || nx < 0 || nx >= map[ny].size) return
        val target = map[ny][nx]
        if (target == '#') return

        if (target == '$') {
            val bx = nx + dx
            val by = ny + dy
            if (by < 0 || by >= map.size || bx < 0 || bx >= map[by].size) return
            val beyond = map[by][bx]
            if (beyond == '#' || beyond == '$' || beyond == '*') return
            snapshot()
            map[by][bx] = if (beyond == '.') '*' else '$'
            map[ny][nx] = ' '
            px = nx
            py = ny
            emit(Game.EV_PUSH)
        } else if (target == '*') {
            return
        } else {
            snapshot()
            px = nx
            py = ny
        }

        if (isDone()) {
            score += 100
            if (level + 1 < levels.size) {
                load(level + 1)
                // 通关后重载会重置 label，这里补回分数累积
                label = "第 ${level + 1} 关 · 共 ${levels.size} 关"
                emit(Game.EV_WIN)
            } else {
                over = true
                label = "全部通关"
                emit(Game.EV_WIN)
                emit(Game.EV_OVER)
            }
        }
    }

    override fun update(dtMs: Long) = Unit

    override fun draw(canvas: Canvas) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        canvas.drawColor(GameStyle.BG)
        if (map.isEmpty()) return

        val rows = map.size
        val cols = map[0].size
        val ox = ((w - cols * cell) / 2f).toInt().toFloat()
        val oy = ((h - rows * cell) / 2f).toInt().toFloat()

        map.forEachIndexed { y, row ->
            row.forEachIndexed { x, c ->
                val gx = ox + x * cell
                val gy = oy + y * cell

                if (c == '#') {
                    GameStyle.rr(canvas, gx + 1, gy + 1, cell - 2, cell - 2, 5f, 0xFF6B5C4A.toInt())
                    GameStyle.rr(canvas, gx + cell * 0.2f, gy + cell * 0.2f, cell * 0.6f, cell * 0.6f, 3f, 0x33FFFFFF)
                    return@forEachIndexed
                }
                GameStyle.rr(canvas, gx + 1, gy + 1, cell - 2, cell - 2, 4f, GameStyle.DARK_PANEL)

                if (c == '.' || c == '*') {
                    GameStyle.circle(canvas, gx + cell / 2f, gy + cell / 2f, cell * 0.18f, GameStyle.WARN, 180)
                }
                if (c == '$' || c == '*') {
                    val color = if (c == '*') GameStyle.GOOD else 0xFFC99A5B.toInt()
                    GameStyle.rr(
                        canvas, gx + cell * 0.15f, gy + cell * 0.15f,
                        cell * 0.7f, cell * 0.7f, 6f, color
                    )
                    GameStyle.line(
                        canvas, gx + cell * 0.2f, gy + cell * 0.2f,
                        gx + cell * 0.8f, gy + cell * 0.8f, 0x80FFFFFF.toInt(), 1.5f
                    )
                }
            }
        }

        // 小人
        val cx = ox + px * cell + cell / 2f
        val cy = oy + py * cell + cell / 2f
        GameStyle.circle(canvas, cx, cy - cell * 0.2f, cell * 0.17f, GameStyle.LIGHT)
        GameStyle.rr(
            canvas, cx - cell * 0.21f, cy - cell * 0.02f,
            cell * 0.42f, cell * 0.26f, cell * 0.1f, GameStyle.LIGHT
        )
        GameStyle.rr(canvas, cx - cell * 0.15f, cy + cell * 0.2f, cell * 0.11f, cell * 0.14f, 2f, GameStyle.BRAND)
        GameStyle.rr(canvas, cx + cell * 0.04f, cy + cell * 0.2f, cell * 0.11f, cell * 0.14f, 2f, GameStyle.BRAND)
    }
}
