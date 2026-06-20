package com.taroqos.firewall

/**
 * Layer 15 — Privacy Firewall (RethinkDNS-style)
 *
 * يمنع التطبيقات من الاتصال بنطاقات التتبع والإعلانات حتى لو تجاوزت DNS.
 * مستوحى من منطق RethinkDNS وNetGuard.
 *
 * يعمل بثلاثة أوضاع:
 *   1. MONITOR — مراقبة فقط (افتراضي)
 *   2. BLOCK_ADS — حجب الإعلانات والتتبع
 *   3. STRICT — حجب كل اتصال غير مصرّح به
 *
 * خصوصية الخوارزمية:
 *   - لا يُخزن محتوى الاتصالات
 *   - يُخزن فقط: النطاق، الوقت، القرار (محجوب/مسموح)
 *   - البيانات تُحذف عند إيقاف الخدمة
 */
object PrivacyFirewall {

    enum class FirewallMode { MONITOR, BLOCK_ADS, STRICT }
    enum class FirewallDecision { ALLOW, BLOCK, MONITOR }

    @Volatile var mode = FirewallMode.BLOCK_ADS
    @Volatile var blockedByFirewall = 0L
    @Volatile var allowedByFirewall = 0L
    @Volatile var monitoredCount = 0L

    // Apps allowed to access internet (all apps by default)
    private val allowedPackages = mutableSetOf<String>()
    // Apps blocked from internet
    private val blockedPackages = mutableSetOf<String>()

    // Recently seen connections (in-memory log, max 500 entries)
    private val connectionLog = ArrayDeque<ConnectionEntry>(500)
    private const val MAX_LOG = 500

    data class ConnectionEntry(
        val timestamp: Long,
        val domain: String,
        val packageName: String?,
        val decision: FirewallDecision,
        val reason: String
    )

    // Known data-harvesting domains that should always be blocked
    private val PRIVACY_VIOLATORS: Set<String> = setOf(
        // Device fingerprinting
        "device-metrics-us.amazon.com",
        "device-metrics-us-2.amazon.com",
        "fls-na.amazon.com",
        // Aggressive tracking
        "scorecardresearch.com",
        "quantserve.com",
        "bluekai.com",
        "exelate.com",
        "lotame.com",
        "krxd.net",
        "eyeota.net",
        "id5-sync.com",
        "pubcid.org",
        "intentiq.com",
        "zeotap.com",
        "liveramp.com",
        "adsrvr.org",
        "casalemedia.com",
        "rfihub.com",
        "rfihub.net",
        "agkn.com",
        "adnxs.com",
        "nexac.com",
        "demdex.net",  // Adobe Audience Manager
        "omtrdc.net",  // Adobe Analytics
        "2mdn.net",    // Google ad serving
        "fwmrm.net",   // FreeWheel
        "sizmek.com",  // Amazon DSP
        "brealtime.com",
        "lijit.com",
        "sonobi.com",
        "sharethrough.com",
        "advertising.com",
        "adtech.de",
        "tribalfusion.com",
        "undertone.com",
        "yieldmo.com",
        "kargo.com",
        "conversant.com",
        "valueclick.com",
        "mediaplex.com",
    )

    /**
     * تقييم قرار الجدار الناري لنطاق معين
     */
    fun evaluate(
        domain: String,
        packageName: String? = null,
        destPort: Int = 443
    ): FirewallDecision {
        val lower = domain.lowercase().trimEnd('.')
        var decision = FirewallDecision.ALLOW
        var reason = "allowed"

        when (mode) {
            FirewallMode.MONITOR -> {
                monitoredCount++
                decision = FirewallDecision.MONITOR
                reason = "monitor mode"
            }

            FirewallMode.BLOCK_ADS -> {
                // Check package-level rules
                if (packageName != null && packageName in blockedPackages) {
                    decision = FirewallDecision.BLOCK
                    reason = "blocked package"
                    blockedByFirewall++
                }
                // Check privacy violators
                else if (lower in PRIVACY_VIOLATORS || PRIVACY_VIOLATORS.any { lower.endsWith(".$it") }) {
                    decision = FirewallDecision.BLOCK
                    reason = "privacy violator"
                    blockedByFirewall++
                }
                // Check ad domains
                else if (com.taroqos.filter.AdBlockList.isBlocked(lower)) {
                    decision = FirewallDecision.BLOCK
                    reason = "ad domain"
                    blockedByFirewall++
                }
                else {
                    allowedByFirewall++
                }
            }

            FirewallMode.STRICT -> {
                // In strict mode, only explicitly allowed packages can connect
                if (packageName != null && packageName !in allowedPackages) {
                    decision = FirewallDecision.BLOCK
                    reason = "strict mode — not whitelisted"
                    blockedByFirewall++
                } else {
                    allowedByFirewall++
                }
            }
        }

        // Log connection
        logConnection(domain, packageName, decision, reason)
        return decision
    }

    fun allowPackage(packageName: String) {
        allowedPackages.add(packageName)
        blockedPackages.remove(packageName)
    }

    fun blockPackage(packageName: String) {
        blockedPackages.add(packageName)
        allowedPackages.remove(packageName)
    }

    fun getConnectionLog(): List<ConnectionEntry> = connectionLog.toList()

    fun getStats(): Map<String, Any> = mapOf(
        "mode" to mode.name,
        "blocked" to blockedByFirewall,
        "allowed" to allowedByFirewall,
        "monitored" to monitoredCount,
        "log_size" to connectionLog.size,
        "privacy_violators_count" to PRIVACY_VIOLATORS.size,
    )

    private fun logConnection(
        domain: String,
        packageName: String?,
        decision: FirewallDecision,
        reason: String
    ) {
        if (connectionLog.size >= MAX_LOG) connectionLog.removeFirst()
        connectionLog.addLast(
            ConnectionEntry(
                timestamp = System.currentTimeMillis(),
                domain = domain,
                packageName = packageName,
                decision = decision,
                reason = reason
            )
        )
    }

    fun reset() {
        blockedByFirewall = 0L
        allowedByFirewall = 0L
        monitoredCount = 0L
        connectionLog.clear()
    }
}
