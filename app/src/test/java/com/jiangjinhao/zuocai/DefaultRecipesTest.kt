package com.jiangjinhao.zuocai

import com.jiangjinhao.zuocai.data.Backup
import com.jiangjinhao.zuocai.data.RecipeParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 内置默认菜谱包（assets/default_recipes.txt）的完整性检查。
 *
 * 这份文件同时被网页版 web/default-recipes.js 复用，格式一旦写坏，
 * 这里会先报出来，不用等装到手机上才发现列表是空的。
 */
class DefaultRecipesTest {

    private val text: String by lazy {
        File("src/main/assets/default_recipes.txt").readText(Charsets.UTF_8)
    }

    @Test
    fun everyBlockParses() {
        val blocks = Backup.split(text)
        assertTrue("默认菜谱包不能为空", blocks.isNotEmpty())

        val bad = blocks.filter { !RecipeParser.parse(it).ok }
        assertTrue("有 ${bad.size} 段解析失败", bad.isEmpty())
    }

    @Test
    fun keepsAllRecipes() {
        assertTrue("默认菜谱少于 40 道，可能被误删", Backup.split(text).size >= 40)
    }

    @Test
    fun namesAreUnique() {
        val names = Backup.split(text).map { RecipeParser.parse(it).name }
        assertEquals("菜名有重复，导入后会互相跳过", names.size, names.toSet().size)
    }

    @Test
    fun legacySamplesAreGone() {
        val names = Backup.split(text).map { RecipeParser.parse(it).name.trim() }.toSet()
        listOf("西红柿炒鸡蛋", "可乐鸡翅", "番茄蛋花汤").forEach {
            assertFalse("默认菜谱不该再包含旧演示菜「$it」", names.contains(it))
        }
    }

    @Test
    fun everyRecipeHasCoverAsset() {
        val coversDir = File("src/main/assets/covers")
        Backup.split(text).forEach { block ->
            val name = RecipeParser.parse(block).name
            val slug = Regex("@封面[：:]\\s*([A-Za-z0-9_-]+)")
                .find(block)?.groupValues?.get(1)
            assertNotNull("「$name」缺少 @封面 行", slug)
            assertTrue("「$name」的封面 ${slug}.jpg 不存在", File(coversDir, "$slug.jpg").isFile)
        }
    }

    @Test
    fun everyRecipeHasIngredientsAndSteps() {
        Backup.split(text).forEach { block ->
            val parsed = RecipeParser.parse(block)
            assertTrue("${parsed.name} 没有食材", parsed.ingredients.isNotEmpty())
            assertTrue("${parsed.name} 没有步骤", parsed.steps.isNotEmpty())
        }
    }
}
