package com.jiangjinhao.zuocai.game

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * 小游戏的统一契约。宿主（GameView）只负责循环、尺寸、按键和结算，
 * 每个游戏的规则自己管。
 */
interface Game {
    val title: String
    val hint: String

    var score: Int
    var over: Boolean
    /** 顶部副标题，会覆盖 hint；空串表示用 hint */
    var label: String

    /** 按住时是否自动连发。跳一跳是「按住蓄力、松开起跳」，必须关掉 */
    val holdRepeat: Boolean get() = true

    /** 这局结束后能不能原地复活（分数保留） */
    fun canRevive(): Boolean = false

    /** 复活：清掉结束状态、把角色放回安全位置，分数不动 */
    fun revive() {}

    /** 按住 / 松开。需要蓄力的游戏用，其他游戏不用管 */
    fun onPress(action: String) {}

    fun onRelease(action: String) {}

    fun resize(w: Int, h: Int)
    fun reset()
    fun update(dtMs: Long)
    fun draw(canvas: Canvas)
    fun input(action: String)

    /**
     * 取走这一帧攒下的事件（音效/震动的数据通道）。
     * 游戏逻辑保持纯函数式、不碰 Android API，发声交给宿主。
     */
    fun drainEvents(): List<String> = emptyList()

    companion object {
        const val UP = "up"
        const val DOWN = "down"
        const val LEFT = "left"
        const val RIGHT = "right"
        const val A = "a"
        const val B = "b"

        // 事件名，宿主据此选音效与震动强度
        const val EV_SHOOT = "shoot"
        const val EV_BOOM = "boom"
        const val EV_HIT = "hit"
        const val EV_HURT = "hurt"
        const val EV_EAT = "eat"
        const val EV_LINE = "line"
        const val EV_DROP = "drop"
        const val EV_PUSH = "push"
        const val EV_WIN = "win"
        const val EV_OVER = "over"
    }
}

/**
 * 带事件队列的基类。游戏往队列里塞事件名，宿主每帧 drain 一次。
 * 队列上限防止单帧狂发事件把内存顶爆（比如一帧炸掉一排敌人）。
 */
abstract class EventGame : Game {
    private val ev = ArrayList<String>(8)

    override fun drainEvents(): List<String> {
        if (ev.isEmpty()) return emptyList()
        val out = ArrayList(ev)
        ev.clear()
        return out
    }

    protected fun emit(name: String) {
        if (ev.size < 24) ev.add(name)
    }
}

/** 五个游戏的颜色与绘制辅助，和网页版保持一致 */
object GameStyle {
    const val BG = 0xFFFBF7F2.toInt()
    const val GRID = 0xFFEFE5D9.toInt()
    const val INK = 0xFF2A211B.toInt()
    const val SUB = 0xFF8A7A6C.toInt()
    const val BRAND = 0xFFE2571E.toInt()
    const val BRAND2 = 0xFFF08A4B.toInt()
    const val SOFT = 0xFFF6E0CE.toInt()
    const val GOOD = 0xFF3E9A5C.toInt()
    const val WARN = 0xFFF2A03D.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()

    // 街机暗色系：游戏画布统一用暗底，与 App 的暖白界面形成「进入游戏」的仪式感
    const val DARK_BG = 0xFF17130F.toInt()
    const val DARK_PANEL = 0xFF241E18.toInt()
    const val DARK_GRID = 0xFF352C23.toInt()
    const val LIGHT = 0xFFF3E9DC.toInt()
    const val RED = 0xFFE9536F.toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()

    fun paint(color: Int, alpha: Int = 255, strokeWidth: Float = 0f): Paint {
        val p = if (strokeWidth > 0f) stroke else fill
        p.color = color
        p.alpha = alpha
        if (strokeWidth > 0f) p.strokeWidth = strokeWidth
        return p
    }

    /** 圆角矩形填充 */
    fun rr(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, color: Int, alpha: Int = 255) {
        val rad = minOf(r, w / 2f, h / 2f)
        rect.set(x, y, x + w, y + h)
        canvas.drawRoundRect(rect, rad, rad, paint(color, alpha))
    }

    fun circle(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, alpha: Int = 255) {
        canvas.drawCircle(cx, cy, r, paint(color, alpha))
    }

    fun line(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, width: Float) {
        canvas.drawLine(x1, y1, x2, y2, paint(color, strokeWidth = width))
    }

    fun text(canvas: Canvas, s: String, x: Float, y: Float, size: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText(s, x, y, p)
    }
}

/** 小工具 */
object G {
    fun rnd(n: Int): Int = (Math.random() * n).toInt().coerceIn(0, n - 1)
    fun clamp(v: Float, a: Float, b: Float): Float = if (v < a) a else if (v > b) b else v
}
