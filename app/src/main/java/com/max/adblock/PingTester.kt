package com.max.adblock

import android.net.VpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

object PingTester {

    @Volatile
    var vpnServiceInstance: VpnService? = null

    suspend fun measurePingMs(
        hostIp: String,
        port: Int = 53,
        timeoutMs: Int = 1500,
        vpnService: VpnService? = null
    ): Long = withContext(Dispatchers.IO) {
        try {
            val startTime = System.currentTimeMillis()
            Socket().use { socket ->
                val vpn = vpnService ?: vpnServiceInstance
                try {
                    vpn?.protect(socket)
                } catch (e: Exception) {
                    // protect can throw if VPN is transitioning
                }
                socket.connect(InetSocketAddress(hostIp, port), timeoutMs)
            }
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed > 0) elapsed else 1L
        } catch (e: Exception) {
            // Secondary fallback via port 443 if port 53 was dropped or filtered
            if (port == 53) {
                try {
                    val startTime = System.currentTimeMillis()
                    Socket().use { socket ->
                        val vpn = vpnService ?: vpnServiceInstance
                        try {
                            vpn?.protect(socket)
                        } catch (ex: Exception) {}
                        socket.connect(InetSocketAddress(hostIp, 443), timeoutMs)
                    }
                    val elapsed = System.currentTimeMillis() - startTime
                    if (elapsed > 0) elapsed else 1L
                } catch (ex2: Exception) {
                    -1L
                }
            } else {
                -1L
            }
        }
    }
}
