package com.max.adblock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AutomationManager(private val context: Context) {

    private val prefs = PreferencesManager(context)
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var sleepTimerJob: Job? = null
    private var isScreenReceiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!prefs.screenOffLockdown || !prefs.isVpnActive) return

            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // Temporarily suspend non-critical traffic
                    if (!prefs.isVpnPaused) {
                        val pauseIntent = Intent(context, AdBlockVpnService::class.java).apply {
                            action = AdBlockVpnService.ACTION_PAUSE_5MIN
                        }
                        ContextCompat.startForegroundService(context, pauseIntent)
                    }
                }
                Intent.ACTION_SCREEN_ON -> {
                    // Resume immediately
                    if (prefs.isVpnPaused) {
                        val resumeIntent = Intent(context, AdBlockVpnService::class.java).apply {
                            action = AdBlockVpnService.ACTION_RESUME
                        }
                        ContextCompat.startForegroundService(context, resumeIntent)
                    }
                }
            }
        }
    }

    fun start() {
        registerScreenReceiver()
    }

    fun stop() {
        unregisterScreenReceiver()
        sleepTimerJob?.cancel()
    }

    private fun registerScreenReceiver() {
        if (!isScreenReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            context.registerReceiver(screenReceiver, filter)
            isScreenReceiverRegistered = true
        }
    }

    private fun unregisterScreenReceiver() {
        if (isScreenReceiverRegistered) {
            try {
                context.unregisterReceiver(screenReceiver)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isScreenReceiverRegistered = false
        }
    }

    fun onNetworkChanged(type: NetworkType) {
        val shouldStart = when (type) {
            NetworkType.WIFI -> prefs.autoOnWifi
            NetworkType.CELLULAR -> prefs.autoOnCellular
            NetworkType.DISCONNECTED -> false
        }

        if (shouldStart && !prefs.isVpnActive) {
            val prepareIntent = VpnService.prepare(context)
            if (prepareIntent == null) {
                val intent = Intent(context, AdBlockVpnService::class.java).apply {
                    action = AdBlockVpnService.ACTION_START
                }
                ContextCompat.startForegroundService(context, intent)
            }
        }
    }

    fun setSleepTimer(minutes: Int, onComplete: () -> Unit) {
        sleepTimerJob?.cancel()
        prefs.sleepTimerMinutes = minutes

        if (minutes <= 0) {
            return
        }

        sleepTimerJob = scope.launch {
            delay(minutes * 60 * 1000L)
            if (prefs.isVpnActive) {
                val intent = Intent(context, AdBlockVpnService::class.java).apply {
                    action = AdBlockVpnService.ACTION_STOP
                }
                ContextCompat.startForegroundService(context, intent)
            }
            prefs.sleepTimerMinutes = 0
            onComplete()
        }
    }

    fun applyProfile(profileName: String): DnsMode {
        prefs.activeProfile = profileName
        return when (profileName) {
            "GAMER" -> {
                prefs.selectedMode = DnsMode.GAMING
                prefs.autoFailoverEnabled = true
                prefs.killSwitchEnabled = false
                prefs.screenOffLockdown = false
                DnsMode.GAMING
            }
            "FOCUS" -> {
                prefs.selectedMode = DnsMode.SECURITY
                prefs.killSwitchEnabled = true
                prefs.autoFailoverEnabled = true
                prefs.screenOffLockdown = false
                DnsMode.SECURITY
            }
            "SAVER" -> {
                prefs.selectedMode = DnsMode.STANDARD
                prefs.screenOffLockdown = true
                prefs.autoOnCellular = true
                prefs.autoFailoverEnabled = true
                DnsMode.STANDARD
            }
            else -> prefs.selectedMode
        }
    }
}
