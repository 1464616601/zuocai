package com.jiangjinhao.zuocai.game

import android.graphics.Canvas
import android.graphics.Path

/**
 * 飞机大战。自动开火，被打中掉一条命，三条命打完结束。
 *
 * 全部逻辑跑在 360 宽的「设计空间」里，绘制时按画布宽度等比放大——
 * 手机物理像素再高，飞机大小、速度的手感都和设计稿一致。
 */
class ShooterGame : EventGame() {
    override val title = "飞机大战"
    override val hint = "自动开火，左右移动躲子弹"

    override var score = 0
    override var over = false
    override var label = "生命 3 / 3"

    /** 可注入随机源：单测里固定种子，用例才可复现 */
    var random = java.util.Random()

    private var scale = 1f
    private var LW = 360f
    private var LH = 560f

    private class Player(var x: Float, var y: Float)
    private class Bullet(var x: Float, var y: Float)
    private class Enemy(var x: Float, var y: Float, var vy: Float, var r: Float, var hp: Int, var fireAcc: Float)
    private class Part(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, var color: Int)

    private var player = Player(180f, 500f)
    private val bullets = ArrayList<Bullet>()
    private val enemies = ArrayList<Enemy>()
    private val eBullets = ArrayList<Bullet>()
    private val parts = ArrayList<Part>()

    private var spawnAcc = 0f
    private var shootAcc = 0f
    private var hp = 3
    private var anim = 0f

    init {
        reset()
    }

    override fun reset() {
        player = Player(LW / 2f, LH - 70f)
        bullets.clear()
        enemies.clear()
        eBullets.clear()
        parts.clear()
        spawnAcc = 0f
        shootAcc = 0f
        hp = 3
        score = 0
        over = false
        label = "生命 3 / 3"
    }

    override fun resize(w: Int, h: Int) {
        scale = (w / 360f).coerceAtLeast(0.5f)
        LW = 360f
        LH = h / scale
        player.x = G.clamp(player.x, 18f, LW - 18f)
        player.y = LH - 70f
    }

    private fun boom(x: Float, y: Float, color: Int) {
        for (i in 0 until 10) {
            val a = random.nextDouble() * 6.28
            val s = 40 + random.nextDouble() * 120
            parts.add(
                Part(x, y, (Math.cos(a) * s).toFloat(), (Math.sin(a) * s).toFloat(), 420f, color)
            )
        }
    }

    override fun input(action: String) {
        if (over) return
        val sp = 28f
        when (action) {
            Game.LEFT -> player.x = G.clamp(player.x - sp, 18f, LW - 18f)
            Game.RIGHT -> player.x = G.clamp(player.x + sp, 18f, LW - 18f)
            Game.UP -> player.y = G.clamp(player.y - sp, LH * 0.45f, LH - 40f)
            Game.DOWN -> player.y = G.clamp(player.y + sp, LH * 0.45f, LH - 40f)
        }
    }

    override fun update(dtMs: Long) {
        if (over) return
        anim += dtMs
        val k = dtMs / 16.7f

        shootAcc += dtMs
        if (shootAcc > 150f) {
            shootAcc = 0f
            bullets.add(Bullet(player.x, player.y - 18f))
            emit(Game.EV_SHOOT)
        }

        spawnAcc += dtMs
        if (spawnAcc > maxOf(320f, 900f - score * 0.4f)) {
            spawnAcc = 0f
            enemies.add(
                Enemy(
                    x = 24f + (random.nextDouble() * (LW - 48)).toFloat(),
                    y = -30f,
                    vy = 1.4f + (random.nextDouble() * 1.3).toFloat() + score * 0.0006f,
                    r = 16f,
                    hp = if (random.nextDouble() < 0.15) 2 else 1,
                    fireAcc = (random.nextDouble() * 900).toFloat()
                )
            )
        }

        bullets.forEach { it.y -= 7.5f * k }
        bullets.removeAll { it.y < -20f }

        eBullets.forEach { it.y += 4.2f * k }
        eBullets.removeAll { it.y > LH + 20f }

        enemies.forEach { e ->
            e.y += e.vy * k
            e.fireAcc += dtMs
            if (e.fireAcc > 1400f && e.y > 0f) {
                e.fireAcc = 0f
                eBullets.add(Bullet(e.x, e.y + 14f))
            }
        }

        // 我方子弹打中敌人
        var i = enemies.size - 1
        while (i >= 0) {
            val e = enemies[i]
            var j = bullets.size - 1
            while (j >= 0) {
                val b = bullets[j]
                if (Math.abs(b.x - e.x) < e.r && Math.abs(b.y - e.y) < e.r) {
                    bullets.removeAt(j)
                    e.hp -= 1
                    if (e.hp <= 0) {
                        boom(e.x, e.y, GameStyle.BRAND)
                        enemies.removeAt(i)
                        score += 10
                        emit(Game.EV_BOOM)
                    } else {
                        emit(Game.EV_HIT)
                    }
                    break
                }
                j--
            }
            i--
        }

        // 敌人撞玩家
        enemies.forEach { e ->
            if (Math.abs(e.x - player.x) < e.r + 10f && Math.abs(e.y - player.y) < e.r + 12f) {
                boom(e.x, e.y, GameStyle.WARN)
                e.y = LH + 100f
                hp -= 1
                emit(Game.EV_HURT)
            }
        }
        enemies.removeAll { it.y > LH + 40f }

        // 敌人子弹打玩家
        i = eBullets.size - 1
        while (i >= 0) {
            val b = eBullets[i]
            if (Math.abs(b.x - player.x) < 12f && Math.abs(b.y - player.y) < 14f) {
                eBullets.removeAt(i)
                boom(player.x, player.y, GameStyle.BRAND2)
                hp -= 1
                emit(Game.EV_HURT)
            }
            i--
        }

        parts.forEach {
            it.x += it.vx * (dtMs / 1000f)
            it.y += it.vy * (dtMs / 1000f)
            it.life -= dtMs
        }
        parts.removeAll { it.life <= 0f }

        if (hp <= 0) {
            over = true
            emit(Game.EV_OVER)
        }
        label = "生命 ${maxOf(0, hp)} / 3"
    }

    override fun draw(canvas: Canvas) {
        canvas.drawColor(GameStyle.DARK_BG)
        canvas.save()
        canvas.scale(scale, scale)

        val w = LW
        val h = LH

        // 星空
        for (i in 0 until 26) {
            val y = ((i * 97 + anim * 0.03f) % h)
            val alpha = if (i % 3 == 0) 102 else 46
            GameStyle.rr(canvas, (i * 53 % w).toFloat(), y, 2f, 2f, 1f, GameStyle.WHITE, alpha)
        }

        // 玩家飞机：机身 + 后掠翼 + 尾翼 + 座舱
        val px = player.x
        val py = player.y
        val body = Path().apply {
            moveTo(px, py - 20)
            quadTo(px + 5, py - 10, px + 5, py + 4)
            lineTo(px + 5, py + 13)
            lineTo(px - 5, py + 13)
            lineTo(px - 5, py + 4)
            quadTo(px - 5, py - 10, px, py - 20)
            close()
        }
        canvas.drawPath(body, GameStyle.paint(GameStyle.BRAND))

        val wingL = Path().apply {
            moveTo(px - 4, py - 2)
            lineTo(px - 17, py + 11)
            lineTo(px - 16, py + 14)
            lineTo(px - 4, py + 10)
            close()
        }
        val wingR = Path().apply {
            moveTo(px + 4, py - 2)
            lineTo(px + 17, py + 11)
            lineTo(px + 16, py + 14)
            lineTo(px + 4, py + 10)
            close()
        }
        val wingPaint = GameStyle.paint(GameStyle.BRAND2)
        canvas.drawPath(wingL, wingPaint)
        canvas.drawPath(wingR, wingPaint)

        val tail = Path().apply {
            moveTo(px - 3, py + 10)
            lineTo(px - 8, py + 15)
            lineTo(px + 8, py + 15)
            lineTo(px + 3, py + 10)
            close()
        }
        canvas.drawPath(tail, wingPaint)

        canvas.drawOval(
            px - 2.6f, py - 11f, px + 2.6f, py - 1f,
            GameStyle.paint(GameStyle.WHITE, 190)
        )

        // 我方子弹
        bullets.forEach { b ->
            GameStyle.rr(canvas, b.x - 2f, b.y - 8f, 4f, 12f, 2f, 0xFFFFD9C2.toInt())
        }

        // 敌机
        enemies.forEach { e ->
            val color = if (e.hp > 1) 0xFF8B5CF6.toInt() else 0xFF7BC96F.toInt()
            val p = Path().apply {
                moveTo(e.x, e.y + 15)
                lineTo(e.x + 14, e.y - 10)
                lineTo(e.x, e.y - 4)
                lineTo(e.x - 14, e.y - 10)
                close()
            }
            canvas.drawPath(p, GameStyle.paint(color))
        }

        // 敌弹
        eBullets.forEach { b ->
            GameStyle.circle(canvas, b.x, b.y, 3.4f, 0xFFFF6B6B.toInt())
        }

        // 爆炸粒子
        parts.forEach { p ->
            val a = ((p.life / 420f) * 255).toInt().coerceIn(0, 255)
            GameStyle.rr(canvas, p.x - 2f, p.y - 2f, 4f, 4f, 1f, p.color, a)
        }

        canvas.restore()
    }
}
