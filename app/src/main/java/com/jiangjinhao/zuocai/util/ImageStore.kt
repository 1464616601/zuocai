package com.jiangjinhao.zuocai.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 菜谱封面图的本地管理：
 *  - 保存时统一压缩到长边 1600px、JPEG 88%，原始文件不进入数据库，只存文件名；
 *  - 读取时按需采样解码并走内存 LruCache，避免列表滚动时 OOM。
 */
object ImageStore {

    private const val DIR_NAME = "images"
    private const val MAX_EDGE = 1600
    private const val JPEG_QUALITY = 88

    private val memoryCache: LruCache<String, Bitmap> by lazy {
        val maxKb = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
        object : LruCache<String, Bitmap>(maxKb) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
        }
    }

    fun imagesDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    fun fileOf(context: Context, name: String?): File? {
        if (name.isNullOrBlank()) return null
        val f = File(imagesDir(context), name)
        return if (f.exists()) f else null
    }

    /**
     * 把外部 Uri 的图片存进 App 私有目录，返回落盘文件名；失败返回 null。
     */
    fun save(context: Context, uri: Uri): String? {
        return runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_EDGE * 2)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            var bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return null

            bitmap = applyExifRotation(resolver.openInputStream(uri), bitmap)
            bitmap = scaleDown(bitmap, MAX_EDGE)

            val name = "r_${UUID.randomUUID().toString().take(16)}.jpg"
            val target = File(imagesDir(context), name)
            FileOutputStream(target).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            bitmap.recycle()
            name
        }.getOrNull()
    }

    /** 直接保存一张 Bitmap（用于已解码的场景） */
    fun saveBitmap(context: Context, bitmap: Bitmap): String? {
        return runCatching {
            val scaled = scaleDown(bitmap, MAX_EDGE)
            val name = "r_${UUID.randomUUID().toString().take(16)}.jpg"
            FileOutputStream(File(imagesDir(context), name)).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            if (scaled !== bitmap) scaled.recycle()
            name
        }.getOrNull()
    }

    fun delete(context: Context, name: String?) {
        if (name.isNullOrBlank()) return
        // 清掉该图的所有尺寸缓存
        memoryCache.snapshot().keys
            .filter { it.startsWith("$name@") }
            .forEach { memoryCache.remove(it) }
        runCatching { File(imagesDir(context), name).delete() }
    }

    /**
     * @param reqPx 期望的目标边长（px），用于计算采样率
     */
    fun load(context: Context, name: String?, reqPx: Int): Bitmap? {
        val file = fileOf(context, name) ?: return null
        val key = cacheKey(name!!, reqPx)
        memoryCache.get(key)?.let { return it }

        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        if (opts.outWidth <= 0) return null

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(opts.outWidth, opts.outHeight, reqPx)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bmp = runCatching {
            BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
        }.getOrNull() ?: return null

        memoryCache.put(key, bmp)
        return bmp
    }

    fun clearMemoryCache() = memoryCache.evictAll()

    // ---------------------------------------------------------------- 异步加载

    private val ioExecutor = java.util.concurrent.Executors.newFixedThreadPool(2)
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * 后台解码后回主线程回填。imageView 需先设置 tag 为图片名，回调时会再次校验，
     * 避免 RecyclerView 复用导致图片错位。
     */
    fun loadAsync(
        context: Context,
        name: String?,
        reqPx: Int,
        onReady: (Bitmap?) -> Unit
    ) {
        val appContext = context.applicationContext
        val key = cacheKey(name ?: "", reqPx)
        memoryCache.get(key)?.let {
            onReady(it)
            return
        }
        ioExecutor.execute {
            val bmp = load(appContext, name, reqPx)
            mainHandler.post { onReady(bmp) }
        }
    }

    /** 只查内存缓存，不触发解码 */
    fun peek(name: String?, reqPx: Int): Bitmap? =
        memoryCache.get(cacheKey(name ?: "", reqPx))

    // ---------------------------------------------------------------- 私有

    private fun cacheKey(name: String, reqPx: Int) = "$name@$reqPx"

    private fun sampleSizeFor(width: Int, height: Int, reqPx: Int): Int {
        if (reqPx <= 0) return 1
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= reqPx && h / 2 >= reqPx) {
            w /= 2; h /= 2; sample *= 2
        }
        return sample
    }

    private fun scaleDown(src: Bitmap, maxEdge: Int): Bitmap {
        val longEdge = maxOf(src.width, src.height)
        if (longEdge <= maxEdge) return src
        val ratio = maxEdge.toFloat() / longEdge
        val w = (src.width * ratio).toInt().coerceAtLeast(1)
        val h = (src.height * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(src, w, h, true)
        if (scaled !== src) src.recycle()
        return scaled
    }

    private fun applyExifRotation(input: java.io.InputStream?, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            input?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                .also { if (it !== bitmap) bitmap.recycle() }
        }.getOrDefault(bitmap)
    }
}
