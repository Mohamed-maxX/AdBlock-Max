package com.max.adblock

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GhostAdApp(
    val appName: String,
    val packageName: String,
    val icon: Drawable?,
    val isSystemApp: Boolean,
    val hasOverlayPermission: Boolean,
    val adTrackers: List<String>,
    val riskLevel: String // "خطر عالي ⚠️" / "متوسط 🟡" / "منخفض 🟢"
)

object GhostAdDetector {

    suspend fun scanForGhostAdApps(context: Context): List<GhostAdApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val flags = PackageManager.GET_PERMISSIONS or
                PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or
                PackageManager.GET_ACTIVITIES

        val packages: List<PackageInfo> = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(flags)
            }
        } catch (e: Exception) {
            emptyList()
        }

        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        val results = mutableListOf<GhostAdApp>()

        for (pkg in packages) {
            if (pkg.packageName == context.packageName) continue
            val appInfo = pkg.applicationInfo ?: continue

            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            // Check if app requests or has overlay permission
            val requestsOverlay = pkg.requestedPermissions?.contains(
                android.Manifest.permission.SYSTEM_ALERT_WINDOW
            ) == true

            val hasOverlayOp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && appOpsManager != null) {
                try {
                    val mode = appOpsManager.checkOpNoThrow(
                        AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                        appInfo.uid,
                        pkg.packageName
                    )
                    mode == AppOpsManager.MODE_ALLOWED
                } catch (e: Exception) {
                    requestsOverlay
                }
            } else {
                requestsOverlay
            }

            // Scan trackers using AppTrackerScanner logic
            val trackers = AppTrackerScanner.scanTrackers(pkg)

            // Only consider non-system apps or apps requesting overlay / containing trackers
            if ((requestsOverlay || hasOverlayOp || trackers.isNotEmpty()) && !isSystem) {
                val risk = when {
                    (hasOverlayOp || requestsOverlay) && trackers.isNotEmpty() -> "خطر عالي (شبهة إعلانات منبثقة) ⚠️"
                    hasOverlayOp || requestsOverlay -> "صلاحية ظهور فوق الشاشة 🟡"
                    else -> "يحتوي على شبكات إعلانية 🟢"
                }

                results.add(
                    GhostAdApp(
                        appName = pm.getApplicationLabel(appInfo).toString(),
                        packageName = pkg.packageName,
                        icon = appInfo.loadIcon(pm),
                        isSystemApp = isSystem,
                        hasOverlayPermission = hasOverlayOp || requestsOverlay,
                        adTrackers = trackers,
                        riskLevel = risk
                    )
                )
            }
        }

        // Sort by risk (high risk first)
        results.sortedWith(
            compareByDescending<GhostAdApp> { it.hasOverlayPermission && it.adTrackers.isNotEmpty() }
                .thenByDescending { it.hasOverlayPermission }
                .thenByDescending { it.adTrackers.size }
        )
    }
}
