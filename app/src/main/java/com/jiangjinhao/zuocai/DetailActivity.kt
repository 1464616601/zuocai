package com.jiangjinhao.zuocai

import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.jiangjinhao.zuocai.data.ApiConfig
import com.jiangjinhao.zuocai.data.Difficulty
import com.jiangjinhao.zuocai.data.Ingredient
import com.jiangjinhao.zuocai.data.LlmClient
import com.jiangjinhao.zuocai.data.Recipe
import com.jiangjinhao.zuocai.data.RecipeCategory
import com.jiangjinhao.zuocai.data.RecipeDb
import com.jiangjinhao.zuocai.data.RecipeParser
import com.jiangjinhao.zuocai.data.Variant
import com.jiangjinhao.zuocai.data.VideoLink
import com.jiangjinhao.zuocai.databinding.ActivityDetailBinding
import com.jiangjinhao.zuocai.databinding.ItemIngredientViewBinding
import com.jiangjinhao.zuocai.databinding.ItemStepViewBinding
import com.jiangjinhao.zuocai.util.ImageStore
import java.util.concurrent.Executors

class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private val db by lazy { RecipeDb.get(this) }
    private var recipeId: Long = 0L
    private var current: Recipe? = null

    /** 当前展示的做法下标，-1 表示原做法 */
    private var activeVariant = -1

    /** 生成好但还没保存的做法，非空时页面处于预览态 */
    private var pending: Variant? = null

    private val ui = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        recipeId = intent.getLongExtra(EXTRA_ID, 0L)

        binding.backButton.setOnClickListener { finish() }
        binding.editButton.setOnClickListener {
            startActivity(
                Intent(this, EditActivity::class.java).putExtra(EditActivity.EXTRA_ID, recipeId)
            )
        }
        binding.aiButton.setOnClickListener { askMissing() }
        binding.pinButton.setOnClickListener { togglePin() }
        binding.previewSave.setOnClickListener { savePending() }
        binding.previewDiscard.setOnClickListener {
            pending = null
            bind()
        }
    }

    override fun onResume() {
        super.onResume()
        bind()
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    private fun bind() {
        val recipe = db.getById(recipeId)
        if (recipe == null) {
            // 已被删除
            finish()
            return
        }
        current = recipe
        if (activeVariant >= recipe.variants.size) activeVariant = -1

        val previewing = pending != null
        val shown: Variant? = pending ?: recipe.variants.getOrNull(activeVariant)

        binding.detailName.text = recipe.name
        binding.detailCategory.text = getString(RecipeCategory.labelResOf(recipe.category))

        binding.detailMinutes.text = if (recipe.minutes > 0) {
            "${recipe.minutes}${getString(R.string.minutes_unit)}"
        } else ""
        binding.detailMinutes.visibility = if (recipe.minutes > 0) View.VISIBLE else View.GONE

        binding.detailServings.text = if (recipe.servings > 0) {
            "${recipe.servings}${getString(R.string.servings_unit)}"
        } else ""
        binding.detailServings.visibility = if (recipe.servings > 0) View.VISIBLE else View.GONE

        binding.detailDifficulty.text = getString(Difficulty.labelRes(recipe.difficulty))
        setSmallIcon(binding.detailMinutes, R.drawable.ic_schedule)
        setSmallIcon(binding.detailServings, R.drawable.ic_people)
        setSmallIcon(binding.detailDifficulty, R.drawable.ic_fire)

        val desc = shown?.description ?: recipe.description
        binding.detailDescription.text = desc.ifBlank { getString(R.string.detail_empty_desc) }

        bindCover(recipe)
        bindVideo(recipe)
        bindIngredients(shown?.ingredients ?: recipe.ingredients)
        bindSteps(shown?.steps ?: recipe.steps)
        bindVariantChips(recipe)

        binding.previewBar.visibility = if (previewing) View.VISIBLE else View.GONE
        binding.aiButton.visibility = if (previewing) View.GONE else View.VISIBLE
        binding.editButton.visibility = if (previewing) View.GONE else View.VISIBLE
        binding.pinButton.visibility = if (previewing) View.GONE else View.VISIBLE
        binding.pinButton.alpha = if (recipe.pinned) 1f else 0.45f
        binding.variantBar.visibility =
            if (!previewing && recipe.variants.isNotEmpty()) View.VISIBLE else View.GONE
    }

    // ---------------------------------------------------------------- 做法切换

    private fun bindVariantChips(recipe: Recipe) {
        binding.variantChips.removeAllViews()
        if (recipe.variants.isEmpty()) return

        val labels = mutableListOf(getString(R.string.variant_original))
        recipe.variants.forEach { labels.add(it.label.ifBlank { getString(R.string.ai_variant) }) }

        labels.forEachIndexed { index, label ->
            val chip = TextView(this).apply {
                text = label
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(7), dp(14), dp(7))
                background = ContextCompat.getDrawable(this@DetailActivity, R.drawable.bg_chip)
                setTextColor(ContextCompat.getColorStateList(this@DetailActivity, R.color.chip_text))
                isSelected = (index - 1) == activeVariant
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    activeVariant = index - 1
                    bind()
                }
                // 长按变体标签管理它：重命名 / 删除（「原做法」不可管理）
                setOnLongClickListener {
                    val vi = index - 1
                    if (vi >= 0) {
                        manageVariant(recipe, vi)
                        true
                    } else false
                }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) }
            binding.variantChips.addView(chip, lp)
        }
    }

    // ---------------------------------------------------------------- AI 换食材

    private fun askMissing() {
        val cfg = ApiConfig.get(this)
        if (!cfg.configured) {
            AlertDialog.Builder(this)
                .setTitle(R.string.ai_no_key_title)
                .setMessage(R.string.ai_no_key_msg)
                .setNegativeButton(R.string.ai_later, null)
                .setPositiveButton(R.string.ai_go_config) { _, _ ->
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
                .show()
            return
        }

        val input = EditText(this).apply {
            hint = getString(R.string.ai_variant_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setPadding(dp(20), dp(10), dp(20), dp(10))
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.ai_variant_title)
            .setMessage(R.string.ai_variant_msg)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ai_generate) { _, _ ->
                val request = input.text.toString().trim()
                if (request.isEmpty()) {
                    toast(getString(R.string.ai_variant_empty))
                } else {
                    generateVariant(request)
                }
            }
            .show()
    }

    private fun generateVariant(request: String) {
        val recipe = current ?: return
        val base = baseTextForVariant(recipe)

        val progress = AlertDialog.Builder(this)
            .setMessage(R.string.ai_variant_generating)
            .setCancelable(false)
            .show()

        executor.execute {
            val result = LlmClient.generateVariant(this, base, request)
            ui.post {
                // 生成要好几秒，其间用户很可能已经退出详情页
                runCatching { progress.dismiss() }
                if (isFinishing || isDestroyed) return@post
                result.fold(
                    onSuccess = { text ->
                        val parsed = RecipeParser.parse(text)
                        if (!parsed.ok) {
                            toast(parsed.hints.firstOrNull() ?: getString(R.string.ai_variant_fail))
                            return@fold
                        }
                        pending = Variant(
                            label = request.replace("\n", " ").take(12),
                            description = parsed.tips,
                            ingredients = parsed.ingredients
                                .map { Ingredient(it.name, it.amount) }
                                .toMutableList(),
                            steps = parsed.steps.toMutableList(),
                            createdAt = System.currentTimeMillis()
                        )
                        bind()
                    },
                    onFailure = { e ->
                        toast(e.message ?: getString(R.string.ai_variant_fail))
                    }
                )
            }
        }
    }

    /** 以当前看到的那一版为底稿，再让 AI 改 */
    private fun baseTextForVariant(recipe: Recipe): String {
        val shown = pending ?: recipe.variants.getOrNull(activeVariant) ?: return RecipeParser.stringify(recipe)
        val shadow = recipe.copy(
            description = shown.description,
            ingredients = shown.ingredients.map { Ingredient(it.name, it.amount) }.toMutableList(),
            steps = shown.steps.toMutableList()
        )
        return RecipeParser.stringify(shadow)
    }

    private fun savePending() {
        val recipe = current ?: return
        val variant = pending ?: return
        recipe.variants.add(variant)
        db.update(recipe)
        activeVariant = recipe.variants.size - 1
        pending = null
        bind()
        toast(getString(R.string.variant_saved))
    }

    // ---------------------------------------------------------------- 变体管理

    /** 长按变体标签：重命名或删除。删除只动变体，原做法不受影响 */
    private fun manageVariant(recipe: Recipe, index: Int) {
        val variant = recipe.variants.getOrNull(index) ?: return
        val label = variant.label.ifBlank { getString(R.string.ai_variant) }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.variant_manage) + " · " + label)
            .setItems(
                arrayOf(
                    getString(R.string.variant_rename),
                    getString(R.string.variant_delete)
                )
            ) { _, which ->
                when (which) {
                    0 -> renameVariant(recipe, index)
                    else -> deleteVariant(recipe, index)
                }
            }
            .show()
    }

    private fun renameVariant(recipe: Recipe, index: Int) {
        val input = EditText(this).apply {
            setText(recipe.variants.getOrNull(index)?.label.orEmpty())
            setSingleLine(true)
            setPadding(dp(20), dp(10), dp(20), dp(10))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.variant_new_name)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                recipe.variants.getOrNull(index)?.label = name
                db.update(recipe)
                bind()
                toast(getString(R.string.variant_renamed))
            }
            .show()
    }

    private fun deleteVariant(recipe: Recipe, index: Int) {
        val label = recipe.variants.getOrNull(index)?.label.orEmpty()
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.variant_delete_msg, label))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.dialog_delete) { _, _ ->
                recipe.variants.removeAt(index)
                if (activeVariant >= recipe.variants.size) activeVariant = -1
                db.update(recipe)
                bind()
                toast(getString(R.string.variant_deleted))
            }
            .show()
    }

    // ---------------------------------------------------------------- 常做标记

    private fun togglePin() {
        val recipe = current ?: return
        recipe.pinned = !recipe.pinned
        // 只改标记不动 updated_at，避免置顶把自己顶到「最近更新」第一位
        db.setPinned(recipe.id, recipe.pinned)
        bind()
        toast(getString(if (recipe.pinned) R.string.pin_on else R.string.pin_off))
    }

    // ---------------------------------------------------------------- 渲染

    private fun bindCover(recipe: Recipe) {
        if (recipe.cover.isNullOrBlank()) {
            binding.cover.setImageDrawable(null)
            binding.coverIcon.visibility = View.VISIBLE
            return
        }
        binding.coverIcon.visibility = View.GONE
        binding.cover.tag = recipe.cover
        val px = (resources.displayMetrics.widthPixels * 0.8f).toInt()
        ImageStore.loadAsync(this, recipe.cover, px) { bmp ->
            if (binding.cover.tag == recipe.cover && bmp != null) {
                binding.cover.setImageBitmap(bmp)
            }
        }
    }

    // ---------------------------------------------------------------- 视频

    private fun bindVideo(recipe: Recipe) {
        val url = recipe.videoUrl.trim()
        // 变体预览时不显示，避免误以为预览里的做法带了这个视频
        if (url.isEmpty() || pending != null) {
            binding.videoCard.visibility = View.GONE
            return
        }
        val type = recipe.videoType.takeIf { it.isNotBlank() } ?: VideoLink.detect(url)
        val site = VideoLink.siteLabel(url)

        binding.videoCard.visibility = View.VISIBLE
        binding.videoTitle.text = getString(R.string.video_card_title_fmt, site)
        binding.videoSub.text = if (VideoLink.canPlayInside(url, type)) {
            getString(R.string.video_card_sub)
        } else {
            getString(R.string.video_kind_outside)
        }
        binding.videoCard.setOnClickListener { openVideo(url, type, site) }
    }

    private fun openVideo(url: String, type: String, site: String) {
        val title = getString(R.string.video_card_title_fmt, site)

        // 直链和 B 站能就地播，直接进播放页
        if (VideoLink.canPlayInside(url, type)) {
            VideoActivity.start(this, url, type, title)
            return
        }

        // 小红书这类平台内嵌不可靠：给用户自己选，别硬塞 WebView 让人撞登录页
        AlertDialog.Builder(this)
            .setTitle(R.string.video_pick_title)
            .setMessage(getString(R.string.video_pick_msg, site))
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.video_open_with_app) { _, _ -> openOutside(url) }
            .setPositiveButton(R.string.video_open_inside) { _, _ ->
                VideoActivity.start(this, url, type, title)
            }
            .show()
    }

    /** 交给系统：装了小红书就唤起，否则浏览器 */
    private fun openOutside(url: String) {
        val ok = runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.isSuccess
        if (!ok) toast(getString(R.string.video_no_app))
    }

    private fun bindIngredients(items: List<Ingredient>) {
        binding.ingredientContainer.removeAllViews()
        val list = items.filterNot { it.isEmpty }
        if (list.isEmpty()) {
            binding.ingredientContainer.visibility = View.GONE
            return
        }
        binding.ingredientContainer.visibility = View.VISIBLE
        list.forEach { ing ->
            val item = ItemIngredientViewBinding.inflate(layoutInflater, binding.ingredientContainer, false)
            item.ingredientName.text = ing.name
            item.ingredientAmount.text = ing.amount
            binding.ingredientContainer.addView(item.root)
        }
    }

    private fun bindSteps(steps: List<String>) {
        binding.stepContainer.removeAllViews()
        val list = steps.filter { it.isNotBlank() }
        if (list.isEmpty()) {
            binding.stepContainer.visibility = View.GONE
            return
        }
        binding.stepContainer.visibility = View.VISIBLE
        list.forEachIndexed { index, text ->
            val item = ItemStepViewBinding.inflate(layoutInflater, binding.stepContainer, false)
            item.stepIndex.text = (index + 1).toString()
            item.stepText.text = text
            binding.stepContainer.addView(item.root)
        }
    }

    private fun setSmallIcon(view: TextView, resId: Int) {
        val size = (13 * resources.displayMetrics.density).toInt()
        val icon: Drawable? = ContextCompat.getDrawable(this, resId)?.mutate()
        icon?.setBounds(0, 0, size, size)
        view.setCompoundDrawables(icon, null, null, null)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val EXTRA_ID = "recipe_id"
    }
}
