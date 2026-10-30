package com.jiangjinhao.zuocai.game

import android.graphics.Canvas

/**
 * 坦克大战。守住底部基地，打掉来袭坦克。
 *
 * 战场是固定的 13×20 逻辑格，格子尺寸按画布自适应、战场整体居中——
 * 无论手机物理像素多大，墙、坦克、子弹的相对比例和手感都一致。
 * 旧版用固定 36px 格子且 resize 不重排，高分屏上墙只占左上角一小块。
 *
 * 敌人从顶部进入后会随机转向并开火；砖墙可被子弹打掉。
 */
class TankGame : EventGame() {
    override val title = "坦克大战"
    override val hint = "方向键移动 · A 开炮"

    override var score = 0
    override var over = false
    override var label = "生命 3 / 3"

    private class Tank(var x: Float, var y: Float, var dir: Int, var hp: Int)
    private class Shot(var x: Float, var y: Float, var vx: Float, var vy: Float, var mine: Boolean, var dead: Boolean)

    /** 墙存逻辑格坐标，像素位置绘制/碰撞时再换算，这样 resize 后自动跟着战场走 */
    private class Wall(val cx: Int, val cy: Int, var hp: Int)
    private class Part(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, var color: Int)

    private var W = 360f
    private var H = 560f
    private var cell = 27f
    private var ox = 0f
    private var oy = 0f
    private var tank = 25f

    private var player = Tank(0f, 0f, 0, 1)
    private val enemies = ArrayList<Tank>()
    private val bullets = ArrayList<Shot>()
    private val walls = ArrayList<Wall>()
    private val parts = ArrayList<Part>()

    private var spawnAcc = 0f
    private var hp = 3
    private var cool = 0f
    private var anim = 0f
    private var baseAlive = true

    // ---- 战场几何 ----
    private val bfLeft get() = ox
    private val bfTop get() = oy
    private val bfRight get() = ox + COLS * cell
    private val bfBottom get() = oy + ROWS * cell
    private val bfCX get() = ox + COLS * cell / 2f

    private fun wallCX(w: Wall) = ox + w.cx * cell + cell / 2f
    private fun wallCY(w: Wall) = oy + w.cy * cell + cell / 2f

    init {
        reset()
    }

    override fun reset() {
        layout()
        player = Tank(bfCX, bfBottom - cell * 1.4f, 0, 1)
        enemies.clear()
        bullets.clear()
        parts.clear()
        hp = 3
        cool = 0f
        baseAlive = true
        spawnAcc = 0f
        score = 0
        over = false
        label = "生命 3 / 3"
        buildWalls()
    }

    override fun resize(w: Int, h: Int) {
        W = w.toFloat()
        H = h.toFloat()
        layout()
        // 格子尺寸变了，把实体按新战场收紧；墙是逻辑格坐标，自动跟着走
        val half = tank / 2f
        player.x = G.clamp(player.x, bfLeft + half, bfRight - half)
        player.y = G.clamp(player.y, bfTop + tank, bfBottom - half)
        enemies.forEach {
            it.x = G.clamp(it.x, bfLeft + half, bfRight - half)
            it.y = G.clamp(it.y, bfTop + half, bfBottom - tank)
        }
        bullets.removeAll {
            it.x < bfLeft || it.x > bfRight || it.y < bfTop || it.y > bfBottom
        }
    }

    private fun layout() {
        cell = minOf(W / COLS, H / ROWS)
        ox = (W - COLS * cell) / 2f
        oy = (H - ROWS * cell) / 2f
        tank = cell * 0.92f
    }

    /** 仅供单元测试观察战场几何：[ox, oy, cell, tank] */
    internal fun gridInfo(): FloatArray = floatArrayOf(ox, oy, cell, tank)

    private fun buildWalls() {
        walls.clear()
        val put = { cx: Int, cy: Int, h: Int ->
            if (cx in 1..(COLS - 2) && cy in 1..(ROWS - 3)) walls.add(Wall(cx, cy, h))
        }

        val left = 2
        val right = COLS - 3
        intArrayOf(3, 4, 8, 9).forEach {
            put(left, it, 2)
            put(right, it, 2)
        }
        intArrayOf(4, 5, 9, 10).forEach {
            put(left + 1, it, 2)
            put(right - 1, it, 2)
        }

        val mid = COLS / 2
        intArrayOf(5, 6).forEach { cx ->
            put(cx, 6, 2)
            put(COLS - 1 - cx, 6, 2)
        }
        put(mid, 7, 2)
        put(mid, 11, 2)
        put(mid - 1, 11, 2)
        put(mid + 1, 11, 2)

        val baseCol = COLS / 2
        put(baseCol - 2, ROWS - 4, 3)
        put(baseCol + 2, ROWS - 4, 3)
        put(baseCol - 1, ROWS - 3, 3)
        put(baseCol + 1, ROWS - 3, 3)
    }

    private fun dirVec(dir: Int): Pair<Float, Float> = when (dir) {
        0 -> Pair(0f, -1f)
        1 -> Pair(1f, 0f)
        2 -> Pair(0f, 1f)
        else -> Pair(-1f, 0f)
    }

    private fun boom(x: Float, y: Float, color: Int) {
        for (i in 0 until 8) {
            val a = Math.random() * 6.28
            val s = cell * (2.0 + Math.random() * 4.0)
            parts.add(
                Part(x, y, (Math.cos(a) * s).toFloat(), (Math.sin(a) * s).toFloat(), 380f, color)
            )
        }
    }

    private fun shoot() {
        if (cool > 0f) return
        cool = 260f
        val (dx, dy) = dirVec(player.dir)
        bullets.add(
            Shot(player.x + dx * tank * 0.55f, player.y + dy * tank * 0.55f, dx, dy, true, false)
        )
        emit(Game.EV_SHOOT)
    }

    private fun enemyShoot(e: Tank) {
        val (dx, dy) = dirVec(e.dir)
        bullets.add(
            Shot(e.x + dx * tank * 0.55f, e.y + dy * tank * 0.55f, dx, dy, false, false)
        )
    }

    private fun hitWall(x: Float, y: Float): Boolean {
        return walls.any {
            Math.abs(wallCX(it) - x) < cell * 0.72f &&
                Math.abs(wallCY(it) - y) < cell * 0.72f
        }
    }

    override fun input(action: String) {
        if (over) return
        val step = cell * 0.62f
        when (action) {
            Game.UP -> { player.dir = 0; player.y = maxOf(bfTop + tank / 2, player.y - step) }
            Game.RIGHT -> { player.dir = 1; player.x = minOf(bfRight - tank / 2, player.x + step) }
            Game.DOWN -> { player.dir = 2; player.y = minOf(bfBottom - tank / 2, player.y + step) }
            Game.LEFT -> { player.dir = 3; player.x = maxOf(bfLeft + tank / 2, player.x - step) }
            Game.A, Game.B -> shoot()
        }
    }

    override fun update(dtMs: Long) {
        if (over) return
        anim += dtMs
        val k = dtMs / 16.7f
        val espd = cell * 0.043f * k
        val bspd = cell * 0.18f * k
        cool = maxOf(0f, cool - dtMs)

        spawnAcc += dtMs
        if (spawnAcc > maxOf(900f, 2000f - score * 3f) && enemies.size < 5) {
            spawnAcc = 0f
            var ex = bfLeft + cell * 1.5f
            for (t in 0 until 16) {
                val cand = bfLeft + cell + (Math.random() * (COLS * cell - cell * 2)).toFloat()
                val tooClose = enemies.any { Math.abs(it.x - cand) < tank * 1.3f }
                if (!hitWall(cand, bfTop + cell * 0.7f) && !tooClose) {
                    ex = cand
                    break
                }
            }
            enemies.add(Tank(ex, bfTop + cell * 0.6f, 2, if (Math.random() < 0.25) 2 else 1))
        }

        enemies.forEach { e ->
            // 简易 AI：随机换向，遇墙再换
            if (Math.random() < dtMs / 700f) e.dir = G.rnd(4)
            val (dx, dy) = dirVec(e.dir)
            val nx = e.x + dx * espd
            val ny = e.y + dy * espd
            if (nx > bfLeft + tank / 2 && nx < bfRight - tank / 2 &&
                ny > bfTop + tank / 2 && ny < bfBottom - tank && !hitWall(nx, ny)
            ) {
                e.x = nx
                e.y = ny
            } else {
                e.dir = G.rnd(4)
            }
            if (Math.random() < dtMs / 1500f) enemyShoot(e)
        }

        bullets.forEach {
            it.x += it.vx * bspd
            it.y += it.vy * bspd
        }

        // 子弹打墙
        bullets.forEach { b ->
            val w = walls.firstOrNull {
                b.x > ox + it.cx * cell && b.x < ox + (it.cx + 1) * cell &&
                    b.y > oy + it.cy * cell && b.y < oy + (it.cy + 1) * cell
            }
            if (w != null) {
                w.hp -= 1
                b.dead = true
                boom(b.x, b.y, 0xFFB08968.toInt())
                if (w.hp <= 0) walls.remove(w)
            }
        }

        // 我方子弹打敌人
        bullets.filter { it.mine && !it.dead }.forEach { b ->
            val hit = enemies.firstOrNull {
                Math.abs(b.x - it.x) < tank * 0.7f && Math.abs(b.y - it.y) < tank * 0.7f
            }
            if (hit != null) {
                b.dead = true
                hit.hp -= 1
                if (hit.hp <= 0) {
                    enemies.remove(hit)
                    score += 20
                    boom(hit.x, hit.y, GameStyle.BRAND)
                    emit(Game.EV_BOOM)
                } else {
                    emit(Game.EV_HIT)
                }
            }
        }

        // 敌弹打玩家
        bullets.filter { !it.mine && !it.dead }.forEach { b ->
            if (Math.abs(b.x - player.x) < tank * 0.6f && Math.abs(b.y - player.y) < tank * 0.6f) {
                b.dead = true
                hp -= 1
                player.x = bfCX
                player.y = bfBottom - cell * 1.4f
                boom(player.x, player.y, GameStyle.BRAND2)
                emit(Game.EV_HURT)
            }
        }

        // 敌人撞玩家
        enemies.filter {
            Math.abs(it.x - player.x) < tank && Math.abs(it.y - player.y) < tank
        }.forEach {
            hp -= 1
            it.hp = 0
            boom(it.x, it.y, GameStyle.WARN)
            emit(Game.EV_HURT)
        }
        enemies.removeAll { it.hp <= 0 }

        parts.forEach {
            it.x += it.vx * (dtMs / 1000f)
            it.y += it.vy * (dtMs / 1000f)
            it.life -= dtMs
        }
        parts.removeAll { it.life <= 0f }

        bullets.removeAll {
            it.dead || it.x < bfLeft - 10f || it.x > bfRight + 10f ||
                it.y < bfTop - 10f || it.y > bfBottom + 10f
        }

        if (hp <= 0 || !baseAlive) {
            over = true
            emit(Game.EV_OVER)
        }
        label = "生命 ${maxOf(0, hp)} / 3"
    }

    override fun draw(canvas: Canvas) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        canvas.drawColor(GameStyle.DARK_BG)

        // 战场地面 + 边框
        GameStyle.rr(canvas, bfLeft, bfTop, COLS * cell, ROWS * cell, 8f, GameStyle.DARK_PANEL)
        GameStyle.rr(
            canvas, bfLeft, bfTop, COLS * cell, ROWS * cell, 8f,
            GameStyle.DARK_GRID, 255
        )

        // 地面细网格
        var gx = 1
        while (gx < COLS) {
            GameStyle.line(canvas, ox + gx * cell, bfTop, ox + gx * cell, bfBottom, GameStyle.DARK_GRID, 1f)
            gx++
        }
        var gy = 1
        while (gy < ROWS) {
            GameStyle.line(canvas, bfLeft, oy + gy * cell, bfRight, oy + gy * cell, GameStyle.DARK_GRID, 1f)
            gy++
        }

        // 砖墙
        walls.forEach { wl ->
            val x = ox + wl.cx * cell
            val y = oy + wl.cy * cell
            val color = if (wl.hp >= 3) 0xFF8A6A45.toInt() else 0xFFB08958.toInt()
            GameStyle.rr(canvas, x + 1f, y + 1f, cell - 2f, cell - 2f, 3f, color)
            GameStyle.line(
                canvas, x + 1f, y + cell / 2f, x + cell - 1f, y + cell / 2f, 0x40000000, 1f
            )
        }

        // 基地
        val bw = cell * 1.1f
        val bh = cell * 0.8f
        GameStyle.rr(
            canvas, bfCX - bw / 2, bfBottom - bh - 4, bw, bh, 5f,
            if (baseAlive) 0xFF8A6A2F.toInt() else 0xFF4A4038.toInt()
        )
        GameStyle.rr(
            canvas, bfCX - bw / 2 + 4, bfBottom - bh, bw - 8, bh - 8, 3f,
            if (baseAlive) GameStyle.WARN else 0xFF6B5F55.toInt()
        )

        // 坦克
        drawTank(canvas, player.x, player.y, player.dir, GameStyle.BRAND, tank)
        enemies.forEach { e ->
            drawTank(canvas, e.x, e.y, e.dir, if (e.hp > 1) 0xFF8B5CF6.toInt() else 0xFF7BC96F.toInt(), tank)
        }

        // 子弹
        bullets.forEach { b ->
            GameStyle.circle(canvas, b.x, b.y, cell * 0.1f, if (b.mine) 0xFFFFD9C2.toInt() else 0xFFFF6B6B.toInt())
        }

        // 爆炸粒子
        parts.forEach { p ->
            val a = ((p.life / 380f) * 255).toInt().coerceIn(0, 255)
            GameStyle.circle(canvas, p.x, p.y, cell * 0.09f, p.color, a)
        }
    }

    private fun drawTank(canvas: Canvas, x: Float, y: Float, dir: Int, color: Int, size: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(dir * 90f)

        // 履带
        GameStyle.rr(canvas, -size * 0.5f, -size * 0.38f, size * 0.14f, size * 0.76f, 2f, 0x66000000)
        GameStyle.rr(canvas, size * 0.36f, -size * 0.38f, size * 0.14f, size * 0.76f, 2f, 0x66000000)
        // 车体
        GameStyle.rr(canvas, -size * 0.42f, -size * 0.42f, size * 0.84f, size * 0.84f, 4f, color)
        // 炮管
        GameStyle.rr(canvas, -size * 0.09f, -size * 0.72f, size * 0.18f, size * 0.5f, 2f, 0x73000000)

        canvas.restore()
    }

    companion object {
        private const val COLS = 13
        private const val ROWS = 20
    }
}
