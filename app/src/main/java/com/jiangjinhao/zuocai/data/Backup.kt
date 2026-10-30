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

    /** zip 版备份的文件名 */
    fun suggestZipName(now: Long = System.currentTimeMillis()): String {
        val fmt = SimpleDateFormat("yyyyMMdd-HHmm", Locale.CHINA)
        return "我的菜谱-${fmt.format(Date(now))}.zip"
    }

    /** 文件头是不是 zip（PK） */
    fun isZip(bytes: ByteArray): Boolean =
        bytes.size > 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()

    /**
     * 打完整备份包：菜谱文本 + 做法变体/置顶/封面映射（backup.json）+ 封面图。
     *
     * TXT 格式存不了封面和变体，所以它们单独放在包里：
     * backup.json 按「菜名」关联，导入时对号入座（同名的老菜谱也能补上）。
     *
     * @param coverBytes 封面文件名 → 字节。由调用方提供（Backup 不碰存储层）
     */
    fun buildZip(recipes: List<Recipe>, coverBytes: (String) -> ByteArray?): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("recipes.txt"))
            zip.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            zip.write(export(recipes).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            val covers = LinkedHashMap<String, ByteArray>()
            val meta = org.json.JSONArray()
            recipes.forEach { r ->
                val entry = org.json.JSONObject()
                entry.put("name", r.name)
                entry.put("pinned", r.pinned)
                if (!r.cover.isNullOrBlank()) {
                    entry.put("cover", r.cover)
                    covers[r.cover!!] = covers[r.cover!!] ?: coverBytes(r.cover!!) ?: ByteArray(0)
                }
                if (r.variants.isNotEmpty()) {
                    val arr = org.json.JSONArray()
                    r.variants.forEach { v ->
                        arr.put(
                            org.json.JSONObject()
                                .put("label", v.label)
                                .put("desc", v.description)
                                .put("ing", ingredientsJson(v.ingredients))
                                .put("steps", stepsJson(v.steps))
                                .put("at", v.createdAt)
                        )
                    }
                    entry.put("variants", arr)
                }
                meta.put(entry)
            }
            zip.putNextEntry(java.util.zip.ZipEntry("backup.json"))
            zip.write(meta.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            covers.forEach { (name, bytes) ->
                if (bytes.isEmpty()) return@forEach
                zip.putNextEntry(java.util.zip.ZipEntry("covers/$name"))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun ingredientsJson(ings: List<Ingredient>): org.json.JSONArray {
        val arr = org.json.JSONArray()
        ings.forEach {
            if (it.isEmpty) return@forEach
            arr.put(org.json.JSONObject().put("n", it.name.trim()).put("a", it.amount.trim()))
        }
        return arr
    }

    private fun stepsJson(steps: List<String>): org.json.JSONArray {
        val arr = org.json.JSONArray()
        steps.forEach { if (it.isNotBlank()) arr.put(it.trim()) }
        return arr
    }

    class ZipImport(
        val result: ImportResult,
        val variantsApplied: Int,
        val coversRestored: Int
    )

    /**
     * 解 zip 备份并还原。
     *
     * @param saveCover 落一张封面图（文件名 → 字节），返回是否成功。由调用方提供存储实现
     */
    fun importZip(
        db: RecipeDb,
        bytes: ByteArray,
        saveCover: (String, ByteArray) -> Boolean
    ): ZipImport {
        var text = ""
        val metas = mutableListOf<org.json.JSONObject>()
        val coverFiles = HashMap<String, ByteArray>()

        java.util.zip.ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                // 只取文件名，防路径穿越（../..这类）
                val safe = entry.name.substringAfterLast('/').substringAfterLast('\\')
                val content = if (entry.isDirectory) ByteArray(0) else zip.readBytes()
                when {
                    entry.name == "recipes.txt" || (safe.endsWith(".txt") && text.isEmpty()) ->
                        text = decode(content)
                    entry.name == "backup.json" ->
                        runCatching {
                            val arr = org.json.JSONArray(String(content, Charsets.UTF_8))
                            for (i in 0 until arr.length()) {
                                arr.optJSONObject(i)?.let { metas.add(it) }
                            }
                        }
                    entry.name.startsWith("covers/") && safe.isNotBlank() ->
                        coverFiles[safe] = content
                }
                entry = zip.nextEntry
            }
        }

        val result = import(db, text)

        // 变体/置顶/封面按菜名对号入座——已存在的同名菜谱也能补上
        val byName = db.list().associateBy { it.name.trim() }
        var variantsApplied = 0
        var coversRestored = 0
        metas.forEach { m ->
            val recipe = byName[m.optString("name")] ?: return@forEach
            val wasPinned = recipe.pinned
            if (m.optBoolean("pinned") && !wasPinned) recipe.pinned = true

            var addedHere = 0
            m.optJSONArray("variants")?.let { arr ->
                val have = recipe.variants.map { it.label }.toHashSet()
                for (i in 0 until arr.length()) {
                    val v = arr.optJSONObject(i) ?: continue
                    val label = v.optString("label")
                    if (label.isNotBlank() && !have.add(label)) continue // 同名做法不重复塞
                    val ings = mutableListOf<Ingredient>()
                    v.optJSONArray("ing")?.let { ia ->
                        for (j in 0 until ia.length()) {
                            val io = ia.optJSONObject(j) ?: continue
                            ings.add(Ingredient(io.optString("n"), io.optString("a")))
                        }
                    }
                    val steps = mutableListOf<String>()
                    v.optJSONArray("steps")?.let { sa ->
                        for (j in 0 until sa.length()) {
                            val s = sa.optString(j, "")
                            if (s.isNotBlank()) steps.add(s)
                        }
                    }
                    recipe.variants.add(
                        Variant(
                            label = label,
                            description = v.optString("desc"),
                            ingredients = ings,
                            steps = steps,
                            createdAt = v.optLong("at")
                        )
                    )
                    addedHere++
                }
            }

            // 变体和置顶都走 update（一次写整行），先 update 再 setCover
            if (addedHere > 0 || recipe.pinned != wasPinned) {
                db.update(recipe)
                variantsApplied += addedHere
            }

            val cover = m.optString("cover")
            if (recipe.cover.isNullOrBlank() && cover.isNotBlank() && coverFiles.containsKey(cover)) {
                if (saveCover(cover, coverFiles[cover]!!)) {
                    db.setCover(recipe.id, cover)
                    coversRestored++
                }
            }
        }

        return ZipImport(result, variantsApplied, coversRestored)
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
