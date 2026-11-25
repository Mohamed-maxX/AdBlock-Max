package com.max.adblock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object SmartNotificationHelper {

    private const val CHANNEL_ID = "adblock_smart_alerts"
    private const val CHANNEL_NAME = "تنبيهات الذكاء التلقائي والأمان"

    private const val NOTIF_ID_EVIL_TWIN = 7001
    private const val NOTIF_ID_PHISHING = 7002
    private const val NOTIF_ID_SPY_ALERT = 7003
    private const val NOTIF_ID_NEW_APP_BASE = 7100
    private const val NOTIF_ID_POST_UPDATE_BASE = 7300
    private const val NOTIF_ID_FAKE_APK = 7004
    private const val NOTIF_ID_WHITELIST_SUGGEST = 7005

    private fun ensureChannel(context: Context): NotificationManager {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "تنبيهات فورية للشبكات المزيفة، الروابط الخبيثة، وتقييم التطبيقات"
                }
                manager.createNotificationChannel(channel)
            }
        }
        return manager
    }

    private fun openSmartHubIntent(context: Context, tabIndex: Int = 0): PendingIntent {
        val intent = Intent(context, SmartHubActivity::class.java).apply {
            putExtra(SmartHubActivity.EXTRA_TAB_INDEX, tabIndex)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            800 + tabIndex,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun showEvilTwinAlert(context: Context, ssid: String, detectedBssid: String) {
        try {
            val manager = ensureChannel(context)
            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_shield_protected)
                .setContentTitle("🚨 تحذير شبكة واي فاي مزيفة (Evil Twin)!")
                .setContentText("شبكة \"$ssid\" تعمل ببصمة راوتر مختلفة ($detectedBssid). تم تفعيل أقصى حماية!")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "رصدنا تطابق اسم شبكة المنزل \"$ssid\" مع عنوان BSSID غير معروف ($detectedBssid). قد تكون هذه نقطة وصول وهمية للتنصت!"
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openSmartHubIntent(context, 1))
                .setAutoCancel(true)
                .build()
            manager.notify(NOTIF_ID_EVIL_TWIN, notif)
        } catch (_: Exception) {
        }
    }

    fun showPhishingAlert(context: Context, sourceApp: String, suspiciousUrl: String) {
        try {
            val manager = ensureChannel(context)
            val safePreview = suspiciousUrl.take(60)
            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_block)
                .setContentTitle("⚠️ رابط مشبوه في إشعار من $sourceApp")
                .setContentText("تم رصد رابط مختصر أو تصيد محتمل: $safePreview")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "تحذير أمني: يحتوي الإشعار الوارد من ($sourceApp) على رابط مشبوه أو مختصر:\n$safePreview\nتجنب الضغط عليه أو افحصه عبر حارس الروابط."
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openSmartHubIntent(context, 2))
                .setAutoCancel(true)
                .build()
            manager.notify(NOTIF_ID_PHISHING, notif)
        } catch (_: Exception) {
        }
    }

    fun showSilentSpyAlert(context: Context, appName: String, packageName: String, mbUsed: Double) {
        try {
            val manager = ensureChannel(context)
            val formattedMb = String.format("%.1f", mbUsed)

            val blockIntent = Intent(context, VpnActionReceiver::class.java).apply {
                action = VpnActionReceiver.ACTION_BLOCK_APP_FIREWALL
                putExtra(VpnActionReceiver.EXTRA_PACKAGE_NAME, packageName)
                putExtra(VpnActionReceiver.EXTRA_APP_NAME, appName)
                setPackage(context.packageName)
            }
            val blockPending = PendingIntent.getBroadcast(
                context,
                packageName.hashCode(),
                blockIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_firewall)
                .setContentTitle("🕵️ نشاط شبكي خفي أثناء قفل الشاشة")
                .setContentText("تطبيق $appName استهلك $formattedMb MB في الخلفية أثناء توقف الشاشة!")
                .addAction(R.drawable.ic_block, "حظر في الجدار الناري 🚫", blockPending)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openSmartHubIntent(context, 2))
                .setAutoCancel(true)
                .build()
            manager.notify(NOTIF_ID_SPY_ALERT, notif)
        } catch (_: Exception) {
        }
    }

    fun showNewAppPrivacyScore(
        context: Context,
        appName: String,
        packageName: String,
        scoreOutOf10: Int,
        trackerCount: Int
    ) {
        try {
            val manager = ensureChannel(context)

            val blockIntent = Intent(context, VpnActionReceiver::class.java).apply {
                action = VpnActionReceiver.ACTION_BLOCK_APP_FIREWALL
                putExtra(VpnActionReceiver.EXTRA_PACKAGE_NAME, packageName)
                putExtra(VpnActionReceiver.EXTRA_APP_NAME, appName)
                setPackage(context.packageName)
            }
            val blockPending = PendingIntent.getBroadcast(
                context,
                ("new_" + packageName).hashCode(),
                blockIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val badge = when {
                scoreOutOf10 >= 8 -> "آمن 🟢"
                scoreOutOf10 >= 5 -> "متوسط 🟡"
                else -> "خطير 🔴"
            }

            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_shield_protected)
                .setContentTitle("تقييم خصوصية تطبيق جديد: $appName ($scoreOutOf10/10 $badge)")
                .setContentText("تم رصد $trackerCount مكتبة إعلان وتتبع بداخل التطبيق.")
                .addAction(R.drawable.ic_block, "قطع الإنترنت عنه 🛡️", blockPending)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openSmartHubIntent(context, 2))
                .setAutoCancel(true)
                .build()
            manager.notify(NOTIF_ID_NEW_APP_BASE + (packageName.hashCode() and 0xFF), notif)
        } catch (_: Exception) {
        }
    }

    fun showPostUpdateTrackerAlert(
        context: Context,
        appName: String,
        packageName: String,
        oldCount: Int,
        newCount: Int
    ) {
        try {
            val manager = ensureChannel(context)
            val added = newCount - oldCount

            val blockIntent = Intent(context, VpnActionReceiver::class.java).apply {
                action = VpnActionReceiver.ACTION_BLOCK_APP_FIREWALL
                putExtra(VpnActionReceiver.EXTRA_PACKAGE_NAME, packageName)
                putExtra(VpnActionReceiver.EXTRA_APP_NAME, appName)
                setPackage(context.packageName)
            }
            val blockPending = PendingIntent.getBroadcast(
                context,
                ("upd_" + packageName).hashCode(),
                blockIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_radar)
                .setContentTitle("🔍 تنبيه تحديث تطبيق: $appName")
                .setContentText("أضاف المطور $added مكتبات تتبع وإعلانات جديدة بعد التحديث (الإجمالي: $newCount)!")
                .addAction(R.drawable.ic_block, "حظر التطبيق 🚫", blockPending)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openSmartHubIntent(context, 2))
                .setAutoCancel(true)
                .build()
            manager.notify(NOTIF_ID_POST_UPDATE_BASE + (packageName.hashCode() and 0xFF), notif)
        } catch (_: Exception) {
        }
    }

    fun showFakeApkDownloadAlert(context: Context, fileName: String) {
        try {
            val manager = ensureChannel(context)
            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_block)
                .setContentTitle("🚨 صائد التحميلات الخبيثة (APK Guard)")
                .setContentText("تم رصد تحميل ملف تثبيت مشبوه: $fileName — افتح الذكاء التلقائي للفحص أو الحذف.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openSmartHubIntent(context, 2))
                .setAutoCancel(true)
                .build()
            manager.notify(NOTIF_ID_FAKE_APK, notif)
        } catch (_: Exception) {
        }
    }

    fun showWhitelistSuggestion(context: Context, appName: String) {
        try {
            val manager = ensureChannel(context)
            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_smart_ai)
                .setContentTitle("🧠 اقتراح ذكي: استثناء $appName")
                .setContentText("لاحظنا إيقاف الحماية 3 مرات أثناء استخدام $appName. اضغط لإضافته للاستثناءات الدائمة.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(openSmartHubIntent(context, 0))
                .setAutoCancel(true)
                .build()
            manager.notify(NOTIF_ID_WHITELIST_SUGGEST, notif)
        } catch (_: Exception) {
        }
    }
}
