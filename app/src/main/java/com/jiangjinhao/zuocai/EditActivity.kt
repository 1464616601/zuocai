package com.jiangjinhao.zuocai

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.jiangjinhao.zuocai.data.ApiConfig
import com.jiangjinhao.zuocai.data.Difficulty
import com.jiangjinhao.zuocai.data.LlmClient
import com.jiangjinhao.zuocai.data.Recipe
import com.jiangjinhao.zuocai.data.RecipeCategory
import com.jiangjinhao.zuocai.data.RecipeDb
import com.jiangjinhao.zuocai.data.RecipeParser
import com.jiangjinhao.zuocai.data.VideoLink
import com.jiangjinhao.zuocai.databinding.ActivityEditBinding
import com.jiangjinhao.zuocai.ui.ImageSearchDialog
import com.jiangjinhao.zuocai.util.ImageStore
import java.io.File
import java.util.concurrent.Executors

/**
 * 新建 / 编辑菜谱。
 * 主输入是一个文本框：粘贴任意格式的菜谱文本，实时解析成结构化的菜名、食材、步骤。
 */
class EditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditBinding
    private val db by lazy { RecipeDb.get(this) }
    private val ui = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    private var recipeId: Long = 0L
    private var origin: Recipe? = null
    private var draft: Recipe = Recipe()

    private var difficulty: Int = Difficulty.EASY
    private var originCover: String? = null
    private var pickedCover: String? = null
    private var coverRemoved = false

    private var dirty = false
    private var suppressWatcher = false
    private var suppressVideoWatcher = false

    /**
     * 用户主动点过「清除视频」。
     *
     * 文本框里的 `@视频：` 行还会被解析出来，这里标记一下，
     * 免得用户清空后又去编辑菜谱正文，视频被自动填回来。
     */
    private var videoCleared = false

    /**
     * 用户手动改过「人份 / 时长 / 难度」。
     *
     * 正文里的 `@份量/@时长/@难度` 会在每次解析后回写这三个输入框，
     * 而 `stringify()` 一定会输出 `@分类/@难度`，所以只要编辑已有菜谱就必然触发。
     * 不加这个开关的话，用户改完数字、再在正文里打一个字，改动就被静默改回去了。
     */
    private var metaEditedByUser = false
    private var suppressMetaWatcher = false
    private var userPickedCategory = false
    private var pendingCameraFile: File? = null
    private var previewExpanded = false

    private var lastParsed: RecipeParser.Parsed? = null
    private val parseTask = Runnable { runParse() }

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { handlePickedImage(it) } }

    private val takePicture = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            pendingCameraFile?.let { file ->
                if (file.exists() && file.length() > 0) handlePickedImage(Uri.fromFile(file))
            }
        }
        pendingCameraFile?.delete()
        pendingCameraFile = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        recipeId = intent.getLongExtra(EXTRA_ID, 0L)
        origin = if (recipeId > 0) db.getById(recipeId) else null
        draft = origin?.copyForEdit() ?: Recipe()

        setupViews()
        setupInputs()
        setupBackHandler()

        val src = origin
        if (src != null) {
            binding.editTitle.text = getString(R.string.edit_title_edit)
            binding.deleteButton.visibility = View.VISIBLE
            originCover = src.cover
            difficulty = Difficulty.normalize(src.difficulty)
            binding.servingsInput.setText(src.servings.toString())
            binding.minutesInput.setText(if (src.minutes > 0) src.minutes.toString() else "")
            userPickedCategory = true
            setTextSafely(RecipeParser.stringify(src))
            binding.videoInput.setText(src.videoUrl)
        } else {
            binding.editTitle.text = getString(R.string.edit_title_new)
            binding.servingsInput.setText("2")
        }

        renderDifficulty()
        renderCategoryChips()
        renderCover()
        renderVideoState()
        runParse()
        dirty = false
    }

    override fun onDestroy() {
        ui.removeCallbacks(parseTask)
        executor.shutdownNow()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- 初始化

    private fun setupViews() {
        binding.closeButton.setOnClickListener { confirmExit() }
        binding.saveButton.setOnClickListener { save() }
        binding.deleteButton.setOnClickListener { confirmDelete() }

        binding.parseToggle.setOnClickListener {
            previewExpanded = !previewExpanded
            binding.previewBox.visibility = if (previewExpanded) View.VISIBLE else View.GONE
            binding.parseToggle.setText(
                if (previewExpanded) R.string.parse_collapse else R.string.parse_preview
            )
            if (previewExpanded) renderPreview()
        }

        binding.moreHeader.setOnClickListener {
            val open = binding.moreBox.visibility == View.VISIBLE
            binding.moreBox.visibility = if (open) View.GONE else View.VISIBLE
            binding.moreArrow.setText(if (open) R.string.expand else R.string.collapse)
        }

        binding.aiButton.setOnClickListener { onAiClick() }
        binding.configButton.setOnClickListener { openSettings() }

        binding.videoClear.setOnClickListener {
            suppressVideoWatcher = true
            binding.videoInput.setText("")
            suppressVideoWatcher = false
            videoCleared = true
            dirty = true
            renderVideoState()
        }

        binding.coverPick.setOnClickListener { showCoverOptions() }
        binding.coverClear.setOnClickListener {
            pickedCover?.let { ImageStore.delete(this, it) }
            pickedCover = null
            coverRemoved = true
            dirty = true
            renderCover()
        }

        binding.difficultyInput.setOnClickListener {
            difficulty = when (difficulty) {
                Difficulty.EASY -> Difficulty.MEDIUM
                Difficulty.MEDIUM -> Difficulty.HARD
                else -> Difficulty.EASY
            }
            dirty = true
            metaEditedByUser = true
            renderDifficulty()
        }
    }

    private fun setupInputs() {
        binding.recipeText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (suppressWatcher) return
                dirty = true
                ui.removeCallbacks(parseTask)
                ui.postDelayed(parseTask, 320)
                if (previewExpanded) {
                    previewExpanded = false
                    binding.previewBox.visibility = View.GONE
                    binding.parseToggle.setText(R.string.parse_preview)
                }
            }
        })

        val numberWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (suppressMetaWatcher) return
                dirty = true
                metaEditedByUser = true
            }
        }
        binding.servingsInput.addTextChangedListener(numberWatcher)
        binding.minutesInput.addTextChangedListener(numberWatcher)

        binding.videoInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (suppressVideoWatcher) return
                if (!s.isNullOrBlank()) videoCleared = false
                dirty = true
                renderVideoState()
            }
        })
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = confirmExit()
        })
    }

    private fun setTextSafely(text: String) {
        suppressWatcher = true
        binding.recipeText.setText(text)
        binding.recipeText.setSelection(binding.recipeText.text?.length ?: 0)
        suppressWatcher = false
    }

    // ---------------------------------------------------------------- 解析

    private fun runParse() {
        val text = binding.recipeText.text?.toString().orEmpty()
        val parsed = RecipeParser.parse(text)
        lastParsed = parsed

        if (text.isBlank()) {
            binding.parseDot.setBackgroundResource(R.drawable.bg_dot_idle)
            binding.parseMsg.setText(R.string.parse_waiting)
            binding.parseToggle.visibility = View.GONE
            return
        }

        if (parsed.ok) {
            binding.parseDot.setBackgroundResource(R.drawable.bg_dot_ok)
            binding.parseMsg.text = buildSummary(parsed)
            binding.parseToggle.visibility = View.VISIBLE
        } else {
            binding.parseDot.setBackgroundResource(R.drawable.bg_dot_warn)
            binding.parseMsg.text = parsed.hints.firstOrNull() ?: getString(R.string.parse_fail)
            binding.parseToggle.visibility = View.GONE
        }

        if (!userPickedCategory) {
            draft.category = parsed.category
            renderCategoryChips()
        }

        // 文本里带 @分类/@时长/@份量/@难度 时（导出文件就是这个格式），
        // 把数值同步到「更多选项」，否则从备份导入的菜谱会丢掉这些信息。
        // 用户一旦自己动过这三项，就不再回写——否则改完数字再动正文会被静默改回去。
        val hasMeta = text.lineSequence().any { it.trimStart().startsWith("@") }
        if (hasMeta && !metaEditedByUser) {
            val wasDirty = dirty
            suppressMetaWatcher = true
            if (parsed.servings > 0) {
                binding.servingsInput.setText(parsed.servings.toString())
            }
            binding.minutesInput.setText(
                if (parsed.minutes > 0) parsed.minutes.toString() else ""
            )
            difficulty = Difficulty.normalize(parsed.difficulty)
            renderDifficulty()
            suppressMetaWatcher = false
            dirty = wasDirty
        }

        // 文本框里带 `@视频：` 时（比如导入的 TXT）回填到视频输入框；
        // 输入框已有内容就以输入框为准，别用旧文本覆盖用户刚改的值
        if (parsed.videoUrl.isNotBlank() && !videoCleared && binding.videoInput.text.isNullOrBlank()) {
            val wasDirty = dirty
            suppressVideoWatcher = true
            binding.videoInput.setText(parsed.videoUrl)
            suppressVideoWatcher = false
            renderVideoState()
            dirty = wasDirty
        }
    }

    // ---------------------------------------------------------------- 视频

    private fun renderVideoState() {
        val raw = binding.videoInput.text?.toString().orEmpty()
        val url = VideoLink.normalizeInput(raw)

        if (url.isEmpty()) {
            if (raw.isBlank()) {
                binding.videoState.visibility = View.GONE
                binding.videoClear.visibility = View.GONE
            } else {
                // 填了东西却抠不出链接：说清楚，别让用户以为已经存上了
                binding.videoState.setText(R.string.video_invalid)
                binding.videoState.setTextColor(ContextCompat.getColor(this, R.color.danger))
                binding.videoState.visibility = View.VISIBLE
                binding.videoClear.visibility = View.VISIBLE
            }
            return
        }

        val type = VideoLink.detect(url)
        val kind = if (VideoLink.canPlayInside(url, type)) {
            R.string.video_kind_inside
        } else {
            R.string.video_kind_outside
        }
        binding.videoState.text =
            getString(R.string.video_detected_fmt, VideoLink.siteLabel(url), getString(kind))
        binding.videoState.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary))
        binding.videoState.visibility = View.VISIBLE
        binding.videoClear.visibility = View.VISIBLE
    }

    private fun buildSummary(p: RecipeParser.Parsed): String {
        val hasIng = p.ingredients.isNotEmpty()
        val hasStep = p.steps.isNotEmpty()
        return when {
            hasIng && hasStep -> getString(R.string.parse_ok_fmt, p.name, p.ingredients.size, p.steps.size)
            hasIng -> getString(R.string.parse_ok_no_step_fmt, p.name, p.ingredients.size, 0)
            else -> getString(R.string.parse_ok_no_ing_fmt, p.name, 0, p.steps.size)
        }
    }

    private fun renderPreview() {
        val p = lastParsed ?: return
        if (!p.ok) return

        binding.pvTitle.text = p.name

        binding.pvIngredients.text = buildString {
            append(getString(R.string.pv_ingredients_fmt, p.ingredients.size)).append('\n')
            p.ingredients.forEach { i ->
                append("· ").append(i.name)
                if (i.amount.isNotBlank()) append("  ").append(i.amount)
                append('\n')
            }
        }.trimEnd()

        binding.pvSteps.text = buildString {
            append(getString(R.string.pv_steps_fmt, p.steps.size)).append('\n')
            p.steps.forEachIndexed { idx, s -> append(idx + 1).append(". ").append(s).append('\n') }
            if (p.tips.isNotBlank()) append('\n').append(p.tips)
        }.trimEnd()
    }

    // ---------------------------------------------------------------- 分类 / 难度

    private fun renderCategoryChips() {
        val bar = binding.categoryBar
        bar.removeAllViews()
        val d = resources.displayMetrics.density

        RecipeCategory.values().forEach { cat ->
            val chip = TextView(this).apply {
                text = getString(cat.labelRes)
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(
                    (d * 6).toInt(), (d * 9).toInt(),
                    (d * 6).toInt(), (d * 9).toInt()
                )
                background = ContextCompat.getDrawable(this@EditActivity, R.drawable.bg_chip)
                setTextColor(ContextCompat.getColorStateList(this@EditActivity, R.color.chip_text))
                isSelected = cat.key == draft.category
                isClickable = true
                setOnClickListener {
                    draft.category = cat.key
                    userPickedCategory = true
                    dirty = true
                    renderCategoryChips()
                }
            }
            val lp = GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins((d * 3).toInt(), (d * 3).toInt(), (d * 3).toInt(), (d * 3).toInt())
            }
            bar.addView(chip, lp)
        }
    }

    private fun renderDifficulty() {
        binding.difficultyInput.setText(getString(Difficulty.labelRes(difficulty)))
    }

    // ---------------------------------------------------------------- 封面

    private fun renderCover() {
        val name = when {
            coverRemoved -> null
            pickedCover != null -> pickedCover
            else -> originCover
        }

        if (name.isNullOrBlank()) {
            binding.coverThumb.visibility = View.GONE
            binding.coverThumb.setImageDrawable(null)
            binding.coverPlaceholder.visibility = View.VISIBLE
            binding.coverClear.visibility = View.GONE
            return
        }

        binding.coverThumb.visibility = View.VISIBLE
        binding.coverPlaceholder.visibility = View.GONE
        binding.coverClear.visibility = View.VISIBLE

        val px = (78 * resources.displayMetrics.density).toInt()
        ImageStore.loadAsync(this, name, px) { bmp ->
            if (bmp != null && !isFinishing) binding.coverThumb.setImageBitmap(bmp)
        }
    }

    private fun showCoverOptions() {
        val hasCover = !(if (coverRemoved) null else (pickedCover ?: originCover)).isNullOrBlank()
        val actions = mutableListOf<String>()
        actions.add(getString(R.string.net_image))           // 0
        actions.add(getString(R.string.pick_from_gallery))   // 1
        actions.add(getString(R.string.take_photo))          // 2
        actions.add(getString(R.string.ai_gen_cover))        // 3
        if (hasCover) actions.add(getString(R.string.remove_cover)) // 4

        AlertDialog.Builder(this)
            .setItems(actions.toTypedArray()) { _, which ->
                when (which) {
                    0 -> openImageSearch()
                    1 -> pickImage.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                    2 -> launchCamera()
                    3 -> generateCoverByAi()
                    else -> {
                        pickedCover?.let { ImageStore.delete(this, it) }
                        pickedCover = null
                        coverRemoved = true
                        dirty = true
                        renderCover()
                    }
                }
            }
            .show()
    }

    /** 从网络搜图并设为封面 */
    private fun openImageSearch() {
        val parsed = RecipeParser.parse(binding.recipeText.text?.toString())
        ImageSearchDialog(this, parsed.name) { bmp ->
            val saved = ImageStore.saveBitmap(this, bmp)
            bmp.recycle()
            if (saved == null) {
                Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show()
                return@ImageSearchDialog
            }
            val old = pickedCover
            pickedCover = saved
            coverRemoved = false
            if (old != null) ImageStore.delete(this, old)
            dirty = true
            renderCover()
            Toast.makeText(this, R.string.net_image_done, Toast.LENGTH_SHORT).show()
        }.show()
    }

    /** 用大模型生成菜品图并设为封面 */
    private fun generateCoverByAi() {
        val cfg = ApiConfig.get(this)
        if (!cfg.configured) {
            AlertDialog.Builder(this)
                .setTitle(R.string.ai_no_key_title)
                .setMessage(R.string.ai_no_key_msg)
                .setNegativeButton(R.string.ai_later, null)
                .setPositiveButton(R.string.ai_go_config) { _, _ -> openSettings() }
                .show()
            return
        }

        val parsed = RecipeParser.parse(binding.recipeText.text?.toString())
        if (parsed.name.isBlank()) {
            Toast.makeText(this, R.string.ai_gen_cover_need_name, Toast.LENGTH_SHORT).show()
            return
        }

        val prompt = LlmClient.buildImagePrompt(
            parsed.name,
            parsed.ingredients.map { it.name }
        )

        val d = resources.displayMetrics.density
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((d * 22).toInt(), (d * 6).toInt(), (d * 22).toInt(), 0)
        }
        box.addView(TextView(this).apply {
            text = getString(R.string.ai_gen_cover_desc)
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@EditActivity, R.color.text_secondary))
        })

        generatingDialog = AlertDialog.Builder(this)
            .setTitle(R.string.ai_gen_cover_title)
            .setView(box)
            .setCancelable(false)
            .show()

        executor.execute {
            val result = LlmClient.generateImage(this, prompt)
            val saved = result.mapCatching { bytes ->
                val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: throw IllegalStateException("图片解码失败")
                ImageStore.saveBitmap(this, bmp)?.also { bmp.recycle() }
                    ?: throw IllegalStateException("图片保存失败")
            }
            ui.post {
                dismissGenerating()
                if (isFinishing || isDestroyed) return@post
                saved.onSuccess { name ->
                    val old = pickedCover
                    pickedCover = name
                    coverRemoved = false
                    if (old != null) ImageStore.delete(this, old)
                    dirty = true
                    renderCover()
                    Toast.makeText(this, R.string.ai_gen_cover_ok, Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    AlertDialog.Builder(this)
                        .setTitle(R.string.ai_gen_cover_fail)
                        .setMessage(err.message.orEmpty())
                        .setPositiveButton(R.string.ai_later, null)
                        .show()
                }
            }
        }
    }

    private fun launchCamera() {
        val dir = File(cacheDir, "cam").apply { if (!exists()) mkdirs() }
        val file = File(dir, "cap_${System.currentTimeMillis()}.jpg")
        pendingCameraFile = file
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        runCatching { takePicture.launch(uri) }
            .onFailure {
                pendingCameraFile = null
                Toast.makeText(this, R.string.camera_unavailable, Toast.LENGTH_SHORT).show()
            }
    }

    private fun handlePickedImage(uri: Uri) {
        val name = ImageStore.save(this, uri)
        if (name == null) {
            Toast.makeText(this, R.string.image_load_failed, Toast.LENGTH_SHORT).show()
            return
        }
        pickedCover?.let { ImageStore.delete(this, it) }
        pickedCover = name
        coverRemoved = false
        dirty = true
        renderCover()
    }

    // ---------------------------------------------------------------- AI

    private fun onAiClick() {
        val cfg = ApiConfig.get(this)
        if (!cfg.configured) {
            AlertDialog.Builder(this)
                .setTitle(R.string.ai_no_key_title)
                .setMessage(R.string.ai_no_key_msg)
                .setNegativeButton(R.string.ai_later, null)
                .setPositiveButton(R.string.ai_go_config) { _, _ -> openSettings() }
                .show()
            return
        }
        askDishName { dish -> generateRecipe(dish, cfg.model) }
    }

    private fun askDishName(onConfirm: (String) -> Unit) {
        val d = resources.displayMetrics.density
        val input = EditText(this).apply {
            hint = getString(R.string.ai_ask_hint)
            setSingleLine()
            textSize = 15f
        }
        val wrapper = FrameLayout(this).apply {
            setPadding((d * 22).toInt(), (d * 8).toInt(), (d * 22).toInt(), 0)
            addView(
                input,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.ai_ask_title)
            .setView(wrapper)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ai_generate) { _, _ ->
                val dish = input.text?.toString()?.trim().orEmpty()
                if (dish.isNotEmpty()) onConfirm(dish)
            }
            .show()
    }

    private var generatingDialog: AlertDialog? = null

    private fun generateRecipe(dish: String, model: String) {
        val d = resources.displayMetrics.density
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((d * 22).toInt(), (d * 6).toInt(), (d * 22).toInt(), 0)
        }
        box.addView(TextView(this).apply {
            text = getString(R.string.ai_generating_desc_fmt, model)
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@EditActivity, R.color.text_secondary))
        })

        generatingDialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.ai_generating_fmt, dish))
            .setView(box)
            .setCancelable(false)
            .show()

        executor.execute {
            val result = LlmClient.generateRecipe(this, dish)
            ui.post {
                dismissGenerating()
                if (isFinishing || isDestroyed) return@post
                result.onSuccess { text -> applyAiText(text) }
                    .onFailure { err -> showAiError(err.message.orEmpty()) }
            }
        }
    }

    private fun dismissGenerating() {
        generatingDialog?.dismiss()
        generatingDialog = null
    }

    private fun showAiError(message: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.ai_failed)
            .setMessage(message.ifBlank { getString(R.string.save_failed) })
            .setPositiveButton(R.string.ai_later, null)
            .show()
    }

    private fun applyAiText(text: String) {
        val current = binding.recipeText.text?.toString()?.trim().orEmpty()
        if (current.isBlank()) {
            setTextSafely(text)
            afterAiApplied()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.ai_replace_title)
            .setMessage(R.string.ai_replace_msg)
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.ai_append) { _, _ ->
                setTextSafely(current + "\n\n" + text)
                afterAiApplied()
            }
            .setPositiveButton(R.string.ai_replace) { _, _ ->
                setTextSafely(text)
                afterAiApplied()
            }
            .show()
    }

    private fun afterAiApplied() {
        dirty = true
        userPickedCategory = false
        binding.recipeText.setSelection(binding.recipeText.text?.length ?: 0)
        runParse()
        Toast.makeText(this, R.string.ai_ok, Toast.LENGTH_SHORT).show()
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    // ---------------------------------------------------------------- 保存

    private fun save() {
        val parsed = RecipeParser.parse(binding.recipeText.text?.toString())
        if (!parsed.ok) {
            Toast.makeText(
                this,
                parsed.hints.firstOrNull() ?: getString(R.string.parse_fail),
                Toast.LENGTH_SHORT
            ).show()
            binding.recipeText.requestFocus()
            return
        }
        if (parsed.name.isBlank()) {
            Toast.makeText(this, R.string.toast_need_name, Toast.LENGTH_SHORT).show()
            return
        }

        val recipe = draft.copyForEdit()
        recipe.id = recipeId
        recipe.name = parsed.name
        recipe.category = draft.category
        recipe.ingredients = parsed.ingredients.toMutableList()
        recipe.steps = parsed.steps.toMutableList()
        recipe.description = parsed.tips
        recipe.difficulty = difficulty

        val servings = binding.servingsInput.text?.toString()?.toIntOrNull() ?: 0
        val minutes = binding.minutesInput.text?.toString()?.toIntOrNull() ?: 0
        recipe.servings = servings.coerceIn(0, 99)
        recipe.minutes = minutes.coerceIn(0, 9999)

        recipe.cover = when {
            coverRemoved -> null
            pickedCover != null -> pickedCover
            else -> originCover
        }

        // 视频以输入框为准：没有 `@视频：` 行的菜谱，正文解析不出链接，只能从这里拿
        val videoUrl = VideoLink.normalizeInput(binding.videoInput.text?.toString())
        recipe.videoUrl = videoUrl
        recipe.videoType = if (videoUrl.isEmpty()) "" else VideoLink.detect(videoUrl)

        // insert 失败返回 -1、update 影响 0 行都不抛异常，只看 isSuccess 会把失败当成功
        val rows = runCatching {
            if (recipeId > 0) db.update(recipe) else if (db.insert(recipe) > 0) 1 else 0
        }.getOrDefault(0)

        if (rows <= 0) {
            Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show()
            return
        }

        if (!originCover.isNullOrBlank() && originCover != recipe.cover) {
            ImageStore.delete(this, originCover)
        }

        dirty = false
        Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_delete_title)
            .setMessage(R.string.dialog_delete_msg)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> doDelete() }
            .show()
    }

    private fun doDelete() {
        // 先确认记录真的删掉了，再清理封面文件——顺序反了会出现「菜还在但没图」
        val rows = runCatching { db.delete(recipeId) }.getOrDefault(0)
        if (rows <= 0) {
            Toast.makeText(this, R.string.delete_failed, Toast.LENGTH_SHORT).show()
            return
        }
        ImageStore.delete(this, originCover)
        ImageStore.delete(this, pickedCover)
        pickedCover = null
        dirty = false
        Toast.makeText(this, R.string.toast_deleted, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun confirmExit() {
        if (!dirty) { finish(); return }
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_discard_title)
            .setMessage(R.string.dialog_discard_msg)
            .setNegativeButton(R.string.keep_editing, null)
            .setPositiveButton(R.string.discard) { _, _ ->
                pickedCover?.let { ImageStore.delete(this, it) }
                pickedCover = null
                finish()
            }
            .show()
    }

    companion object {
        const val EXTRA_ID = "recipe_id"
    }
}
