package com.max.adblock

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("adblock_max_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SELECTED_MODE = "key_selected_mode"
        private const val KEY_CUSTOM_IPV4 = "key_custom_ipv4"
        private const val KEY_CUSTOM_IPV6 = "key_custom_ipv6"
        private const val KEY_SESSION_START_TIME = "key_session_start_time"
        private const val KEY_VPN_ACTIVE = "key_vpn_active"
        private const val KEY_VPN_PAUSED = "key_vpn_paused"

        // Phase 2 Keys
        private const val KEY_AUTO_BOOT = "key_auto_boot"
        private const val KEY_AUTO_WIFI = "key_auto_wifi"
        private const val KEY_AUTO_CELLULAR = "key_auto_cellular"
        private const val KEY_SCREEN_OFF_LOCKDOWN = "key_screen_off_lockdown"
        private const val KEY_AUTO_FAILOVER = "key_auto_failover"
        private const val KEY_KILL_SWITCH = "key_kill_switch"
        private const val KEY_SLEEP_TIMER_MINUTES = "key_sleep_timer_minutes"
        private const val KEY_ACTIVE_PROFILE = "key_active_profile"
        private const val KEY_IS_FAILOVER_ACTIVE = "key_is_failover_active"

        // Phase 3 Keys
        private const val KEY_BLOCKED_FIREWALL_APPS = "key_blocked_firewall_apps"
        private const val KEY_BYPASSED_APPS = "key_bypassed_apps"
        private const val KEY_BLOCK_AUTO_UPDATES = "key_block_auto_updates"
        private const val PREFIX_APP_QUOTA = "quota_"
        private const val PREFIX_APP_BASELINE_BYTES = "baseline_bytes_"

        // Phase 4 Keys
        private const val KEY_CUSTOM_BLACKLIST = "key_custom_blacklist"
        private const val KEY_CUSTOM_WHITELIST = "key_custom_whitelist"
        private const val KEY_GEO_BLOCKED_TLDS = "key_geo_blocked_tlds"
        private const val KEY_FILTER_GENERAL_ADS = "key_filter_general_ads"
        private const val KEY_FILTER_TRACKERS = "key_filter_trackers"
        private const val KEY_FILTER_CRYPTOMINING = "key_filter_cryptomining"
        private const val KEY_FILTER_SOCIAL = "key_filter_social"
        private const val KEY_GEO_BLOCKING_ENABLED = "key_geo_blocking_enabled"
        private const val KEY_BLOCKED_TODAY = "key_blocked_today"
        private const val KEY_BLOCKED_TOTAL = "key_blocked_total"
        private const val KEY_NIGHT_WATCH_COUNT = "key_night_watch_count"

        // Phase 6 Keys
        private const val KEY_APP_LOCK_ENABLED = "key_app_lock_enabled"
        private const val KEY_APP_LOCK_PIN = "key_app_lock_pin"
        private const val KEY_AUDIO_MUTER_ENABLED = "key_audio_muter_enabled"
        private const val KEY_STEALTH_CAMOUFLAGE = "key_stealth_camouflage"
        private const val KEY_PREFERRED_UA = "key_preferred_ua"

        // v2.1.0 Smart Hub Keys
        private const val KEY_SMART_AUTO_SKIP_ADS = "key_smart_auto_skip_ads"
        private const val KEY_SMART_ANTI_REDIRECT = "key_smart_anti_redirect"
        private const val KEY_SMART_AUTO_REJECT_COOKIES = "key_smart_auto_reject_cookies"
        private const val KEY_SMART_APP_AWARE_DNS = "key_smart_app_aware_dns"
        private const val KEY_SMART_GAMING_APPS = "key_smart_gaming_apps"
        private const val KEY_SMART_PRE_GAME_DNS_MODE = "key_smart_pre_game_dns_mode"
        private const val KEY_SMART_AUTO_LEARN_WHITELIST = "key_smart_auto_learn_whitelist"
        private const val KEY_PENDING_WHITELIST_PKG = "key_pending_whitelist_pkg"
        private const val KEY_LAST_FOREGROUND_PKG = "key_last_foreground_pkg"
        private const val PREFIX_APP_PAUSE_COUNT = "pause_count_"

        private const val KEY_SMART_WIFI_TRIGGERS = "key_smart_wifi_triggers"
        private const val KEY_TRUSTED_HOME_SSID = "key_trusted_home_ssid"
        private const val KEY_TRUSTED_HOME_BSSID = "key_trusted_home_bssid"
        private const val KEY_SMART_EVIL_TWIN = "key_smart_evil_twin"
        private const val KEY_SMART_SELF_HEALING = "key_smart_self_healing"
        private const val KEY_SELF_HEALING_COUNT = "key_self_healing_count"
        private const val KEY_SMART_SCHEDULE_ENABLED = "key_smart_schedule_enabled"
        private const val KEY_SMART_SCHEDULE_START_HOUR = "key_smart_schedule_start_hour"
        private const val KEY_SMART_SCHEDULE_START_MIN = "key_smart_schedule_start_min"
        private const val KEY_SMART_SCHEDULE_END_HOUR = "key_smart_schedule_end_hour"
        private const val KEY_SMART_SCHEDULE_END_MIN = "key_smart_schedule_end_min"
        private const val KEY_SMART_SCHEDULE_DAYS = "key_smart_schedule_days"
        private const val KEY_SMART_BATTERY_SURVIVAL = "key_smart_battery_survival"
        private const val KEY_IS_BATTERY_SURVIVAL_ACTIVE = "key_is_battery_survival_active"
        private const val KEY_MONTHLY_PACKAGE_GB = "key_monthly_package_gb"

        private const val KEY_SMART_PHISHING_SHIELD = "key_smart_phishing_shield"
        private const val KEY_SMART_SILENT_SPY = "key_smart_silent_spy"
        private const val KEY_SMART_POST_UPDATE_WATCHER = "key_smart_post_update_watcher"
        private const val KEY_SMART_NEW_APP_SCORE = "key_smart_new_app_score"
        private const val KEY_SMART_FAKE_APK_GUARD = "key_smart_fake_apk_guard"
        private const val KEY_SMART_SCREEN_RECORD_PRIVACY = "key_smart_screen_record_privacy"
        private const val PREFIX_SAVED_TRACKER_COUNT = "saved_trackers_"
        private const val KEY_LAST_SPY_ALERT_SUMMARY = "key_last_spy_alert_summary"
        private const val KEY_LAST_SPY_ALERT_PKG = "key_last_spy_alert_pkg"
        private const val KEY_LAST_SUSPICIOUS_APK_NAME = "key_last_suspicious_apk_name"
        private const val KEY_LAST_SUSPICIOUS_APK_PATH = "key_last_suspicious_apk_path"

        val DEFAULT_GAMING_PACKAGES = setOf(
            "com.tencent.ig",
            "com.pubg.krmobile",
            "com.dts.freefireth",
            "com.dts.freefiremax",
            "com.activision.callofduty.shooter",
            "com.mobile.legends",
            "com.supercell.brawlstars",
            "com.roblox.client"
        )
    }

    var selectedMode: DnsMode
        get() {
            val name = prefs.getString(KEY_SELECTED_MODE, DnsMode.STANDARD.name) ?: DnsMode.STANDARD.name
            return try {
                DnsMode.valueOf(name)
            } catch (e: Exception) {
                DnsMode.STANDARD
            }
        }
        set(value) {
            prefs.edit().putString(KEY_SELECTED_MODE, value.name).apply()
        }

    var customIpv4: String
        get() = prefs.getString(KEY_CUSTOM_IPV4, "94.140.14.14") ?: "94.140.14.14"
        set(value) = prefs.edit().putString(KEY_CUSTOM_IPV4, value).apply()

    var customIpv6: String
        get() = prefs.getString(KEY_CUSTOM_IPV6, "2a10:50c0::ad1:ff") ?: "2a10:50c0::ad1:ff"
        set(value) = prefs.edit().putString(KEY_CUSTOM_IPV6, value).apply()

    var sessionStartTime: Long
        get() = prefs.getLong(KEY_SESSION_START_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_SESSION_START_TIME, value).apply()

    var isVpnActive: Boolean
        get() = prefs.getBoolean(KEY_VPN_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_VPN_ACTIVE, value).apply()

    var isVpnPaused: Boolean
        get() = prefs.getBoolean(KEY_VPN_PAUSED, false)
        set(value) = prefs.edit().putBoolean(KEY_VPN_PAUSED, value).apply()

    // Phase 2 Properties
    var autoStartOnBoot: Boolean
        get() = prefs.getBoolean(KEY_AUTO_BOOT, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_BOOT, value).apply()

    var autoOnWifi: Boolean
        get() = prefs.getBoolean(KEY_AUTO_WIFI, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_WIFI, value).apply()

    var autoOnCellular: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CELLULAR, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CELLULAR, value).apply()

    var screenOffLockdown: Boolean
        get() = prefs.getBoolean(KEY_SCREEN_OFF_LOCKDOWN, false)
        set(value) = prefs.edit().putBoolean(KEY_SCREEN_OFF_LOCKDOWN, value).apply()

    var autoFailoverEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_FAILOVER, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_FAILOVER, value).apply()

    var killSwitchEnabled: Boolean
        get() = prefs.getBoolean(KEY_KILL_SWITCH, false)
        set(value) = prefs.edit().putBoolean(KEY_KILL_SWITCH, value).apply()

    var sleepTimerMinutes: Int
        get() = prefs.getInt(KEY_SLEEP_TIMER_MINUTES, 0)
        set(value) = prefs.edit().putInt(KEY_SLEEP_TIMER_MINUTES, value).apply()

    var activeProfile: String
        get() = prefs.getString(KEY_ACTIVE_PROFILE, "NONE") ?: "NONE"
        set(value) = prefs.edit().putString(KEY_ACTIVE_PROFILE, value).apply()

    var isFailoverActive: Boolean
        get() = prefs.getBoolean(KEY_IS_FAILOVER_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_FAILOVER_ACTIVE, value).apply()

    // Phase 3 Properties & Methods
    var blockAutoUpdatesOnCellular: Boolean
        get() = prefs.getBoolean(KEY_BLOCK_AUTO_UPDATES, false)
        set(value) = prefs.edit().putBoolean(KEY_BLOCK_AUTO_UPDATES, value).apply()

    fun getBlockedApps(): Set<String> {
        return prefs.getStringSet(KEY_BLOCKED_FIREWALL_APPS, emptySet()) ?: emptySet()
    }

    fun isAppBlocked(pkg: String): Boolean {
        return getBlockedApps().contains(pkg)
    }

    fun setAppBlocked(pkg: String, blocked: Boolean) {
        val set = getBlockedApps().toMutableSet()
        if (blocked) {
            set.add(pkg)
            // Cannot be both blocked and bypassed
            setAppBypassed(pkg, false)
        } else {
            set.remove(pkg)
        }
        prefs.edit().putStringSet(KEY_BLOCKED_FIREWALL_APPS, set).apply()
    }

    fun getBypassedApps(): Set<String> {
        return prefs.getStringSet(KEY_BYPASSED_APPS, emptySet()) ?: emptySet()
    }

    fun isAppBypassed(pkg: String): Boolean {
        return getBypassedApps().contains(pkg)
    }

    fun setAppBypassed(pkg: String, bypassed: Boolean) {
        val set = getBypassedApps().toMutableSet()
        if (bypassed) {
            set.add(pkg)
            // Cannot be both bypassed and blocked
            val blockedSet = getBlockedApps().toMutableSet()
            blockedSet.remove(pkg)
            prefs.edit().putStringSet(KEY_BLOCKED_FIREWALL_APPS, blockedSet).apply()
        } else {
            set.remove(pkg)
        }
        prefs.edit().putStringSet(KEY_BYPASSED_APPS, set).apply()
    }

    fun getAppQuota(pkg: String): Int {
        return prefs.getInt(PREFIX_APP_QUOTA + pkg, 0)
    }

    fun setAppQuota(pkg: String, quotaMb: Int) {
        prefs.edit().putInt(PREFIX_APP_QUOTA + pkg, quotaMb).apply()
    }

    fun getBaselineBytes(pkg: String): Long {
        return prefs.getLong(PREFIX_APP_BASELINE_BYTES + pkg, 0L)
    }

    fun setBaselineBytes(pkg: String, bytes: Long) {
        prefs.edit().putLong(PREFIX_APP_BASELINE_BYTES + pkg, bytes).apply()
    }

    // Phase 4 Properties & Methods
    var filterGeneralAds: Boolean
        get() = prefs.getBoolean(KEY_FILTER_GENERAL_ADS, true)
        set(value) = prefs.edit().putBoolean(KEY_FILTER_GENERAL_ADS, value).apply()

    var filterTrackers: Boolean
        get() = prefs.getBoolean(KEY_FILTER_TRACKERS, true)
        set(value) = prefs.edit().putBoolean(KEY_FILTER_TRACKERS, value).apply()

    var filterCryptomining: Boolean
        get() = prefs.getBoolean(KEY_FILTER_CRYPTOMINING, true)
        set(value) = prefs.edit().putBoolean(KEY_FILTER_CRYPTOMINING, value).apply()

    var filterSocialDistractions: Boolean
        get() = prefs.getBoolean(KEY_FILTER_SOCIAL, false)
        set(value) = prefs.edit().putBoolean(KEY_FILTER_SOCIAL, value).apply()

    var geoBlockingEnabled: Boolean
        get() = prefs.getBoolean(KEY_GEO_BLOCKING_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_GEO_BLOCKING_ENABLED, value).apply()

    var blockedCountToday: Int
        get() = prefs.getInt(KEY_BLOCKED_TODAY, 142)
        set(value) = prefs.edit().putInt(KEY_BLOCKED_TODAY, value).apply()

    var blockedCountTotal: Int
        get() = prefs.getInt(KEY_BLOCKED_TOTAL, 1284)
        set(value) = prefs.edit().putInt(KEY_BLOCKED_TOTAL, value).apply()

    var nightWatchCount: Int
        get() = prefs.getInt(KEY_NIGHT_WATCH_COUNT, 38)
        set(value) = prefs.edit().putInt(KEY_NIGHT_WATCH_COUNT, value).apply()

    fun getCustomBlacklist(): Set<String> {
        return prefs.getStringSet(KEY_CUSTOM_BLACKLIST, emptySet()) ?: emptySet()
    }

    fun addCustomBlacklist(domain: String) {
        val set = getCustomBlacklist().toMutableSet()
        set.add(domain.lowercase().trim())
        prefs.edit().putStringSet(KEY_CUSTOM_BLACKLIST, set).apply()
    }

    fun removeCustomBlacklist(domain: String) {
        val set = getCustomBlacklist().toMutableSet()
        set.remove(domain.lowercase().trim())
        prefs.edit().putStringSet(KEY_CUSTOM_BLACKLIST, set).apply()
    }

    fun getCustomWhitelist(): Set<String> {
        return prefs.getStringSet(KEY_CUSTOM_WHITELIST, emptySet()) ?: emptySet()
    }

    fun addCustomWhitelist(domain: String) {
        val set = getCustomWhitelist().toMutableSet()
        set.add(domain.lowercase().trim())
        prefs.edit().putStringSet(KEY_CUSTOM_WHITELIST, set).apply()
    }

    fun removeCustomWhitelist(domain: String) {
        val set = getCustomWhitelist().toMutableSet()
        set.remove(domain.lowercase().trim())
        prefs.edit().putStringSet(KEY_CUSTOM_WHITELIST, set).apply()
    }

    fun getGeoBlockedTlds(): Set<String> {
        return prefs.getStringSet(KEY_GEO_BLOCKED_TLDS, FilterRulesManager.DEFAULT_GEO_TLDS)
            ?: FilterRulesManager.DEFAULT_GEO_TLDS
    }

    fun setGeoBlockedTlds(tlds: Set<String>) {
        prefs.edit().putStringSet(KEY_GEO_BLOCKED_TLDS, tlds).apply()
    }

    // Phase 6: App Lock, Audio Muter, Camouflage & User Agent
    var isAppLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_APP_LOCK_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_APP_LOCK_ENABLED, value).apply()

    var appLockPin: String
        get() = prefs.getString(KEY_APP_LOCK_PIN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_APP_LOCK_PIN, value).apply()

    var isAudioMuterEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUDIO_MUTER_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_AUDIO_MUTER_ENABLED, value).apply()

    var isStealthCamouflageEnabled: Boolean
        get() = prefs.getBoolean(KEY_STEALTH_CAMOUFLAGE, false)
        set(value) = prefs.edit().putBoolean(KEY_STEALTH_CAMOUFLAGE, value).apply()

    var preferredUserAgent: String
        get() = prefs.getString(KEY_PREFERRED_UA, "DEFAULT") ?: "DEFAULT"
        set(value) = prefs.edit().putString(KEY_PREFERRED_UA, value).apply()

    // v2.1.0 Smart Hub Properties
    var smartAutoSkipAds: Boolean
        get() = prefs.getBoolean(KEY_SMART_AUTO_SKIP_ADS, false)
        set(value) = prefs.edit().putBoolean(KEY_SMART_AUTO_SKIP_ADS, value).apply()

    var smartAntiRedirect: Boolean
        get() = prefs.getBoolean(KEY_SMART_ANTI_REDIRECT, false)
        set(value) = prefs.edit().putBoolean(KEY_SMART_ANTI_REDIRECT, value).apply()

    var smartAutoRejectCookies: Boolean
        get() = prefs.getBoolean(KEY_SMART_AUTO_REJECT_COOKIES, true)
        set(value) = prefs.edit().putBoolean(KEY_SMART_AUTO_REJECT_COOKIES, value).apply()

    var smartAppAwareDns: Boolean
        get() = prefs.getBoolean(KEY_SMART_APP_AWARE_DNS, false)
        set(value) = prefs.edit().putBoolean(KEY_SMART_APP_AWARE_DNS, value).apply()

    fun getSmartGamingApps(): Set<String> {
        return prefs.getStringSet(KEY_SMART_GAMING_APPS, DEFAULT_GAMING_PACKAGES) ?: DEFAULT_GAMING_PACKAGES
    }

    fun setSmartGamingApps(apps: Set<String>) {
        prefs.edit().putStringSet(KEY_SMART_GAMING_APPS, apps).apply()
    }

    fun toggleSmartGamingApp(pkg: String, isGaming: Boolean) {
        val set = getSmartGamingApps().toMutableSet()
        if (isGaming) set.add(pkg) else set.remove(pkg)
        setSmartGamingApps(set)
    }

    var smartPreGameDnsMode: String
        get() = prefs.getString(KEY_SMART_PRE_GAME_DNS_MODE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SMART_PRE_GAME_DNS_MODE, value).apply()

    var smartAutoLearnWhitelist: Boolean
        get() = prefs.getBoolean(KEY_SMART_AUTO_LEARN_WHITELIST, true)
        set(value) = prefs.edit().putBoolean(KEY_SMART_AUTO_LEARN_WHITELIST, value).apply()

    var pendingWhitelistSuggestionPkg: String
        get() = prefs.getString(KEY_PENDING_WHITELIST_PKG, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PENDING_WHITELIST_PKG, value).apply()

    var lastForegroundAppPkg: String
        get() = prefs.getString(KEY_LAST_FOREGROUND_PKG, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_FOREGROUND_PKG, value).apply()

    fun getAppPauseCount(pkg: String): Int = prefs.getInt(PREFIX_APP_PAUSE_COUNT + pkg, 0)

    fun incrementAppPauseCount(pkg: String): Int {
        val next = getAppPauseCount(pkg) + 1
        prefs.edit().putInt(PREFIX_APP_PAUSE_COUNT + pkg, next).apply()
        return next
    }

    fun resetAppPauseCount(pkg: String) {
        prefs.edit().remove(PREFIX_APP_PAUSE_COUNT + pkg).apply()
    }

    var smartWifiSsidTriggers: Boolean
        get() = prefs.getBoolean(KEY_SMART_WIFI_TRIGGERS, false)
        set(value) = prefs.edit().putBoolean(KEY_SMART_WIFI_TRIGGERS, value).apply()

    var trustedHomeSsid: String
        get() = prefs.getString(KEY_TRUSTED_HOME_SSID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TRUSTED_HOME_SSID, value).apply()

    var trustedHomeBssid: String
        get() = prefs.getString(KEY_TRUSTED_HOME_BSSID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TRUSTED_HOME_BSSID, value).apply()

    var smartEvilTwinDetector: Boolean
        get() = prefs.getBoolean(KEY_SMART_EVIL_TWIN, true)
        set(value) = prefs.edit().putBoolean(KEY_SMART_EVIL_TWIN, value).apply()

    var smartSelfHealingWatchdog: Boolean
        get() = prefs.getBoolean(KEY_SMART_SELF_HEALING, true)
        set(value) = prefs.edit().putBoolean(KEY_SMART_SELF_HEALING, value).apply()

    var selfHealingCount: Int
        get() = prefs.getInt(KEY_SELF_HEALING_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_SELF_HEALING_COUNT, value).apply()

    var smartScheduleEnabled: Boolean
        get() = prefs.getBoolean(KEY_SMART_SCHEDULE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SMART_SCHEDULE_ENABLED, value).apply()

    var smartScheduleStartHour: Int
        get() = prefs.getInt(KEY_SMART_SCHEDULE_START_HOUR, 22)
        set(value) = prefs.edit().putInt(KEY_SMART_SCHEDULE_START_HOUR, value).apply()

    var smartScheduleStartMinute: Int
        get() = prefs.getInt(KEY_SMART_SCHEDULE_START_MIN, 0)
        set(value) = prefs.edit().putInt(KEY_SMART_SCHEDULE_START_MIN, value).apply()

    var smartScheduleEndHour: Int
        get() = prefs.getInt(KEY_SMART_SCHEDULE_END_HOUR, 6)
        set(value) = prefs.edit().putInt(KEY_SMART_SCHEDULE_END_HOUR, value).apply()

    var smartScheduleEndMinute: Int
        get() = prefs.getInt(KEY_SMART_SCHEDULE_END_MIN, 0)
        set(value) = prefs.edit().putInt(KEY_SMART_SCHEDULE_END_MIN, value).apply()

    fun getSmartScheduleDays(): Set<String> {
        val defaultDays = setOf("1", "2", "3", "4", "5", "6", "7")
        return prefs.getStringSet(KEY_SMART_SCHEDULE_DAYS, defaultDays) ?: defaultDays
    }

    fun setSmartScheduleDays(days: Set<String>) {
        prefs.edit().putStringSet(KEY_SMART_SCHEDULE_DAYS, days).apply()
    }

    var smartBatterySurvivalEnabled: Boolean
        get() = prefs.getBoolean(KEY_SMART_BATTERY_SURVIVAL, true)
        set(value) = prefs.edit().putBoolean(KEY_SMART_BATTERY_SURVIVAL, value).apply()

    var isBatterySurvivalActive: Boolean
        get() = prefs.getBoolean(KEY_IS_BATTERY_SURVIVAL_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_BATTERY_SURVIVAL_ACTIVE, value).apply()

    var monthlyPackageGb: Int
        get() = prefs.getInt(KEY_MONTHLY_PACKAGE_GB, 50)
        set(value) = prefs.edit().putInt(KEY_MONTHLY_PACKAGE_GB, value).apply()

    var smartPhishingNotifShield: Boolean
        get() = prefs.getBoolean(KEY_SMART_PHISHING_SHIELD, true)
        set(value) = prefs.edit().putBoolean(KEY_SMART_PHISHING_SHIELD, value).apply()

    var smartSilentSpyDetector: Boolean
        get() = prefs.getBoolean(KEY_SMART_SILENT_SPY, true)
        set(value) = prefs.edit().putBoolean(KEY_SMART_SILENT_SPY, value).apply()

    var smartPostUpdateWatcher: Boolean
        get() = prefs.getBoolean(KEY_SMART_POST_UPDATE_WATCHER, true)
        set(value) = prefs.edit().putBoolean(KEY_SMART_POST_UPDATE_WATCHER, value).apply()

    var smartNewAppPrivacyScore: Boolean
        get() = prefs.getBoolean(KEY_SMART_NEW_APP_SCORE, true)
        set(value) = prefs.edit().putBoolean(KEY_SMART_NEW_APP_SCORE, value).apply()

    var smartFakeApkGuard: Boolean
        get() = prefs.getBoolean(KEY_SMART_FAKE_APK_GUARD, true)
        set(value) = prefs.edit().putBoolean(KEY_SMART_FAKE_APK_GUARD, value).apply()

    var smartScreenRecordPrivacy: Boolean
        get() = prefs.getBoolean(KEY_SMART_SCREEN_RECORD_PRIVACY, true)
        set(value) = prefs.edit().putBoolean(KEY_SMART_SCREEN_RECORD_PRIVACY, value).apply()

    fun getSavedTrackerCount(pkg: String): Int = prefs.getInt(PREFIX_SAVED_TRACKER_COUNT + pkg, -1)

    fun setSavedTrackerCount(pkg: String, count: Int) {
        prefs.edit().putInt(PREFIX_SAVED_TRACKER_COUNT + pkg, count).apply()
    }

    var lastSpyAlertSummary: String
        get() = prefs.getString(KEY_LAST_SPY_ALERT_SUMMARY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_SPY_ALERT_SUMMARY, value).apply()

    var lastSpyAlertPkg: String
        get() = prefs.getString(KEY_LAST_SPY_ALERT_PKG, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_SPY_ALERT_PKG, value).apply()

    var lastSuspiciousApkName: String
        get() = prefs.getString(KEY_LAST_SUSPICIOUS_APK_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_SUSPICIOUS_APK_NAME, value).apply()

    var lastSuspiciousApkPath: String
        get() = prefs.getString(KEY_LAST_SUSPICIOUS_APK_PATH, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_SUSPICIOUS_APK_PATH, value).apply()

    // Backup & Restore
    fun exportSettingsToJson(): String {
        val root = org.json.JSONObject()
        val all = prefs.all
        for ((key, value) in all) {
            when (value) {
                is String -> root.put(key, value)
                is Boolean -> root.put(key, value)
                is Int -> root.put(key, value)
                is Long -> root.put(key, value)
                is Float -> root.put(key, value.toDouble())
                is Set<*> -> {
                    val arr = org.json.JSONArray()
                    value.forEach { item -> if (item is String) arr.put(item) }
                    root.put(key, arr)
                }
            }
        }
        return root.toString(2)
    }

    fun importSettingsFromJson(jsonString: String): Boolean {
        return try {
            val root = org.json.JSONObject(jsonString)
            val editor = prefs.edit()
            val keys = root.keys()

            // Strict Whitelist of allowed import keys (protects against PIN override or malicious key injection)
            val allowedKeys = setOf(
                KEY_SELECTED_MODE, KEY_CUSTOM_IPV4, KEY_CUSTOM_IPV6,
                KEY_AUTO_BOOT, KEY_AUTO_WIFI, KEY_AUTO_CELLULAR,
                KEY_SCREEN_OFF_LOCKDOWN, KEY_AUTO_FAILOVER, KEY_KILL_SWITCH,
                KEY_SLEEP_TIMER_MINUTES, KEY_ACTIVE_PROFILE,
                KEY_BLOCKED_FIREWALL_APPS, KEY_BYPASSED_APPS, KEY_BLOCK_AUTO_UPDATES,
                KEY_CUSTOM_BLACKLIST, KEY_CUSTOM_WHITELIST, KEY_GEO_BLOCKED_TLDS,
                KEY_FILTER_GENERAL_ADS, KEY_FILTER_TRACKERS, KEY_FILTER_CRYPTOMINING,
                KEY_FILTER_SOCIAL, KEY_GEO_BLOCKING_ENABLED,
                KEY_AUDIO_MUTER_ENABLED, KEY_STEALTH_CAMOUFLAGE, KEY_PREFERRED_UA,
                KEY_SMART_AUTO_SKIP_ADS, KEY_SMART_ANTI_REDIRECT, KEY_SMART_AUTO_REJECT_COOKIES,
                KEY_SMART_APP_AWARE_DNS, KEY_SMART_GAMING_APPS, KEY_SMART_AUTO_LEARN_WHITELIST,
                KEY_SMART_WIFI_TRIGGERS, KEY_TRUSTED_HOME_SSID, KEY_TRUSTED_HOME_BSSID,
                KEY_SMART_EVIL_TWIN, KEY_SMART_SELF_HEALING, KEY_SMART_SCHEDULE_ENABLED,
                KEY_SMART_SCHEDULE_START_HOUR, KEY_SMART_SCHEDULE_START_MIN,
                KEY_SMART_SCHEDULE_END_HOUR, KEY_SMART_SCHEDULE_END_MIN, KEY_SMART_SCHEDULE_DAYS,
                KEY_SMART_BATTERY_SURVIVAL, KEY_MONTHLY_PACKAGE_GB,
                KEY_SMART_PHISHING_SHIELD, KEY_SMART_SILENT_SPY, KEY_SMART_POST_UPDATE_WATCHER,
                KEY_SMART_NEW_APP_SCORE, KEY_SMART_FAKE_APK_GUARD, KEY_SMART_SCREEN_RECORD_PRIVACY
            )

            while (keys.hasNext()) {
                val key = keys.next()
                if (!allowedKeys.contains(key) && !key.startsWith(PREFIX_APP_QUOTA)) {
                    continue
                }

                val value = root.get(key)
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Double -> editor.putFloat(key, value.toFloat())
                    is String -> editor.putString(key, value)
                    is org.json.JSONArray -> {
                        val set = mutableSetOf<String>()
                        for (i in 0 until value.length()) {
                            val str = value.optString(i)
                            if (str.isNotBlank()) set.add(str)
                        }
                        editor.putStringSet(key, set)
                    }
                }
            }
            editor.apply()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
