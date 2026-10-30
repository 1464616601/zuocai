package com.jiangjinhao.zuocai.game

import android.graphics.Canvas
import android.graphics.Paint

/**
 * 贪吃蛇。按方向键才开始移动——否则一进游戏就自己撞墙。
 */
class SnakeGame : EventGame() {
    override val title = "贪吃蛇"
    override val hint = "方向键转向，吃到红点得分"

    override var score = 0
    override var over = false
    override var label = ""

    private val cols = 15
    private val rows = 22
    private var cell = 16f

    private class Node(val x: Int, val y: Int)

    private var snake = ArrayList<Node>()
    private var dirX = 0
    private var dirY = -1
    private var nextX = 0
    private var nextY = -1
    private var foodX = 0
    private var foodY = 0
    private var acc = 0f
    private var stepMs = 150f
    private var started = false

    init {
        reset()
    }

    override fun reset() {
        snake.clear()
        snake.add(Node(7, 11))
        snake.add(Node(7, 12))
        snake.add(Node(7, 13))
        dirX = 0; dirY = -1
        nextX = 0; nextY = -1
        acc = 0f
        stepMs = 150f
        started = false
        score = 0
        over = false
        label = ""
        placeFood()
    }

    private fun placeFood() {
        var tries = 0
        do {
            foodX = G.rnd(cols)
            foodY = G.rnd(rows)
            tries++
        } while (tries < 200 && snake.any { it.x == foodX && it.y == foodY })
    }

    override fun resize(w: Int, h: Int) {
        cell = minOf(w.toFloat() / cols, (h - 8f) / rows)
    }

    override fun input(action: String) {
        val dx = when (action) {
            Game.LEFT -> -1
            Game.RIGHT -> 1
            else -> 0
        }
        val dy = when (action) {
            Game.UP -> -1
            Game.DOWN -> 1
            else -> 0
        }
        if (dx == 0 && dy == 0) return
        // 不能直接反向
        if (dx == -dirX && dy == -dirY) return
        nextX = dx
        nextY = dy
        started = true
    }

    override fun update(dtMs: Long) {
        if (over) return
        if (!started) {
            label = "按方向键开始"
            return
        }
        label = ""
        acc += dtMs
        while (acc >= stepMs) {
            acc -= stepMs
            step()
            if (over) return
        }
    }

    private fun step() {
        dirX = nextX
        dirY = nextY
        val head = Node(snake[0].x + dirX, snake[0].y + dirY)

        if (head.x < 0 || head.y < 0 || head.x >= cols || head.y >= rows) {
            over = true
            emit(Game.EV_OVER)
            return
        }
        if (snake.any { it.x == head.x && it.y == head.y }) {
            over = true
            emit(Game.EV_OVER)
            return
        }

        snake.add(0, head)
        if (head.x == foodX && head.y == foodY) {
            score += 10
            placeFood()
            if (stepMs > 70f) stepMs -= 3f
            emit(Game.EV_EAT)
        } else {
            snake.removeAt(snake.size - 1)
        }
    }

    override fun draw(canvas: Canvas) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        canvas.drawColor(GameStyle.DARK_BG)

        val ox = ((w - cols * cell) / 2f).toInt().toFloat()
        val oy = ((h - rows * cell) / 2f).toInt().toFloat()

        // 棋盘底
        GameStyle.rr(canvas, ox - 5f, oy - 5f, cols * cell + 10f, rows * cell + 10f, 12f, GameStyle.DARK_PANEL)

        // 网格
        var i = 0
        while (i <= cols) {
            GameStyle.line(canvas, ox + i * cell, oy, ox + i * cell, oy + rows * cell, GameStyle.DARK_GRID, 1f)
            i++
        }
        var j = 0
        while (j <= rows) {
            GameStyle.line(canvas, ox, oy + j * cell, ox + cols * cell, oy + j * cell, GameStyle.DARK_GRID, 1f)
            j++
        }

        // 食物：外圈光晕 + 实心
        val fx = ox + foodX * cell + cell / 2f
        val fy = oy + foodY * cell + cell / 2f
        GameStyle.circle(canvas, fx, fy, cell * 0.46f, GameStyle.RED, 36)
        GameStyle.circle(canvas, fx, fy, cell * 0.3f, GameStyle.RED)

        // 蛇：头亮尾淡。撞墙那一下头部会越界，跳过不画
        snake.forEachIndexed { idx, s ->
            if (s.x < 0 || s.y < 0 || s.x >= cols || s.y >= rows) return@forEachIndexed
            val t = idx.toFloat() / maxOf(1, snake.size - 1)
            val color = if (idx == 0) GameStyle.BRAND else GameStyle.LIGHT
            val alpha = if (idx == 0) 255 else (255 * (0.9f - t * 0.5f)).toInt()
            GameStyle.rr(
                canvas,
                ox + s.x * cell + 1.5f,
                oy + s.y * cell + 1.5f,
                cell - 3f,
                cell - 3f,
                if (idx == 0) cell * 0.32f else cell * 0.24f,
                color,
                alpha.coerceIn(60, 255)
            )
        }
    }
}
