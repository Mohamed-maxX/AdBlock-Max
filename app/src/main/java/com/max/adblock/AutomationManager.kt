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
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // Feature 14: Capture Silent Background Spy Traffic Baseline
                    SmartSecurityGuards.captureScreenOffSnapshot(context)

                    // Phase 2: Screen-Off Lockdown
                    if (prefs.screenOffLockdown && prefs.isVpnActive && !prefs.isVpnPaused) {
                        val pauseIntent = Intent(context, AdBlockVpnService::class.java).apply {
                            action = AdBlockVpnService.ACTION_PAUSE_5MIN
                        }
                        ContextCompat.startForegroundService(context, pauseIntent)
                    }
                }
                Intent.ACTION_SCREEN_ON -> {
                    // Feature 14: Detect suspicious screen-off background traffic spikes
                    SmartSecurityGuards.evaluateScreenOnSpyActivity(context)
                    // Feature 10: Evaluate Weekly Schedule
                    SmartSecurityGuards.evaluateWeeklySchedule(context)

                    if (prefs.screenOffLockdown && prefs.isVpnActive && prefs.isVpnPaused) {
                        val resumeIntent = Intent(context, AdBlockVpnService::class.java).apply {
                            action = AdBlockVpnService.ACTION_RESUME
                        }
                        ContextCompat.startForegroundService(context, resumeIntent)
                    }
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    // Feature 11: Low-Battery Survival Mode (< 15%)
                    handleBatteryChanged(context, intent)
                }
            }
        }
    }

    private fun handleBatteryChanged(context: Context, intent: Intent) {
        if (!prefs.smartBatterySurvivalEnabled) return
        val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
            status == android.os.BatteryManager.BATTERY_STATUS_FULL

        if (level < 0) return
        val pct = (level * 100) / scale

        if (!isCharging && pct <= 15 && !prefs.isBatterySurvivalActive) {
            prefs.isBatterySurvivalActive = true
            prefs.screenOffLockdown = true
            prefs.selectedMode = DnsMode.STANDARD
            // Stop heavy floating overlay if running
            try {
                context.stopService(Intent(context, FloatingMonitorService::class.java))
            } catch (_: Exception) {
            }
        } else if ((isCharging || pct > 22) && prefs.isBatterySurvivalActive) {
            prefs.isBatterySurvivalActive = false
        }
    }

    fun start() {
        registerScreenReceiver()
        SmartSecurityGuards.evaluateWeeklySchedule(context)
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
                addAction(Intent.ACTION_BATTERY_CHANGED)
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
        if (type == NetworkType.WIFI) {
            SmartNetworkGuard.evaluateWifiSecurity(context)
        }
        SmartSecurityGuards.evaluateWeeklySchedule(context)

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
