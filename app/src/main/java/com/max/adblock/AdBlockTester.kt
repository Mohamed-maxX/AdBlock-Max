package com.max.adblock

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.UnknownHostException

data class AdBlockTestResult(
    val isBlocked: Boolean,
    val domain: String,
    val resolvedIp: String
)

object AdBlockTester {
    private val TEST_DOMAINS = listOf(
        "pagead2.googlesyndication.com",
        "adservice.google.com",
        "securepubads.g.doubleclick.net"
    )

    suspend fun runTest(): AdBlockTestResult = withContext(Dispatchers.IO) {
        for (domain in TEST_DOMAINS) {
            try {
                val addresses = InetAddress.getAllByName(domain)
                val isZeroIp = addresses.any {
                    val hostAddress = it.hostAddress ?: ""
                    hostAddress == "0.0.0.0" || hostAddress == "127.0.0.1" || hostAddress == "::" || hostAddress == "::1"
                }

                if (isZeroIp) {
                    return@withContext AdBlockTestResult(
                        isBlocked = true,
                        domain = domain,
                        resolvedIp = "0.0.0.0 (Sinkholed)"
                    )
                } else {
                    // Domain resolved to actual real IP -> Not blocked!
                    val firstIp = addresses.firstOrNull()?.hostAddress ?: "Unknown"
                    return@withContext AdBlockTestResult(
                        isBlocked = false,
                        domain = domain,
                        resolvedIp = firstIp
                    )
                }
            } catch (e: UnknownHostException) {
                // Domain rejected / NXDOMAIN by DNS filter -> Blocked!
                return@withContext AdBlockTestResult(
                    isBlocked = true,
                    domain = domain,
                    resolvedIp = "NXDOMAIN (Blocked)"
                )
            } catch (e: Exception) {
                return@withContext AdBlockTestResult(
                    isBlocked = true,
                    domain = domain,
                    resolvedIp = "Filtered (${e.javaClass.simpleName})"
                )
            }
        }

        AdBlockTestResult(
            isBlocked = true,
            domain = TEST_DOMAINS.first(),
            resolvedIp = "Blocked"
        )
    }
}
