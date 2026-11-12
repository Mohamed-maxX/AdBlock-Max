package com.max.adblock

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DnsTrafficManager {

    private val logBuffer = mutableListOf<DnsLogEntry>()
    private val _liveLogs = MutableStateFlow<List<DnsLogEntry>>(emptyList())
    val liveLogs: StateFlow<List<DnsLogEntry>> = _liveLogs

    private val SAMPLE_DOMAINS = listOf(
        Pair("pagead2.googlesyndication.com", true),
        Pair("google.com", false),
        Pair("graph.facebook.com", true),
        Pair("github.com", false),
        Pair("adservice.google.com", true),
        Pair("cloudflare.com", false),
        Pair("securepubads.g.doubleclick.net", true),
        Pair("wikipedia.org", false),
        Pair("applovin.com", true),
        Pair("play.google.com", false),
        Pair("analytics.google.com", true),
        Pair("coinhive.com", true),
        Pair("android.com", false)
    )

    fun initialize(context: Context) {
        if (logBuffer.isEmpty()) {
            val now = System.currentTimeMillis()
            var offset = 120000L
            for ((domain, _) in SAMPLE_DOMAINS) {
                val (blocked, rule) = FilterRulesManager.checkDomain(context, domain)
                val entry = DnsLogEntry(
                    id = now - offset,
                    domain = domain,
                    timestamp = now - offset,
                    isBlocked = blocked,
                    ruleMatched = rule,
                    queryType = if ((0..1).random() == 0) "A" else "AAAA",
                    sourceApp = if (blocked) "متعقب إعلانات خلفي" else "متصفح / تطبيق"
                )
                logBuffer.add(0, entry)
                offset -= 9000L
            }
            _liveLogs.value = logBuffer.toList()
        }
    }

    fun recordQuery(context: Context, domain: String, customApp: String? = null) {
        val prefs = PreferencesManager(context)
        val (blocked, rule) = FilterRulesManager.checkDomain(context, domain)

        val entry = DnsLogEntry(
            domain = domain,
            timestamp = System.currentTimeMillis(),
            isBlocked = blocked,
            ruleMatched = rule,
            queryType = "A",
            sourceApp = customApp ?: if (blocked) "حزمة إعلانية تم اعتراضها" else "اتصال آمن"
        )

        synchronized(logBuffer) {
            logBuffer.add(0, entry)
            if (logBuffer.size > 150) {
                logBuffer.removeAt(logBuffer.size - 1)
            }
            _liveLogs.value = logBuffer.toList()
        }

        if (blocked) {
            prefs.blockedCountToday = prefs.blockedCountToday + 1
            prefs.blockedCountTotal = prefs.blockedCountTotal + 1

            // Night Watch: if between 12 AM and 6 AM
            val hour = SimpleDateFormat("HH", Locale.US).format(Date()).toIntOrNull() ?: 12
            if (hour in 0..6) {
                prefs.nightWatchCount = prefs.nightWatchCount + 1
            }
        }
    }

    fun generateRouterBlocklist(): String {
        val sb = StringBuilder()
        sb.append("# ====================================================\n")
        sb.append("# AdBlock Max - ملف فلاتر الراوتر المنزلي (Router Blocklist)\n")
        sb.append("# متوافق مع: TP-Link, Huawei, Keenetic, OpenWrt, Mikrotik\n")
        sb.append("# يحمي جميع الهواتف، الشاشات الذكية، والأجهزة المنزلية\n")
        sb.append("# ====================================================\n\n")

        val domains = FilterRulesManager.getAllKnownBlockedDomains()
        for (domain in domains) {
            sb.append("0.0.0.0 ").append(domain).append("\n")
        }

        sb.append("\n# طريقة التركيب في الراوتر:\n")
        sb.append("# 1. افتح صفحة إعدادات الراوتر (192.168.1.1).\n")
        sb.append("# 2. اذهب إلى Network -> DNS أو Hosts / Access Control.\n")
        sb.append("# 3. قم بلصق أو استيراد هذه القائمة واضغط Save.\n")

        return sb.toString()
    }
}
