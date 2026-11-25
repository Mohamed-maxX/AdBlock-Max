package com.max.adblock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class VpnActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_BLOCK_APP_FIREWALL = "com.max.adblock.ACTION_BLOCK_APP_FIREWALL"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_NAME = "extra_app_name"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (action == ACTION_BLOCK_APP_FIREWALL) {
            val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return
            val prefs = PreferencesManager(context)
            prefs.setAppBlocked(pkg, true)
            if (prefs.isVpnActive && !prefs.isVpnPaused) {
                val restartIntent = Intent(context, AdBlockVpnService::class.java).apply {
                    this.action = AdBlockVpnService.ACTION_START
                }
                ContextCompat.startForegroundService(context, restartIntent)
            }
            return
        }

        // Feature 5: Auto-Learn Whitelist pause tracking
        if (action == AdBlockVpnService.ACTION_PAUSE_5MIN || action == AdBlockVpnService.ACTION_STOP) {
            recordPauseForAutoLearn(context)
        }

        val serviceIntent = Intent(context, AdBlockVpnService::class.java).apply {
            this.action = action
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    private fun recordPauseForAutoLearn(context: Context) {
        val prefs = PreferencesManager(context)
        if (!prefs.smartAutoLearnWhitelist) return
        val lastApp = prefs.lastForegroundAppPkg
        if (lastApp.isBlank() || lastApp == context.packageName || prefs.isAppBypassed(lastApp)) return

        val count = prefs.incrementAppPauseCount(lastApp)
        if (count >= 3) {
            prefs.pendingWhitelistSuggestionPkg = lastApp
            val appName = try {
                val ai = context.packageManager.getApplicationInfo(lastApp, 0)
                context.packageManager.getApplicationLabel(ai).toString()
            } catch (_: Exception) {
                lastApp
            }
            SmartNotificationHelper.showWhitelistSuggestion(context, appName)
        }
    }
}
