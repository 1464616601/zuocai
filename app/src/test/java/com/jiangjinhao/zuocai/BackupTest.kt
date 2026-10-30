package com.jiangjinhao.zuocai

import com.jiangjinhao.zuocai.data.Backup
import com.jiangjinhao.zuocai.data.Difficulty
import com.jiangjinhao.zuocai.data.Ingredient
import com.jiangjinhao.zuocai.data.Recipe
import com.jiangjinhao.zuocai.data.RecipeCategory
import com.jiangjinhao.zuocai.data.RecipeParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导出/导入的往返测试。
 *
 * 重点保证：导出成 TXT 再解析回来，菜名、食材、步骤、分类、时长、份量、难度都不丢。
 * 这是跨手机迁移的唯一依据，错一个字段用户就会丢数据。
 */
class BackupTest {

    private fun sampleRecipe() = Recipe(
        name = "红烧肉",
        category = RecipeCategory.HOME.key,
        description = "冰糖先炒糖色，肉才红亮",
        servings = 4,
        minutes = 45,
        difficulty = Difficulty.MEDIUM
    ).apply {
        ingredients.add(Ingredient("五花肉", "500g"))
        ingredients.add(Ingredient("冰糖", "30g"))
        ingredients.add(Ingredient("生抽", "2 勺"))
        steps.add("五花肉切块，冷水下锅焯水去腥。")
        steps.add("冰糖小火炒出糖色，下肉翻炒上色。")
        steps.add("加水没过肉，小火炖 40 分钟收汁。")
    }

    private fun soupRecipe() = Recipe(
        name = "番茄蛋花汤",
        category = RecipeCategory.SOUP.key,
        description = "",
        servings = 2,
        minutes = 10,
        difficulty = Difficulty.EASY
    ).apply {
        ingredients.add(Ingredient("番茄", "2 个"))
        ingredients.add(Ingredient("鸡蛋", "2 个"))
        steps.add("番茄切块炒出汁，加水煮开。")
        steps.add("淋入蛋液，加盐调味。")
    }

    @Test
    fun export_producesOneBlockPerRecipe() {
        val text = Backup.export(listOf(sampleRecipe(), soupRecipe()))
        val blocks = Backup.split(text)
        assertEquals(2, blocks.size)
        assertTrue(blocks[0].contains("红烧肉"))
        assertTrue(blocks[1].contains("番茄蛋花汤"))
    }

    @Test
    fun export_emptyListReturnsEmpty() {
        assertEquals("", Backup.export(emptyList()))
        assertEquals(0, Backup.split("").size)
    }

    @Test
    fun roundTrip_keepsAllFields() {
        val origin = sampleRecipe()
        val text = Backup.export(listOf(origin))
        val blocks = Backup.split(text)
        assertEquals(1, blocks.size)

        val parsed = RecipeParser.parse(blocks[0])
        assertTrue(parsed.ok)
        assertEquals("红烧肉", parsed.name)
        assertEquals(RecipeCategory.HOME.key, parsed.category)
        assertEquals(4, parsed.servings)
        assertEquals(45, parsed.minutes)
        assertEquals(Difficulty.MEDIUM, parsed.difficulty)
        assertEquals("冰糖先炒糖色，肉才红亮", parsed.tips)
        assertEquals(3, parsed.ingredients.size)
        assertEquals("五花肉", parsed.ingredients[0].name)
        assertEquals("500g", parsed.ingredients[0].amount)
        assertEquals(3, parsed.steps.size)
        assertTrue(parsed.steps[0].contains("冷水下锅"))
    }

    @Test
    fun roundTrip_keepsCategoryOfSoup() {
        val text = Backup.export(listOf(soupRecipe()))
        val parsed = RecipeParser.parse(Backup.split(text).first())
        assertEquals(RecipeCategory.SOUP.key, parsed.category)
        assertEquals(10, parsed.minutes)
        assertEquals(2, parsed.servings)
        assertEquals(Difficulty.EASY, parsed.difficulty)
    }

    @Test
    fun roundTrip_survivesCommasInSteps() {
        // 步骤里的中文逗号、顿号不能被当成食材分隔符
        val r = Recipe(name = "测试菜", category = RecipeCategory.OTHER.key).apply {
            ingredients.add(Ingredient("土豆", "2 个"))
            steps.add("切丝，泡水，沥干；再下锅。")
        }
        val parsed = RecipeParser.parse(Backup.split(Backup.export(listOf(r))).first())
        assertEquals(1, parsed.steps.size)
        assertEquals("切丝，泡水，沥干；再下锅。", parsed.steps[0])
    }

    @Test
    fun split_handlesPlainEqualsLine() {
        // 用户手工整理的文本，用 ==== 分隔也应能拆
        val text = """
            # 菜一
            ### 食材
            土豆 2 个
            ### 步骤
            1. 切丝

            ====

            # 菜二
            ### 食材
            白菜 1 颗
            ### 步骤
            1. 洗净
        """.trimIndent()

        val blocks = Backup.split(text)
        assertEquals(2, blocks.size)
        assertEquals("菜一", RecipeParser.parse(blocks[0]).name)
        assertEquals("菜二", RecipeParser.parse(blocks[1]).name)
    }

    @Test
    fun decode_stripsUtf8Bom() {
        // Windows 记事本保存的 UTF-8 带 BOM，不剥掉会让菜名首字符变成脏字符
        val body = "# 番茄炒蛋\n### 食材\n番茄 2 个\n### 步骤\n1. 炒"
        val withBom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            body.toByteArray(Charsets.UTF_8)

        val text = Backup.decode(withBom)
        assertTrue(text.startsWith("# 番茄炒蛋"))
        assertEquals("番茄炒蛋", RecipeParser.parse(text).name)
    }

    @Test
    fun decode_readsGbk() {
        // 记事本「另存为 ANSI」是 GBK，按 UTF-8 硬解会全是乱码
        val body = "# 蒜蓉油麦菜\n### 食材\n油麦菜 1 把\n### 步骤\n1. 蒜末爆香"
        val gbkBytes = body.toByteArray(charset("GBK"))

        val text = Backup.decode(gbkBytes)
        assertEquals("蒜蓉油麦菜", RecipeParser.parse(text).name)
    }

    @Test
    fun export_suggestFileNameHasTxtSuffix() {
        val name = Backup.suggestFileName(0L)
        assertTrue(name.endsWith(".txt"))
        assertNotNull(name)
    }
}
