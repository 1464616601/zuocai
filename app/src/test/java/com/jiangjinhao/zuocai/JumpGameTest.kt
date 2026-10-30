package com.jiangjinhao.zuocai

import com.jiangjinhao.zuocai.game.Game
import com.jiangjinhao.zuocai.game.JumpGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 跳一跳的状态机。
 *
 * 蓄力跳的落点由随机方块决定，但「跳太短 → 掉下去 → 结束 → 复活」这条路是确定的，
 * 正好把最容易写错的三件事咬住：起跳、判负、复活后分数不清零。
 */
class JumpGameTest {

    private fun newGame(): JumpGame = JumpGame().apply {
        resize(1080, 1800)
    }

    /** 轻点一下就起跳（蓄力接近 0），必然落回脚下这块方块，应该判负 */
    private fun jumpTooShort(game: JumpGame) {
        game.onPress(Game.A)
        game.update(1)
        game.onRelease(Game.A)
        repeat(600) { game.update(16) }
    }

    @Test
    fun startsClean() {
        val game = newGame()
        assertEquals(0, game.score)
        assertFalse(game.over)
        assertFalse(game.canRevive())
    }

    @Test
    fun shortJumpFallsAndEnds() {
        val game = newGame()
        jumpTooShort(game)
        assertTrue("跳太短应该掉下去并结束", game.over)
        assertTrue("结束后才允许复活", game.canRevive())
    }

    @Test
    fun reviveKeepsScoreAndContinues() {
        val game = newGame()
        jumpTooShort(game)
        assertTrue(game.over)

        game.revive()
        assertFalse("复活后应该能继续玩", game.over)
        assertEquals("复活不清零分数", 0, game.score)

        // 复活之后还能再跳、再判负，说明状态确实重置干净了
        jumpTooShort(game)
        assertTrue(game.over)
    }

    @Test
    fun releaseWithoutPressDoesNothing() {
        val game = newGame()
        game.onRelease(Game.A)
        repeat(10) { game.update(16) }
        assertFalse(game.over)
        assertEquals(0, game.score)
    }
}
