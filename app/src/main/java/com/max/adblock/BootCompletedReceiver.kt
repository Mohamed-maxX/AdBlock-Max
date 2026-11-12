package com.max.adblock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON") {
            val prefs = PreferencesManager(context)
            if (prefs.autoStartOnBoot && prefs.isVpnActive) {
                // Ensure VPN permission is already granted
                val prepareIntent = VpnService.prepare(context)
                if (prepareIntent == null) {
                    val serviceIntent = Intent(context, AdBlockVpnService::class.java).apply {
                        this.action = AdBlockVpnService.ACTION_START
                    }
                    ContextCompat.startForegroundService(context, serviceIntent)
                }
            }
        }
    }
}
