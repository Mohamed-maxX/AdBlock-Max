package com.max.adblock

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.net.TrafficStats
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.view.Display
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

data class OneTapCleanReport(
    val dnsFlushed: Boolean,
    val clipboardCleaned: Boolean,
    val removedTrackerParams: Int,
    val suspiciousApksFound: Int
)

object SmartSecurityGuards {

    // Feature 18: Screen-Record & Cast Privacy Guard
    private val _isScreenRecordingActive = MutableStateFlow(false)
    val isScreenRecordingActive: StateFlow<Boolean> = _isScreenRecordingActive

    private var displayListener: DisplayManager.DisplayListener? = null
    private var downloadObserver: FileObserver? = null

    // Feature 14: Silent Spy Snapshot
    private val screenOffTrafficSnapshot = mutableMapOf<String, Long>()

    // Feature 6: One-Tap Smart Clean
    suspend fun performOneTapClean(context: Context): OneTapCleanReport = withContext(Dispatchers.IO) {
        val prefs = PreferencesManager(context)
        var dnsFlushed = false

        if (prefs.isVpnActive && !prefs.isVpnPaused) {
            try {
                val intent = Intent(context, AdBlockVpnService::class.java).apply {
                    action = AdBlockVpnService.ACTION_START
                }
                ContextCompat.startForegroundService(context, intent)
                dnsFlushed = true
            } catch (_: Exception) {
            }
        }

        // Clean tracking parameters from Clipboard on Main thread safely
        var clipboardCleaned = false
        var removedCount = 0
        withContext(Dispatchers.Main) {
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val rawText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                if (!rawText.isNullOrBlank() && (rawText.startsWith("http://") || rawText.startsWith("https://"))) {
                    val result = LinkCleaner.cleanUrl(rawText)
                    if (result.removedParams.isNotEmpty()) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("Clean URL", result.cleanUrl))
                        clipboardCleaned = true
                        removedCount = result.removedParams.size
                    }
                }
            } catch (_: Exception) {
            }
        }

        // Scan Downloads for suspicious APKs
        val suspiciousApks = scanDownloadsForSuspiciousApks()
        if (suspiciousApks.isNotEmpty()) {
            val latest = suspiciousApks.first()
            prefs.lastSuspiciousApkName = latest.name
            prefs.lastSuspiciousApkPath = latest.absolutePath
        }

        OneTapCleanReport(
            dnsFlushed = dnsFlushed,
            clipboardCleaned = clipboardCleaned,
            removedTrackerParams = removedCount,
            suspiciousApksFound = suspiciousApks.size
        )
    }

    // Feature 10: Smart Weekly Schedule
    fun isWithinScheduleWindow(prefs: PreferencesManager): Boolean {
        if (!prefs.smartScheduleEnabled) return false

        val cal = Calendar.getInstance()
        val dayStr = cal.get(Calendar.DAY_OF_WEEK).toString()
        if (!prefs.getSmartScheduleDays().contains(dayStr)) return false

        val nowMins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val startMins = prefs.smartScheduleStartHour * 60 + prefs.smartScheduleStartMinute
        val endMins = prefs.smartScheduleEndHour * 60 + prefs.smartScheduleEndMinute

        return if (startMins <= endMins) {
            nowMins in startMins until endMins
        } else {
            // Overnight schedule (e.g., 22:00 to 06:00)
            nowMins >= startMins || nowMins < endMins
        }
    }

    fun evaluateWeeklySchedule(context: Context) {
        val prefs = PreferencesManager(context)
        if (!prefs.smartScheduleEnabled) return

        val inWindow = isWithinScheduleWindow(prefs)
        if (inWindow && !prefs.filterSocialDistractions) {
            prefs.filterSocialDistractions = true
            prefs.selectedMode = DnsMode.SECURITY
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
    }

    // Feature 14: Silent Background Spy Snapshot & Check
    fun captureScreenOffSnapshot(context: Context) {
        val prefs = PreferencesManager(context)
        if (!prefs.smartSilentSpyDetector) return

        try {
            screenOffTrafficSnapshot.clear()
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(0)
            for (app in packages) {
                if (app.packageName == context.packageName) continue
                val isSystem = (app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                if (isSystem) continue

                val bytes = TrafficStats.getUidRxBytes(app.uid) + TrafficStats.getUidTxBytes(app.uid)
                if (bytes >= 0) {
                    screenOffTrafficSnapshot[app.packageName] = bytes
                }
            }
        } catch (_: Exception) {
        }
    }

    fun evaluateScreenOnSpyActivity(context: Context) {
        val prefs = PreferencesManager(context)
        if (!prefs.smartSilentSpyDetector || screenOffTrafficSnapshot.isEmpty()) return

        try {
            val pm = context.packageManager
            val thresholdBytes = 2L * 1024L * 1024L // 2 MB spike while locked
            var topSpyName = ""
            var topSpyPkg = ""
            var maxDelta = 0L

            for ((pkg, prevBytes) in screenOffTrafficSnapshot) {
                if (prefs.isAppBlocked(pkg) || prefs.isAppBypassed(pkg)) continue
                val appInfo = try {
                    pm.getApplicationInfo(pkg, 0)
                } catch (_: Exception) {
                    continue
                }
                val currentBytes = TrafficStats.getUidRxBytes(appInfo.uid) + TrafficStats.getUidTxBytes(appInfo.uid)
                if (currentBytes > prevBytes) {
                    val delta = currentBytes - prevBytes
                    if (delta > thresholdBytes && delta > maxDelta) {
                        maxDelta = delta
                        topSpyPkg = pkg
                        topSpyName = pm.getApplicationLabel(appInfo).toString()
                    }
                }
            }

            screenOffTrafficSnapshot.clear()

            if (topSpyPkg.isNotBlank()) {
                val mb = maxDelta / (1024.0 * 1024.0)
                prefs.lastSpyAlertPkg = topSpyPkg
                prefs.lastSpyAlertSummary = "تطبيق $topSpyName سحب ${String.format("%.1f", mb)} MB أثناء قفل الشاشة"
                SmartNotificationHelper.showSilentSpyAlert(context, topSpyName, topSpyPkg, mb)
            }
        } catch (_: Exception) {
        }
    }

    // Feature 17: Download APK Guard
    fun startDownloadApkGuard(context: Context) {
        val prefs = PreferencesManager(context)
        if (!prefs.smartFakeApkGuard) return
        stopDownloadApkGuard()

        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir == null || !downloadsDir.exists()) return

            @Suppress("DEPRECATION")
            val observer = object : FileObserver(
                downloadsDir.absolutePath,
                CREATE or MOVED_TO or CLOSE_WRITE
            ) {
                override fun onEvent(event: Int, path: String?) {
                    if (path == null) return
                    if (path.endsWith(".apk", ignoreCase = true) && !path.equals("AdBlockMax_Update.apk", ignoreCase = true)) {
                        val fullFile = File(downloadsDir, path)
                        prefs.lastSuspiciousApkName = path
                        prefs.lastSuspiciousApkPath = fullFile.absolutePath
                        SmartNotificationHelper.showFakeApkDownloadAlert(context, path)
                    }
                }
            }
            observer.startWatching()
            downloadObserver = observer
        } catch (_: Exception) {
        }
    }

    fun stopDownloadApkGuard() {
        try {
            downloadObserver?.stopWatching()
        } catch (_: Exception) {
        } finally {
            downloadObserver = null
        }
    }

    fun scanDownloadsForSuspiciousApks(): List<File> {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir != null && downloadsDir.exists()) {
                downloadsDir.listFiles { file ->
                    file.isFile &&
                        file.name.endsWith(".apk", ignoreCase = true) &&
                        !file.name.equals("AdBlockMax_Update.apk", ignoreCase = true)
                }?.sortedByDescending { it.lastModified() } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun deleteSuspiciousApk(path: String): Boolean {
        if (path.isBlank()) return false
        return try {
            val file = File(path)
            if (file.exists() && file.name.endsWith(".apk", ignoreCase = true)) {
                file.delete()
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    // Feature 18: Screen Record & Cast Privacy Guard
    fun startScreenRecordMonitor(context: Context) {
        val prefs = PreferencesManager(context)
        if (!prefs.smartScreenRecordPrivacy) {
            _isScreenRecordingActive.value = false
            return
        }

        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return
        checkDisplays(dm, prefs)

        if (displayListener == null) {
            val listener = object : DisplayManager.DisplayListener {
                override fun onDisplayAdded(displayId: Int) = checkDisplays(dm, prefs)
                override fun onDisplayRemoved(displayId: Int) = checkDisplays(dm, prefs)
                override fun onDisplayChanged(displayId: Int) = checkDisplays(dm, prefs)
            }
            dm.registerDisplayListener(listener, null)
            displayListener = listener
        }
    }

    fun stopScreenRecordMonitor(context: Context) {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        displayListener?.let { listener ->
            try {
                dm?.unregisterDisplayListener(listener)
            } catch (_: Exception) {
            }
        }
        displayListener = null
        _isScreenRecordingActive.value = false
    }

    private fun checkDisplays(dm: DisplayManager, prefs: PreferencesManager) {
        if (!prefs.smartScreenRecordPrivacy) {
            _isScreenRecordingActive.value = false
            return
        }
        val activeRecording = try {
            dm.displays.any { display ->
                display.displayId != Display.DEFAULT_DISPLAY &&
                    ((display.flags and Display.FLAG_PRESENTATION) != 0 ||
                        display.name.contains("recorder", ignoreCase = true) ||
                        display.name.contains("screen", ignoreCase = true) ||
                        display.name.contains("virtual", ignoreCase = true) ||
                        display.name.contains("cast", ignoreCase = true))
            }
        } catch (_: Exception) {
            false
        }
        _isScreenRecordingActive.value = activeRecording
    }
}
