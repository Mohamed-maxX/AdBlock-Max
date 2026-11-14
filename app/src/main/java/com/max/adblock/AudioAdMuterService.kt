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

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        prefs = PreferencesManager(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (!prefs.isAudioMuterEnabled) return
        val notification = sbn?.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence("android.title")?.toString()?.lowercase() ?: ""
        val text = extras.getCharSequence("android.text")?.toString()?.lowercase() ?: ""
        val subText = extras.getCharSequence("android.subText")?.toString()?.lowercase() ?: ""

        val isAd = adKeywords.any { kw ->
            title.contains(kw) || text.contains(kw) || subText.contains(kw)
        }

        if (isAd && !isCurrentlyMuted) {
            muteAudio()
        } else if (!isAd && isCurrentlyMuted) {
            unmuteAudio()
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
