package com.max.adblock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PackageWatcherReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pkgName = intent.data?.schemeSpecificPart ?: return
        if (pkgName == context.packageName) return

        val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
        val prefs = PreferencesManager(context)

        if (action == Intent.ACTION_PACKAGE_ADDED && !isReplacing && prefs.smartNewAppPrivacyScore) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    inspectNewApp(context, prefs, pkgName)
                } finally {
                    pendingResult.finish()
                }
            }
        } else if (action == Intent.ACTION_PACKAGE_REPLACED && prefs.smartPostUpdateWatcher) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    inspectUpdatedApp(context, prefs, pkgName)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun inspectNewApp(context: Context, prefs: PreferencesManager, pkgName: String) {
        val pm = context.packageManager
        val pkgInfo = getPackageInfoCompat(pm, pkgName) ?: return
        val appInfo = pkgInfo.applicationInfo ?: return
        val appName = pm.getApplicationLabel(appInfo).toString()

        val trackers = AppTrackerScanner.scanTrackers(pkgInfo)
        val trackerCount = trackers.size
        prefs.setSavedTrackerCount(pkgName, trackerCount)

        // Calculate Privacy Score out of 10
        val riskyPerms = pkgInfo.requestedPermissions?.count { perm ->
            perm.contains("LOCATION") || perm.contains("CAMERA") || perm.contains("RECORD_AUDIO") || perm.contains("CONTACTS")
        } ?: 0
        val penalty = (trackerCount * 2) + (riskyPerms / 2)
        val score = (10 - penalty).coerceIn(1, 10)

        SmartNotificationHelper.showNewAppPrivacyScore(
            context = context,
            appName = appName,
            packageName = pkgName,
            scoreOutOf10 = score,
            trackerCount = trackerCount
        )
    }

    private fun inspectUpdatedApp(context: Context, prefs: PreferencesManager, pkgName: String) {
        val pm = context.packageManager
        val pkgInfo = getPackageInfoCompat(pm, pkgName) ?: return
        val appInfo = pkgInfo.applicationInfo ?: return
        val appName = pm.getApplicationLabel(appInfo).toString()

        val newTrackers = AppTrackerScanner.scanTrackers(pkgInfo)
        val newCount = newTrackers.size
        val oldCount = prefs.getSavedTrackerCount(pkgName)

        prefs.setSavedTrackerCount(pkgName, newCount)

        if (oldCount >= 0 && newCount > oldCount) {
            SmartNotificationHelper.showPostUpdateTrackerAlert(
                context = context,
                appName = appName,
                packageName = pkgName,
                oldCount = oldCount,
                newCount = newCount
            )
        }
    }

    private fun getPackageInfoCompat(pm: PackageManager, pkgName: String) = try {
        val flags = PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or
            PackageManager.GET_ACTIVITIES or
            PackageManager.GET_PERMISSIONS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(pkgName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkgName, flags)
        }
    } catch (_: Exception) {
        null
    }
}
