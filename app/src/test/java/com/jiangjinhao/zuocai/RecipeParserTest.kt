package com.jiangjinhao.zuocai

import com.jiangjinhao.zuocai.data.RecipeParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 解析器回归测试。用例与网页版 web/test-parser.cjs 对齐，
 * 保证两端对同一段文本产出相同的结构。
 */
class RecipeParserTest {

    private val sample = """
        # 花菜炒肉
        ### 食材
        花菜半颗、瘦肉 200g、蒜 3 瓣、干辣椒可选、生抽 2 勺、蚝油 1 勺、盐、少许白糖、食用油
        ### 步骤
        1. 花菜掰小朵，盐水浸泡 5 分钟，开水下锅焯**30 秒**捞出沥干（去生味，缩短炒制时间）；肉切片，用生抽 + 淀粉抓腌 10 分钟；蒜切片。
        2. 热锅放油，肉片下锅大火炒至变色盛出。
        3. 留底油，蒜片、干辣椒爆香，倒入花菜中火翻炒 2 分钟。
        4. 倒回肉片，放生抽、蚝油、一点点白糖提鲜，加盐调味，大火翻炒均匀就可以出锅。
        >
        > 小贴士：焯水别太久，不然花菜软塌不清脆；喜欢干香口感可以不焯水，多炒一会儿。
    """.trimIndent()

    @Test
    fun parse_fullSample() {
        val p = RecipeParser.parse(sample)

        assertEquals("花菜炒肉", p.name)
        assertEquals(9, p.ingredients.size)
        assertEquals(4, p.steps.size)
        assertTrue(p.ok)
        assertTrue(p.tips.startsWith("焯水别太久"))

        // 抽查几个食材拆分
        assertEquals("花菜", p.ingredients[0].name)
        assertEquals("半颗", p.ingredients[0].amount)
        assertEquals("瘦肉", p.ingredients[1].name)
        assertEquals("200g", p.ingredients[1].amount)
        assertEquals("干辣椒", p.ingredients[3].name)
        assertEquals("可选", p.ingredients[3].amount)
        assertEquals("盐", p.ingredients[6].name)
        assertEquals("", p.ingredients[6].amount)
        assertEquals("白糖", p.ingredients[7].name)
        assertEquals("少许", p.ingredients[7].amount)

        // Markdown 强调符号应被清掉
        assertTrue(p.steps[0].contains("焯30 秒"))
        assertTrue(!p.steps[0].contains("**"))
    }

    @Test
    fun parse_plainTextWithoutHeadings() {
        val p = RecipeParser.parse(
            """
            青椒炒蛋
            鸡蛋2个、青椒1个、盐适量
            1、打散鸡蛋
            2、炒熟
            """.trimIndent()
        )
        assertEquals("青椒炒蛋", p.name)
        assertEquals(3, p.ingredients.size)
        assertEquals(2, p.steps.size)
        assertTrue(p.ok)
    }

    @Test
    fun parse_colonSeparated() {
        val p = RecipeParser.parse(
            """
            菜名：土豆丝
            食材：土豆 2个、醋 1勺
            步骤：
            1. 切丝
            2. 炒
            """.trimIndent()
        )
        assertEquals("土豆丝", p.name)
        assertEquals(2, p.ingredients.size)
        assertEquals(2, p.steps.size)
    }

    @Test
    fun parse_bulletList() {
        val p = RecipeParser.parse(
            """
            # 番茄炒蛋
            ## 材料
            - 番茄 2个
            - 鸡蛋 3个
            ## 做法
            - 番茄切块
            - 打蛋液
            - 一起炒
            """.trimIndent()
        )
        assertEquals("番茄炒蛋", p.name)
        assertEquals(2, p.ingredients.size)
        assertEquals(3, p.steps.size)
    }

    @Test
    fun parse_compactHeadings() {
        val p = RecipeParser.parse(
            """
            # 蒜蓉西兰花
            食材
            西兰花一颗、蒜末适量、盐少许
            步骤
            1. 西兰花焯水
            2. 爆香蒜末翻炒
            """.trimIndent()
        )
        assertEquals("蒜蓉西兰花", p.name)
        assertEquals(3, p.ingredients.size)
        assertEquals(2, p.steps.size)
    }

    @Test
    fun parse_boldInlineHeadings() {
        val p = RecipeParser.parse(
            """
            **红烧肉**
            **用料**：五花肉500g、冰糖30g、老抽2勺
            **做法**：
            1. 五花肉切块焯水
            2. 炒糖色
            3. 加水炖40分钟
            """.trimIndent()
        )
        assertEquals("红烧肉", p.name)
        assertEquals(3, p.ingredients.size)
        assertEquals(3, p.steps.size)
    }

    @Test
    fun parse_placeholderHeading() {
        // 模型把模板里的「# 菜名」原样输出时，应取下一行作为真实菜名
        val p = RecipeParser.parse(
            """
            # 菜名
            蒜蓉油麦菜

            ### 食材
            - 油麦菜 500克、大蒜 3瓣、盐 适量

            ### 步骤
            1. 洗净切段
            2. 蒜蓉爆香翻炒
            > 小贴士：蒜蓉别炒糊
            """.trimIndent()
        )
        assertEquals("蒜蓉油麦菜", p.name)
        assertEquals(3, p.ingredients.size)
        assertEquals(2, p.steps.size)
        assertTrue(p.ok)
    }

    @Test
    fun parse_emptyReturnsNotOk() {
        val p = RecipeParser.parse("")
        assertTrue(!p.ok)
        assertEquals("", p.name)
    }

    @Test
    fun guessCategory_works() {
        val soup = RecipeParser.parse("# 番茄蛋花汤\n### 食材\n番茄 1个\n### 步骤\n1. 煮")
        assertEquals("soup", soup.category)

        val staple = RecipeParser.parse("# 蛋炒饭\n### 食材\n米饭 1碗\n### 步骤\n1. 炒")
        assertEquals("staple", staple.category)

        val home = RecipeParser.parse("# 青椒炒肉\n### 食材\n青椒 2个\n### 步骤\n1. 炒")
        assertEquals("home", home.category)
    }

    @Test
    fun stringify_roundTrip() {
        val p = RecipeParser.parse(sample)
        val recipe = com.jiangjinhao.zuocai.data.Recipe(
            name = p.name,
            ingredients = p.ingredients.toMutableList(),
            steps = p.steps.toMutableList(),
            description = p.tips
        )
        val text = RecipeParser.stringify(recipe)
        val again = RecipeParser.parse(text)

        assertEquals(p.name, again.name)
        assertEquals(p.ingredients.size, again.ingredients.size)
        assertEquals(p.steps.size, again.steps.size)
        // 关键食材与用量不能丢
        assertEquals(p.ingredients[1].name, again.ingredients[1].name)
        assertEquals(p.ingredients[1].amount, again.ingredients[1].amount)
    }
}
