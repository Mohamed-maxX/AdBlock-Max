package com.max.adblock

data class DnsLogEntry(
    val id: Long = System.currentTimeMillis() + (0..1000).random(),
    val domain: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isBlocked: Boolean,
    val ruleMatched: String,
    val queryType: String = "A",
    val sourceApp: String = "نظام الأندرويد"
)
