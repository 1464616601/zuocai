package com.jiangjinhao.zuocai.data

import androidx.annotation.StringRes
import com.jiangjinhao.zuocai.R

/**
 * 菜谱分类。key 落库，labelRes 用于界面展示。
 */
enum class RecipeCategory(val key: String, @StringRes val labelRes: Int) {
    HOME("home", R.string.cat_home),
    SOUP("soup", R.string.cat_soup),
    STAPLE("staple", R.string.cat_staple),
    COLD("cold", R.string.cat_cold),
    DESSERT("dessert", R.string.cat_dessert),
    OTHER("other", R.string.cat_other);

    companion object {
        fun fromKey(key: String?): RecipeCategory =
            values().firstOrNull { it.key == key } ?: OTHER

        fun labelResOf(key: String?): Int = fromKey(key).labelRes

        /**
         * 从导出文本里的中文分类名反查 key，例如「汤羹」→ SOUP。
         * 硬编码是因为解析器拿不到 Context，而本 App 只有中文一种资源。
         */
        /** key → 中文名，导出 TXT 时用（解析器没有 Context，只能硬编码） */
        fun textOf(key: String?): String = TEXT[fromKey(key)] ?: "其他"

        fun fromLabel(label: String?): RecipeCategory? {
            val t = label?.trim().orEmpty()
            if (t.isEmpty()) return null
            return LABELS[t]
        }

        private val TEXT = mapOf(
            HOME to "家常菜", SOUP to "汤羹", STAPLE to "主食",
            COLD to "凉菜", DESSERT to "甜点", OTHER to "其他"
        )

        private val LABELS = mapOf(
            "家常菜" to HOME,
            "汤羹" to SOUP, "汤" to SOUP,
            "主食" to STAPLE,
            "凉菜" to COLD,
            "甜点" to DESSERT, "甜品" to DESSERT,
            "其他" to OTHER
        )
    }
}

/**
 * 难度：1 简单 / 2 中等 / 3 有点难
 */
object Difficulty {
    const val EASY = 1
    const val MEDIUM = 2
    const val HARD = 3

    @StringRes
    fun labelRes(level: Int): Int = when (level) {
        MEDIUM -> R.string.difficulty_medium
        HARD -> R.string.difficulty_hard
        else -> R.string.difficulty_easy
    }

    fun normalize(level: Int): Int = level.coerceIn(EASY, HARD)

    /** 导出 TXT 时用的中文名 */
    fun textOf(level: Int): String = when (normalize(level)) {
        MEDIUM -> "中等"
        HARD -> "有点难"
        else -> "简单"
    }
}

/**
 * 排序方式
 */
enum class SortOrder {
    /** 最近更新 */
    RECENT,

    /** 按菜名 */
    NAME,

    /** 使用最多：打开次数 + 搜索命中次数 */
    USAGE
}
