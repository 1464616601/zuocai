package com.jiangjinhao.zuocai.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * 调用 OpenAI 兼容的 /chat/completions 接口生成菜谱。
 * 同步方法，必须在后台线程调用。
 */
object LlmClient {

    private const val CONNECT_TIMEOUT = 15_000
    private const val READ_TIMEOUT = 60_000

    fun buildPrompt(dish: String): String = """
        你是一位专业中餐厨师，请为「$dish」写一份可以直接下厨的菜谱。

        只输出菜谱正文，不要开场白、解释或结尾。严格照下面的示例格式，把内容换成这道菜的：

        # 番茄炒蛋
        ### 食材
        番茄 2 个、鸡蛋 3 个、小葱 1 根、盐 适量、食用油 2 勺
        ### 步骤
        1. 番茄切块，鸡蛋加少许盐打散。
        2. 热锅倒油，蛋液下锅凝固后盛出。
        3. 底油下番茄炒出汁，倒回鸡蛋翻炒均匀，加盐调味出锅。
        > 小贴士：蛋液里加一勺清水，炒出来更嫩。

        要求：
        - 第一行必须是「# $dish」，不要把「菜名」两个字照抄成标题
        - 食材 5–9 项，用量写具体（200g / 2 勺 / 半个），没有固定用量写「适量」
        - 步骤 3–6 步，每步 20–60 字，写清火候和时间
        - 小贴士一句话，点明容易失败的地方
    """.trimIndent()

    /**
     * @return Result.success(菜谱文本) 或 Result.failure(可直接展示的中文错误)
     */
    fun generateRecipe(context: Context, dish: String): Result<String> {
        val cfg = ApiConfig.get(context)
        if (!cfg.configured) {
            return Result.failure(IllegalStateException("还没配置 API Key"))
        }
        return try {
            Result.success(doRequest(cfg, buildPrompt(dish), 3000))
        } catch (e: Exception) {
            Result.failure(IllegalStateException(friendlyMessage(e), e))
        }
    }

    /**
     * 换食材用的提示词：给出现有做法 + 用户手头的情况，让模型只改受影响的部分。
     */
    fun buildVariantPrompt(recipeText: String, request: String): String = """
        你是一位专业中餐厨师。下面是同一道菜现有的做法，用户临时缺东西或条件变了，
        请给出一份仍然好吃的替代方案。

        【现有做法】
        $recipeText

        【用户的情况】
        $request

        只输出改好后的菜谱正文，不要开场白、解释或结尾。严格照现有做法的格式：

        # 菜名
        ### 食材
        ...
        ### 步骤
        1. ...
        > 小贴士：...

        要求：
        - 第一行沿用原菜名，不要加「改良版」「替代版」这类后缀
        - 只改受影响的部分，其他食材和步骤尽量保持原样
        - 缺的食材直接换成替代品，并在小贴士里说明替代后口感有什么变化
        - 步骤 3–6 步，写清火候和时间
    """.trimIndent()

    /**
     * 基于现有做法生成一个变体。
     * @return Result.success(菜谱文本) 或 Result.failure(可直接展示的中文错误)
     */
    fun generateVariant(context: Context, recipeText: String, request: String): Result<String> {
        val cfg = ApiConfig.get(context)
        if (!cfg.configured) {
            return Result.failure(IllegalStateException("还没配置 API Key"))
        }
        return try {
            Result.success(doRequest(cfg, buildVariantPrompt(recipeText, request), 2500))
        } catch (e: Exception) {
            Result.failure(IllegalStateException(friendlyMessage(e), e))
        }
    }

    /**
     * 用一次极小的请求验证 Key 与模型是否可用。
     * @return Result.success(模型回了什么) 或 Result.failure(原因)
     */
    fun testConnection(context: Context): Result<String> {
        val cfg = ApiConfig.get(context)
        if (!cfg.configured) {
            return Result.failure(IllegalStateException("还没填 API Key"))
        }
        return try {
            val reply = doRequest(cfg, "只回复两个字：可用", 24).trim()
            Result.success(reply.ifBlank { "可用" })
        } catch (e: Exception) {
            Result.failure(IllegalStateException(friendlyMessage(e), e))
        }
    }

    /**
     * 生成菜品图并下载为字节数组。
     * 默认用 cogview-3-flash（免费），生成的图带「AI生成」水印，属平台合规标识。
     */
    fun generateImage(context: Context, prompt: String): Result<ByteArray> {
        val cfg = ApiConfig.get(context)
        if (!cfg.configured) {
            return Result.failure(IllegalStateException("还没配置 API Key"))
        }
        return try {
            val endpoint = cfg.baseUrl.trimEnd('/') + "/images/generations"
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = 180_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Authorization", "Bearer ${cfg.apiKey}")
                setRequestProperty("Accept", "application/json")
            }

            val body = JSONObject().apply {
                put("model", cfg.imageModel)
                put("prompt", prompt)
                put("size", "1024x1024")
            }

            val imageUrl = try {
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.let {
                    BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { br ->
                        buildString { while (true) { val l = br.readLine() ?: break; append(l).append('\n') } }
                    }
                }.orEmpty()

                if (code !in 200..299) {
                    throw IllegalStateException(explainImageError(code, text))
                }
                JSONObject(text).optJSONArray("data")
                    ?.optJSONObject(0)
                    ?.optString("url")
                    ?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("图像接口没有返回图片地址")
            } finally {
                conn.disconnect()
            }

            // 图片 URL 带签名会过期，立刻下载
            val dl = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = 90_000
                instanceFollowRedirects = true
            }
            try {
                if (dl.responseCode !in 200..299) {
                    throw IllegalStateException("图片下载失败（HTTP ${dl.responseCode}）")
                }
                dl.inputStream.use { it.readBytes() }
            } finally {
                dl.disconnect()
            }.let { Result.success(it) }
        } catch (e: Exception) {
            Result.failure(IllegalStateException(friendlyMessage(e), e))
        }
    }

    private fun explainImageError(code: Int, body: String): String {
        val friendly = when (code) {
            401, 403 -> "API Key 无效，或没有该图像模型的权限"
            404 -> "图像模型名不对，或接口地址有误"
            429 -> "生成太频繁或免费额度用完，稍后再试"
            in 500..599 -> "图像服务暂时不可用"
            else -> "图片生成失败（HTTP $code）"
        }
        val detail = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")
        }.getOrNull()?.takeIf { it.isNotBlank() }?.take(100)
        return if (detail != null) "$friendly\n\n（服务端：$detail）" else friendly
    }

    /** 根据菜名与主要食材拼出图像提示词 */
    fun buildImagePrompt(name: String, ingredients: List<String>): String {
        val main = ingredients.filter { it.isNotBlank() }.take(3).joinToString("、")
        return buildString {
            append("一盘").append(name)
            if (main.isNotEmpty()) append("，主要食材是").append(main)
            append("，白色瓷盘盛装，暖色调，俯拍摄影，高清美食摄影，自然光，浅景深，画面中不要出现文字")
        }
    }

    private fun doRequest(cfg: ApiConfig.Config, prompt: String, maxTokens: Int): String {
        val endpoint = cfg.baseUrl.trimEnd('/') + "/chat/completions"
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer ${cfg.apiKey}")
            setRequestProperty("Accept", "application/json")
        }

        val body = JSONObject().apply {
            put("model", cfg.model)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            }))
            put("temperature", 0.7)
            // 智谱部分模型默认开启 thinking，思考过程也消耗额度，留足余量避免正文被截断
            put("max_tokens", maxTokens)
            put("stream", false)
        }

        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.let {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { br ->
                    buildString { while (true) { val l = br.readLine() ?: break; append(l).append('\n') } }
                }
            }.orEmpty()

            if (code !in 200..299) {
                throw IllegalStateException(explainHttpError(code, text))
            }

            val message = JSONObject(text)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")

            val content = message?.optString("content").orEmpty()

            if (content.isBlank()) {
                // 部分模型（如 glm-4.5-flash）默认开启 thinking，思考 token 会吃满额度导致正文为空
                val reasoning = message?.optString("reasoning_content").orEmpty()
                throw IllegalStateException(
                    if (reasoning.isNotBlank()) {
                        "「${cfg.model}」默认开启深度思考，思考过程占满了输出额度。换成 glm-4-flash 就能正常出结果"
                    } else {
                        "接口返回了空内容：可能是模型名不对、免费额度用尽，或被限流"
                    }
                )
            }
            return content.trim()
        } finally {
            conn.disconnect()
        }
    }

    /** 把异常翻译成用户能看懂的一句话 */
    private fun friendlyMessage(e: Throwable): String = when (e) {
        is java.net.UnknownHostException -> "无法连接服务器，检查网络，或确认 API 地址填写正确"
        is java.net.SocketTimeoutException -> "请求超时，网络较慢或服务没有响应"
        is javax.net.ssl.SSLException -> "HTTPS 连接失败，请检查 API 地址"
        is IllegalStateException -> e.message ?: "未知错误"
        else -> e.message ?: e.javaClass.simpleName
    }

    private fun explainHttpError(code: Int, body: String): String {
        val friendly = when (code) {
            401, 403 -> "API Key 无效或没有该模型的权限，请到设置里检查"
            404 -> "接口地址或模型名不对，请检查设置"
            429 -> "请求太频繁，触发了限流，等一会儿再试"
            in 500..599 -> "服务端暂时不可用，稍后再试"
            else -> "请求失败（HTTP $code）"
        }

        // 服务端返回的原文只作为辅助信息，截断后附在括号里
        val detail = runCatching {
            JSONObject(body).let { o ->
                o.optJSONObject("error")?.optString("message")?.ifBlank { null }
                    ?: o.optString("message").ifBlank { null }
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }?.take(100)

        return if (detail != null) "$friendly\n\n（服务端：$detail）" else friendly
    }
}
