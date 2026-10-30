package com.jiangjinhao.zuocai

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.jiangjinhao.zuocai.databinding.ActivityGamesBinding
import com.jiangjinhao.zuocai.databinding.ItemGameBinding
import com.jiangjinhao.zuocai.game.GameCatalog

/**
 * 小游戏列表。数据来自 [GameCatalog]，最高分存 SharedPreferences。
 */
class GamesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGamesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGamesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.gamesBack.setOnClickListener { finish() }
        buildCards()
    }

    override fun onResume() {
        super.onResume()
        // 从游戏返回时刷新最高分
        buildCards()
    }

    private fun buildCards() {
        binding.gameList.removeAllViews()
        val inflater = LayoutInflater.from(this)

        GameCatalog.ALL.forEach { meta ->
            val item = ItemGameBinding.inflate(inflater, binding.gameList, false)
            item.itemIcon.setImageResource(meta.icon)
            item.itemName.text = meta.name
            item.itemDesc.text = meta.desc

            val best = GameCatalog.best(this, meta.key)
            item.itemBest.text = if (best > 0) getString(R.string.games_best, best) else ""

            item.root.setOnClickListener { openGame(meta.key) }
            binding.gameList.addView(item.root)
        }
    }

    private fun openGame(key: String) {
        startActivity(
            Intent(this, GameActivity::class.java).putExtra(GameActivity.EXTRA_KEY, key)
        )
    }

    /** 方便外部（首页菜单）直接进来 */
    companion object {
        fun start(activity: android.app.Activity) {
            activity.startActivity(Intent(activity, GamesActivity::class.java))
            activity.overridePendingTransition(
                android.R.anim.slide_in_left, android.R.anim.fade_out
            )
        }
    }
}
