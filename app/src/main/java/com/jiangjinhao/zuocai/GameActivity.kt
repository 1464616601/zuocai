package com.jiangjinhao.zuocai

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.jiangjinhao.zuocai.databinding.ActivityGameBinding
import com.jiangjinhao.zuocai.databinding.DialogGameOverBinding
import com.jiangjinhao.zuocai.game.Game
import com.jiangjinhao.zuocai.game.GameCatalog
import com.jiangjinhao.zuocai.game.GameFeedback
import com.jiangjinhao.zuocai.ui.GameView

/**
 * 单个小游戏的宿主：顶部分数、中间画布、底部控制台。
 *
 * 按键做成「按住连续触发」——俄罗斯方块、飞机大战这类需要连续操作，
 * 单击一次只动一格会很难受。跳一跳反过来，靠 onPress/onRelease 蓄力，
 * 由 [Game.holdRepeat] 关掉连发。
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
        applyPadMode()
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

    /**
     * 只留一个大按钮的游戏（跳一跳）把方向键和 B 隐藏掉，
     * 免得用户以为要按方向键。
     */
    private fun applyPadMode() {
        if (meta.pad == GameCatalog.Pad.FULL) return

        binding.padGrid.visibility = View.GONE
        binding.padSpacer.visibility = View.GONE
        binding.btnB.visibility = View.GONE
        binding.btnA.text = meta.primaryLabel ?: getString(R.string.game_a)

        val size = dp(96)
        binding.btnA.layoutParams = binding.btnA.layoutParams.apply {
            width = size
            height = size
        }
        (binding.actsBox.layoutParams as? LinearLayout.LayoutParams)?.apply {
            weight = 1f
            gravity = Gravity.CENTER
        }
        binding.actsBox.requestLayout()
    }

    private fun bindHold(view: View, action: String) {
        val repeat = Runnable {
            val game = binding.gameView.game ?: return@Runnable
            game.input(action)
            ui.postDelayed(repeatRunnable[action] ?: return@Runnable, 95)
        }
        repeatRunnable[action] = repeat

        view.setOnTouchListener { v, e ->
            val game = binding.gameView.game
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    // 按压反馈：轻震 + 缩放，视觉和手感同时到位
                    GameFeedback.tick()
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(70).start()
                    game?.onPress(action)
                    game?.input(action)
                    ui.removeCallbacks(repeat)
                    // 跳一跳这类要蓄力的游戏不能连发，否则一按就跳出去了
                    if (game?.holdRepeat != false) ui.postDelayed(repeat, 280)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    v.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
                    ui.removeCallbacks(repeat)
                    game?.onRelease(action)
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
            // 结算弹窗排队执行时页面可能已经在销毁，直接 show 会 BadTokenException
            if (isFinishing || isDestroyed || !binding.gameView.isAttachedToWindow) return@post

            val prev = GameCatalog.best(this, meta.key)
            val isNew = score > prev
            if (isNew) {
                GameCatalog.saveBest(this, meta.key, score)
                binding.gameBest.text = score.toString()
            }

            val game = binding.gameView.game
            val canRevive = game?.canRevive() == true

            val content = DialogGameOverBinding.inflate(LayoutInflater.from(this))
            content.dlgTitle.setText(if (isNew) R.string.game_over_new else R.string.game_over_old)
            content.dlgScore.text = score.toString()
            content.dlgBest.text = maxOf(prev, score).toString()

            val builder = AlertDialog.Builder(this)
                .setView(content.root)
                .setCancelable(false)

            if (canRevive) {
                // 跳一跳：复活是主操作，放右上（positive），分数不清零
                builder
                    .setPositiveButton(R.string.game_revive) { _, _ ->
                        val g = binding.gameView.game ?: return@setPositiveButton
                        g.revive()
                        binding.gameScore.text = g.score.toString()
                        binding.gameView.start()
                    }
                    .setNeutralButton(R.string.game_again) { _, _ ->
                        binding.gameScore.text = "0"
                        binding.gameView.restart()
                    }
                    .setNegativeButton(R.string.game_to_list) { _, _ -> finish() }
            } else {
                builder
                    .setPositiveButton(R.string.game_again) { _, _ ->
                        binding.gameScore.text = "0"
                        binding.gameView.restart()
                    }
                    .setNegativeButton(R.string.game_to_list) { _, _ -> finish() }
            }
            builder.show()
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
        // 无弹窗时继续跑；有弹窗说明已经结束，等用户点「再来一局」或「复活」
        if (!isFinishing) binding.gameView.start()
    }

    override fun onDestroy() {
        // 先清理再交给父类，避免父类销毁后还在读已释放的资源
        saveBestIfBetter()
        repeatRunnable.values.forEach { ui.removeCallbacks(it) }
        binding.gameView.stop()
        GameFeedback.release()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
