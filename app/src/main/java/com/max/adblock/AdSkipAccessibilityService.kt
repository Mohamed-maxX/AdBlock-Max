package com.max.adblock

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat

class AdSkipAccessibilityService : AccessibilityService() {

    private lateinit var prefs: PreferencesManager
    private var lastSkipClickTimeMs = 0L
    private var lastAutoBackTimeMs = 0L
    private var lastForegroundPkg = ""
    private var lastAppSwitchTimeMs = 0L
    private var isInGamingDnsOverride = false

    companion object {
        private val SKIP_LABELS = listOf(
            "skip ad", "skip ads", "skip video", "skip",
            "تخطي الإعلان", "تخطي الإعلانات", "تخطي"
        )

        private val SKIP_VIEW_ID_HINTS = listOf(
            "skip_ad_button", "skip_button", "ad_skip"
        )

        private val REDIRECT_TARGET_PACKAGES = setOf(
            "com.android.vending",
            "com.sec.android.app.samsungapps"
        )

        private val IGNORED_SWITCH_PACKAGES = setOf(
            "com.android.systemui",
            "com.max.adblock",
            "com.google.android.inputmethod.latin",
            "com.samsung.android.honeyboard"
        )

        fun isServiceEnabled(context: Context): Boolean {
            return try {
                val expected = ComponentName(context, AdSkipAccessibilityService::class.java).flattenToString()
                val enabledServices = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return false

                val splitter = TextUtils.SimpleStringSplitter(':')
                splitter.setString(enabledServices)
                while (splitter.hasNext()) {
                    val componentName = splitter.next()
                    if (componentName.equals(expected, ignoreCase = true)) {
                        return true
                    }
                }
                false
            } catch (_: Exception) {
                false
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = PreferencesManager(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!::prefs.isInitialized) prefs = PreferencesManager(this)

        val pkg = event.packageName?.toString() ?: return
        val now = System.currentTimeMillis()

        // 1. Track foreground app & handle App-Aware DNS + Anti-Redirect
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            !IGNORED_SWITCH_PACKAGES.contains(pkg) &&
            !pkg.contains("launcher", ignoreCase = true)
        ) {
            val prevPkg = lastForegroundPkg
            val prevTime = lastAppSwitchTimeMs

            if (pkg != prevPkg) {
                // Feature 2: Anti-Redirect Auto-Back
                if (prefs.smartAntiRedirect &&
                    prevPkg.isNotBlank() &&
                    !REDIRECT_TARGET_PACKAGES.contains(prevPkg) &&
                    REDIRECT_TARGET_PACKAGES.contains(pkg) &&
                    (now - prevTime) > 1500L &&
                    (now - lastAutoBackTimeMs) > 4000L
                ) {
                    // Rapid unexpected jump from an active game/app straight to Play Store
                    lastAutoBackTimeMs = now
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    return
                }

                lastForegroundPkg = pkg
                lastAppSwitchTimeMs = now
                prefs.lastForegroundAppPkg = pkg

                // Feature 4: App-Aware DNS Switching
                handleAppAwareDnsSwitch(pkg)
            }
        }

        // 2. Feature 1: Auto-Skip Ad Clicker
        if (prefs.smartAutoSkipAds && (now - lastSkipClickTimeMs) > 1200L) {
            val root = rootInActiveWindow ?: return
            try {
                if (findAndClickSkipButton(root, depth = 0)) {
                    lastSkipClickTimeMs = now
                }
            } catch (_: Exception) {
            } finally {
                @Suppress("DEPRECATION")
                root.recycle()
            }
        }
    }

    private fun handleAppAwareDnsSwitch(foregroundPkg: String) {
        if (!prefs.smartAppAwareDns || !prefs.isVpnActive || prefs.isVpnPaused) return

        val gamingApps = prefs.getSmartGamingApps()
        val isGamingApp = gamingApps.contains(foregroundPkg)

        if (isGamingApp && !isInGamingDnsOverride) {
            if (prefs.selectedMode != DnsMode.GAMING) {
                prefs.smartPreGameDnsMode = prefs.selectedMode.name
                prefs.selectedMode = DnsMode.GAMING
                isInGamingDnsOverride = true
                restartVpnSilently()
            }
        } else if (!isGamingApp && isInGamingDnsOverride) {
            val savedModeName = prefs.smartPreGameDnsMode
            val restoreMode = try {
                if (savedModeName.isNotBlank()) DnsMode.valueOf(savedModeName) else DnsMode.STANDARD
            } catch (_: Exception) {
                DnsMode.STANDARD
            }
            prefs.selectedMode = restoreMode
            prefs.smartPreGameDnsMode = ""
            isInGamingDnsOverride = false
            restartVpnSilently()
        }
    }

    private fun restartVpnSilently() {
        try {
            val intent = Intent(this, AdBlockVpnService::class.java).apply {
                action = AdBlockVpnService.ACTION_START
            }
            ContextCompat.startForegroundService(this, intent)
        } catch (_: Exception) {
        }
    }

    private fun findAndClickSkipButton(node: AccessibilityNodeInfo?, depth: Int): Boolean {
        if (node == null || depth > 14) return false

        val text = node.text?.toString()?.trim()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.trim()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""

        val matchesLabel = SKIP_LABELS.any { label ->
            text == label || desc == label || (label.length > 4 && (text.contains(label) || desc.contains(label)))
        }
        val matchesViewId = SKIP_VIEW_ID_HINTS.any { hint -> viewId.contains(hint) }

        if ((matchesLabel || matchesViewId) && node.isEnabled && node.isVisibleToUser) {
            if (node.isClickable) {
                return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            // Check immediate clickable parent (common in YouTube/player wrappers)
            var parent = node.parent
            var hops = 0
            while (parent != null && hops < 3) {
                if (parent.isClickable && parent.isEnabled) {
                    val clicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    @Suppress("DEPRECATION")
                    parent.recycle()
                    return clicked
                }
                val nextParent = parent.parent
                @Suppress("DEPRECATION")
                parent.recycle()
                parent = nextParent
                hops++
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val clicked = findAndClickSkipButton(child, depth + 1)
            @Suppress("DEPRECATION")
            child.recycle()
            if (clicked) return true
        }
        return false
    }

    override fun onInterrupt() {
        // Restore previous DNS mode if overridden
        if (::prefs.isInitialized && isInGamingDnsOverride) {
            val savedModeName = prefs.smartPreGameDnsMode
            if (savedModeName.isNotBlank()) {
                try {
                    prefs.selectedMode = DnsMode.valueOf(savedModeName)
                } catch (_: Exception) {
                }
            }
            isInGamingDnsOverride = false
        }
    }

    override fun onDestroy() {
        onInterrupt()
        super.onDestroy()
    }
}
