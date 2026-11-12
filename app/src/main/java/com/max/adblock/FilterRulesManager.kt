package com.max.adblock

import android.content.Context

object FilterRulesManager {

    private val CORE_AD_DOMAINS = setOf(
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "admob.com",
        "applovin.com",
        "unity3d.com",
        "adcolony.com",
        "chartboost.com",
        "vungle.com",
        "ironsrc.mobi",
        "adnxs.com",
        "taboola.com",
        "outbrain.com",
        "criteo.com",
        "popads.net",
        "propellerads.com",
        "adservice.google.com",
        "pagead2.googlesyndication.com",
        "securepubads.g.doubleclick.net"
    )

    private val CORE_TRACKER_DOMAINS = setOf(
        "graph.facebook.com",
        "analytics.google.com",
        "app-measurement.com",
        "appsflyer.com",
        "adjust.com",
        "branch.io",
        "kochava.com",
        "segment.io",
        "amplitude.com",
        "mixpanel.com",
        "flurry.com",
        "telemetry.sdk"
    )

    private val CORE_CRYPTO_DOMAINS = setOf(
        "coinhive.com",
        "coin-hive.com",
        "crypto-loot.com",
        "jsecoin.com",
        "minr.pw",
        "monerominer.rocks",
        "webminepool.com"
    )

    private val CORE_SOCIAL_DISTRACTIONS = setOf(
        "tiktok.com",
        "musical.ly",
        "byteoversea.com",
        "snssdk.com",
        "reels.instagram.com"
    )

    val DEFAULT_GEO_TLDS = setOf(
        ".ru",
        ".cn",
        ".kp",
        ".top",
        ".xyz",
        ".click"
    )

    fun checkDomain(context: Context, domain: String): Pair<Boolean, String> {
        val prefs = PreferencesManager(context)
        val lowerDomain = domain.lowercase().trim()

        // 1. Whitelist override
        if (prefs.getCustomWhitelist().contains(lowerDomain)) {
            return Pair(false, "قائمة الاستثناء (Whitelist)")
        }

        // 2. Custom Blacklist / Spoof
        if (prefs.getCustomBlacklist().contains(lowerDomain)) {
            return Pair(true, "حظر مخصص (Custom Hosts)")
        }

        // 3. Geo-IP / TLD Blocker
        if (prefs.geoBlockingEnabled) {
            val geoTlds = prefs.getGeoBlockedTlds()
            for (tld in geoTlds) {
                if (lowerDomain.endsWith(tld)) {
                    return Pair(true, "الحظر الجغرافي ($tld)")
                }
            }
        }

        // 4. General Ads Filter
        if (prefs.filterGeneralAds) {
            for (ad in CORE_AD_DOMAINS) {
                if (lowerDomain == ad || lowerDomain.endsWith(".$ad")) {
                    return Pair(true, "فلتر الإعلانات (AdBlock Core)")
                }
            }
        }

        // 5. Anti-Tracker Filter
        if (prefs.filterTrackers) {
            for (tr in CORE_TRACKER_DOMAINS) {
                if (lowerDomain == tr || lowerDomain.endsWith(".$tr")) {
                    return Pair(true, "فلتر منع التتبع (Anti-Tracker)")
                }
            }
        }

        // 6. Cryptojacking Filter
        if (prefs.filterCryptomining) {
            for (crypto in CORE_CRYPTO_DOMAINS) {
                if (lowerDomain == crypto || lowerDomain.endsWith(".$crypto")) {
                    return Pair(true, "فلتر التعدين الخفي (Cryptojacking)")
                }
            }
        }

        // 7. Social Distractions Filter
        if (prefs.filterSocialDistractions) {
            for (soc in CORE_SOCIAL_DISTRACTIONS) {
                if (lowerDomain == soc || lowerDomain.endsWith(".$soc")) {
                    return Pair(true, "فلتر حظر السوشيال والريلز")
                }
            }
        }

        return Pair(false, "مسموح")
    }

    fun getAllKnownBlockedDomains(): List<String> {
        val list = mutableListOf<String>()
        list.addAll(CORE_AD_DOMAINS)
        list.addAll(CORE_TRACKER_DOMAINS)
        list.addAll(CORE_CRYPTO_DOMAINS)
        return list.sorted()
    }
}
