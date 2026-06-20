package com.taroqos.filter

import java.net.InetAddress

/**
 * Layer 2 — DoH / DoT Protection
 *
 * يمنع التطبيقات من تجاوز DNS الجهاز عبر:
 *   - DNS over HTTPS (DoH) على منفذ 443 لخوادم معروفة
 *   - DNS over TLS (DoT) على منفذ 853
 *
 * خوادم DoH المعروفة التي تتجاوز فلتر DNS:
 *   - 8.8.8.8, 8.8.4.4 (Google)
 *   - 1.1.1.1, 1.0.0.1 (Cloudflare)
 *   - 9.9.9.9 (Quad9)
 *   - 208.67.222.222 (OpenDNS)
 */
object DohProtection {

    @Volatile var blockedByDoh = 0L
    @Volatile var inspectedCount = 0L

    // Known DoH/DoT server IPs
    private val DOH_SERVER_IPS: Set<String> = setOf(
        // Google Public DNS
        "8.8.8.8", "8.8.4.4",
        // Cloudflare
        "1.1.1.1", "1.0.0.1", "2606:4700:4700::1111", "2606:4700:4700::1001",
        // Quad9
        "9.9.9.9", "149.112.112.112",
        // OpenDNS
        "208.67.222.222", "208.67.220.220",
        // AdGuard DNS
        "94.140.14.14", "94.140.15.15",
        // NextDNS
        "45.90.28.0", "45.90.30.0",
        // Comodo Secure DNS
        "8.26.56.26", "8.20.247.20",
    )

    // DoH domains that apps use directly (bypassing system DNS)
    private val DOH_DOMAINS: Set<String> = setOf(
        "dns.google",
        "dns.google.com",
        "8888.google",
        "cloudflare-dns.com",
        "1dot1dot1dot1.cloudflare-dns.com",
        "mozilla.cloudflare-dns.com",
        "dns.quad9.net",
        "dns10.quad9.net",
        "dns.adguard.com",
        "dns-family.adguard.com",
        "doh.opendns.com",
        "doh.familyshield.opendns.com",
        "dns.nextdns.io",
        "dns-unfiltered.adguard.com",
        "doh.cleanbrowsing.org",
        "doh.tiar.app",
        "security.cloudflare-dns.com",
        "family.cloudflare-dns.com",
    )

    // Port 853 = DoT, Port 443 to DoH servers = DoH
    private const val DOT_PORT = 853
    private const val HTTPS_PORT = 443

    /**
     * فحص حزمة للتحقق من كونها طلب DoH/DoT مشبوه
     * يُستخدم في حلقة VPN لاعتراض الحزم قبل إرسالها
     */
    fun isDoHorDoTPacket(destIp: ByteArray, destPort: Int): Boolean {
        inspectedCount++

        // Check DoT port (853)
        if (destPort == DOT_PORT) {
            blockedByDoh++
            return true
        }

        // Check if destination is a known DoH server on HTTPS port
        if (destPort == HTTPS_PORT) {
            val ipStr = destIp.joinToString(".") { (it.toInt() and 0xFF).toString() }
            if (ipStr in DOH_SERVER_IPS) {
                blockedByDoh++
                return true
            }
        }

        return false
    }

    /**
     * فحص اسم نطاق من DNS لمعرفة إن كان خادم DoH
     */
    fun isDoHDomain(domain: String): Boolean {
        val lower = domain.lowercase().trimEnd('.')
        if (lower in DOH_DOMAINS) {
            blockedByDoh++
            return true
        }
        // Check subdomains
        return DOH_DOMAINS.any { lower.endsWith(".$it") }
    }

    fun reset() {
        blockedByDoh = 0L
        inspectedCount = 0L
    }
}
