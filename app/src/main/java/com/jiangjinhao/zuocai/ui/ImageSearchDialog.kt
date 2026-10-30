package com.jiangjinhao.zuocai.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.jiangjinhao.zuocai.R
import com.jiangjinhao.zuocai.data.ImageSearch
import java.util.concurrent.Executors

/**
 * 网络搜图选择器：输入菜名 → 展示 12 张候选缩略图 → 点一张设为封面。
 *
 * 选中后会下载原图并居中裁成 4:3，和列表卡片、详情页的封面比例一致。
 */
class ImageSearchDialog(
    private val activity: Activity,
    private val presetQuery: String,
    private val onPicked: (Bitmap) -> Unit
) {

    private val d = activity.resources.displayMetrics.density
    private val executor = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    private var page = 0
    private var items: List<ImageSearch.Candidate> = emptyList()
    private val thumbs = ArrayList<Bitmap?>()
    private var busy = false
    private var dialog: AlertDialog? = null

    private val input: EditText = EditText(activity).apply {
        hint = "输入菜名，例如 宫保鸡丁"
        setText(presetQuery)
        isSingleLine = true
        textSize = 14f
        inputType = InputType.TYPE_CLASS_TEXT
        setImeOptions(EditorInfo.IME_ACTION_SEARCH)
        setPadding(dp(12), dp(8), dp(12), dp(8))
    }
    private val goBtn: Button = Button(activity).apply {
        text = "搜索"
        textSize = 14f
        setOnClickListener { search(reset = true) }
    }
    private val status: TextView = TextView(activity).apply {
        text = "输入菜名后点「搜索」。图来自公开网络，仅供参考。"
        textSize = 12.5f
        setTextColor(Color.parseColor("#8A7A6C"))
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(18), dp(16), dp(18))
    }
    private val progress: ProgressBar = ProgressBar(activity).apply {
        visibility = View.GONE
    }
    private val grid: GridView = GridView(activity).apply {
        numColumns = 3
        horizontalSpacing = dp(4)
        verticalSpacing = dp(4)
        stretchMode = GridView.STRETCH_COLUMN_WIDTH
        visibility = View.GONE
        setPadding(dp(12), 0, dp(12), 0)
        clipToPadding = false
    }
    private val moreBtn: Button = Button(activity).apply {
        text = "换一批"
        textSize = 13f
        visibility = View.GONE
        setOnClickListener { search(reset = false) }
    }

    fun show() {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(12))
        }

        val topRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(8))
        }
        topRow.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        topRow.addView(goBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(8) })

        val middle = FrameLayout(activity).apply {
            addView(status, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            addView(progress, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER })
        }
        middle.minimumHeight = dp(300)
        val gridParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        middle.addView(grid, gridParams)

        root.addView(topRow)
        root.addView(middle, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(320)
        ))
        root.addView(moreBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(dp(16), dp(8), dp(16), 0) })

        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search(reset = true); true
            } else false
        }

        dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.net_image_title)
            .setView(root)
            .setNegativeButton(R.string.net_image_close, null)
            .create()

        dialog?.setOnDismissListener { executor.shutdownNow() }
        dialog?.show()
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        if (presetQuery.isNotBlank()) search(reset = true)
    }

    // ------------------------------------------------------------ 搜索

    private fun search(reset: Boolean) {
        val q = input.text?.toString()?.trim().orEmpty()
        if (q.isEmpty() || busy) return
        busy = true

        if (reset) {
            page = 0
            items = emptyList()
            thumbs.clear()
            grid.visibility = View.GONE
            moreBtn.visibility = View.GONE
            status.visibility = View.VISIBLE
            status.text = "正在搜索「$q」…"
        } else {
            moreBtn.isEnabled = false
            moreBtn.text = "正在加载…"
        }
        progress.visibility = View.VISIBLE

        executor.execute {
            val found = runCatching { ImageSearch.search(q, page) }.getOrElse { emptyList() }
            val bitmaps = if (found.isEmpty()) emptyList()
            else ImageSearch.loadThumbs(found, dp(110))

            main.post {
                if (activity.isFinishing || dialog?.isShowing != true) return@post
                busy = false
                progress.visibility = View.GONE
                moreBtn.isEnabled = true
                moreBtn.text = "换一批"

                if (found.isEmpty()) {
                    if (reset) {
                        status.visibility = View.VISIBLE
                        status.text = "没有搜到「$q」的图片，换个更通用的菜名试试。"
                    } else {
                        moreBtn.text = "没有更多了"
                        moreBtn.isEnabled = false
                    }
                    return@post
                }

                // 翻页去重
                val seen = items.map { it.imageUrl }.toHashSet()
                found.forEachIndexed { i, c ->
                    if (seen.add(c.imageUrl)) {
                        items = items + c
                        thumbs.add(bitmaps.getOrNull(i))
                    }
                }
                page += 1

                status.visibility = View.GONE
                grid.visibility = View.VISIBLE
                moreBtn.visibility = View.VISIBLE
                grid.adapter = ThumbAdapter()
                grid.setOnItemClickListener { _, _, pos, _ ->
                    pick(items[pos])
                }
            }
        }
    }

    private fun pick(item: ImageSearch.Candidate) {
        if (busy) return
        busy = true
        progress.visibility = View.VISIBLE
        grid.visibility = View.GONE
        status.visibility = View.VISIBLE
        status.text = "正在下载并裁切图片…"

        executor.execute {
            val bmp = runCatching { ImageSearch.fetchCover(item) }.getOrNull()
            main.post {
                // 下载期间用户可能已经关掉对话框或离开编辑页
                if (activity.isFinishing || activity.isDestroyed) return@post
                busy = false
                progress.visibility = View.GONE
                if (bmp != null) {
                    onPicked(bmp)
                    dialog?.dismiss()
                } else {
                    grid.visibility = View.VISIBLE
                    status.visibility = View.GONE
                    Toast.makeText(activity, R.string.net_image_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ------------------------------------------------------------ 适配器

    private inner class ThumbAdapter : BaseAdapter() {
        override fun getCount() = thumbs.size

        override fun getItem(position: Int) = thumbs[position]

        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val iv = (convertView as? ImageView) ?: ImageView(activity).apply {
                layoutParams = AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(104)
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.parseColor("#F1E9DF"))
            }
            val bmp = thumbs[position]
            if (bmp != null) iv.setImageBitmap(bmp) else iv.setImageDrawable(null)
            return iv
        }
    }

    private fun dp(v: Int) = (v * d).toInt()
}
