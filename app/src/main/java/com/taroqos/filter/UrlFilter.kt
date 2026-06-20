package com.taroqos.filter

/**
 * Layer 4 — URL Filter
 *
 * يطبّق قواعد تصفية مستوحاة من EasyList/OISD على:
 *   - مسارات URL (path patterns)
 *   - معاملات الاستعلام (query parameters)
 *   - أنماط النطاقات الفرعية
 *   - ترويسات HTTP
 *
 * يعمل على الحزم النصية التي يعترضها VPN على منفذ 80.
 */
object UrlFilter {

    @Volatile var blockedByUrl = 0L

    // ─── EasyList-style path rules ───────────────────────────────────────────
    private val AD_PATH_SEGMENTS: Set<String> = setOf(
        "/ads/", "/ad/", "/advert/", "/advertisement/", "/advertising/",
        "/banner/", "/banners/", "/popup/", "/popunder/", "/popups/",
        "/sponsored/", "/sponsor/", "/promoted/", "/promotion/",
        "/tracking/", "/tracker/", "/track/", "/pixel/", "/beacon/",
        "/impression/", "/click/", "/conversion/", "/clickthrough/",
        "/openrtb/", "/prebid/", "/bidder/", "/bidding/", "/rtb/",
        "/adserver/", "/adserving/", "/adtech/", "/adnetwork/",
        "/interstitial/", "/overlay/", "/rewarded/", "/native_ad/",
        "/adsense/", "/dfp/", "/gpt/", "/tagging/",
        "/collect/", "/event/", "/analytics/", "/stats/",
        "/telemetry/", "/ping/", "/heartbeat/",
    )

    // ─── Query parameters that indicate ad requests ───────────────────────────
    private val AD_QUERY_PARAMS: Set<String> = setOf(
        "adid", "ad_id", "ad_unit", "adunit", "ad_pos", "adpos",
        "ad_type", "adtype", "ad_format", "adformat", "ad_size", "adsize",
        "bid", "cpm", "ecpm", "floor_price", "price_floor",
        "campaign", "campaign_id", "campaignid", "cmpgn", "cmpid",
        "impression", "impression_id", "impressionid", "impid",
        "click_id", "clickid", "clkid", "clickurl", "click_url",
        "conversion", "conversion_id", "convid",
        "affiliate", "aff_id", "affid", "affiliate_id",
        "advertiser", "advertiser_id", "advertiserid",
        "creative", "creative_id", "creativeid",
        "placement", "placement_id", "placementid",
        "referer", "utm_source", "utm_medium", "utm_campaign", "utm_term",
        "fbclid", "gclid", "msclkid", "ttclid", "li_fat_id",
    )

    // ─── EasyList regex-style rules ───────────────────────────────────────────
    private val AD_URL_PATTERNS: List<Regex> = listOf(
        Regex("(?i)/ads?[/_-]\\d"),
        Regex("(?i)/\\d+x\\d+[._-]"),
        Regex("(?i)\\.(ad|ads|adv)\\.(js|json|gif|png|jpg)"),
        Regex("(?i)/pagead/"),
        Regex("(?i)/getad\\b"),
        Regex("(?i)/showad\\b"),
        Regex("(?i)/ad\\.php"),
        Regex("(?i)/adframe\\."),
        Regex("(?i)/adiframe\\."),
        Regex("(?i)/adchoices"),
        Regex("(?i)/adsystem"),
        Regex("(?i)/admanager"),
        Regex("(?i)/doubleclick"),
        Regex("(?i)/googlesyndication"),
        Regex("(?i)/googletagmanager"),
        Regex("(?i)/googletagservices"),
        Regex("(?i)/googleadservices"),
        Regex("(?i)/fbadnw"),
        Regex("(?i)/openx\\."),
        Regex("(?i)/pubmatic\\."),
        Regex("(?i)/criteo\\."),
        Regex("(?i)/outbrain\\."),
        Regex("(?i)/taboola\\."),
        Regex("(?i)/bid\\."),
        Regex("(?i)/rtb/"),
        Regex("(?i)/prebid/"),
        Regex("(?i)/header-bidding"),
        Regex("(?i)/vast\\."),
        Regex("(?i)/vpaid\\."),
        Regex("(?i)/ima3\\."),
        Regex("(?i)/adsinline"),
        Regex("(?i)\\btrack(ing)?[=/_]"),
        Regex("(?i)\\bbeacon[=/_]"),
        Regex("(?i)\\bpixel[=/_]"),
    )

    // ─── OISD-style host patterns (subdomains indicating ad infra) ────────────
    private val AD_SUBDOMAIN_PREFIXES: Set<String> = setOf(
        "ads", "ad", "advert", "advertising", "adsrv", "adserver",
        "adservice", "adserving", "adtrack", "adlog", "adtech",
        "adnetwork", "banner", "banners", "pixel", "track", "tracking",
        "tracker", "click", "clicks", "stat", "stats", "analytics",
        "metric", "metrics", "beacon", "collect", "telemetry",
        "impression", "rtb", "prebid", "bid", "bidder",
    )

    /**
     * فحص URL كامل (مسار + query) وتحديد إن كان إعلاناً
     */
    fun isAdUrl(url: String): Boolean {
        val lower = url.lowercase()

        // 1. Path segments
        for (segment in AD_PATH_SEGMENTS) {
            if (lower.contains(segment)) {
                blockedByUrl++
                return true
            }
        }

        // 2. URL patterns (regex)
        for (pattern in AD_URL_PATTERNS) {
            if (pattern.containsMatchIn(lower)) {
                blockedByUrl++
                return true
            }
        }

        // 3. Query parameters
        val queryIdx = lower.indexOf('?')
        if (queryIdx != -1) {
            val query = lower.substring(queryIdx + 1)
            for (param in AD_QUERY_PARAMS) {
                if (query.contains("$param=") || query.contains("&$param=") || query.startsWith("$param=")) {
                    blockedByUrl++
                    return true
                }
            }
        }

        return false
    }

    /**
     * فحص subdomain لمعرفة إن كان infrastructure إعلانية
     */
    fun isAdSubdomain(host: String): Boolean {
        val parts = host.lowercase().split(".")
        if (parts.isEmpty()) return false
        val sub = parts.first()
        return sub in AD_SUBDOMAIN_PREFIXES
    }

    /**
     * فحص Request-Line من HTTP وإرجاع قرار الحجب
     */
    fun isAdHttpRequest(requestLine: String): Boolean {
        val parts = requestLine.trim().split(" ")
        if (parts.size < 2) return false
        return isAdUrl(parts[1])
    }

    fun reset() {
        blockedByUrl = 0L
    }
}
