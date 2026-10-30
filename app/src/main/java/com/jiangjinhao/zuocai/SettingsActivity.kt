package com.jiangjinhao.zuocai

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jiangjinhao.zuocai.data.ApiConfig
import com.jiangjinhao.zuocai.data.LlmClient
import com.jiangjinhao.zuocai.databinding.ActivitySettingsBinding
import java.util.concurrent.Executors

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val ui = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val cfg = ApiConfig.get(this)
        binding.urlInput.setText(cfg.baseUrl)
        binding.modelInput.setText(cfg.model)
        binding.keyInput.setText(cfg.apiKey)

        binding.presetZhipu.setOnClickListener {
            binding.urlInput.setText(ApiConfig.ZHIPU_URL)
            binding.modelInput.setText(ApiConfig.DEFAULT_MODEL)
        }
        binding.presetSilicon.setOnClickListener {
            binding.urlInput.setText(ApiConfig.ALT_URL)
            binding.modelInput.setText(ApiConfig.ALT_MODEL)
        }

        binding.backButton.setOnClickListener { finish() }
        binding.saveButton.setOnClickListener { saveAndExit() }
        binding.testButton.setOnClickListener { testConnection() }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun collect(): ApiConfig.Config = ApiConfig.Config(
        baseUrl = binding.urlInput.text?.toString()?.trim().orEmpty()
            .ifEmpty { ApiConfig.DEFAULT_URL },
        model = binding.modelInput.text?.toString()?.trim().orEmpty()
            .ifEmpty { ApiConfig.DEFAULT_MODEL },
        apiKey = binding.keyInput.text?.toString()?.trim().orEmpty()
    )

    private fun saveAndExit() {
        val cfg = collect()
        if (!cfg.baseUrl.startsWith("http")) {
            Toast.makeText(this, "API 地址要以 http 开头", Toast.LENGTH_SHORT).show()
            return
        }
        ApiConfig.save(this, cfg)
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun testConnection() {
        val cfg = collect()
        if (!cfg.baseUrl.startsWith("http")) {
            Toast.makeText(this, "API 地址要以 http 开头", Toast.LENGTH_SHORT).show()
            return
        }
        if (cfg.apiKey.isBlank()) {
            Toast.makeText(this, R.string.ai_no_key_title, Toast.LENGTH_SHORT).show()
            return
        }

        // 先落盘，保证测试用的就是即将保存的配置
        ApiConfig.save(this, cfg)

        binding.testButton.isEnabled = false
        binding.testButton.setText(R.string.test_testing)

        executor.execute {
            val result = LlmClient.testConnection(this)
            ui.post {
                if (isFinishing || isDestroyed) return@post
                binding.testButton.isEnabled = true
                binding.testButton.setText(R.string.test_connection)

                result.onSuccess {
                    AlertDialog.Builder(this)
                        .setTitle(R.string.test_ok_title)
                        .setMessage(getString(R.string.test_ok_msg, cfg.model))
                        .setPositiveButton(R.string.save, null)
                        .show()
                }.onFailure { e ->
                    AlertDialog.Builder(this)
                        .setTitle(R.string.test_fail_title)
                        .setMessage(e.message.orEmpty())
                        .setPositiveButton(R.string.ai_later, null)
                        .show()
                }
            }
        }
    }
}
