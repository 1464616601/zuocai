package com.jiangjinhao.zuocai

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jiangjinhao.zuocai.data.Backup
import com.jiangjinhao.zuocai.data.Recipe
import com.jiangjinhao.zuocai.data.RecipeCategory
import com.jiangjinhao.zuocai.data.RecipeDb
import com.jiangjinhao.zuocai.data.SeedData
import com.jiangjinhao.zuocai.data.SortOrder
import com.jiangjinhao.zuocai.databinding.ActivityMainBinding
import com.jiangjinhao.zuocai.ui.RecipeAdapter

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: RecipeAdapter
    private val db by lazy { RecipeDb.get(this) }

    private var selectedCategory: String? = null

    /** 出厂默认「使用最多」——这个 App 的核心是常做常吃的菜；用户手动换过就记住他的选择 */
    private var sortOrder = SortOrder.USAGE

    /** 上一次计入使用量的搜索词，避免边打字边重复累加 */
    private var lastCountedKeyword: String? = null

    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        restoreSortOrder()
        seedIfNeeded()
        setupList()
        setupSearch()
        setupActions()
        buildCategoryBar()
        applySortLabel()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroy() {
        searchRunnable?.let { searchHandler.removeCallbacks(it) }
        super.onDestroy()
    }

    // ---------------------------------------------------------------- 初始化

    /**
     * 写入内置菜谱。
     *
     * 按菜名去重，所以即使库里已经有用户自己的菜谱也安全：
     * 只补上缺的那几道，不会覆盖也不会重复。版本号用于将来换一批默认数据时再触发一次。
     */
    private fun seedIfNeeded() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SEEDED, false)) return
        runCatching {
            SeedData.removeLegacySamples(db)
            SeedData.insert(this, db)
        }
        prefs.edit().putBoolean(KEY_SEEDED, true).apply()
    }

    private fun setupList() {
        adapter = RecipeAdapter { recipe -> openDetail(recipe.id) }
        binding.recipeList.layoutManager = GridLayoutManager(this, 2)
        binding.recipeList.adapter = adapter
        binding.recipeList.addItemDecoration(GridSpacingDecoration(2, dp(12)))
        binding.recipeList.itemAnimator?.changeDuration = 150

        binding.emptyAction.setOnClickListener { openEditor(null) }
    }

    private fun setupSearch() {
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                binding.searchClear.visibility =
                    if (s.isNullOrBlank()) View.GONE else View.VISIBLE
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                searchRunnable = Runnable {
                    recordSearch()
                    refresh()
                }
                searchHandler.postDelayed(searchRunnable!!, 260)
            }
        })
        binding.searchClear.setOnClickListener {
            binding.searchInput.setText("")
            binding.searchInput.clearFocus()
        }
    }

    /**
     * 记一次搜索命中。
     *
     * 同一个关键词只记一次：输入框是边打字边刷新的，不去重的话「辣椒炒肉」
     * 会在用户敲字过程中被反复累加。清空搜索后允许重新计数。
     */
    private fun recordSearch() {
        val kw = binding.searchInput.text?.toString()?.trim().orEmpty()
        if (kw.isEmpty()) {
            lastCountedKeyword = null
            return
        }
        if (kw.length < 2 || kw == lastCountedKeyword) return
        lastCountedKeyword = kw
        db.bumpSearch(kw)
    }

    private fun setupActions() {
        binding.addButton.setOnClickListener { openEditor(null) }
        binding.sortButton.setOnClickListener { showSortMenu() }
        binding.exportButton.setOnClickListener { startExport() }
        binding.importButton.setOnClickListener { startImport() }
    }

    // ---------------------------------------------------------------- 导入导出

    /** 系统「新建文档」：让用户选保存位置，不申请存储权限也能写文件 */
    private val createDoc = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> if (uri != null) writeExport(uri) }

    /** 系统「打开文档」：同理，不需要读存储权限 */
    private val openDoc = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) readImport(uri) }

    private fun startExport() {
        val recipes = db.list()
        if (recipes.isEmpty()) {
            Toast.makeText(this, R.string.export_empty, Toast.LENGTH_SHORT).show()
            return
        }
        runCatching { createDoc.launch(Backup.suggestFileName()) }
            .onFailure { Toast.makeText(this, R.string.export_failed, Toast.LENGTH_SHORT).show() }
    }

    private fun writeExport(uri: android.net.Uri) {
        val recipes = db.list()
        val text = Backup.export(recipes)

        val ok = runCatching {
            contentResolver.openOutputStream(uri, "wt")?.use { out ->
                // 带 UTF-8 BOM：Windows 记事本靠它识别编码，否则中文可能显示成乱码。
                // 导入端会剥掉 BOM，不影响解析。
                out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                out.write(text.toByteArray(Charsets.UTF_8))
            } ?: return@runCatching false
            true
        }.getOrDefault(false)

        if (!ok) {
            Toast.makeText(this, R.string.export_failed, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.export_done_title)
            .setMessage(getString(R.string.export_done_msg, recipes.size))
            .setPositiveButton(R.string.dialog_ok, null)
            .show()
    }

    private fun startImport() {
        runCatching { openDoc.launch(arrayOf("text/plain", "text/*")) }
            .onFailure { Toast.makeText(this, R.string.import_failed, Toast.LENGTH_SHORT).show() }
    }

    private fun readImport(uri: android.net.Uri) {
        val bytes = runCatching {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()

        if (bytes == null || bytes.isEmpty()) {
            Toast.makeText(this, R.string.import_read_failed, Toast.LENGTH_SHORT).show()
            return
        }

        val result = runCatching { Backup.import(db, Backup.decode(bytes)) }
            .getOrElse {
                Toast.makeText(this, R.string.import_failed, Toast.LENGTH_SHORT).show()
                return
            }

        if (result.total == 0 || (result.added == 0 && result.skipped == 0)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.import_done_title)
                .setMessage(R.string.import_empty)
                .setPositiveButton(R.string.dialog_ok, null)
                .show()
            return
        }

        selectedCategory = null
        binding.searchInput.setText("")
        syncChipSelection()
        refresh()

        val msg = when {
            result.failed > 0 -> getString(
                R.string.import_done_msg_fail, result.added, result.skipped, result.failed
            )
            result.skipped > 0 -> getString(
                R.string.import_done_msg_skip, result.added, result.skipped
            )
            else -> getString(R.string.import_done_msg, result.added)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.import_done_title)
            .setMessage(msg)
            .setPositiveButton(R.string.dialog_ok, null)
            .show()
    }

    private fun showSortMenu() {
        val popup = PopupMenu(this, binding.sortButton)
        popup.menu.add(0, 1, 0, getString(R.string.sort_recent))
        popup.menu.add(0, 2, 1, getString(R.string.sort_name))
        popup.menu.add(0, 3, 2, getString(R.string.sort_usage))
        // 分组 2：和排序不是一类，中间会有分隔感（部分 ROM 不画线，但顺序清楚）
        popup.menu.add(2, 4, 3, getString(R.string.menu_games))
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                4 -> {
                    GamesActivity.start(this)
                    true
                }
                else -> {
                    sortOrder = when (item.itemId) {
                        1 -> SortOrder.RECENT
                        2 -> SortOrder.NAME
                        else -> SortOrder.USAGE
                    }
                    saveSortOrder()
                    applySortLabel()
                    refresh()
                    true
                }
            }
        }
        popup.show()
    }

    private fun restoreSortOrder() {
        val saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_SORT, null)
        sortOrder = saved?.let { runCatching { SortOrder.valueOf(it) }.getOrNull() } ?: SortOrder.USAGE
    }

    private fun saveSortOrder() {
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putString(KEY_SORT, sortOrder.name)
            .apply()
    }

    private fun applySortLabel() {
        binding.sortButton.contentDescription = when (sortOrder) {
            SortOrder.RECENT -> getString(R.string.sort_recent)
            SortOrder.NAME -> getString(R.string.sort_name)
            SortOrder.USAGE -> getString(R.string.sort_usage)
        }
    }

    // ---------------------------------------------------------------- 分类

    private fun buildCategoryBar() {
        binding.categoryBar.removeAllViews()
        val entries = mutableListOf<Pair<String?, Int>>()
        entries.add(null to R.string.cat_all)
        RecipeCategory.values().forEach { entries.add(it.key to it.labelRes) }

        entries.forEach { (key, labelRes) ->
            val chip = TextView(this).apply {
                text = getString(labelRes)
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(dp(15), dp(8), dp(15), dp(8))
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_chip)
                setTextColor(ContextCompat.getColorStateList(this@MainActivity, R.color.chip_text))
                isSelected = key == selectedCategory
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectedCategory = key
                    syncChipSelection()
                    refresh()
                }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) }
            binding.categoryBar.addView(chip, lp)
        }
    }

    private fun syncChipSelection() {
        val entries = mutableListOf<String?>()
        entries.add(null)
        RecipeCategory.values().forEach { entries.add(it.key) }
        for (i in 0 until binding.categoryBar.childCount) {
            val view = binding.categoryBar.getChildAt(i) as? TextView ?: continue
            view.isSelected = entries.getOrNull(i) == selectedCategory
        }
    }

    // ---------------------------------------------------------------- 数据

    private fun refresh() {
        val keyword = binding.searchInput.text?.toString()?.trim()
        val list = db.list(
            keyword = keyword.takeUnless { it.isNullOrBlank() },
            category = selectedCategory,
            sort = sortOrder
        )
        adapter.setKeyword(keyword)
        adapter.submitList(list)
        updateHeader(list.size, keyword)
    }

    private fun updateHeader(shownCount: Int, keyword: String?) {
        val total = db.count()
        binding.subtitle.text =
            if (total > 0) getString(R.string.recipe_count, total) else getString(R.string.home_subtitle)

        val isEmpty = shownCount == 0
        binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recipeList.visibility = if (isEmpty) View.INVISIBLE else View.VISIBLE

        if (isEmpty) {
            val filtering = !keyword.isNullOrBlank() || selectedCategory != null
            binding.emptyTitle.setText(
                if (filtering) R.string.search_empty_title else R.string.empty_title
            )
            binding.emptyDesc.setText(
                if (filtering) R.string.search_empty_desc else R.string.empty_desc
            )
            binding.emptyAction.visibility = if (filtering) View.GONE else View.VISIBLE
        }
    }

    // ---------------------------------------------------------------- 跳转

    private fun openDetail(id: Long) {
        // 点开即算一次使用，「使用最多」排序靠它累计
        db.bumpOpen(id)
        startActivity(
            Intent(this, DetailActivity::class.java).putExtra(DetailActivity.EXTRA_ID, id)
        )
    }

    private fun openEditor(id: Long?) {
        startActivity(
            Intent(this, EditActivity::class.java).apply {
                if (id != null) putExtra(EditActivity.EXTRA_ID, id)
            }
        )
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    /**
     * 两列网格的等距间隙
     */
    private class GridSpacingDecoration(
        private val spanCount: Int,
        private val spacing: Int
    ) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State
        ) {
            val position = parent.getChildAdapterPosition(view)
            if (position == RecyclerView.NO_POSITION) return
            val column = position % spanCount
            outRect.left = spacing * column / spanCount
            outRect.right = spacing * (spanCount - 1 - column) / spanCount
            outRect.bottom = spacing
        }
    }

    companion object {
        private const val PREFS = "zuocai_prefs"
        private const val KEY_SEEDED = "seeded_v4"
        private const val KEY_SORT = "sort_order"
    }
}
