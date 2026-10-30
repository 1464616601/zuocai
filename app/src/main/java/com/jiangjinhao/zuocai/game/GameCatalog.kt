package com.jiangjinhao.zuocai.game

import androidx.annotation.DrawableRes
import com.jiangjinhao.zuocai.R

/**
 * 小游戏目录。列表页和游戏页都从这里取元信息，避免两处不同步。
 */
object GameCatalog {

    data class Meta(
        val key: String,
        val name: String,
        val desc: String,
        @DrawableRes val icon: Int
    )

    val ALL = listOf(
        Meta("snake", "贪吃蛇", "吃到红点得分，别撞墙也别咬到自己", R.drawable.ic_game_snake),
        Meta("tetris", "俄罗斯方块", "转一转、堆一堆，消掉整行拿高分", R.drawable.ic_game_tetris),
        Meta("sokoban", "推箱子", "把木箱推到圆点上，共三关", R.drawable.ic_game_box),
        Meta("shooter", "飞机大战", "自动开火，躲子弹，活下来", R.drawable.ic_game_plane),
        Meta("tank", "坦克大战", "守住基地，打掉来袭的坦克", R.drawable.ic_game_tank)
    )

    fun find(key: String?): Meta? = ALL.firstOrNull { it.key == key }

    fun create(key: String?): Game? = when (key) {
        "snake" -> SnakeGame()
        "tetris" -> TetrisGame()
        "sokoban" -> SokobanGame()
        "shooter" -> ShooterGame()
        "tank" -> TankGame()
        else -> null
    }

    fun best(context: android.content.Context, key: String): Int =
        context.getSharedPreferences("zuocai_games", android.content.Context.MODE_PRIVATE)
            .getInt("best_$key", 0)

    fun saveBest(context: android.content.Context, key: String, score: Int) {
        context.getSharedPreferences("zuocai_games", android.content.Context.MODE_PRIVATE)
            .edit().putInt("best_$key", score).apply()
    }
}
