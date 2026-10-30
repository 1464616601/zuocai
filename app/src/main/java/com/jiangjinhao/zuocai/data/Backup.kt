package com.jiangjinhao.zuocai.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 菜谱备份：把全部菜谱导出成一个 TXT，或从 TXT 批量导入。
 *
 * 用纯文本是为了跨设备迁移时不依赖任何工具——微信文件传输、网盘、邮件都能带走，
 * 出问题了用记事本也能改。代价是**封面图不会一起导出**（TXT 存不了二进制）。
 *
 * 格式：每道菜谱是一段 Markdown（和「粘贴文本建菜谱」用的格式完全一致），
 * 段与段之间用 `==========  菜谱 N  ==========` 分隔，解析器直接复用。
 */
object Backup {

    /** 分隔行：4 个以上等号独占一行。菜谱正文里基本不会出现，不会误拆 */
    private val RE_SPLIT = Regex("^={4,}.*$", RegexOption.MULTILINE)

    data class ImportResult(
        val total: Int = 0,
        val added: Int = 0,
        val skipped: Int = 0,
        val failed: Int = 0
    )

    /** 把所有菜谱拼成一个 TXT */
    fun export(recipes: List<Recipe>): String {
        if (recipes.isEmpty()) return ""

        val sb = StringBuilder()
        recipes.forEachIndexed { index, recipe ->
            if (index > 0) sb.append("\n\n")
            sb.append("==========  菜谱 ").append(index + 1).append("  ==========\n\n")
            sb.append(RecipeParser.stringify(recipe))
        }
        return sb.toString()
    }

    /** 建议的导出文件名，例如 我的菜谱-20261029-1950.txt */
    fun suggestFileName(now: Long = System.currentTimeMillis()): String {
        val fmt = SimpleDateFormat("yyyyMMdd-HHmm", Locale.CHINA)
        return "我的菜谱-${fmt.format(Date(now))}.txt"
    }

    /**
     * 解析 TXT 并批量入库。
     *
     * 同名菜谱会被跳过（避免重复导入产生一堆重复项），
     * 解析不出菜名的段落计入 failed。
     */
    fun import(db: RecipeDb, text: String): ImportResult {
        val blocks = split(text)
        if (blocks.isEmpty()) return ImportResult()

        val existing = db.list().map { it.name.trim() }.toHashSet()
        var added = 0
        var skipped = 0
        var failed = 0

        for (block in blocks) {
            val parsed = RecipeParser.parse(block)
            if (!parsed.ok || parsed.name.isBlank()) {
                failed++
                continue
            }
            if (!existing.add(parsed.name.trim())) {
                skipped++
                continue
            }

            val recipe = Recipe(
                name = parsed.name,
                category = parsed.category,
                description = parsed.tips,
                servings = parsed.servings,
                minutes = parsed.minutes,
                difficulty = Difficulty.normalize(parsed.difficulty)
            )
            parsed.ingredients.forEach { recipe.ingredients.add(Ingredient(it.name, it.amount)) }
            parsed.steps.forEach { recipe.steps.add(it) }

            if (db.insert(recipe) > 0) added++ else failed++
        }

        return ImportResult(
            total = blocks.size,
            added = added,
            skipped = skipped,
            failed = failed
        )
    }

    /** 按分隔行切成若干段，过滤空段 */
    fun split(text: String): List<String> {
        val normalized = text.replace("\r\n", "\n").replace("\r", "\n")
        return RE_SPLIT.split(normalized)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /**
     * 把文件字节解成文本。
     *
     * 用户的 TXT 可能来自 Windows 记事本——那可能是带 BOM 的 UTF-8，
     * 也可能是 GBK（另存为 ANSI）。按 UTF-8 硬解会变乱码或让第一行多出 BOM 字符，
     * 导致菜名解析带脏字符，所以先判 BOM，再严格试 UTF-8，最后回落 GBK。
     */
    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }

        val strictUtf8 = runCatching {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes))
                .toString()
        }.getOrNull()

        return strictUtf8 ?: runCatching {
            String(bytes, charset("GBK"))
        }.getOrDefault(String(bytes, Charsets.UTF_8))
    }
}
