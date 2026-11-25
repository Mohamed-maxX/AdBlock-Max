package com.max.adblock

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class AudioAdMuterService : NotificationListenerService() {

    private lateinit var audioManager: AudioManager
    private lateinit var prefs: PreferencesManager
    private var previousVolume: Int = -1
    private var isCurrentlyMuted = false

    private val adKeywords = listOf(
        "advertisement", "spotify ad", "إعلان", "sponsored", "ad",
        "commercial", "فاصل إعلاني"
    )

    private val urlRegex = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)
    private val suspiciousDomains = setOf(
        "bit.ly", "tinyurl.com", "t.co", "cutt.ly", "rb.gy", "is.gd", "ow.ly", "shorturl.at"
    )
    private val suspiciousTlds = listOf(".xyz", ".top", ".click", ".tk", ".ml", ".ga", ".gq")
    private var lastAlertedUrl = ""

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        prefs = PreferencesManager(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val extras = notification.extras ?: return
        val sourcePkg = sbn.packageName ?: ""
        if (sourcePkg == packageName) return

        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""
        val subText = extras.getCharSequence("android.subText")?.toString() ?: ""

        // Feature 13: Phishing Notification Shield
        if (prefs.smartPhishingNotifShield) {
            inspectNotificationForPhishing(sourcePkg, "$title $text $bigText")
        }

        if (!prefs.isAudioMuterEnabled) return

        val lowerTitle = title.lowercase()
        val lowerText = text.lowercase()
        val lowerSub = subText.lowercase()

        val isAd = adKeywords.any { kw ->
            lowerTitle.contains(kw) || lowerText.contains(kw) || lowerSub.contains(kw)
        }

        if (isAd && !isCurrentlyMuted) {
            muteAudio()
        } else if (!isAd && isCurrentlyMuted) {
            unmuteAudio()
        }
    }

    private fun inspectNotificationForPhishing(sourcePkg: String, combinedContent: String) {
        try {
            val matches = urlRegex.findAll(combinedContent)
            for (match in matches) {
                val url = match.value
                val lowerUrl = url.lowercase()
                val host = try {
                    java.net.URL(url).host.lowercase()
                } catch (_: Exception) {
                    ""
                }

                val isShortener = suspiciousDomains.any { host == it || host.endsWith(".$it") }
                val hasSuspiciousTld = suspiciousTlds.any { host.endsWith(it) }
                val isIpUrl = Regex("""^\d{1,3}(\.\d{1,3}){3}$""").matches(host)
                val (isBlacklisted, _) = if (host.isNotBlank()) FilterRulesManager.checkDomain(this, host) else Pair(false, "")

                if ((isShortener || hasSuspiciousTld || isIpUrl || isBlacklisted) && url != lastAlertedUrl) {
                    lastAlertedUrl = url
                    val appLabel = try {
                        val ai = packageManager.getApplicationInfo(sourcePkg, 0)
                        packageManager.getApplicationLabel(ai).toString()
                    } catch (_: Exception) {
                        sourcePkg
                    }
                    SmartNotificationHelper.showPhishingAlert(this, appLabel, url)
                    break
                }
            }
        } catch (_: Exception) {
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (isCurrentlyMuted) {
            unmuteAudio()
        }
    }

    private fun muteAudio() {
        try {
            previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
            } else {
                @Suppress("DEPRECATION")
                audioManager.setStreamMute(AudioManager.STREAM_MUSIC, true)
            }
            isCurrentlyMuted = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun unmuteAudio() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            } else {
                @Suppress("DEPRECATION")
                audioManager.setStreamMute(AudioManager.STREAM_MUSIC, false)
            }
            if (previousVolume >= 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, previousVolume, 0)
            }
            isCurrentlyMuted = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        if (isCurrentlyMuted) {
            unmuteAudio()
        }
        super.onDestroy()
    }

    override fun onListenerDisconnected() {
        if (isCurrentlyMuted) {
            unmuteAudio()
        }
        super.onListenerDisconnected()
    }
}
