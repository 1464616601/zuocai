package com.jiangjinhao.zuocai

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.MediaController
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jiangjinhao.zuocai.data.VideoLink
import com.jiangjinhao.zuocai.databinding.ActivityVideoBinding

/**
 * 视频播放页，三种链接共用：
 *  - 直链（mp4/m3u8）→ 系统 VideoView，零第三方依赖
 *  - B 站（能解析出 BV 号）→ WebView 加载官方播放器 iframe
 *  - 其他平台 → WebView 直接加载原页面，右上角留一个「浏览器打开」的出口
 *
 * 不引入 ExoPlayer：只有一个播放场景，VideoView 够用，还能省 1MB 体积。
 */
class VideoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val url = VideoLink.extractUrl(intent.getStringExtra(EXTRA_URL))
        if (url.isEmpty()) {
            finish()
            return
        }
        val type = intent.getStringExtra(EXTRA_TYPE)
            ?.takeIf { it.isNotBlank() }
            ?: VideoLink.detect(url)

        binding.videoTitle.text = intent.getStringExtra(EXTRA_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.video_card_title_fmt, VideoLink.siteLabel(url))
        binding.closeButton.setOnClickListener { finish() }

        if (type == VideoLink.TYPE_DIRECT) playDirect(url) else playInWebView(url, type)
    }

    override fun onPause() {
        super.onPause()
        // 直链视频用系统播放器，onPause 不暂停的话按返回后声音还在响
        if (binding.videoView.isPlaying) binding.videoView.pause()
        binding.webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
    }

    override fun onDestroy() {
        runCatching { binding.videoView.stopPlayback() }
        destroyWebView()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- 直链

    private fun playDirect(url: String) {
        binding.webView.visibility = View.GONE
        binding.videoView.visibility = View.VISIBLE

        val controller = MediaController(this)
        controller.setAnchorView(binding.videoView)
        binding.videoView.setMediaController(controller)

        binding.videoView.setOnPreparedListener { player ->
            binding.videoProgress.visibility = View.GONE
            player.isLooping = false
            binding.videoView.start()
            controller.show()
        }
        binding.videoView.setOnErrorListener { _, _, _ ->
            binding.videoProgress.visibility = View.GONE
            showTip(getString(R.string.video_play_failed))
            true
        }

        runCatching { binding.videoView.setVideoURI(Uri.parse(url)) }
            .onFailure {
                binding.videoProgress.visibility = View.GONE
                showTip(getString(R.string.video_play_failed))
            }
    }

    // ---------------------------------------------------------------- 网页

    @SuppressLint("SetJavaScriptEnabled")
    private fun playInWebView(url: String, type: String) {
        binding.videoView.visibility = View.GONE
        val web = binding.webView
        web.visibility = View.VISIBLE

        // 只有能解析出 BV 号时才用 B 站播放器；短链解析不了，直接开原页面
        val embed = if (type == VideoLink.TYPE_BILIBILI) VideoLink.bilibiliEmbed(url) else null

        if (embed == null) {
            // 小红书这类平台在 WebView 里常被引导去登录或唤起客户端，给用户一个出口
            binding.openExternal.visibility = View.VISIBLE
            binding.openExternal.setOnClickListener { openOutside(url) }
        }

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(false)
            builtInZoomControls = false
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val target = request.url?.toString().orEmpty()
                // 站内跳转继续留在 WebView 里
                if (target.startsWith("http://") || target.startsWith("https://")) return false
                // intent:// xhsdiscover:// snssdk1128:// 之类交给系统，别在 WebView 里报错
                openOutside(target)
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.videoProgress.visibility = View.GONE
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                // 只处理主文档的错误：子资源失败不该打断整页
                if (!request.isForMainFrame) return
                binding.videoProgress.visibility = View.GONE
                showTip(getString(R.string.video_play_failed))
                // 打不开的页面（小红书风控、B 站播放器异常）总要给条出路
                binding.openExternal.visibility = View.VISIBLE
                binding.openExternal.setOnClickListener { openOutside(url) }
            }
        }

        web.loadUrl(embed ?: url)
    }

    /** 交给系统处理：装了对应 App 就唤起，否则用浏览器 */
    private fun openOutside(url: String) {
        if (url.isBlank()) return
        // about:blank / data: 这类内部地址不是给用户点的，别去 startActivity
        val scheme = url.substringBefore(':').lowercase()
        if (scheme == "about" || scheme == "data" || scheme == "javascript") return

        val primary = if (url.startsWith("intent:", ignoreCase = true)) {
            runCatching { Intent.parseUri(url, Intent.URI_INTENT_SCHEME) }.getOrNull()
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
        }

        if (primary != null && runCatching { startActivity(primary) }.isSuccess) return

        // intent:// 里带的兜底地址（通常是应用商店或网页版）
        val fallback = primary?.getStringExtra("browser_fallback_url")
        if (!fallback.isNullOrBlank() &&
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallback))) }.isSuccess
        ) {
            return
        }
        toast(getString(R.string.video_no_app))
    }

    // ---------------------------------------------------------------- 工具

    private fun destroyWebView() {
        runCatching {
            val web = binding.webView
            web.stopLoading()
            web.webViewClient = WebViewClient()
            web.loadUrl("about:blank")
            // 先从父容器摘掉再 destroy，否则 WebView 会留一个已销毁但仍挂着的引用
            (web.parent as? ViewGroup)?.removeView(web)
            web.destroy()
        }
    }

    private fun showTip(text: String) {
        binding.videoTip.text = text
        binding.videoTip.visibility = View.VISIBLE
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val EXTRA_URL = "video_url"
        private const val EXTRA_TYPE = "video_type"
        private const val EXTRA_TITLE = "video_title"

        fun start(context: Context, url: String, type: String, title: String) {
            context.startActivity(
                Intent(context, VideoActivity::class.java)
                    .putExtra(EXTRA_URL, url)
                    .putExtra(EXTRA_TYPE, type)
                    .putExtra(EXTRA_TITLE, title)
            )
        }
    }
}
