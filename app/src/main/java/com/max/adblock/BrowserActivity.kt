package com.max.adblock

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.max.adblock.databinding.ActivityBrowserBinding
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger

class BrowserActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "extra_browser_url"

        private val AD_KEYWORDS = listOf(
            "doubleclick.net", "googlesyndication.com", "googleads.g.doubleclick.net",
            "adservice.google", "pagead2", "adnxs.com", "applovin.com", "criteo.com",
            "taboola.com", "outbrain.com", "adcolony.com", "popads.net", "propellerads",
            "exoclick.com", "adroll.com", "bidswitch.net", "facebook.com/tr/", "analytics.google"
        )

        private const val UA_DESKTOP =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        private const val UA_IOS =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"
        private const val UA_LINUX =
            "Mozilla/5.0 (X11; Linux x86_64; rv:129.0) Gecko/20100101 Firefox/129.0"
    }

    private lateinit var binding: ActivityBrowserBinding
    private lateinit var prefs: PreferencesManager
    private val blockedAdsCounter = AtomicInteger(0)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager(this)

        if (AppLockManager.isLockRequired(this)) {
            AppLockManager.promptAppLock(this) {
                initBrowserUi()
            }
        } else {
            initBrowserUi()
        }
    }

    private fun initBrowserUi() {
        setupWebView()
        setupListeners()

        val initialUrl = intent.getStringExtra(EXTRA_URL) ?: "https://duckduckgo.com"
        loadUrl(initialUrl)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = binding.wvBrowser.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false

        // Security Hardening: Block local file and cross-origin file URL access
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        @Suppress("DEPRECATION")
        settings.allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        settings.allowUniversalAccessFromFileURLs = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.safeBrowsingEnabled = true
        }

        applyUserAgent(prefs.preferredUserAgent)

        binding.wvBrowser.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.pbBrowserLoading.progress = newProgress
                binding.pbBrowserLoading.visibility = if (newProgress < 100) android.view.View.VISIBLE else android.view.View.GONE
            }
        }

        binding.wvBrowser.webViewClient = object : WebViewClient() {
            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                // Strict Security: Cancel immediately on SSL errors, never proceed!
                handler?.cancel()
                runOnUiThread {
                    Toast.makeText(
                        this@BrowserActivity,
                        "تم حظر الصفحة: خطأ في شهادة الأمان (SSL Error)",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                blockedAdsCounter.set(0)
                updateBlockedCounter()
                binding.etBrowserUrl.setText(url ?: "")
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url?.toString()?.lowercase() ?: return null

                for (adKeyword in AD_KEYWORDS) {
                    if (url.contains(adKeyword)) {
                        blockedAdsCounter.incrementAndGet()
                        runOnUiThread { updateBlockedCounter() }
                        // Return empty response to block the ad request
                        return WebResourceResponse(
                            "text/plain",
                            "UTF-8",
                            ByteArrayInputStream(ByteArray(0))
                        )
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.etBrowserUrl.setText(url ?: "")
                injectAdBlockerScripts()
            }
        }
    }

    private fun injectAdBlockerScripts() {
        val cosmeticCss = """
            (function() {
                var css = '.adsbygoogle, [class*="ad-box"], [id*="google_ads"], div[class*="sponsored"], .ytp-ad-module, .video-ads, .ytp-ad-overlay-container, .ytp-ad-player-overlay, ytd-promoted-sparkles-web-renderer, ytd-display-ad-renderer { display: none !important; }';
                var head = document.head || document.getElementsByTagName('head')[0];
                var style = document.createElement('style');
                style.type = 'text/css';
                style.appendChild(document.createTextNode(css));
                if (head) head.appendChild(style);

                // Video Ad Auto-Skipper
                setInterval(function() {
                    var skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-skip-ad-button, .ytp-ad-skip-button-modern, [class*="skip-button"]');
                    if (skipBtn) { skipBtn.click(); }
                    var video = document.querySelector('video');
                    var adModule = document.querySelector('.ad-showing, .ytp-ad-module');
                    if (video && adModule) {
                        video.playbackRate = 16.0;
                        video.currentTime = video.duration || 1000;
                    }
                }, 500);
            })();
        """.trimIndent()

        binding.wvBrowser.evaluateJavascript(cosmeticCss, null)

        // v2.1.0 Feature 3: Auto Cookie-Banner Rejecter
        if (prefs.smartAutoRejectCookies) {
            val cookieRejectJs = """
                (function() {
                    var rejectSelectors = [
                        '#onetrust-reject-all-handler',
                        '#CybotCookiebotDialogBodyButtonDecline',
                        '#didomi-notice-disagree-button',
                        'button[aria-label*="reject" i]',
                        'button[aria-label*="decline" i]',
                        'button[id*="reject" i]',
                        'button[class*="reject" i]',
                        'a[class*="reject" i]'
                    ];
                    function tryRejectCookies() {
                        for (var i = 0; i < rejectSelectors.length; i++) {
                            var btn = document.querySelector(rejectSelectors[i]);
                            if (btn && btn.offsetParent !== null) {
                                btn.click();
                                return true;
                            }
                        }
                        var buttons = document.querySelectorAll('button, a[role="button"]');
                        for (var j = 0; j < buttons.length; j++) {
                            var txt = (buttons[j].innerText || '').trim().toLowerCase();
                            if (txt === 'reject all' || txt === 'decline all' || txt === 'only necessary' || txt === 'رفض الكل' || txt === 'رفض') {
                                buttons[j].click();
                                return true;
                            }
                        }
                        return false;
                    }
                    tryRejectCookies();
                    setTimeout(tryRejectCookies, 1200);
                    var hideCookieStyle = document.createElement('style');
                    hideCookieStyle.textContent = '#onetrust-banner-sdk, #CybotCookiebotDialog, #didomi-host, .cookie-banner, #cookie-consent, [class*="cookie-notice"] { display: none !important; }';
                    (document.head || document.documentElement).appendChild(hideCookieStyle);
                })();
            """.trimIndent()
            binding.wvBrowser.evaluateJavascript(cookieRejectJs, null)
        }
    }

    private fun updateBlockedCounter() {
        val count = blockedAdsCounter.get()
        binding.tvBlockedAdsCount.text = getString(R.string.browser_ads_blocked_badge, count)
    }

    private fun setupListeners() {
        binding.btnBrowserBack.setOnClickListener {
            finish()
        }

        binding.btnBrowserRefresh.setOnClickListener {
            binding.wvBrowser.reload()
        }

        binding.btnBrowserGo.setOnClickListener {
            performSearchOrNavigate()
        }

        binding.etBrowserUrl.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                performSearchOrNavigate()
                true
            } else false
        }

        binding.btnWebBack.setOnClickListener {
            if (binding.wvBrowser.canGoBack()) binding.wvBrowser.goBack()
        }

        binding.btnWebForward.setOnClickListener {
            if (binding.wvBrowser.canGoForward()) binding.wvBrowser.goForward()
        }

        binding.btnUserAgent.setOnClickListener {
            showUserAgentDialog()
        }

        binding.btnWebShareClean.setOnClickListener {
            val currentUrl = binding.wvBrowser.url ?: return@setOnClickListener
            val clean = LinkCleaner.cleanUrl(currentUrl).cleanUrl
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, clean)
            }
            startActivity(Intent.createChooser(shareIntent, "مشاركة الرابط النظيف"))
        }
    }

    private fun performSearchOrNavigate() {
        val query = binding.etBrowserUrl.text.toString().trim()
        if (query.isBlank()) return

        val targetUrl = when {
            query.startsWith("http://") || query.startsWith("https://") -> query
            query.contains(".") && !query.contains(" ") -> "https://$query"
            else -> "https://duckduckgo.com/?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
        }
        loadUrl(targetUrl)
    }

    private fun loadUrl(url: String) {
        binding.wvBrowser.loadUrl(url)
    }

    private fun applyUserAgent(uaKey: String) {
        prefs.preferredUserAgent = uaKey
        val settings = binding.wvBrowser.settings

        when (uaKey) {
            "DESKTOP" -> {
                settings.userAgentString = UA_DESKTOP
                binding.tvCurrentUaBadge.text = "Desktop Chrome 💻"
            }
            "IOS" -> {
                settings.userAgentString = UA_IOS
                binding.tvCurrentUaBadge.text = "iPhone Safari 🍏"
            }
            "LINUX" -> {
                settings.userAgentString = UA_LINUX
                binding.tvCurrentUaBadge.text = "Linux Firefox 🐧"
            }
            else -> {
                settings.userAgentString = null // Default Android
                binding.tvCurrentUaBadge.text = "Android Chrome 📱"
            }
        }
    }

    private fun showUserAgentDialog() {
        val options = arrayOf(
            getString(R.string.browser_ua_desktop),
            getString(R.string.browser_ua_mobile),
            getString(R.string.browser_ua_ios),
            getString(R.string.browser_ua_linux)
        )

        val keys = arrayOf("DESKTOP", "DEFAULT", "IOS", "LINUX")
        val currentIndex = keys.indexOf(prefs.preferredUserAgent).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.browser_ua_title)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                applyUserAgent(keys[which])
                binding.wvBrowser.reload()
                Toast.makeText(this, "تم تغيير بصمة التصفح بنجاح", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.wvBrowser.canGoBack()) {
            binding.wvBrowser.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
