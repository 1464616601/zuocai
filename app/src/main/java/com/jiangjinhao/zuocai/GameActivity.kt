package com.jiangjinhao.zuocai

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.jiangjinhao.zuocai.game.Game
import com.jiangjinhao.zuocai.game.GameCatalog
import com.jiangjinhao.zuocai.game.GameFeedback
import com.jiangjinhao.zuocai.databinding.ActivityGameBinding
import com.jiangjinhao.zuocai.databinding.DialogGameOverBinding
import com.jiangjinhao.zuocai.ui.GameView

/**
 * 单个小游戏的宿主：顶部分数、中间画布、底部方向键与 A/B。
 *
 * 按键做成「按住连续触发」——俄罗斯方块、飞机大战这类需要连续操作，
 * 单击一次只动一格会很难受。
 */
class GameActivity : AppCompatActivity(), GameView.Listener {

    companion object {
        const val EXTRA_KEY = "game_key"
    }

    private lateinit var binding: ActivityGameBinding
    private lateinit var meta: GameCatalog.Meta
    private val ui = Handler(Looper.getMainLooper())
    private val repeatRunnable = HashMap<String, Runnable>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        meta = GameCatalog.find(intent.getStringExtra(EXTRA_KEY)) ?: run {
            finish()
            return
        }

        binding.gameTitle.text = meta.name

        GameFeedback.init(this)
        binding.gameView.onEvents = { GameFeedback.play(it) }

        val game = GameCatalog.create(meta.key)
        binding.gameView.game = game
        binding.gameLabel.text = game?.hint.orEmpty()
        binding.gameView.listener = this
        binding.gameView.start()

        binding.gameBest.text = GameCatalog.best(this, meta.key).toString()
        binding.gameBack.setOnClickListener { finish() }

        bindHold(binding.padUp, Game.UP)
        bindHold(binding.padDown, Game.DOWN)
        bindHold(binding.padLeft, Game.LEFT)
        bindHold(binding.padRight, Game.RIGHT)
        bindHold(binding.btnA, Game.A)
        bindHold(binding.btnB, Game.B)
    }

    private fun bindHold(view: View, action: String) {
        val repeat = Runnable {
            binding.gameView.game?.input(action)
            ui.postDelayed(repeatRunnable[action] ?: return@Runnable, 95)
        }
        repeatRunnable[action] = repeat

        view.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    // 按压反馈：轻震 + 缩放，视觉和手感同时到位
                    GameFeedback.tick()
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(70).start()
                    binding.gameView.game?.input(action)
                    ui.removeCallbacks(repeat)
                    ui.postDelayed(repeat, 280)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    v.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
                    ui.removeCallbacks(repeat)
                    true
                }
                else -> false
            }
        }
    }

    override fun onTick(score: Int, label: String) {
        binding.gameScore.text = score.toString()
        if (binding.gameLabel.text != label) binding.gameLabel.text = label
    }

    override fun onGameOver(score: Int) {
        ui.post {
            val prev = GameCatalog.best(this, meta.key)
            val isNew = score > prev
            if (isNew) {
                GameCatalog.saveBest(this, meta.key, score)
                binding.gameBest.text = score.toString()
            }

            val content = DialogGameOverBinding.inflate(LayoutInflater.from(this))
            content.dlgTitle.setText(if (isNew) R.string.game_over_new else R.string.game_over_old)
            content.dlgScore.text = score.toString()
            content.dlgBest.text = maxOf(prev, score).toString()

            AlertDialog.Builder(this)
                .setView(content.root)
                .setPositiveButton(R.string.game_again) { _, _ ->
                    binding.gameScore.text = "0"
                    binding.gameView.restart()
                }
                .setNegativeButton(R.string.game_to_list) { _, _ -> finish() }
                .setCancelable(false)
                .show()
        }
    }

    /** 中途退出也记住最高分，否则玩得再高也白费 */
    private fun saveBestIfBetter() {
        val score = binding.gameView.game?.score ?: return
        if (score > GameCatalog.best(this, meta.key)) {
            GameCatalog.saveBest(this, meta.key, score)
        }
    }

    override fun onPause() {
        super.onPause()
        saveBestIfBetter()
        binding.gameView.stop()
        repeatRunnable.values.forEach { ui.removeCallbacks(it) }
    }

    override fun onResume() {
        super.onResume()
        // 无弹窗时继续跑；有弹窗说明已经结束，等用户点「再来一局」
        if (!isFinishing) binding.gameView.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        saveBestIfBetter()
        repeatRunnable.values.forEach { ui.removeCallbacks(it) }
        GameFeedback.release()
    }
}
