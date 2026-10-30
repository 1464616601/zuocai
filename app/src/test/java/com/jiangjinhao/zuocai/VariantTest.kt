package com.jiangjinhao.zuocai

import com.jiangjinhao.zuocai.data.Ingredient
import com.jiangjinhao.zuocai.data.Recipe
import com.jiangjinhao.zuocai.data.Variant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 做法变体的 JSON 往返。
 *
 * 变体是整条塞进 recipes 表的一个 TEXT 列里的，序列化漏字段不会报错，
 * 只会在用户切换做法时内容凭空少一半，所以这里把字段逐个咬住。
 */
class VariantTest {

    private fun sample() = Recipe(
        name = "辣椒炒肉",
        variants = mutableListOf(
            Variant(
                label = "没有大蒜",
                description = "用洋葱替代蒜香",
                ingredients = mutableListOf(
                    Ingredient("猪肉", "300g"),
                    Ingredient("洋葱", "半个")
                ),
                steps = mutableListOf("肉片腌制", "洋葱爆香后下肉"),
                createdAt = 1700000000L
            )
        )
    )

    @Test
    fun roundTripKeepsEveryField() {
        val json = sample().variantsJson()
        val parsed = Variant.parse(json)

        assertEquals(1, parsed.size)
        val v = parsed.first()
        assertEquals("没有大蒜", v.label)
        assertEquals("用洋葱替代蒜香", v.description)
        assertEquals(2, v.ingredients.size)
        assertEquals("洋葱", v.ingredients[1].name)
        assertEquals("半个", v.ingredients[1].amount)
        assertEquals(listOf("肉片腌制", "洋葱爆香后下肉"), v.steps)
        assertEquals(1700000000L, v.createdAt)
    }

    @Test
    fun emptyVariantsRoundTrip() {
        val json = Recipe(name = "x").variantsJson()
        assertTrue(Variant.parse(json).isEmpty())
    }

    @Test
    fun brokenJsonDoesNotThrow() {
        assertTrue(Variant.parse("not json").isEmpty())
        assertTrue(Variant.parse(null).isEmpty())
        assertTrue(Variant.parse("").isEmpty())
    }

    @Test
    fun copyForEditKeepsVariantsIndependent() {
        val original = sample()
        val copy = original.copyForEdit()

        assertEquals(1, copy.variants.size)
        copy.variants[0].steps.add("多了一步")
        // 改副本不能影响原对象
        assertEquals(2, original.variants[0].steps.size)
    }
}
