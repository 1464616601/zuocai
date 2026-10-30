package com.jiangjinhao.zuocai.game

import android.graphics.Canvas
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * 跳一跳（复刻微信小程序那一版）。
 *
 * 规则：按住「跳」蓄力，松开起跳，蓄得越久跳得越远。
 * 落到下一块方块上得分，正中中心加倍；掉进空隙就结束。
 * 结束可以**无限复活**：分数保留，角色回到当前方块中心接着跳。
 *
 * 坐标系：世界坐标向右延伸，绘制时减去 [cameraX] 换算成屏幕坐标。
 * 所有尺寸都以 [unit]（画布宽度 / 100）为基准，换机型不用改数值。
 */
class JumpGame : EventGame() {

    override val title = "跳一跳"
    override val hint = "按住「跳」蓄力，松开起跳"
    override val holdRepeat = false

    override var score = 0
    override var over = false
    override var label = ""

    private class Block(val x: Float, val w: Float, val color: Int)

    private val blocks = ArrayList<Block>(64)

    private val blockColors = intArrayOf(
        0xFF6E5B4B.toInt(),
        0xFF5C6B57.toInt(),
        0xFF6B565C.toInt(),
        0xFF4F6170.toInt(),
        0xFF7A6448.toInt()
    )

    private var canvasW = 0f
    private var canvasH = 0f
    private var groundY = 0f
    private var unit = 0f

    /** 当前站着的方块下标 */
    private var curIndex = 0

    private var playerX = 0f
    private var playerY = 0f

    private var charging = false
    private var power = 0f

    private var jumping = false
    private var jumpElapsed = 0f
    private var jumpDur = 380f
    private var jumpFrom = 0f
    private var jumpTo = 0f
    private var jumpPeak = 0f

    private var falling = false
    private var fallSpeed = 0f

    private var cameraX = 0f
    private var cameraTarget = 0f

    /** 结算文案停留时间，用完自动恢复成 hint */
    private var labelTtl = 0f

    // ---------------------------------------------------------------- 尺寸 / 重置

    override fun resize(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        canvasW = w.toFloat()
        canvasH = h.toFloat()
        groundY = canvasH * 0.62f
        unit = canvasW / 100f
        // 首次拿到真实尺寸时铺好场地；之后只更新基准值
        if (blocks.isEmpty()) reset()
    }

    override fun reset() {
        score = 0
        over = false
        label = ""
        labelTtl = 0f
        charging = false
        power = 0f
        jumping = false
        falling = false
        fallSpeed = 0f
        jumpElapsed = 0f
        blocks.clear()
        curIndex = 0
        cameraX = 0f
        cameraTarget = 0f
        if (unit <= 0f) return
        buildWorld()
    }

    private fun buildWorld() {
        blocks.clear()
        val firstW = unit * 26f
        val firstX = unit * 10f
        blocks.add(Block(firstX, firstW, blockColors[0]))
        spawnNext()

        curIndex = 0
        playerX = firstX + firstW / 2f
        playerY = groundY
        cameraX = 0f
        cameraTarget = 0f
    }

    private fun spawnNext() {
        val last = blocks.last()
        val gap = unit * (8f + Math.random().toFloat() * 14f)
        val width = unit * (14f + Math.random().toFloat() * 12f)
        blocks.add(
            Block(
                last.x + last.w + gap,
                width,
                blockColors[blocks.size % blockColors.size]
            )
        )
    }

    // ---------------------------------------------------------------- 输入

    override fun onPress(action: String) {
        if (action != Game.A) return
        if (over || jumping || falling || charging) return
        charging = true
        power = 0f
        labelTtl = 0f
    }

    override fun onRelease(action: String) {
        if (action != Game.A) return
        if (!charging) return
        startJump()
    }

    /** 按住蓄力靠 onPress/onRelease，单击输入这里不处理 */
    override fun input(action: String) = Unit

    private fun startJump() {
        // 最短也要跳出脚下这块方块（最宽的方块半宽 13u），否则轻点一下就原地摔死
        val dist = unit * (16f + power * 32f)
        jumpFrom = playerX
        jumpTo = playerX + dist
        jumpElapsed = 0f
        jumpDur = 330f + power * 170f
        jumpPeak = unit * (11f + power * 7f)
        jumping = true
        charging = false
        power = 0f
        label = ""
        labelTtl = 0f
        emit(Game.EV_DROP)
    }

    // ---------------------------------------------------------------- 帧循环

    override fun update(dtMs: Long) {
        if (over || unit <= 0f) return
        val dt = dtMs.toFloat()

        cameraX += (cameraTarget - cameraX) * min(1f, dt / 110f)
        if (labelTtl > 0f) {
            labelTtl -= dt
            if (labelTtl <= 0f) label = ""
        }

        if (falling) {
            fallSpeed += dt * 0.0026f
            playerY += fallSpeed * dt
            if (playerY > canvasH + unit * 16f) {
                over = true
                emit(Game.EV_OVER)
            }
            return
        }

        if (charging) {
            power = (power + dt * 0.0017f).coerceAtMost(1f)
            label = "蓄力 " + (power * 100).toInt() + "%"
            return
        }

        if (jumping) {
            jumpElapsed += dt
            val t = (jumpElapsed / jumpDur).coerceIn(0f, 1f)
            playerX = jumpFrom + (jumpTo - jumpFrom) * t
            playerY = groundY - sin(t * PI).toFloat() * jumpPeak
            if (t >= 1f) {
                playerY = groundY
                jumping = false
                land()
            }
        }
    }

    private fun land() {
        val idx = blocks.indexOfFirst { playerX >= it.x && playerX <= it.x + it.w }
        // 没踩到下一块：要么跳短了，要么跳过头
        if (idx <= curIndex) {
            falling = true
            fallSpeed = unit * 0.06f
            label = ""
            emit(Game.EV_HURT)
            return
        }

        curIndex = idx
        val b = blocks[idx]
        val offset = abs(playerX - (b.x + b.w / 2f))
        if (offset < unit * 2.2f) {
            score += 2
            label = "正中中心 +2"
            emit(Game.EV_LINE)
        } else {
            score += 1
            label = "+1"
            emit(Game.EV_EAT)
        }
        labelTtl = 900f

        spawnNext()
        cameraTarget = (playerX - canvasW * 0.34f).coerceAtLeast(0f)
    }

    // ---------------------------------------------------------------- 复活

    override fun canRevive(): Boolean = over

    override fun revive() {
        over = false
        falling = false
        jumping = false
        charging = false
        power = 0f
        fallSpeed = 0f
        jumpElapsed = 0f
        label = ""
        labelTtl = 0f

        // 回到最后站稳的那块方块中心
        val b = blocks.getOrNull(curIndex) ?: return
        playerX = b.x + b.w / 2f
        playerY = groundY
        cameraTarget = (playerX - canvasW * 0.34f).coerceAtLeast(0f)
        cameraX = cameraTarget

        // 前面没方块了（理论上不会）就补一块，免得复活后无路可跳
        if (blocks.size <= curIndex + 1) spawnNext()
    }

    // ---------------------------------------------------------------- 绘制

    override fun draw(canvas: Canvas) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        canvas.drawColor(GameStyle.DARK_BG)
        if (unit <= 0f || w <= 0f) return

        // 远景地平线，让画面不至于太空
        val horizon = groundY + (h - groundY) * 0.62f
        GameStyle.line(canvas, 0f, horizon, w, horizon, GameStyle.DARK_GRID, 1.2f)

        // 方块
        val blockH = h - groundY
        blocks.forEach { b ->
            val sx = b.x - cameraX
            if (sx > w + unit * 26f || sx + b.w < -unit * 26f) return@forEach
            GameStyle.rr(canvas, sx, groundY, b.w, blockH, unit * 2.6f, b.color)
            // 顶面提亮，落点看得更清楚
            GameStyle.rr(
                canvas, sx, groundY, b.w, unit * 2.2f, unit * 1.6f,
                GameStyle.LIGHT, 40
            )
        }

        // 地面影子
        if (!falling) {
            GameStyle.circle(
                canvas, playerX - cameraX, groundY + unit * 1.4f,
                unit * 3.2f, 0xFF000000.toInt(), 70
            )
        }

        // 角色：蓄力时压扁、变宽
        val squash = if (charging) power else 0f
        val bodyW = unit * 7f * (1f + squash * 0.34f)
        val bodyH = unit * 12f * (1f - squash * 0.36f)
        val px = playerX - cameraX
        GameStyle.rr(
            canvas, px - bodyW / 2f, playerY - bodyH, bodyW, bodyH,
            unit * 2.4f, GameStyle.LIGHT
        )
        GameStyle.circle(
            canvas, px, playerY - bodyH - unit * 1.4f,
            unit * 2.8f * (1f - squash * 0.2f), GameStyle.BRAND2
        )

        // 蓄力条
        if (charging) {
            val barW = w * 0.56f
            val barX = (w - barW) / 2f
            val barY = h - unit * 13f
            GameStyle.rr(canvas, barX, barY, barW, unit * 3f, unit * 1.5f, GameStyle.DARK_PANEL)
            if (power > 0.01f) {
                GameStyle.rr(canvas, barX, barY, barW * power, unit * 3f, unit * 1.5f, GameStyle.BRAND)
            }
        }
    }
}
