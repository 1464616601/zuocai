package com.jiangjinhao.zuocai.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * 网络搜图：用 Bing 图片搜索找菜品图。
 *
 * 为什么放在服务端之外的这里直连：APK 没有跨域限制，可以直接抓搜索结果页。
 * 图片下载要带 Referer（多数站点有防盗链），否则会被 403。
 */
object ImageSearch {

    data class Candidate(
        val thumbUrl: String,
        val imageUrl: String,
        val pageUrl: String,
        val title: String
    )

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/130.0 Safari/537.36"

    /** 付费图库：图上有满屏水印，直接剔除 */
    private val WATERMARK_HOSTS = listOf(
        "699pic.com", "588ku.com", "zhitu.com", "tukuppt.com", "veer.com",
        "gettyimages", "shutterstock", "dreamstime", "alamy.com", "123rf.com",
        "photophoto.cn", "tuchong.com", "ivsky.com", "51yuansu.com", "pngtree",
        "lovepik.com", "bigstock", "depositphotos", "istockphoto", "huitu.com",
        "nipic.com", "freep.cn"
    )

    private val RE_BLOCK = Regex("m=\"(\\{&quot;.*?\\})\"")
    private val pool = Executors.newFixedThreadPool(6)

    /** 搜索候选图。page 从 0 开始，每页约 35 条原始结果。 */
    fun search(query: String, page: Int = 0, want: Int = 12): List<Candidate> {
        val offset = 1 + page * 35
        val url = "https://cn.bing.com/images/search?q=" +
            URLEncoder.encode(query, "UTF-8") + "&form=HDRSC2&first=$offset"

        val html = httpGet(url, null, 25)?.toString(Charsets.UTF_8) ?: return emptyList()
        val out = ArrayList<Candidate>()
        val seen = HashSet<String>()

        for (m in RE_BLOCK.findAll(html)) {
            val json = m.groupValues[1]
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")

            val obj = runCatching { JSONObject(json) }.getOrNull() ?: continue
            val imageUrl = obj.optString("murl").trim()
            val thumbUrl = obj.optString("turl").trim()
            val pageUrl = obj.optString("purl").trim()
            if (imageUrl.isEmpty() || thumbUrl.isEmpty()) continue
            if (!seen.add(imageUrl)) continue
            if (WATERMARK_HOSTS.any { pageUrl.lowercase().contains(it) }) continue

            out.add(
                Candidate(
                    thumbUrl = thumbUrl,
                    imageUrl = imageUrl,
                    pageUrl = pageUrl,
                    title = obj.optString("t").take(80)
                )
            )
            if (out.size >= want * 2) break
        }
        return out
    }

    /** 并行下载缩略图，用于选择器列表 */
    fun loadThumbs(items: List<Candidate>, targetPx: Int = 320): List<Bitmap?> {
        val futures = items.map { c ->
            pool.submit<Bitmap?> {
                runCatching {
                    val bytes = httpGet(c.thumbUrl, "https://cn.bing.com/", 15)
                        ?: return@runCatching null
                    decode(bytes, targetPx)
                }.getOrNull()
            }
        }
        return futures.map { runCatching { it.get() }.getOrNull() }
    }

    /**
     * 下载原图并居中裁成 4:3（与卡片、详情页的封面比例一致）。
     * 失败返回 null。
     */
    fun fetchCover(item: Candidate, longEdge: Int = 1600): Bitmap? {
        // 先带来源页做 Referer，防盗链站点认这个；不行再退回 Bing
        val bytes = httpGet(item.imageUrl, item.pageUrl.ifBlank { "https://cn.bing.com/" }, 30)
            ?: httpGet(item.imageUrl, "https://cn.bing.com/", 30)
            ?: return null

        val full = decode(bytes, longEdge * 2) ?: return null
        return centerCrop43(full, longEdge)
    }

    /** 居中裁成 4:3 并缩放到长边不超过 longEdge */
    fun centerCrop43(src: Bitmap, longEdge: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= 0 || h <= 0) return src

        var cw = w
        var ch = Math.round(w / 4f * 3f)
        if (ch > h) {
            ch = h
            cw = Math.round(h / 3f * 4f)
        }
        val sx = (w - cw) / 2
        val sy = (h - ch) / 2

        val cropped = Bitmap.createBitmap(src, sx, sy, cw, ch)
        if (cropped !== src) src.recycle()

        val maxEdge = maxOf(cw, ch)
        if (maxEdge <= longEdge) return cropped

        val k = longEdge.toFloat() / maxEdge
        val scaled = Bitmap.createScaledBitmap(
            cropped,
            Math.round(cw * k).coerceAtLeast(1),
            Math.round(ch * k).coerceAtLeast(1),
            true
        )
        if (scaled !== cropped) cropped.recycle()
        return scaled
    }

    // ------------------------------------------------------------ 私有

    private fun httpGet(url: String, referer: String?, timeoutSec: Int): ByteArray? {
        return runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutSec * 1000
                readTimeout = timeoutSec * 1000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "image/*,text/html,*/*;q=0.8")
                // 不带这个头时 Bing 会按 IP 猜语言，中文菜名可能返回英文结果集
                setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.6")
                if (!referer.isNullOrBlank()) setRequestProperty("Referer", referer)
            }
            try {
                if (conn.responseCode !in 200..299) return@runCatching null
                conn.inputStream.use { input ->
                    val buf = ByteArrayOutputStream()
                    val chunk = ByteArray(16 * 1024)
                    var total = 0
                    while (true) {
                        val n = input.read(chunk)
                        if (n <= 0) break
                        total += n
                        if (total > 8 * 1024 * 1024) break
                        buf.write(chunk, 0, n)
                    }
                    buf.toByteArray()
                }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private fun decode(bytes: ByteArray, targetPx: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        if (opts.outWidth <= 0) return null

        var sample = 1
        while (opts.outWidth / (sample * 2) >= targetPx) sample *= 2

        val real = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, real) }.getOrNull()
    }
}
