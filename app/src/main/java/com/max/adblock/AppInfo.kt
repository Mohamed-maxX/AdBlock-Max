package com.max.adblock

import android.graphics.drawable.Drawable

enum class BatteryDrainRisk {
    LOW,     // خالي من المتتبعات أو منخفض جداً
    MEDIUM,  // يحتوي على 1-2 شبكات إعلانية
    HIGH     // يحتوي على 3+ شبكات إعلانية أو خدمات تتبع نشطة
}

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val uid: Int,
    val isSystemApp: Boolean,
    var isFirewallBlocked: Boolean = false,
    var isBypassed: Boolean = false,
    var dailyQuotaMb: Int = 0, // 0 = unlimited
    var usedTodayBytes: Long = 0L,
    val trackerCount: Int = 0,
    val trackersFound: List<String> = emptyList(),
    val batteryDrainRisk: BatteryDrainRisk = BatteryDrainRisk.LOW
)
