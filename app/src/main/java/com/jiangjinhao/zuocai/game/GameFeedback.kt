package com.jiangjinhao.zuocai.game

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.jiangjinhao.zuocai.R

/**
 * 游戏的音效与震动，宿主每帧把 [Game.drainEvents] 的结果丢进来。
 *
 * 音效用 SoundPool 预加载短采样（都在 res/raw），延迟低、不卡主线程；
 * 震动按事件分级：按键轻点一下，受伤/爆炸重一些，结束给一段节奏。
 */
object GameFeedback {

    private var pool: SoundPool? = null
    private val sounds = HashMap<String, Int>()
    private var vib: Vibrator? = null

    fun init(context: Context) {
        if (pool != null) return
        pool = SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()

        mapOf(
            Game.EV_SHOOT to R.raw.sfx_shoot,
            Game.EV_HIT to R.raw.sfx_hit,
            Game.EV_BOOM to R.raw.sfx_boom,
            Game.EV_HURT to R.raw.sfx_hurt,
            Game.EV_EAT to R.raw.sfx_eat,
            Game.EV_LINE to R.raw.sfx_line,
            Game.EV_DROP to R.raw.sfx_drop,
            Game.EV_PUSH to R.raw.sfx_push,
            Game.EV_WIN to R.raw.sfx_win,
            Game.EV_OVER to R.raw.sfx_over
        ).forEach { (name, resId) ->
            val id = pool!!.load(context, resId, 1)
            sounds[name] = id
        }

        vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun release() {
        pool?.release()
        pool = null
        sounds.clear()
        vib = null
    }

    /** 按键按下：极短轻震 */
    fun tick() = vibrate(14, 90)

    fun play(events: List<String>) {
        events.forEach { ev ->
            when (ev) {
                Game.EV_SHOOT -> playSound(Game.EV_SHOOT, 0.28f)
                Game.EV_HIT -> playSound(Game.EV_HIT, 0.6f)
                Game.EV_BOOM -> { playSound(Game.EV_BOOM, 0.9f); vibrate(32, 140) }
                Game.EV_HURT -> { playSound(Game.EV_HURT, 0.9f); vibrate(48, 180) }
                Game.EV_EAT -> playSound(Game.EV_EAT, 0.7f)
                Game.EV_LINE -> playSound(Game.EV_LINE, 0.8f)
                Game.EV_DROP -> { playSound(Game.EV_DROP, 0.7f); vibrate(20, 110) }
                Game.EV_PUSH -> { playSound(Game.EV_PUSH, 0.7f); vibrate(16, 100) }
                Game.EV_WIN -> playSound(Game.EV_WIN, 0.9f)
                Game.EV_OVER -> { playSound(Game.EV_OVER, 1f); vibratePattern() }
            }
        }
    }

    private fun playSound(name: String, volume: Float) {
        val id = sounds[name] ?: return
        pool?.play(id, volume, volume, 1, 0, 1f)
    }

    private fun vibrate(ms: Long, amplitude: Int) {
        val v = vib ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(ms, amplitude))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(ms)
        }
    }

    private fun vibratePattern() {
        val v = vib ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 40, 70, 40, 70, 90)
            val amps = intArrayOf(0, 120, 0, 150, 0, 200)
            v.vibrate(VibrationEffect.createWaveform(timings, amps, -1))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(longArrayOf(0, 40, 70, 40, 70, 90), -1)
        }
    }
}
