package com.max.adblock

enum class DnsMode(
    val titleResId: Int,
    val primaryIpv4: String,
    val secondaryIpv4: String,
    val primaryIpv6: String,
    val secondaryIpv6: String,
    val dotHostname: String
) {
    STANDARD(
        titleResId = R.string.mode_standard_title,
        primaryIpv4 = "94.140.14.14",
        secondaryIpv4 = "94.140.15.15",
        primaryIpv6 = "2a10:50c0::ad1:ff",
        secondaryIpv6 = "2a10:50c0::ad2:ff",
        dotHostname = "dns.adguard-dns.com"
    ),
    SECURITY(
        titleResId = R.string.mode_security_title,
        primaryIpv4 = "9.9.9.9",
        secondaryIpv4 = "149.112.112.112",
        primaryIpv6 = "2620:fe::fe",
        secondaryIpv6 = "2620:fe::9",
        dotHostname = "dns.quad9.net"
    ),
    FAMILY(
        titleResId = R.string.mode_family_title,
        primaryIpv4 = "94.140.14.15",
        secondaryIpv4 = "94.140.15.16",
        primaryIpv6 = "2a10:50c0::bad1:ff",
        secondaryIpv6 = "2a10:50c0::bad2:ff",
        dotHostname = "family.adguard-dns.com"
    ),
    GAMING(
        titleResId = R.string.mode_gaming_title,
        primaryIpv4 = "1.1.1.1",
        secondaryIpv4 = "1.0.0.1",
        primaryIpv6 = "2606:4700:4700::1111",
        secondaryIpv6 = "2606:4700:4700::1001",
        dotHostname = "1dot1dot1dot1.cloudflare-dns.com"
    ),
    CUSTOM(
        titleResId = R.string.mode_custom_title,
        primaryIpv4 = "94.140.14.14",
        secondaryIpv4 = "94.140.15.15",
        primaryIpv6 = "2a10:50c0::ad1:ff",
        secondaryIpv6 = "2a10:50c0::ad2:ff",
        dotHostname = "custom.dns.local"
    )
}
