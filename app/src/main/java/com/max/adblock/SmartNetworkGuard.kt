package com.max.adblock

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

data class WifiIdentity(
    val ssid: String,
    val bssid: String
)

data class QuotaPrediction(
    val monthlyLimitGb: Int,
    val estimatedUsedTodayMb: Long,
    val estimatedDaysRemaining: Int,
    val topLeakerName: String,
    val topLeakerPkg: String,
    val topLeakerMb: Long
)

object SmartNetworkGuard {

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @Suppress("DEPRECATION")
    fun readCurrentWifiInfo(context: Context): WifiIdentity? {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
            val activeNetwork = cm.activeNetwork ?: return null
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return null
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null

            var wifiInfo: WifiInfo? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val tInfo = caps.transportInfo
                if (tInfo is WifiInfo) {
                    wifiInfo = tInfo
                }
            }
            if (wifiInfo == null) {
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                wifiInfo = wm?.connectionInfo
            }
            if (wifiInfo == null) return null

            val rawSsid = wifiInfo.ssid?.removeSurrounding("\"") ?: ""
            val rawBssid = wifiInfo.bssid ?: ""

            if (rawSsid.isBlank() || rawSsid == "<unknown ssid>") {
                return null
            }
            return WifiIdentity(ssid = rawSsid, bssid = rawBssid)
        } catch (_: Exception) {
            return null
        }
    }

    fun evaluateWifiSecurity(context: Context) {
        val prefs = PreferencesManager(context)
        if (!prefs.smartWifiSsidTriggers && !prefs.smartEvilTwinDetector) return

        val currentWifi = readCurrentWifiInfo(context) ?: return
        val homeSsid = prefs.trustedHomeSsid.trim()
        val homeBssid = prefs.trustedHomeBssid.trim()

        // Feature 8: Evil Twin / Rogue Wi-Fi Detector
        if (prefs.smartEvilTwinDetector &&
            homeSsid.isNotBlank() &&
            homeBssid.isNotBlank() &&
            currentWifi.ssid == homeSsid &&
            currentWifi.bssid.isNotBlank() &&
            currentWifi.bssid != "02:00:00:00:00:00" &&
            !currentWifi.bssid.equals(homeBssid, ignoreCase = true)
        ) {
            SmartNotificationHelper.showEvilTwinAlert(context, currentWifi.ssid, currentWifi.bssid)
            if (prefs.selectedMode != DnsMode.SECURITY) {
                prefs.selectedMode = DnsMode.SECURITY
                restartVpnIfActive(context, prefs)
            }
            return
        }

        // Feature 7: Smart Wi-Fi SSID Triggers
        if (prefs.smartWifiSsidTriggers && homeSsid.isNotBlank()) {
            val targetMode = if (currentWifi.ssid == homeSsid) {
                DnsMode.STANDARD
            } else {
                DnsMode.SECURITY
            }

            if (prefs.selectedMode != targetMode) {
                prefs.selectedMode = targetMode
                restartVpnIfActive(context, prefs)
            }
        }
    }

    private fun restartVpnIfActive(context: Context, prefs: PreferencesManager) {
        if (prefs.isVpnActive && !prefs.isVpnPaused) {
            try {
                val intent = Intent(context, AdBlockVpnService::class.java).apply {
                    action = AdBlockVpnService.ACTION_START
                }
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
            }
        }
    }

    suspend fun predictQuotaAndTopLeaker(context: Context): QuotaPrediction = withContext(Dispatchers.IO) {
        val prefs = PreferencesManager(context)
        val limitGb = prefs.monthlyPackageGb.coerceAtLeast(1)

        var totalBytes = 0L
        var topAppName = "لا يوجد تطبيق مستنزف"
        var topAppPkg = ""
        var topAppBytes = 0L

        try {
            val apps = AppTrackerScanner.scanAllInstalledApps(context)
            for (app in apps) {
                if (app.usedTodayBytes > 0) {
                    totalBytes += app.usedTodayBytes
                    if (!app.isSystemApp && !app.isFirewallBlocked && app.usedTodayBytes > topAppBytes) {
                        topAppBytes = app.usedTodayBytes
                        topAppName = app.appName
                        topAppPkg = app.packageName
                    }
                }
            }
        } catch (_: Exception) {
        }

        val mobileTotal = (TrafficStats.getMobileRxBytes() + TrafficStats.getMobileTxBytes()).coerceAtLeast(0L)
        val effectiveBytes = if (mobileTotal > 0L) mobileTotal else totalBytes
        val usedMb = (effectiveBytes / (1024L * 1024L)).coerceAtLeast(1L)

        val dayOfMonth = Calendar.getInstance().get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
        val dailyAvgMb = (usedMb / dayOfMonth).coerceAtLeast(120L)
        val totalLimitMb = limitGb.toLong() * 1024L
        val remainingMb = (totalLimitMb - usedMb).coerceAtLeast(0L)
        val daysRemaining = (remainingMb / dailyAvgMb).toInt().coerceIn(0, 30)

        QuotaPrediction(
            monthlyLimitGb = limitGb,
            estimatedUsedTodayMb = usedMb,
            estimatedDaysRemaining = daysRemaining,
            topLeakerName = topAppName,
            topLeakerPkg = topAppPkg,
            topLeakerMb = topAppBytes / (1024L * 1024L)
        )
    }
}
