package com.max.adblock

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.max.adblock.databinding.ActivityCleanShareBinding

class CleanShareActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCleanShareBinding
    private var cleanResult: CleanLinkResult? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCleanShareBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val rawText = handleIntent(intent)
        if (rawText.isNullOrBlank()) {
            Toast.makeText(this, "لم يتم العثور على رابط للمشاركة", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        processUrl(rawText)
        setupActions()
    }

    private fun handleIntent(incomingIntent: Intent?): String? {
        if (incomingIntent?.action == Intent.ACTION_SEND && incomingIntent.type == "text/plain") {
            return incomingIntent.getStringExtra(Intent.EXTRA_TEXT)
        }
        return null
    }

    private fun processUrl(rawText: String) {
        val result = LinkCleaner.cleanUrl(rawText)
        cleanResult = result

        binding.tvCleanUrlDisplay.text = result.cleanUrl
        if (result.removedParams.isNotEmpty()) {
            binding.tvRemovedParamsDisplay.text = getString(
                R.string.clean_share_removed_params,
                result.removedParams.joinToString(", ")
            )
        } else {
            binding.tvRemovedParamsDisplay.text = "الرابط نظيف بالفعل ولا يحتوي على معلمات تتبع معروفة ✅"
        }

        // Auto copy clean URL immediately for convenience
        copyToClipboard(result.cleanUrl, showToast = false)
    }

    private fun setupActions() {
        binding.btnCleanClose.setOnClickListener {
            finish()
        }

        binding.btnCopyCleanUrl.setOnClickListener {
            cleanResult?.let {
                copyToClipboard(it.cleanUrl, showToast = true)
            }
        }

        binding.btnOpenInAdFreeBrowser.setOnClickListener {
            cleanResult?.let {
                val intent = Intent(this, BrowserActivity::class.java).apply {
                    putExtra(BrowserActivity.EXTRA_URL, it.cleanUrl)
                }
                startActivity(intent)
                finish()
            }
        }

        binding.btnShareCleanUrl.setOnClickListener {
            cleanResult?.let {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, it.cleanUrl)
                }
                startActivity(Intent.createChooser(shareIntent, "مشاركة الرابط النظيف"))
                finish()
            }
        }
    }

    private fun copyToClipboard(text: String, showToast: Boolean) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Clean URL", text)
        clipboard.setPrimaryClip(clip)
        if (showToast) {
            Toast.makeText(this, R.string.clean_share_done, Toast.LENGTH_SHORT).show()
        }
    }
}
