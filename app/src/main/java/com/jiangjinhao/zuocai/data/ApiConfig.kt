package com.jiangjinhao.zuocai.data

import android.content.Context

/**
 * 大模型接口配置。Key 只保存在本机 SharedPreferences，不上传。
 */
object ApiConfig {

    data class Config(
        val baseUrl: String = DEFAULT_URL,
        val model: String = DEFAULT_MODEL,
        val apiKey: String = "",
        val imageModel: String = DEFAULT_IMAGE_MODEL
    ) {
        val configured: Boolean get() = apiKey.isNotBlank()
    }

    // 默认智谱 glm-4-flash：实测可用、响应快、不消耗思考 token，且属官方免费模型。
    // 说明：glm-4.7-flash 虽然更新，但免费额度限流严重（实测频繁 429）；
    //      glm-4.5-flash 默认开启 thinking，思考过程会吃掉输出额度，不适合短文本生成。
    const val DEFAULT_URL = "https://open.bigmodel.cn/api/paas/v4"
    const val DEFAULT_MODEL = "glm-4-flash"

    /**
     * 出厂自带的 Key：装上就能用，用户在设置页填了自己的 Key 后以用户的为准。
     *
     * 注意：Key 编译进 APK 就等于公开，额度谁都能消耗。个人自用没问题，
     * 公开发布前要么换成服务端代理，要么接受被刷额度的风险。
     */
    const val DEFAULT_API_KEY = "0214776f7c53423bb64eb4c20261dc0b.j8uG29SXkMoy0uNf"

    // 备选：硅基流动（9B 以下模型永久免费，接口返回 CORS 头，网页版可用）
    const val ALT_URL = "https://api.siliconflow.cn/v1"
    const val ALT_MODEL = "Qwen/Qwen2.5-7B-Instruct"

    const val ZHIPU_URL = "https://open.bigmodel.cn/api/paas/v4"
    const val ZHIPU_MODEL_FLASH = "glm-4-flash"
    const val ZHIPU_MODEL_FLASH_250414 = "glm-4-flash-250414"

    // 图像生成：cogview-3-flash 免费，但生成的图带「AI生成」水印
    const val DEFAULT_IMAGE_MODEL = "cogview-3-flash"

    private const val PREFS = "zuocai_llm"
    private const val K_URL = "base_url"
    private const val K_MODEL = "model"
    private const val K_KEY = "api_key"
    private const val K_IMG_MODEL = "image_model"

    fun get(context: Context): Config {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Config(
            baseUrl = p.getString(K_URL, DEFAULT_URL)?.takeIf { it.isNotBlank() } ?: DEFAULT_URL,
            model = p.getString(K_MODEL, DEFAULT_MODEL)?.takeIf { it.isNotBlank() } ?: DEFAULT_MODEL,
            // 用户没填就用出厂 Key，保证开箱即用
            apiKey = p.getString(K_KEY, "").orEmpty().ifBlank { DEFAULT_API_KEY },
            imageModel = p.getString(K_IMG_MODEL, DEFAULT_IMAGE_MODEL)
                ?.takeIf { it.isNotBlank() } ?: DEFAULT_IMAGE_MODEL
        )
    }

    fun save(context: Context, config: Config) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(K_URL, config.baseUrl.trim())
            .putString(K_MODEL, config.model.trim())
            .putString(K_KEY, config.apiKey.trim())
            .putString(K_IMG_MODEL, config.imageModel.trim().ifEmpty { DEFAULT_IMAGE_MODEL })
            .apply()
    }
}
