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
            while (keys.hasNext()) {
                val key = keys.next()
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
                            set.add(value.getString(i))
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
