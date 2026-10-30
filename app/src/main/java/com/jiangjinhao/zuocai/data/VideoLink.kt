package com.jiangjinhao.zuocai.data

/**
 * 视频链接解析：从分享文案里抠出 URL、识别平台、判断能不能在 App 内直接播。
 *
 * 三种类型：
 *  - [TYPE_DIRECT]   mp4 / m3u8 等直链，用系统播放器播，最稳
 *  - [TYPE_BILIBILI] 能解析出 BV 号的 B 站视频，走官方播放器 iframe
 *  - [TYPE_EXTERNAL] 其余（小红书、抖音、B 站短链……），只能用 WebView 兜底，
 *                    或交给对应的 App / 浏览器打开
 *
 * 小红书没有面向开发者的嵌入接口，抓直链又需要伪造签名、且链接几小时就过期，
 * 所以这里不做抓取，一律归到 external，由用户决定用 WebView 还是小红书 App 打开。
 */
object VideoLink {

    const val TYPE_DIRECT = "direct"
    const val TYPE_BILIBILI = "bilibili"
    const val TYPE_EXTERNAL = "external"

    /**
     * 分享文案里正文和链接混在一起，抓第一个 http(s) 链接。
     *
     * 字符集里排掉中文标点：小红书分享文案常写成 `http://xhslink.com/a/xxx，复制本条信息`，
     * 中间没有空格，不排除的话会把「，复制本条信息」整段当成链接的一部分。
     */
    private val RE_URL = Regex("""https?://[^\s<>"'，。；、）】」』》！？…]+""", RegexOption.IGNORE_CASE)

    /** B 站 BV 号，固定 12 位：BV + 10 位 */
    private val RE_BV = Regex("""BV[0-9A-Za-z]{10}""")

    private val DIRECT_EXT = listOf(".mp4", ".m3u8", ".webm", ".mov", ".m4v")

    /** 链接尾巴常被中文标点粘住，切链接时一并剥掉 */
    private const val TRAILING = "，。；、）】」』》》,;！？…"

    /**
     * 从任意文本里取视频地址。
     *
     * 兼容「只填了链接」和「整段分享文案」两种情况，例如：
     * `3 复制打开小红书，看看【红烧肉】... http://xhslink.com/a/abcd 赶紧试试`
     */
    fun extractUrl(raw: String?): String {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return ""
        // 统一走正则：URL 本身不含空格和中文标点，开头截断和中间截断是同一套规则
        val found = RE_URL.find(text) ?: return ""
        return trimTail(found.value)
    }

    private fun trimTail(url: String): String = url.trimEnd { it in TRAILING }

    /**
     * 用户输入框专用：允许只填 `b23.tv/abc` 这种不带协议头的地址。
     *
     * 先按常规解析，解析不出来再补 `https://` 试一次。
     * 只在「整串看起来像一个域名」时才补，避免把一段中文正文硬拼成链接。
     */
    fun normalizeInput(raw: String?): String {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return ""
        extractUrl(text).let { if (it.isNotEmpty()) return it }

        val head = text.substringBefore(' ').substringBefore('\n').trim()
        if (head.length in 4..300 && head.contains('.') && !head.contains(' ') &&
            !head.startsWith("http", ignoreCase = true)
        ) {
            return extractUrl("https://$head")
        }
        return ""
    }

    /** 按链接特征判断类型，空字符串表示没有视频 */
    fun detect(url: String): String {
        val u = url.trim().lowercase()
        if (u.isEmpty()) return ""
        if (u.contains("bilibili.com") || u.contains("b23.tv")) return TYPE_BILIBILI

        val path = u.substringBefore('?').substringBefore('#')
        if (DIRECT_EXT.any { path.endsWith(it) }) return TYPE_DIRECT
        return TYPE_EXTERNAL
    }

    /**
     * B 站官方播放器地址。解析不出 BV 号（例如 b23.tv 短链）就返回 null，
     * 说明只能当外部链接处理。
     */
    fun bilibiliEmbed(url: String): String? {
        val bv = RE_BV.find(url)?.value ?: return null
        return "https://player.bilibili.com/player.html" +
            "?bvid=$bv&autoplay=0&high_quality=1&danmaku=0"
    }

    /** 能否在 App 内直接播 */
    fun canPlayInside(url: String, type: String): Boolean = when (type) {
        TYPE_DIRECT -> true
        TYPE_BILIBILI -> bilibiliEmbed(url) != null
        else -> false
    }

    /** 卡片上显示的来源名 */
    fun siteLabel(url: String): String {
        val u = url.lowercase()
        return when {
            u.contains("xiaohongshu.com") || u.contains("xhslink.com") -> "小红书"
            u.contains("bilibili.com") || u.contains("b23.tv") -> "哔哩哔哩"
            u.contains("douyin.com") || u.contains("iesdouyin.com") -> "抖音"
            u.contains("kuaishou.com") -> "快手"
            u.contains("weibo.com") || u.contains("weibo.cn") -> "微博"
            u.contains("ixigua.com") -> "西瓜视频"
            u.contains("youtube.com") || u.contains("youtu.be") -> "YouTube"
            u.contains("mp.weixin.qq.com") -> "公众号"
            u.contains("zhihu.com") -> "知乎"
            else -> "视频"
        }
    }
}
