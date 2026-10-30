package com.jiangjinhao.zuocai.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import com.jiangjinhao.zuocai.game.Game

/**
 * 承载小游戏的画布。
 *
 * 用 Choreographer 跟随屏幕刷新率驱动，dt 上限 60ms——
 * 切到后台再回来时不会因为一次性补帧把游戏瞬移。
 */
class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        fun onTick(score: Int, label: String)
        fun onGameOver(score: Int)
    }

    var game: Game? = null
        set(value) {
            field = value
            value?.resize(width, height)
            lastFrame = 0L
        }

    var listener: Listener? = null

    /** 游戏每帧产出的音效/震动事件，宿主接到后播放 */
    var onEvents: ((List<String>) -> Unit)? = null

    private var lastFrame = 0L
    private var running = false
    private var finished = false

    private val callback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            val g = game ?: return

            val now = frameTimeNanos / 1_000_000L
            val dt = if (lastFrame == 0L) 16L else (now - lastFrame).coerceIn(0L, 60L)
            lastFrame = now

            g.update(dt)
            val events = g.drainEvents()
            if (events.isNotEmpty()) onEvents?.invoke(events)
            invalidate()
            listener?.onTick(g.score, if (g.label.isNotEmpty()) g.label else g.hint)

            if (g.over) {
                if (!finished) {
                    finished = true
                    listener?.onGameOver(g.score)
                }
                return
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun start() {
        val g = game ?: return
        // 已经结束的局不要自动重跑，否则切前台会重复弹结算框
        if (g.over) return
        if (width > 0 && height > 0) g.resize(width, height)
        running = true
        finished = false
        lastFrame = 0L
        Choreographer.getInstance().removeFrameCallback(callback)
        Choreographer.getInstance().postFrameCallback(callback)
    }

    fun restart() {
        game?.reset()
        start()
    }

    fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(callback)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        game?.resize(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        game?.draw(canvas)
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (running) start()
    }
}
