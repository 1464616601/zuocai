package com.jiangjinhao.zuocai.data

import java.util.regex.Pattern

/**
 * 菜谱文本解析器（对应网页版 web/parser.js，规则保持一致）。
 *
 * 把用户粘贴的自由格式文本解析成结构化菜谱：菜名 / 食材 / 步骤 / 小贴士 / 分类。
 */
object RecipeParser {

    data class Parsed(
        val name: String = "",
        val ingredients: List<Ingredient> = emptyList(),
        val steps: List<String> = emptyList(),
        val tips: String = "",
        val category: String = RecipeCategory.HOME.key,
        val minutes: Int = 0,
        val servings: Int = 2,
        val difficulty: Int = Difficulty.EASY,
        val ok: Boolean = false,
        val hints: List<String> = emptyList()
    )

    // ---------------------------------------------------------------- 词表

    private val UNITS = listOf(
        "千克", "公斤", "毫升", "汤匙", "茶匙", "小勺", "大勺", "小碗", "大碗",
        "kg", "ml", "mL", "KG", "ML", "克", "升", "斤", "两", "g", "L",
        "勺", "个", "颗", "瓣", "片", "根", "只", "条", "块", "把", "碗", "杯",
        "段", "张", "粒", "朵", "撮", "滴", "寸", "oz", "lb"
    )

    private val AMOUNTS = listOf(
        "适量", "少许", "少量", "若干", "随意", "可选", "按需", "一点点", "一点"
    )

    private const val CN_NUM = "０-９0-9一二三四五六七八九十百半两几"

    private val UNIT_ALT = UNITS.joinToString("|")
    private val AMOUNT_ALT = AMOUNTS.joinToString("|")

    private const val ING_WORDS =
        "食材|用料|材料|原料|主料|配料|辅料|调料|所需材料|准备材料|食材清单|用料清单"
    private const val STEP_WORDS =
        "步骤|做法|烹饪步骤|制作步骤|制作方法|烹饪方法|操作步骤|流程|方法|详细步骤"
    private const val TIP_WORDS = "小贴士|贴士|提示|注意|注意点|tips?|备注|心得"
    private const val NAME_WORDS = "菜名|名称|菜品|标题"

    // ---------------------------------------------------------------- 正则

    /** 名称 + 末尾用量：瘦肉 200g / 花菜半颗 / 干辣椒可选 */
    private val RE_TRAILING_QTY = Regex(
        "^(.+?)[\\s:：]*([$CN_NUM]+(?:\\.\\d+)?\\s*(?:$UNIT_ALT)|$AMOUNT_ALT)\$",
        RegexOption.IGNORE_CASE
    )

    /** 量词在前：少许白糖 */
    private val RE_LEADING_QTY = Regex("^($AMOUNT_ALT)(.+)\$")

    private val RE_NUM_START = Regex("^[0-9０-９][0-9０-９.]*\\s*.*\$")

    private val RE_ING_HEAD = Regex("^($ING_WORDS)[：:]?\$")
    private val RE_STEP_HEAD = Regex("^($STEP_WORDS)[：:]?\$")
    private val RE_TIP_HEAD = Regex("^($TIP_WORDS)[：:]?", RegexOption.IGNORE_CASE)
    private val RE_ING_INLINE = Regex("^($ING_WORDS)[：:]\\s*(.+)\$")
    private val RE_STEP_INLINE = Regex("^($STEP_WORDS)[：:]\\s*(.+)\$")
    private val RE_NAME_INLINE = Regex("^($NAME_WORDS)[：:]\\s*(.+)\$")

    /** 模型有时把模板里的「# 菜名」原样输出，这种标题不是真菜名 */
    private val RE_PLACEHOLDER_NAME = Regex("^(菜名|名称|菜品|菜品名|菜谱名|食谱名|标题)\$")

    /** 数字编号，任何语境下都视为步骤 */
    private val RE_STEP_NUM = Regex("^\\s*(?:\\(?\\d{1,2}\\)?[.、)．]|[①-⑩]|\\d{1,2}\\s*[.、])\\s*")

    /** 项目符号，仅在步骤语境下算步骤标记 */
    private val RE_STEP_BULLET = Regex("^\\s*[-*•·]\\s+")

    private val RE_MD_HEAD = Regex("^\\s*(#{1,6})\\s+(.*)\$")
    private val RE_ING_SPLIT = Regex("[、，,；;]+")

    private val RE_BOLD = Regex("\\*\\*(.+?)\\*\\*")
    private val RE_UNDER_BOLD = Regex("__(.+?)__")
    private val RE_ITALIC = Regex("\\*([^*\\n]+?)\\*")
    private val RE_CODE = Regex("`([^`]+)`")
    private val RE_STRIKE = Regex("~~(.+?)~~")
    private val RE_LINK = Regex("\\[([^\\]]+)]\\([^)]*\\)")
    private val RE_MULTI_SPACE = Regex("\\s+")
    private val RE_QUOTE = Regex("^\\s*>\\s?")
    private val RE_WRAP = Regex("^[《【\\[]+|[》】\\]]+\$")

    // ---------------------------------------------------------------- 公共方法

    /** 去掉 Markdown 标记 */
    fun stripMd(src: String?): String {
        var t = src ?: ""
        t = RE_BOLD.replace(t, "\$1")
        t = RE_UNDER_BOLD.replace(t, "\$1")
        t = RE_ITALIC.replace(t, "\$1")
        t = RE_CODE.replace(t, "\$1")
        t = RE_STRIKE.replace(t, "\$1")
        t = RE_LINK.replace(t, "\$1")
        t = RE_MULTI_SPACE.replace(t, " ")
        return t.trim()
    }

    /** 单个食材 → Ingredient */
    fun splitIngredient(raw: String): Ingredient? {
        var s = stripMd(raw).replace(Regex("^[-*•·\\s]+"), "").trim()
        s = s.replace(Regex("^[：:]\\s*"), "")
        if (s.isEmpty()) return null

        // 量词在前：少许白糖
        RE_LEADING_QTY.find(s)?.let { m ->
            val name = m.groupValues[2].trim()
            if (name.isNotEmpty()) return Ingredient(name, m.groupValues[1])
        }

        // 名称 + 末尾用量
        RE_TRAILING_QTY.find(s)?.let { m ->
            val name = m.groupValues[1].trim()
            if (name.isNotEmpty()) return Ingredient(name, m.groupValues[2].trim())
        }

        // 空格分隔且后半段以数字开头
        val parts = s.split(Regex("\\s+"))
        if (parts.size >= 2) {
            val last = parts.last()
            if (RE_NUM_START.matches(last)) {
                return Ingredient(parts.dropLast(1).joinToString(" "), last)
            }
        }

        return Ingredient(s, "")
    }

    /** 一行食材文本 → 列表 */
    fun parseIngredientLine(line: String): List<Ingredient> {
        val out = mutableListOf<Ingredient>()
        val cleaned = stripMd(line).replace(Regex("^[-*•·]\\s+"), "")
        cleaned.split(RE_ING_SPLIT).forEach { seg ->
            splitIngredient(seg)?.let { ing ->
                if (ing.name.isNotEmpty() || ing.amount.isNotEmpty()) out.add(ing)
            }
        }
        return out
    }

    /** 根据菜名与食材猜分类 */
    fun guessCategory(name: String, ingredients: List<Ingredient>): String {
        val t = (name + " " + ingredients.joinToString(" ") { it.name }).lowercase()
        return when {
            Regex("(汤|羹|煲|锅仔|炖盅)").containsMatchIn(t) -> RecipeCategory.SOUP.key
            Regex("(凉拌|拌菜|沙拉|冷盘|口水)").containsMatchIn(t) -> RecipeCategory.COLD.key
            Regex("(蛋糕|布丁|甜品|糖水|饼干|派|酥|奶冻|慕斯|双皮奶)").containsMatchIn(t) ->
                RecipeCategory.DESSERT.key
            Regex("(炒饭|拌饭|焖饭|盖饭|面条|拉面|炒面|汤面|米粉|河粉|粥|饺子|包子|馒头|花卷|烙饼|煎饼|年糕|意面|披萨)")
                .containsMatchIn(t) -> RecipeCategory.STAPLE.key
            else -> RecipeCategory.HOME.key
        }
    }

    // ---------------------------------------------------------------- 主解析

    fun parse(text: String?): Parsed {
        val raw = (text ?: "").replace("\r\n", "\n").replace("\r", "\n")
        if (raw.isBlank()) return Parsed()

        val lines = raw.split("\n")
        var mode = "name"
        var nameFound = false
        var name = ""
        val ingredients = mutableListOf<Ingredient>()
        val stepBuffer = mutableListOf<String>()
        val tipLines = mutableListOf<String>()
        val meta = mutableMapOf<String, String>()

        for (rawLine in lines) {
            if (rawLine.isBlank()) continue

            val isQuote = rawLine.trimStart().startsWith(">")
            val plain = stripMd(RE_QUOTE.replace(rawLine, ""))
            if (plain.isEmpty()) continue

            // 0. 导出文件带的元信息：@分类：家常菜 / @时长：45分钟
            if (plain.startsWith("@")) {
                readMetaLine(plain, meta)
                continue
            }

            // 1. 小贴士
            if (RE_TIP_HEAD.containsMatchIn(plain)) {
                val body = RE_TIP_HEAD.replace(plain, "").trim()
                if (body.isNotEmpty()) tipLines.add(body)
                mode = "tip"
                continue
            }

            // 2. Markdown 标题
            val mdMatch = RE_MD_HEAD.find(rawLine)
            if (mdMatch != null) {
                val title = stripMd(mdMatch.groupValues[2])
                when {
                    RE_ING_HEAD.matches(title) -> mode = "ing"
                    RE_STEP_HEAD.matches(title) -> mode = "step"
                    !nameFound -> {
                        nameFound = true
                        if (RE_PLACEHOLDER_NAME.matches(title.trim())) {
                            mode = "awaitName"
                        } else {
                            name = title
                            mode = "ing"
                        }
                    }
                    else -> mode = "ing"
                }
                continue
            }

            // 3. 前缀式：菜名：xxx
            val nameMatch = RE_NAME_INLINE.find(plain)
            if (nameMatch != null) {
                name = nameMatch.groupValues[2].trim()
                nameFound = true
                mode = "ing"
                continue
            }

            // 4. 前缀式：食材：xxx
            val ingMatch = RE_ING_INLINE.find(plain)
            if (ingMatch != null) {
                mode = "ing"
                ingredients.addAll(parseIngredientLine(ingMatch.groupValues[2]))
                continue
            }

            // 5. 前缀式：步骤：xxx
            val stepMatch = RE_STEP_INLINE.find(plain)
            if (stepMatch != null) {
                mode = "step"
                stepBuffer.add(stepMatch.groupValues[2])
                continue
            }

            // 6. 纯文本段落标题
            if (RE_ING_HEAD.matches(plain)) { mode = "ing"; continue }
            if (RE_STEP_HEAD.matches(plain)) { mode = "step"; continue }

            // 7. 引用行
            if (isQuote) {
                tipLines.add(plain)
                continue
            }

            // 8. 首个非空行兜底当菜名
            if (!nameFound && mode == "name") {
                val first = plain.replace(Regex("^[#\\s]+"), "").trim()
                nameFound = true
                if (RE_PLACEHOLDER_NAME.matches(first)) {
                    mode = "awaitName"
                } else {
                    name = first
                    mode = "ing"
                }
                continue
            }

            // 8.5 上一行是「# 菜名」这类占位标题，用这一行当真实菜名
            if (mode == "awaitName") {
                val real = plain.replace(Regex("^[#\\s]+"), "").trim()
                if (real.isNotEmpty() && !RE_ING_HEAD.matches(real) && !RE_STEP_HEAD.matches(real)) {
                    name = real
                    mode = "ing"
                    continue
                }
            }

            // 9. 数字编号行 → 切到步骤
            if (mode != "step" && RE_STEP_NUM.containsMatchIn(rawLine)) {
                mode = "step"
                stepBuffer.add(rawLine)
                continue
            }

            // 10. 按模式归集
            when (mode) {
                "ing" -> ingredients.addAll(parseIngredientLine(rawLine))
                "step" -> stepBuffer.add(rawLine)
                "tip" -> tipLines.add(plain)
                else -> ingredients.addAll(parseIngredientLine(rawLine))
            }
        }

        // 合并步骤
        val steps = mutableListOf<String>()
        val cur = StringBuilder()
        fun flush() {
            val t = stripMd(cur.toString()).trim()
            if (t.isNotEmpty()) steps.add(t)
            cur.setLength(0)
        }
        for (line in stepBuffer) {
            val numbered = RE_STEP_NUM.containsMatchIn(line) || RE_STEP_BULLET.containsMatchIn(line)
            var cleaned = RE_STEP_NUM.replace(line, "")
            cleaned = RE_STEP_BULLET.replace(cleaned, "").trim()
            if (cleaned.isEmpty()) continue
            if (numbered) {
                flush()
                cur.append(cleaned)
            } else if (cur.isEmpty()) {
                cur.append(cleaned)
            } else {
                cur.append(' ').append(cleaned)
            }
        }
        flush()

        val finalName = RE_WRAP.replace(name, "").trim()
        val deduped = dedupe(ingredients)
        val tips = tipLines.filter { it.isNotBlank() }.joinToString(" ")

        val hints = mutableListOf<String>()
        if (finalName.isEmpty()) hints.add("没识别到菜名，可在第一行写 # 菜名")
        if (deduped.isEmpty()) hints.add("没识别到食材，检查是否有「### 食材」小标题")
        if (steps.isEmpty()) hints.add("没识别到步骤，检查是否有「### 步骤」小标题")

        val ok = finalName.isNotEmpty() && (deduped.isNotEmpty() || steps.isNotEmpty())

        return Parsed(
            name = finalName,
            ingredients = deduped,
            steps = steps,
            tips = tips,
            category = RecipeCategory.fromLabel(meta["分类"])?.key
                ?: guessCategory(finalName, deduped),
            minutes = firstInt(meta["时长"]) ?: 0,
            servings = firstInt(meta["份量"]) ?: 2,
            difficulty = parseDifficulty(meta["难度"]),
            ok = ok,
            hints = hints
        )
    }

    /** 读取一行 `@键：值`，值可带单位（45分钟 / 4人份），取数时再剥 */
    private fun readMetaLine(line: String, out: MutableMap<String, String>) {
        val body = line.removePrefix("@").trim()
        val idx = body.indexOfFirst { it == '：' || it == ':' }
        if (idx <= 0) return
        val key = body.substring(0, idx).trim()
        val value = body.substring(idx + 1).trim()
        if (key.isNotEmpty() && value.isNotEmpty()) out[key] = value
    }

    private fun firstInt(text: String?): Int? =
        text?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() }

    private fun parseDifficulty(text: String?): Int {
        val t = text?.trim().orEmpty()
        return when {
            t.contains("难") -> Difficulty.HARD
            t.contains("中") -> Difficulty.MEDIUM
            else -> Difficulty.EASY
        }
    }

    private fun dedupe(list: List<Ingredient>): List<Ingredient> {
        val seen = HashSet<String>()
        val out = mutableListOf<Ingredient>()
        for (it in list) {
            val key = it.name.trim()
            if (key.isEmpty()) {
                if (it.amount.isNotEmpty()) out.add(it)
                continue
            }
            if (seen.contains(key)) continue
            seen.add(key)
            out.add(it)
        }
        return out
    }

    // ---------------------------------------------------------------- 反向序列化

    /** 菜谱 → 文本，用于编辑时回填文本框 */
    fun stringify(recipe: Recipe): String {
        val ings = recipe.ingredients
            .filter { it.name.isNotEmpty() || it.amount.isNotEmpty() }
            .joinToString("、") { if (it.amount.isNotEmpty()) "${it.name} ${it.amount}" else it.name }

        val steps = recipe.steps
            .filter { it.isNotBlank() }
            .mapIndexed { i, s -> "${i + 1}. ${s.trim()}" }
            .joinToString("\n")

        val sb = StringBuilder()
        sb.append("# ").append(recipe.name.ifBlank { "未命名菜谱" }).append("\n\n")

        // 元信息：导出成 TXT 时带上，导入到另一台手机才能还原分类与份量
        sb.append("@分类：").append(RecipeCategory.textOf(recipe.category)).append("\n")
        if (recipe.minutes > 0) {
            sb.append("@时长：").append(recipe.minutes).append("分钟\n")
        }
        if (recipe.servings > 0) {
            sb.append("@份量：").append(recipe.servings).append("人份\n")
        }
        sb.append("@难度：").append(Difficulty.textOf(recipe.difficulty)).append("\n")

        sb.append("\n### 食材\n").append(ings).append("\n\n")
        sb.append("### 步骤\n").append(steps)
        if (recipe.description.isNotBlank()) {
            sb.append("\n\n> 小贴士：").append(recipe.description.trim())
        }
        return sb.toString()
    }
}
