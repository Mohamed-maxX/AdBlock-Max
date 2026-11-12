package com.max.adblock

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

data class CleanLinkResult(
    val originalUrl: String,
    val cleanUrl: String,
    val removedParams: List<String>
)

object LinkCleaner {

    private val TRACKING_PARAMS = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "fbclid", "gclid", "igshid", "mc_eid", "ref", "si", "spm", "_hsenc",
        "_hsmi", "mkt_tok", "yclid", "wickedid", "twclid", "zanpid", "msclkid",
        "aff_id", "sc_channel", "trk", "trk_campaign", "vero_id", "vero_conv",
        "ncid", "nr_email_referer", "cmpid", "action_object_map", "action_type_map",
        "action_ref_map", "gs_l", "cvid", "ei"
    )

    fun cleanUrl(rawUrl: String): CleanLinkResult {
        try {
            val trimmed = rawUrl.trim()
            val uri = URI(trimmed)
            val query = uri.rawQuery ?: return CleanLinkResult(rawUrl, rawUrl, emptyList())

            val removed = mutableListOf<String>()
            val kept = mutableListOf<String>()

            query.split("&").forEach { pair ->
                val key = pair.substringBefore("=").lowercase()
                if (TRACKING_PARAMS.contains(key) || key.startsWith("utm_")) {
                    removed.add(key)
                } else {
                    kept.add(pair)
                }
            }

            val newQuery = if (kept.isNotEmpty()) kept.joinToString("&") else null
            val cleanUri = URI(
                uri.scheme,
                uri.authority,
                uri.path,
                newQuery,
                uri.fragment
            )

            return CleanLinkResult(
                originalUrl = rawUrl,
                cleanUrl = cleanUri.toASCIIString(),
                removedParams = removed
            )
        } catch (e: Exception) {
            return CleanLinkResult(rawUrl, rawUrl, emptyList())
        }
    }

    suspend fun resolveRedirects(initialUrl: String): Pair<String, Int> = withContext(Dispatchers.IO) {
        var currentUrl = initialUrl.trim()
        if (!currentUrl.startsWith("http://") && !currentUrl.startsWith("https://")) {
            currentUrl = "https://$currentUrl"
        }

        var hops = 0
        val maxHops = 8

        try {
            while (hops < maxHops) {
                val urlObj = URL(currentUrl)
                val conn = urlObj.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.requestMethod = "HEAD"
                conn.connectTimeout = 3500
                conn.readTimeout = 3500
                conn.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
                )
                conn.connect()

                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (location.isNullOrBlank()) break

                    currentUrl = if (location.startsWith("http://") || location.startsWith("https://")) {
                        location
                    } else {
                        URL(urlObj, location).toString()
                    }
                    hops++
                } else {
                    conn.disconnect()
                    break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        Pair(currentUrl, hops)
    }
}
