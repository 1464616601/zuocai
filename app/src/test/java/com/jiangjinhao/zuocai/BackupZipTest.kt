package com.jiangjinhao.zuocai

import com.jiangjinhao.zuocai.data.Backup
import com.jiangjinhao.zuocai.data.Ingredient
import com.jiangjinhao.zuocai.data.Recipe
import com.jiangjinhao.zuocai.data.Variant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.ZipInputStream

/**
 * zip 备份包的结构：菜谱文本、做法变体、封面都得在包里。
 *
 * 导入端的变体回填要碰 RecipeDb（依赖 Android Context），JVM 测不了；
 * 这里守住"导出这一半"——包里少了什么，导入时就是丢什么。
 */
class BackupZipTest {

    private fun sample(): Recipe = Recipe(
        name = "辣椒炒肉",
        description = "湘菜代表",
        pinned = true,
        cover = "c01.jpg"
    ).apply {
        ingredients.add(Ingredient("猪肉", "300g"))
        ingredients.add(Ingredient("辣椒", "5个"))
        steps.add("热锅下肉")
        variants.add(
            Variant(
                label = "没有大蒜",
                description = "用洋葱替代",
                ingredients = mutableListOf(Ingredient("洋葱", "半个")),
                steps = mutableListOf("洋葱爆香后下肉"),
                createdAt = 1700000000L
            )
        )
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val out = HashMap<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                if (!e.isDirectory) out[e.name] = zis.readBytes()
                e = zis.nextEntry
            }
        }
        return out
    }

    @Test
    fun zipPacksTextMetaAndCovers() {
        val zip = Backup.buildZip(listOf(sample())) { name ->
            if (name == "c01.jpg") byteArrayOf(1, 2, 3) else null
        }
        val entries = unzip(zip)

        assertTrue("要有菜谱文本", entries.containsKey("recipes.txt"))
        assertTrue("要有变体/封面映射", entries.containsKey("backup.json"))
        assertTrue("封面图要打进包里", entries.containsKey("covers/c01.jpg"))
        assertEquals("封面字节要原样打包", 3, entries["covers/c01.jpg"]!!.size)

        val text = entries["recipes.txt"]!!.toString(Charsets.UTF_8)
        assertTrue("正文要能被解析出菜名", text.contains("# 辣椒炒肉"))

        val meta = entries["backup.json"]!!.toString(Charsets.UTF_8)
        assertTrue("变体要在 backup.json 里", meta.contains("没有大蒜"))
        assertTrue("置顶标记要备份", meta.contains("\"pinned\":true"))
        assertTrue("封面映射要按菜名关联", meta.contains("\"cover\":\"c01.jpg\""))
    }

    @Test
    fun coverResolverMissKeepsZipValid() {
        // 封面文件丢了（比如用户清过图片目录）也不能让导出失败
        val zip = Backup.buildZip(listOf(sample())) { null }
        val entries = unzip(zip)
        assertTrue(entries.containsKey("recipes.txt"))
        assertTrue(!entries.containsKey("covers/c01.jpg"))
    }

    @Test
    fun isZipChecksPkHeader() {
        val zip = Backup.buildZip(listOf(sample())) { null }
        assertTrue(Backup.isZip(zip))
        assertTrue(!Backup.isZip("菜谱文本".toByteArray()))
        assertTrue(!Backup.isZip(ByteArray(0)))
    }
}
