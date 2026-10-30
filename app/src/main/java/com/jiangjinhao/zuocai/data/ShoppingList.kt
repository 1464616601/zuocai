package com.jiangjinhao.zuocai.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 买菜清单：勾选几道菜 → 食材合并成一张可勾选的清单。
 *
 * 数据很小（几十个文本项），直接存 SharedPreferences 里的一个 JSON 数组，
 * 不值得为它开一张表。
 */
object ShoppingList {

    data class Item(var name: String, var amount: String, var checked: Boolean)

    private const val PREFS = "zuocai_shopping"
    private const val KEY = "items"

    fun load(context: Context): MutableList<Item> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return mutableListOf()
        val out = mutableListOf<Item>()
        runCatching {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(Item(o.optString("n"), o.optString("a"), o.optBoolean("c")))
            }
        }
        return out
    }

    fun save(context: Context, items: List<Item>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject().put("n", it.name).put("a", it.amount).put("c", it.checked)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    /**
     * 把几道菜的食材并进清单，返回实际新增/合并的条数。
     *
     * 同名食材合并：用量相同就只留一条，不同就并排列出（「半根 / 200g」）——
     * 中文用量没法可靠地做加法，并排是最不容易出错的做法。
     */
    fun addRecipes(context: Context, recipes: List<Recipe>): Int {
        val items = load(context)
        var changed = 0
        recipes.forEach { recipe ->
            recipe.ingredients.filterNot { it.isEmpty }.forEach { ing ->
                val key = ing.name.trim()
                if (key.isEmpty()) return@forEach
                val exist = items.firstOrNull { it.name.equals(key, ignoreCase = true) }
                val amount = ing.amount.trim()
                when {
                    exist == null -> {
                        items.add(Item(key, amount, false))
                        changed++
                    }
                    amount.isNotBlank() && amount != exist.amount &&
                        !exist.amount.split(" / ").contains(amount) -> {
                        exist.amount = listOf(exist.amount, amount)
                            .filter { it.isNotBlank() }.joinToString(" / ")
                        changed++
                    }
                }
            }
        }
        save(context, items)
        return changed
    }
}
