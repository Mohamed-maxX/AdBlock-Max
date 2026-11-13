package com.max.adblock

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
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

    /**
     * Strict SSRF Protection: Checks if a host resolves to loopback, private, link-local, or multicast addresses.
     */
    fun isPrivateOrLocalHost(host: String): Boolean {
        val trimmed = host.trim().lowercase()
        if (trimmed == "localhost" ||
            trimmed.endsWith(".local") ||
            trimmed.endsWith(".internal") ||
            trimmed.endsWith(".lan") ||
            trimmed.endsWith(".home")
        ) {
            return true
        }

        return try {
            val addresses = InetAddress.getAllByName(trimmed)
            addresses.any { addr ->
                addr.isLoopbackAddress ||
                addr.isSiteLocalAddress ||
                addr.isLinkLocalAddress ||
                addr.isAnyLocalAddress ||
                addr.isMulticastAddress ||
                isPrivateIpBytes(addr.address)
            }
        } catch (e: Exception) {
            // If DNS resolution fails, reject to be safe
            true
        }
    }

    private fun isPrivateIpBytes(bytes: ByteArray): Boolean {
        if (bytes.size == 4) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            // 10.0.0.0/8
            if (b0 == 10) return true
            // 127.0.0.0/8
            if (b0 == 127) return true
            // 172.16.0.0/12
            if (b0 == 172 && (b1 in 16..31)) return true
            // 192.168.0.0/16
            if (b0 == 192 && b1 == 168) return true
            // 169.254.0.0/16 (Link Local)
            if (b0 == 169 && b1 == 254) return true
            // 0.0.0.0/8
            if (b0 == 0) return true
        } else if (bytes.size == 16) {
            // IPv6 ::1
            val isIpv6Loopback = bytes.take(15).all { it == 0.toByte() } && bytes[15] == 1.toByte()
            if (isIpv6Loopback) return true
            // IPv6 Unique Local fc00::/7
            val firstByte = bytes[0].toInt() and 0xFF
            if ((firstByte and 0xFE) == 0xFC) return true
            // IPv6 Link-Local fe80::/10
            if (firstByte == 0xFE && (bytes[1].toInt() and 0xC0) == 0x80) return true
        }
        return false
    }

    suspend fun resolveRedirects(initialUrl: String): Pair<String, Int> = withContext(Dispatchers.IO) {
        var currentUrl = initialUrl.trim()
        if (!currentUrl.startsWith("http://", ignoreCase = true) && !currentUrl.startsWith("https://", ignoreCase = true)) {
            currentUrl = "https://$currentUrl"
        }

        var hops = 0
        val maxHops = 5

        try {
            while (hops < maxHops) {
                val urlObj = URL(currentUrl)

                // Only allow HTTP/HTTPS
                val scheme = urlObj.protocol.lowercase()
                if (scheme != "http" && scheme != "https") {
                    return@withContext Pair("تم حظر الرابط: بروتوكول غير آمن ($scheme)", hops)
                }

                // SSRF check for target host
                val host = urlObj.host
                if (host.isNullOrBlank() || isPrivateOrLocalHost(host)) {
                    return@withContext Pair("تم حظر الرابط: عنوان شبكة داخلية محلي (SSRF Protected)", hops)
                }

                val conn = urlObj.openConnection() as HttpURLConnection
                try {
                    conn.instanceFollowRedirects = false
                    conn.requestMethod = "HEAD"
                    conn.connectTimeout = 4000
                    conn.readTimeout = 4000
                    conn.setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
                    )
                    conn.connect()

                    val code = conn.responseCode
                    if (code in 300..399) {
                        val location = conn.getHeaderField("Location")
                        if (location.isNullOrBlank()) break

                        currentUrl = if (location.startsWith("http://", ignoreCase = true) || location.startsWith("https://", ignoreCase = true)) {
                            location
                        } else {
                            URL(urlObj, location).toString()
                        }
                        hops++
                    } else {
                        break
                    }
                } finally {
                    conn.disconnect()
                }
            }
        } catch (e: Exception) {
            // Silently handle resolution error, keep last safe URL
        }

        Pair(currentUrl, hops)
    }
}
