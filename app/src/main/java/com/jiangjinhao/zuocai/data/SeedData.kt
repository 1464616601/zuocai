package com.jiangjinhao.zuocai.data

import android.content.Context
import com.jiangjinhao.zuocai.util.ImageStore
import java.io.File

/**
 * 内置默认菜谱。
 *
 * 菜谱文本放在 `assets/default_recipes.txt`，格式和「导入 TXT」完全一致，所以这里直接复用
 * [Backup.import] 解析入库；封面图放在 `assets/covers/`，每条菜谱用 `@封面：cNN` 指定。
 *
 * [ImageStore] 只认 `filesDir/images` 下的文件，所以首次启动要把用到的图从 assets 复制过去，
 * 之后列表页、详情页、编辑页都能按普通封面图处理，渲染逻辑不用改。
 *
 * 导入按菜名去重：老用户升级后再跑一次也只会补上缺的，不会产生重复项。
 */
object SeedData {

    private const val ASSET = "default_recipes.txt"
    private const val COVER_DIR = "covers"

    private val RE_COVER = Regex("@封面[：:]\\s*([A-Za-z0-9_-]+)")

    /**
     * 导入内置菜谱并挂上封面。
     *
     * @return 实际被补上封面的菜谱条数
     */
    fun insert(context: Context, db: RecipeDb): Int {
        val text = runCatching {
            context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrNull() ?: return 0

        // 菜名 → 封面文件名；同时把图复制到 filesDir/images
        val covers = mutableMapOf<String, String>()
        Backup.split(text).forEach { block ->
            val slug = RE_COVER.find(block)?.groupValues?.get(1) ?: return@forEach
            val name = RecipeParser.parse(block).name.trim()
            if (name.isEmpty()) return@forEach
            val fileName = "$slug.jpg"
            covers[name] = fileName
            copyCover(context, slug, fileName)
        }

        Backup.import(db, text)
        return applyCovers(db, covers)
    }

    /**
     * 给库里同名菜谱补封面。
     *
     * 新导入的菜谱和用户早就导入过的菜谱都会走到这里，所以首次升级安装也能看到图；
     * 用户自己换过封面的保持原样，不覆盖。
     */
    private fun applyCovers(db: RecipeDb, covers: Map<String, String>): Int {
        var applied = 0
        db.list().forEach { recipe ->
            val want = covers[recipe.name.trim()] ?: return@forEach
            if (recipe.cover == want) return@forEach
            db.setCover(recipe.id, want)
            applied++
        }
        return applied
    }

    private fun copyCover(context: Context, slug: String, fileName: String) {
        val target = File(ImageStore.imagesDir(context), fileName)
        if (target.exists() && target.length() > 0) return
        runCatching {
            context.assets.open("$COVER_DIR/$slug.jpg").use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
        }.onFailure {
            if (target.exists()) target.delete()
        }
    }
}
