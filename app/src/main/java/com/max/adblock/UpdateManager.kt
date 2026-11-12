package com.max.adblock

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val isUpdateAvailable: Boolean,
    val latestVersion: String,
    val currentVersion: String,
    val releaseTitle: String,
    val changelog: String,
    val downloadUrl: String,
    val releaseHtmlUrl: String
)

object UpdateManager {

    var repoOwner: String = "Mohamed-maxX"
    var repoName: String = "AdBlock-Max"

    fun getCurrentVersion(context: Context): String {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            pInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    suspend fun checkGitHubRelease(context: Context): UpdateInfo = withContext(Dispatchers.IO) {
        val currentVer = getCurrentVersion(context)
        val apiUrl = "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"

        try {
            val url = URL(apiUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.setRequestProperty("User-Agent", "AdBlockMax-Android")
            conn.connect()

            if (conn.responseCode == 200) {
                val jsonString = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonString)
                val rawTag = root.optString("tag_name", "1.0.0")
                val cleanTag = rawTag.removePrefix("v").trim()
                val title = root.optString("name", "AdBlock Max v$cleanTag")
                val body = root.optString("body", "تحديث أمني وتحسينات في استقرار الحظر.")
                val htmlUrl = root.optString("html_url", "https://github.com/$repoOwner/$repoName/releases")

                // Find APK asset
                var apkUrl = ""
                val assets = root.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url", "")
                            break
                        }
                    }
                }
                if (apkUrl.isBlank()) {
                    apkUrl = htmlUrl
                }

                val hasNewer = isNewerVersion(cleanTag, currentVer)
                val displayChangelog = if (hasNewer) {
                    body.ifBlank { "يتوفر تحديث أمني وتحسينات جديدة لأداء الحظر." }
                } else {
                    "أنت تستخدم أحدث إصدار بالفعل (v$currentVer) 🟢\n\n" +
                    "• الإصدار المثبت: v$currentVer\n" +
                    "• أحدث إصدار متاح على GitHub: v$cleanTag\n" +
                    "• كافة ميزات درع الحظر، الجدار الناري، الرادار، والمتصفح محدثة وتعمل بأعلى كفاءة."
                }

                return@withContext UpdateInfo(
                    isUpdateAvailable = hasNewer,
                    latestVersion = cleanTag,
                    currentVersion = currentVer,
                    releaseTitle = title,
                    changelog = displayChangelog,
                    downloadUrl = apkUrl,
                    releaseHtmlUrl = htmlUrl
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback info if 404 Not Found (no releases published yet) or offline
        UpdateInfo(
            isUpdateAvailable = false,
            latestVersion = currentVer,
            currentVersion = currentVer,
            releaseTitle = "AdBlock Max v$currentVer",
            changelog = "أنت تستخدم أحدث إصدار بالفعل (v$currentVer) 🟢\n\n" +
                    "• المستودع الرسمي: github.com/$repoOwner/$repoName\n" +
                    "• حالة التحديث: لا توجد إصدارات أحدث منشورة حالياً.\n" +
                    "• جميع أنظمة الحظر، الرادار الحي، الجدار الناري، والمتصفح تعمل بكفاءة تامة.",
            downloadUrl = "https://github.com/$repoOwner/$repoName/releases",
            releaseHtmlUrl = "https://github.com/$repoOwner/$repoName/releases"
        )
    }

    private fun isNewerVersion(remoteVer: String, currentVer: String): Boolean {
        try {
            val remoteParts = remoteVer.split(".").map { it.toIntOrNull() ?: 0 }
            val currentParts = currentVer.split(".").map { it.toIntOrNull() ?: 0 }

            val maxLen = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val r = if (i < remoteParts.size) remoteParts[i] else 0
                val c = if (i < currentParts.size) currentParts[i] else 0
                if (r > c) return true
                if (r < c) return false
            }
        } catch (e: Exception) {
            return false
        }
        return false
    }

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val url = URL(downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.connect()

            val fileLength = connection.contentLength
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
            val outputFile = File(dir, "AdBlockMax_Update.apk")

            connection.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val data = ByteArray(8192)
                    var total = 0L
                    var count: Int
                    while (input.read(data).also { count = it } != -1) {
                        total += count
                        output.write(data, 0, count)
                        if (fileLength > 0) {
                            val progress = ((total * 100) / fileLength).toInt()
                            onProgress(progress)
                        }
                    }
                    output.flush()
                }
            }
            outputFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun promptInstallApk(activity: Activity, apkFile: File) {
        try {
            val apkUri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(activity, "تعذر تشغيل مثبت الحزم: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun showUpdateDialog(activity: Activity) {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_update_checker, null)

        val tvStatus = dialogView.findViewById<TextView>(R.id.tvUpdateStatus)
        val tvChangelog = dialogView.findViewById<TextView>(R.id.tvUpdateChangelog)
        val pbChecking = dialogView.findViewById<ProgressBar>(R.id.pbUpdateChecking)
        val pbDownloading = dialogView.findViewById<ProgressBar>(R.id.pbUpdateDownloading)
        val btnAction = dialogView.findViewById<MaterialButton>(R.id.btnUpdateAction)
        val btnSecondary = dialogView.findViewById<MaterialButton>(R.id.btnUpdateSecondary)
        val btnClose = dialogView.findViewById<MaterialButton>(R.id.btnUpdateClose)

        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()

        val scope = CoroutineScope(Dispatchers.Main)

        fun check() {
            pbChecking.visibility = View.VISIBLE
            tvStatus.text = "جارٍ الاتصال بمستودع GitHub للتحقق... ⏳"
            tvChangelog.text = ""
            btnAction.visibility = View.GONE
            btnSecondary.visibility = View.GONE

            scope.launch {
                val updateInfo = checkGitHubRelease(activity)
                pbChecking.visibility = View.GONE

                if (updateInfo.isUpdateAvailable) {
                    tvStatus.text = "🎉 يتوفر تحديث جديد: v${updateInfo.latestVersion}"
                    tvStatus.setTextColor(Color.parseColor("#00E5FF")) // Neon Cyan
                    tvChangelog.text = updateInfo.changelog

                    btnAction.visibility = View.VISIBLE
                    btnAction.text = "تحميل وتثبيت التحديث الآن ⬇️"
                    btnAction.setOnClickListener {
                        btnAction.isEnabled = false
                        pbDownloading.visibility = View.VISIBLE
                        pbDownloading.progress = 0

                        scope.launch {
                            val apk = downloadApk(activity, updateInfo.downloadUrl) { progress ->
                                activity.runOnUiThread {
                                    pbDownloading.progress = progress
                                }
                            }
                            pbDownloading.visibility = View.GONE
                            btnAction.isEnabled = true

                            if (apk != null && apk.exists()) {
                                promptInstallApk(activity, apk)
                                dialog.dismiss()
                            } else {
                                Toast.makeText(activity, "فشل التحميل المباشر، جاري الفتح في المتصفح...", Toast.LENGTH_SHORT).show()
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.downloadUrl))
                                activity.startActivity(browserIntent)
                            }
                        }
                    }

                    btnSecondary.visibility = View.VISIBLE
                    btnSecondary.text = "عرض على GitHub 🌐"
                    btnSecondary.setOnClickListener {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.releaseHtmlUrl))
                        activity.startActivity(browserIntent)
                    }
                } else {
                    tvStatus.text = "🟢 أنت تستخدم أحدث إصدار بالفعل (v${updateInfo.currentVersion})"
                    tvStatus.setTextColor(Color.parseColor("#00E676")) // Neon Green
                    tvChangelog.text = updateInfo.changelog

                    btnAction.visibility = View.VISIBLE
                    btnAction.text = "إعادة الفحص الآن 🔄"
                    btnAction.setOnClickListener {
                        check()
                    }

                    btnSecondary.visibility = View.VISIBLE
                    btnSecondary.text = "مستودع GitHub 🌐"
                    btnSecondary.setOnClickListener {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.releaseHtmlUrl))
                        activity.startActivity(browserIntent)
                    }
                }
            }
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        // Start checking
        check()
    }
}
