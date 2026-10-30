package com.jiangjinhao.zuocai.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 一条食材：名称 + 用量
 */
data class Ingredient(
    var name: String = "",
    var amount: String = ""
) {
    val isEmpty: Boolean
        get() = name.isBlank() && amount.isBlank()
}

/**
 * 同一道菜的另一种做法。
 *
 * 典型场景是「手头缺料」：比如没有大蒜，AI 给一版替代方案。
 * 封面、分类、时长这些属于菜谱本身，变体只改描述、食材和步骤，
 * 所以点进同一道菜可以来回切换看不同做法。
 */
data class Variant(
    var label: String = "",
    var description: String = "",
    var ingredients: MutableList<Ingredient> = mutableListOf(),
    var steps: MutableList<String> = mutableListOf(),
    var createdAt: Long = 0L
) {
    fun copyDeep(): Variant = copy(
        ingredients = ingredients.map { it.copy() }.toMutableList(),
        steps = steps.toMutableList()
    )

    companion object {
        fun parse(json: String?): List<Variant> {
            if (json.isNullOrBlank()) return emptyList()
            val out = mutableListOf<Variant>()
            runCatching {
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val ings = mutableListOf<Ingredient>()
                    o.optJSONArray("ing")?.let { ia ->
                        for (j in 0 until ia.length()) {
                            val io = ia.optJSONObject(j) ?: continue
                            ings.add(Ingredient(io.optString("n"), io.optString("a")))
                        }
                    }
                    val steps = mutableListOf<String>()
                    o.optJSONArray("steps")?.let { sa ->
                        for (j in 0 until sa.length()) {
                            val s = sa.optString(j, "")
                            if (s.isNotBlank()) steps.add(s)
                        }
                    }
                    out.add(
                        Variant(
                            label = o.optString("label"),
                            description = o.optString("desc"),
                            ingredients = ings,
                            steps = steps,
                            createdAt = o.optLong("at")
                        )
                    )
                }
            }
            return out
        }
    }
}

/**
 * 菜谱实体。ingredients / steps / variants 以 JSON 形式存在同一条记录里，
 * 避免多表联查，读写简单且足够快（单机菜谱量级很小）。
 */
data class Recipe(
    var id: Long = 0L,
    var name: String = "",
    var category: String = RecipeCategory.HOME.key,
    var cover: String? = null,
    var description: String = "",
    var servings: Int = 2,
    var minutes: Int = 0,
    var difficulty: Int = Difficulty.EASY,
    var ingredients: MutableList<Ingredient> = mutableListOf(),
    var steps: MutableList<String> = mutableListOf(),
    var createdAt: Long = 0L,
    var updatedAt: Long = 0L,
    var variants: MutableList<Variant> = mutableListOf(),
    var pinned: Boolean = false
) {

    /** 搜索用的全文（菜名 + 描述 + 食材名） */
    val searchBlob: String
        get() = buildString {
            append(name).append(' ')
            append(description).append(' ')
            ingredients.forEach { append(it.name).append(' ') }
        }

    fun variantsJson(): String {
        val arr = JSONArray()
        variants.forEach { v ->
            val ing = JSONArray()
            v.ingredients.forEach { item ->
                if (item.isEmpty) return@forEach
                ing.put(JSONObject().apply {
                    put("n", item.name.trim())
                    put("a", item.amount.trim())
                })
            }
            val st = JSONArray()
            v.steps.forEach { s -> if (s.isNotBlank()) st.put(s.trim()) }
            arr.put(JSONObject().apply {
                put("label", v.label)
                put("desc", v.description)
                put("ing", ing)
                put("steps", st)
                put("at", v.createdAt)
            })
        }
        return arr.toString()
    }

    fun ingredientsJson(): String {
        val arr = JSONArray()
        ingredients.forEach { ing ->
            if (ing.isEmpty) return@forEach
            arr.put(
                JSONObject().apply {
                    put("n", ing.name.trim())
                    put("a", ing.amount.trim())
                }
            )
        }
        return arr.toString()
    }

    fun stepsJson(): String {
        val arr = JSONArray()
        steps.forEach { s -> if (s.isNotBlank()) arr.put(s.trim()) }
        return arr.toString()
    }

    fun copyForEdit(): Recipe = copy(
        ingredients = ingredients.map { it.copy() }.toMutableList(),
        steps = steps.toMutableList(),
        variants = variants.map { it.copyDeep() }.toMutableList()
    )

    companion object {

        fun parseIngredients(json: String?): MutableList<Ingredient> {
            val out = mutableListOf<Ingredient>()
            if (json.isNullOrBlank()) return out
            runCatching {
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    out.add(Ingredient(o.optString("n"), o.optString("a")))
                }
            }
            return out
        }

        fun parseSteps(json: String?): MutableList<String> {
            val out = mutableListOf<String>()
            if (json.isNullOrBlank()) return out
            runCatching {
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i, "")
                    if (s.isNotBlank()) out.add(s)
                }
            }
            return out
        }
    }
}
