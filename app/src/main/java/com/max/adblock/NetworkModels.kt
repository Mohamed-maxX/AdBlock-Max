package com.max.adblock

data class WifiDevice(
    val ip: String,
    val name: String,
    val isGateway: Boolean = false,
    val isThisDevice: Boolean = false,
    val pingMs: Long = 0L
)

data class PortScanResult(
    val port: Int,
    val serviceName: String,
    val isOpen: Boolean,
    val description: String
)

data class GamingPingResult(
    val regionName: String,
    val hostIp: String,
    val pingMs: Long,
    val status: String
)
