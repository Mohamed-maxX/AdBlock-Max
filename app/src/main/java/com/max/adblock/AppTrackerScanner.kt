package com.max.adblock

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.TrafficStats
import android.os.Build

object AppTrackerScanner {

    private val TRACKER_SIGNATURES = mapOf(
        "com.google.android.gms.ads" to "Google AdMob",
        "com.facebook.ads" to "Meta Audience Network",
        "com.facebook.analytics" to "Meta Analytics Tracker",
        "com.applovin" to "AppLovin MAX",
        "com.unity3d.ads" to "Unity Ads",
        "com.unity3d.services" to "Unity Analytics",
        "com.ironsource" to "ironSource / Unity LevelPlay",
        "com.mbridge" to "Mintegral Ads",
        "com.mintegral" to "Mintegral SDK",
        "com.adjust.sdk" to "Adjust Attribution",
        "com.appsflyer" to "AppsFlyer Tracker",
        "com.bytedance" to "TikTok Pangle Ads",
        "com.pangle" to "Pangle Ad Network",
        "com.vungle" to "Liftoff / Vungle Ads",
        "com.chartboost" to "Chartboost Ads",
        "com.inmobi" to "InMobi Ads",
        "com.flurry" to "Flurry Analytics"
    )

    fun scanAllInstalledApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val prefs = PreferencesManager(context)

        val flags = PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or
                PackageManager.GET_ACTIVITIES or
                PackageManager.GET_PERMISSIONS

        val installedPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(flags)
        }

        val resultList = mutableListOf<AppInfo>()

        for (pkg in installedPackages) {
            // Skip our own app
            if (pkg.packageName == context.packageName) continue

            val appInfo = pkg.applicationInfo ?: continue
            val appName = pm.getApplicationLabel(appInfo).toString()
            val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0

            // Detect Trackers
            val detectedTrackers = scanTrackers(pkg)
            val trackerCount = detectedTrackers.size

            // Evaluate Battery Drain Risk
            val batteryRisk = when {
                trackerCount >= 3 -> BatteryDrainRisk.HIGH
                trackerCount in 1..2 -> BatteryDrainRisk.MEDIUM
                else -> BatteryDrainRisk.LOW
            }

            // Measure Data Usage via TrafficStats
            val rxBytes = TrafficStats.getUidRxBytes(appInfo.uid)
            val txBytes = TrafficStats.getUidTxBytes(appInfo.uid)
            val totalBytes = if (rxBytes >= 0 && txBytes >= 0) rxBytes + txBytes else 0L

            val isBlocked = prefs.isAppBlocked(pkg.packageName)
            val isBypassed = prefs.isAppBypassed(pkg.packageName)
            val quota = prefs.getAppQuota(pkg.packageName)

            resultList.add(
                AppInfo(
                    packageName = pkg.packageName,
                    appName = appName,
                    icon = appInfo.loadIcon(pm),
                    uid = appInfo.uid,
                    isSystemApp = isSystem,
                    isFirewallBlocked = isBlocked,
                    isBypassed = isBypassed,
                    dailyQuotaMb = quota,
                    usedTodayBytes = totalBytes,
                    trackerCount = trackerCount,
                    trackersFound = detectedTrackers,
                    batteryDrainRisk = batteryRisk
                )
            )
        }

        // Sort: user apps first, then by tracker count and name
        return resultList.sortedWith(
            compareBy<AppInfo> { it.isSystemApp }
                .thenByDescending { it.trackerCount }
                .thenBy { it.appName.lowercase() }
        )
    }

    fun scanTrackers(pkg: PackageInfo): List<String> {
        val detected = mutableSetOf<String>()

        fun checkClass(className: String?) {
            if (className == null) return
            for ((sig, name) in TRACKER_SIGNATURES) {
                if (className.contains(sig, ignoreCase = true)) {
                    detected.add(name)
                }
            }
        }

        pkg.services?.forEach { checkClass(it.name) }
        pkg.receivers?.forEach { checkClass(it.name) }
        pkg.activities?.forEach { checkClass(it.name) }

        return detected.toList()
    }
}
