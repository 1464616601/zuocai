package com.jiangjinhao.zuocai.ui

import android.content.res.Resources
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jiangjinhao.zuocai.R
import com.jiangjinhao.zuocai.data.Difficulty
import com.jiangjinhao.zuocai.data.Recipe
import com.jiangjinhao.zuocai.data.RecipeCategory
import com.jiangjinhao.zuocai.databinding.ItemRecipeBinding
import com.jiangjinhao.zuocai.util.ImageStore

class RecipeAdapter(
    private val onClick: (Recipe) -> Unit
) : ListAdapter<Recipe, RecipeAdapter.VH>(Diff) {

    /** 当前搜索词：用于高亮命中片段，并解释「菜名没含这个词为什么会搜出来」 */
    private var keyword: String? = null

    fun setKeyword(kw: String?) {
        if (keyword == kw) return
        keyword = kw
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemRecipeBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemRecipeBinding) : RecyclerView.ViewHolder(b.root) {

        init {
            b.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onClick(getItem(pos))
            }
        }

        fun bind(recipe: Recipe) {
            val ctx = b.root.context
            val words = keyword?.trim()?.split(Regex("\\s+"))?.filter { it.isNotBlank() }.orEmpty()

            val name = recipe.name.ifBlank { ctx.getString(R.string.untitled) }
            b.name.text = highlight(name, words, ctx)

            val meta = buildList {
                add(ctx.getString(Difficulty.labelRes(recipe.difficulty)))
                if (recipe.minutes > 0) add("${recipe.minutes}${ctx.getString(R.string.minutes_unit)}")
                if (recipe.servings > 0) add("${recipe.servings}${ctx.getString(R.string.servings_unit)}")
            }.joinToString(" · ")

            // 菜名没含搜索词但被搜出来，多半是食材命中——告诉用户原因，
            // 不然搜「土豆」冒出腊肉焖饭会像出了 bug
            val nameHit = words.isEmpty() || words.any { name.contains(it, ignoreCase = true) }
            if (!nameHit) {
                val ing = recipe.ingredients.firstOrNull { item ->
                    words.any { item.name.contains(it, ignoreCase = true) || item.amount.contains(it, ignoreCase = true) }
                }
                b.meta.text = if (ing != null) "食材含「${ing.name}」 · $meta" else "描述命中 · $meta"
            } else {
                b.meta.text = meta
            }

            b.categoryBadge.text = ctx.getString(RecipeCategory.labelResOf(recipe.category))
            b.pinBadge.visibility = if (recipe.pinned) android.view.View.VISIBLE else android.view.View.GONE

            val targetPx = (120 * Resources.getSystem().displayMetrics.density).toInt()
            b.cover.tag = recipe.cover ?: ""

            if (recipe.cover.isNullOrBlank()) {
                b.cover.setImageDrawable(null)
                b.coverIcon.visibility = android.view.View.VISIBLE
                return
            }

            b.coverIcon.visibility = android.view.View.GONE
            val cached = ImageStore.peek(recipe.cover, targetPx)
            if (cached != null) {
                b.cover.setImageBitmap(cached)
                return
            }
            b.cover.setImageDrawable(null)
            ImageStore.loadAsync(ctx, recipe.cover, targetPx) { bmp ->
                if (b.cover.tag == recipe.cover) {
                    if (bmp != null) {
                        b.cover.setImageBitmap(bmp)
                    } else {
                        b.coverIcon.visibility = android.view.View.VISIBLE
                    }
                }
            }
        }
    }

    /** 菜名里的命中片段标成品牌色，一眼看出是哪部分匹配 */
    private fun highlight(text: String, words: List<String>, ctx: android.content.Context): CharSequence {
        if (words.isEmpty() || text.isEmpty()) return text
        val span = SpannableStringBuilder(text)
        val lower = text.lowercase()
        val color = ContextCompat.getColor(ctx, R.color.brand)
        words.forEach { w ->
            val lw = w.lowercase()
            var idx = lower.indexOf(lw)
            while (idx >= 0) {
                span.setSpan(
                    ForegroundColorSpan(color), idx, idx + lw.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                idx = lower.indexOf(lw, idx + lw.length)
            }
        }
        return span
    }

    object Diff : DiffUtil.ItemCallback<Recipe>() {
        override fun areItemsTheSame(oldItem: Recipe, newItem: Recipe) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Recipe, newItem: Recipe) =
            oldItem.updatedAt == newItem.updatedAt &&
                oldItem.name == newItem.name &&
                oldItem.cover == newItem.cover &&
                oldItem.category == newItem.category &&
                oldItem.pinned == newItem.pinned
    }
}
