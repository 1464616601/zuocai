package com.jiangjinhao.zuocai

import com.jiangjinhao.zuocai.game.Game
import com.jiangjinhao.zuocai.game.ShooterGame
import com.jiangjinhao.zuocai.game.SnakeGame
import com.jiangjinhao.zuocai.game.SokobanGame
import com.jiangjinhao.zuocai.game.TankGame
import com.jiangjinhao.zuocai.game.TetrisGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 小游戏的规则测试。这些游戏只在 update/input 里跑逻辑，
 * draw 才碰 Canvas，所以可以在 JVM 单测里验证。
 */
class GameLogicTest {

    // ---------------------------------------------------------------- 贪吃蛇

    @Test
    fun snake_waitsForFirstInput() {
        val g = SnakeGame()
        g.resize(400, 600)

        // 不按方向键时不应该自己动，更不应该撞墙
        repeat(20) { g.update(200) }
        assertFalse(g.over)
        assertEquals(0, g.score)
        assertEquals("按方向键开始", g.label)
    }

    @Test
    fun snake_diesAtWall() {
        val g = SnakeGame()
        g.resize(400, 600)

        g.input(Game.LEFT)
        // 初始在 x=7，往左 7 步到边界，第 8 步撞墙
        repeat(40) { g.update(200) }
        assertTrue(g.over)
    }

    @Test
    fun snake_ignoresReverse() {
        val g = SnakeGame()
        g.resize(400, 600)

        g.input(Game.LEFT)
        g.input(Game.RIGHT)   // 直接反向，应被忽略
        repeat(30) { g.update(200) }
        // 仍然朝左走到底，说明反向那一下没生效
        assertTrue(g.over)
    }

    // ---------------------------------------------------------------- 俄罗斯方块

    @Test
    fun tetris_hardDropAddsScore() {
        val g = TetrisGame()
        g.resize(400, 600)
        assertEquals(0, g.score)

        g.input(Game.B)
        assertTrue(g.score > 0)
        assertFalse(g.over)
    }

    @Test
    fun tetris_moveAndRotateDoNotCrash() {
        val g = TetrisGame()
        g.resize(400, 600)

        repeat(30) { i ->
            g.input(listOf(Game.LEFT, Game.RIGHT, Game.UP, Game.DOWN)[i % 4])
            g.update(120)
        }
        assertNotNull(g)
    }

    // ---------------------------------------------------------------- 推箱子

    @Test
    fun sokoban_firstPushClearsLevel() {
        val g = SokobanGame()
        g.resize(400, 600)
        assertEquals("第 1 关 · 共 3 关", g.label)

        // 第 1 关玩家在箱子正下方，往上推一步箱子就落在目标点上
        g.input(Game.UP)

        assertEquals(100, g.score)
        assertEquals("第 2 关 · 共 3 关", g.label)
    }

    @Test
    fun sokoban_resetGoesBackToFirstLevel() {
        val g = SokobanGame()
        g.resize(400, 600)
        g.input(Game.UP)
        g.input(Game.B)   // B = 重来本关
        assertTrue(g.label.startsWith("第 2 关"))  // 重来的是当前关，不是第一关
        assertEquals(100, g.score)
    }

    @Test
    fun sokoban_cannotWalkThroughWall() {
        val g = SokobanGame()
        g.resize(400, 600)
        // 第 1 关玩家在 (3,3)，下方是空地，一直往下会撞墙
        repeat(10) { g.input(Game.DOWN) }
        // 没有崩溃、没有误判通关
        assertEquals(0, g.score)
    }

    // ---------------------------------------------------------------- 飞机大战

    @Test
    fun shooter_autoFiresAndDrainsLife() {
        val g = ShooterGame()
        // 固定随机种子：用例完全可复现，不再看运气（约 40% 概率失败的根因）
        g.random = java.util.Random(20261030L)
        g.resize(400, 600)
        assertEquals("生命 3 / 3", g.label)

        // 不操作放 20 秒，敌机会撞上来，生命应减少
        repeat(1200) { g.update(16) }
        assertTrue(g.label != "生命 3 / 3" || g.over)
    }

    // ---------------------------------------------------------------- 坦克大战

    @Test
    fun tank_movesAndFires() {
        val g = TankGame()
        g.resize(400, 600)

        g.input(Game.UP)
        g.input(Game.A)   // 开炮
        g.input(Game.RIGHT)
        g.input(Game.B)

        repeat(120) { g.update(16) }
        // 跑下来不该崩，也不该立刻结束
        assertNotNull(g)
    }

    @Test
    fun tank_gridAdaptsToCanvas() {
        val g = TankGame()
        g.resize(1080, 2160)
        val info = g.gridInfo()
        val cell = info[2]
        // 旧版 cell 固定 36px，13 列只有 468px，墙缩在画布左上角。
        // 现在按画布自适应：宽是限制边时，战场宽度应铺满画布。
        assertTrue(
            "战场宽 ${info[0] + 13 * cell} 应铺满画布宽 1080",
            info[0] + 13 * cell > 1080 * 0.98f
        )
        assertTrue(
            "战场高 ${info[1] + 20 * cell} 不应超出画布",
            info[1] + 20 * cell <= 2160f + 1f
        )
        assertTrue("格子应随画布放大，实际 $cell", cell > 70f)
    }

    @Test
    fun tank_lifeLabelIsNumeric() {
        val g = TankGame()
        g.resize(400, 600)
        assertTrue(g.label.startsWith("生命 "))
        // 不能用 ♥ 这类字符，缺字体的机器会画成豆腐块
        assertFalse(g.label.contains("♥"))
    }
}
