package com.jiangjinhao.zuocai.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 菜谱本地存储。单表 + JSON 子结构，读写走参数化 SQL。
 */
class RecipeDb private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                category TEXT NOT NULL DEFAULT 'home',
                cover TEXT,
                description TEXT NOT NULL DEFAULT '',
                servings INTEGER NOT NULL DEFAULT 2,
                minutes INTEGER NOT NULL DEFAULT 0,
                difficulty INTEGER NOT NULL DEFAULT 1,
                ingredients TEXT NOT NULL DEFAULT '[]',
                steps TEXT NOT NULL DEFAULT '[]',
                created_at INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL DEFAULT 0,
                open_count INTEGER NOT NULL DEFAULT 0,
                search_count INTEGER NOT NULL DEFAULT 0,
                variants TEXT NOT NULL DEFAULT '[]',
                pinned INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_${TABLE}_updated ON $TABLE (updated_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // v2：记使用量，用于「使用最多」排序
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN open_count INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN search_count INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 3) {
            // v3：同一道菜的其他做法
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN variants TEXT NOT NULL DEFAULT '[]'")
        }
        if (oldVersion < 4) {
            // v4：常做标记（置顶）
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
        }
    }

    // ---------------------------------------------------------------- 写

    fun insert(recipe: Recipe): Long {
        val now = System.currentTimeMillis()
        val values = toValues(recipe).apply {
            put("created_at", if (recipe.createdAt > 0) recipe.createdAt else now)
            put("updated_at", now)
        }
        val id = writableDatabase.insert(TABLE, null, values)
        recipe.id = id
        recipe.createdAt = values.getAsLong("created_at") ?: now
        recipe.updatedAt = now
        return id
    }

    fun update(recipe: Recipe) {
        val now = System.currentTimeMillis()
        val values = toValues(recipe).apply { put("updated_at", now) }
        writableDatabase.update(TABLE, values, "id = ?", arrayOf(recipe.id.toString()))
        recipe.updatedAt = now
    }

    fun delete(id: Long) {
        writableDatabase.delete(TABLE, "id = ?", arrayOf(id.toString()))
    }

    /**
     * 只改封面，不动 updated_at。
     *
     * 内置菜谱回填图片时用：走 [update] 会把 41 道菜的更新时间全刷成现在，
     * 「按最近更新」排序下会把用户原有的顺序整个打乱。
     */
    fun setCover(id: Long, cover: String?) {
        val values = ContentValues().apply { put("cover", cover) }
        writableDatabase.update(TABLE, values, "id = ?", arrayOf(id.toString()))
    }

    // ---------------------------------------------------------------- 使用量

    /**
     * 打开一次菜谱，热度 +1。
     *
     * 刻意不更新 updated_at：「最近更新」的语义是用户改过这道菜，
     * 光看了一眼不该把它顶到列表最前面。
     */
    fun bumpOpen(id: Long) {
        writableDatabase.execSQL(
            "UPDATE $TABLE SET open_count = open_count + 1 WHERE id = ?",
            arrayOf(id)
        )
    }

    /**
     * 搜索命中，给菜名匹配的菜谱热度各 +1。
     *
     * 只匹配菜名、不匹配食材：搜「肉」会把几十道菜全加上去，那就失去区分度了。
     *
     * @return 被加过的菜谱条数
     */
    fun bumpSearch(keyword: String): Int {
        val kw = keyword.trim()
        if (kw.isEmpty()) return 0
        val st = writableDatabase.compileStatement(
            "UPDATE $TABLE SET search_count = search_count + 1 WHERE name LIKE ? ESCAPE '\\'"
        )
        return st.use {
            it.bindString(1, "%${escapeLike(kw)}%")
            it.executeUpdateDelete()
        }
    }

    /** LIKE 通配符转义，配合 `ESCAPE '\'` 使用，搜「100%」「a_b」这类词不会变成通配符 */
    private fun escapeLike(s: String): String =
        s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    /**
     * 只改置顶标记，不动 updated_at——置顶是个视图偏好，不算"改过这道菜"
     */
    fun setPinned(id: Long, pinned: Boolean) {
        val values = ContentValues().apply { put("pinned", if (pinned) 1 else 0) }
        writableDatabase.update(TABLE, values, "id = ?", arrayOf(id.toString()))
    }

    // ---------------------------------------------------------------- 读

    fun getById(id: Long): Recipe? {
        readableDatabase.query(
            TABLE, null, "id = ?", arrayOf(id.toString()),
            null, null, null, "1"
        ).use { c ->
            return if (c.moveToFirst()) fromCursor(c) else null
        }
    }

    /**
     * @param keyword 空或 null 表示不按关键词过滤。
     *   支持空格分词（「辣 肉」= 两词都要命中），且结果按相关度排序：
     *   菜名命中 > 描述命中 > 食材命中——不然搜「土豆」时，
     *   腊肉焖饭（食材里有土豆）可能排在土豆炒肉前面，看起来像搜错了。
     * @param category null 表示全部分类
     */
    fun list(keyword: String? = null, category: String? = null, sort: SortOrder = SortOrder.RECENT): List<Recipe> {
        val words = keyword?.trim()?.split(Regex("\\s+"))?.filter { it.isNotBlank() }.orEmpty()

        val where = StringBuilder()
        val args = mutableListOf<String>()
        val relArgs = mutableListOf<String>()

        if (words.isNotEmpty()) {
            // 过滤：每个词都要在 菜名/描述/食材 之一里出现
            where.append(
                words.joinToString(" AND ") {
                    val like = "%${escapeLike(it)}%"
                    args.add(like); args.add(like); args.add(like)
                    "(name LIKE ? ESCAPE '\\' OR description LIKE ? ESCAPE '\\' OR ingredients LIKE ? ESCAPE '\\')"
                }
            )

            // 相关度打分只进 ORDER BY：菜名 2 分、描述 1 分、食材 0 分。
            // 注意不能把它塞进 WHERE——食材命中的行会在这里被打成 0 分被过滤掉。
            words.forEach {
                val like = "%${escapeLike(it)}%"
                relArgs.add(like); relArgs.add(like)
            }
        }
        if (!category.isNullOrBlank()) {
            if (where.isNotEmpty()) where.append(" AND ")
            where.append("category = ?")
            args.add(category)
        }

        val base = when (sort) {
            SortOrder.RECENT -> "updated_at DESC, id DESC"
            SortOrder.NAME -> "name COLLATE NOCASE ASC"
            // 打开次数和搜索命中次数合并成一个热度，次数相同的再按最近更新排
            SortOrder.USAGE -> "(open_count + search_count) DESC, updated_at DESC, id DESC"
        }
        // 常做的菜永远置顶，不受排序方式影响；有搜索词时相关度再放在最前面
        val orderBy = "pinned DESC, " + if (relArgs.isNotEmpty()) {
            val rel = words.joinToString(" + ") { "(CASE WHEN name LIKE ? THEN 2 WHEN description LIKE ? THEN 1 ELSE 0 END)" }
            "($rel) DESC, $base"
        } else base

        args.addAll(relArgs)

        val result = mutableListOf<Recipe>()
        readableDatabase.query(
            TABLE, null,
            where.toString().ifEmpty { null },
            args.takeIf { it.isNotEmpty() }?.toTypedArray(),
            null, null, orderBy
        ).use { c ->
            while (c.moveToNext()) result.add(fromCursor(c))
        }
        return result
    }

    fun count(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE", null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    /** 各分类下的菜谱数量，用于首页统计 */
    fun countByCategory(): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        readableDatabase.rawQuery(
            "SELECT category, COUNT(*) FROM $TABLE GROUP BY category", null
        ).use { c ->
            while (c.moveToNext()) map[c.getString(0) ?: "other"] = c.getInt(1)
        }
        return map
    }

    // ---------------------------------------------------------------- 内部

    private fun toValues(recipe: Recipe): ContentValues = ContentValues().apply {
        put("name", recipe.name.trim())
        put("category", recipe.category)
        put("cover", recipe.cover)
        put("description", recipe.description.trim())
        put("servings", recipe.servings)
        put("minutes", recipe.minutes)
        put("difficulty", Difficulty.normalize(recipe.difficulty))
        put("ingredients", recipe.ingredientsJson())
        put("steps", recipe.stepsJson())
        put("variants", recipe.variantsJson())
        put("pinned", if (recipe.pinned) 1 else 0)
    }

    private fun fromCursor(c: Cursor): Recipe = Recipe(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        name = c.getString(c.getColumnIndexOrThrow("name")) ?: "",
        category = c.getString(c.getColumnIndexOrThrow("category")) ?: RecipeCategory.HOME.key,
        cover = c.getString(c.getColumnIndexOrThrow("cover")),
        description = c.getString(c.getColumnIndexOrThrow("description")) ?: "",
        servings = c.getInt(c.getColumnIndexOrThrow("servings")),
        minutes = c.getInt(c.getColumnIndexOrThrow("minutes")),
        difficulty = c.getInt(c.getColumnIndexOrThrow("difficulty")),
        ingredients = Recipe.parseIngredients(c.getString(c.getColumnIndexOrThrow("ingredients"))),
        steps = Recipe.parseSteps(c.getString(c.getColumnIndexOrThrow("steps"))),
        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
        updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at")),
        variants = Variant.parse(c.getString(c.getColumnIndexOrThrow("variants"))).toMutableList(),
        pinned = c.getInt(c.getColumnIndexOrThrow("pinned")) == 1
    )

    companion object {
        private const val DB_NAME = "zuocai.db"
        private const val DB_VERSION = 3
        private const val TABLE = "recipes"

        @Volatile
        private var instance: RecipeDb? = null

        fun get(context: Context): RecipeDb =
            instance ?: synchronized(this) {
                instance ?: RecipeDb(context).also { instance = it }
            }
    }
}
