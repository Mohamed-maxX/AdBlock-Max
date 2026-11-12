package com.max.adblock

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.TrafficStats
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

object QuotaManager {

    private const val QUOTA_CHANNEL_ID = "adblock_max_channel"
    private const val QUOTA_NOTIF_ID_BASE = 5000

    fun checkAppQuotas(context: Context, apps: List<AppInfo>, onQuotaExceeded: (AppInfo) -> Unit) {
        val prefs = PreferencesManager(context)

        for (app in apps) {
            val quotaMb = prefs.getAppQuota(app.packageName)
            if (quotaMb <= 0) continue

            val currentBytes = TrafficStats.getUidRxBytes(app.uid) + TrafficStats.getUidTxBytes(app.uid)
            if (currentBytes < 0) continue

            var baseline = prefs.getBaselineBytes(app.packageName)
            if (baseline == 0L || baseline > currentBytes) {
                baseline = currentBytes
                prefs.setBaselineBytes(app.packageName, baseline)
            }

            val consumedBytes = currentBytes - baseline
            val quotaBytes = quotaMb.toLong() * 1024L * 1024L

            if (consumedBytes >= quotaBytes && !app.isFirewallBlocked) {
                // Exceeded quota! Block in firewall
                prefs.setAppBlocked(app.packageName, true)
                app.isFirewallBlocked = true

                showQuotaExceededNotification(context, app, quotaMb)
                onQuotaExceeded(app)

                // Restart VPN to enforce block
                if (prefs.isVpnActive && !prefs.isVpnPaused) {
                    val intent = Intent(context, AdBlockVpnService::class.java).apply {
                        action = AdBlockVpnService.ACTION_START
                    }
                    ContextCompat.startForegroundService(context, intent)
                }
            }
        }
    }

    private fun showQuotaExceededNotification(context: Context, app: AppInfo, quotaMb: Int) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(context, QUOTA_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_block)
            .setContentTitle("تنبيه استهلاك الباقة • AdBlock Max")
            .setContentText("تخطى تطبيق ${app.appName} الكوتة اليومية (${quotaMb}MB) وتم تقييد اتصاله تلقائياً.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(QUOTA_NOTIF_ID_BASE + app.uid, notification)
    }
}
