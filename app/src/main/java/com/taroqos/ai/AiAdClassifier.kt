package com.taroqos.ai

/**
 * Layer 6 — AI Ad Classification
 *
 * مصنّف ذكي للإعلانات يعمل على الجهاز بالكامل (on-device).
 * يستخدم نموذج تسجيل نقاط مرجح (Weighted Scoring Model)
 * مبني على تحليل آلاف النطاقات الإعلانية.
 *
 * النموذج:
 *  - تحليل نمط النطاق (Domain Pattern Analysis)
 *  - تسجيل الكلمات المفتاحية (Keyword Scoring)
 *  - تحليل بنية URL (URL Structure Analysis)
 *  - تحليل أنماط المحتوى (Content Pattern Analysis)
 *
 * العتبة للتصنيف كإعلان: score >= 0.65
 */
object AiAdClassifier {

    data class ClassificationResult(
        val isAd: Boolean,
        val confidence: Float,          // 0.0 - 1.0
        val reasons: List<String>
    )

    // كلمات مفتاحية إعلانية مع وزنها النسبي
    private val AD_KEYWORDS = mapOf(
        // وزن عالي جداً (0.9)
        "doubleclick"   to 0.95f,
        "googlesyndication" to 0.95f,
        "googletag"     to 0.90f,
        "adservice"     to 0.95f,
        "adserver"      to 0.95f,
        "adnetwork"     to 0.90f,
        "adsystem"      to 0.90f,
        "adnxs"         to 0.95f,
        "moatads"       to 0.90f,
        "criteo"        to 0.90f,
        "taboola"       to 0.90f,
        "outbrain"      to 0.90f,
        // وزن عالي (0.8)
        "pagead"        to 0.85f,
        "prebid"        to 0.80f,
        "pubmatic"      to 0.80f,
        "openrtb"       to 0.85f,
        "headerb"       to 0.75f,
        "bidder"        to 0.75f,
        "bidding"       to 0.75f,
        "rtb"           to 0.70f,
        "impression"    to 0.70f,
        "clicktrack"    to 0.80f,
        "clickserv"     to 0.80f,
        "convtrack"     to 0.75f,
        // وزن متوسط (0.6)
        "tracking"      to 0.65f,
        "tracker"       to 0.65f,
        "analytics"     to 0.55f,
        "telemetry"     to 0.60f,
        "pixel"         to 0.60f,
        "beacon"        to 0.65f,
        "collect"       to 0.50f,
        "metrics"       to 0.50f,
        "measurement"   to 0.55f,
        // وزن خفيف (0.4)
        "promo"         to 0.45f,
        "sponsor"       to 0.50f,
        "affiliate"     to 0.55f,
        "campaign"      to 0.45f,
        "offer"         to 0.35f,
        "recommend"     to 0.30f,
    )

    // أنماط بنيوية قوية للنطاقات الإعلانية
    private val STRUCTURAL_PATTERNS = listOf(
        Regex("^ads?[0-9]*\\.") to 0.85f,
        Regex("^ad\\.") to 0.85f,
        Regex("\\.ads?\\.") to 0.75f,
        Regex("-ads?\\.(com|net|io|co)") to 0.80f,
        Regex("^static\\.ads?") to 0.80f,
        Regex("cdn[0-9]*\\.ads?") to 0.75f,
        Regex("^img[0-9]+\\.ads?") to 0.75f,
        Regex("^pixel\\.") to 0.65f,
        Regex("^track(er)?[0-9]*\\.") to 0.70f,
        Regex("^click\\.") to 0.65f,
        Regex("^imp[0-9]*\\.") to 0.60f,
        Regex("^b\\.") to 0.35f,   // e.g. b.scorecardresearch.com
        Regex("[0-9a-f]{8,}\\.") to 0.40f,   // Random hex subdomain pattern
    )

    // خصائص URL تدل على إعلان
    private val URL_AD_PATTERNS = listOf(
        Regex("(?i)[?&](?:ad_type|adtype|ad_unit|adunit|ad_id|adid|ad_pos)="),
        Regex("(?i)[?&](?:campaign|cmpgn|cmpid)="),
        Regex("(?i)[?&](?:impression|impr|imp_id)="),
        Regex("(?i)[?&](?:click_id|clkid|clickid)="),
        Regex("(?i)[?&](?:affiliate|aff_id|affid)="),
        Regex("(?i)/(?:ads?|advert(?:isement)?)/"),
        Regex("(?i)/(?:banner|popup|interstitial)/"),
        Regex("(?i)/(?:sponsored|promoted)/"),
        Regex("(?i)/(?:tracking|tracker|pixel|beacon)/"),
        Regex("(?i)\\.(?:ad|ads|adv)\\."),
    )

    // أنماط المحتوى الإعلاني في JSON
    val JSON_AD_KEYS = setOf(
        "ad_id", "adid", "ad_unit_id", "adunitid",
        "ad_type", "adtype", "ad_format", "adformat",
        "ad_network", "adnetwork", "advertiser_id",
        "campaign_id", "campaignid", "impression_id",
        "click_url", "clickurl", "tracking_url",
        "beacon_url", "pixel_url", "conversion_url",
        "sponsored", "is_sponsored", "is_ad", "is_promoted",
        "promoted_content", "native_ad", "display_ad",
        "rtb_bid", "bid_price", "ecpm", "cpm",
    )

    /**
     * يصنّف النطاق هل هو إعلاني أم لا
     * @param domain النطاق المراد فحصه
     * @param url الرابط الكامل (اختياري، يزيد الدقة)
     * @return نتيجة التصنيف مع درجة الثقة والأسباب
     */
    fun classify(domain: String, url: String = ""): ClassificationResult {
        val lower = domain.lowercase().trimEnd('.')
        val urlLower = url.lowercase()
        val reasons = mutableListOf<String>()
        var score = 0f
        var hits = 0

        // 1. فحص الكلمات المفتاحية في النطاق
        for ((keyword, weight) in AD_KEYWORDS) {
            if (lower.contains(keyword)) {
                score += weight
                hits++
                reasons.add("كلمة مفتاحية: '$keyword' (وزن=$weight)")
                if (hits >= 3) break
            }
        }

        // 2. فحص الأنماط البنيوية
        for ((pattern, weight) in STRUCTURAL_PATTERNS) {
            if (pattern.containsMatchIn(lower)) {
                score += weight
                reasons.add("نمط بنيوي: ${pattern.pattern}")
                break
            }
        }

        // 3. فحص بنية URL
        if (urlLower.isNotEmpty()) {
            for (pattern in URL_AD_PATTERNS) {
                if (pattern.containsMatchIn(urlLower)) {
                    score += 0.55f
                    reasons.add("نمط URL إعلاني: ${pattern.pattern}")
                    break
                }
            }
        }

        // 4. تحليل بنية النطاق (subdomains)
        val parts = lower.split(".")
        if (parts.size >= 3) {
            val sub = parts.first()
            if (sub.matches(Regex("[a-z]{1,3}[0-9]{1,4}"))) {
                // e.g. s3.ads.com, a12.tracking.net
                score += 0.20f
                reasons.add("نمط subomain عشوائي")
            }
        }

        // تطبيع النتيجة إلى [0, 1]
        val normalized = (score / 2.5f).coerceIn(0f, 1f)
        val isAd = normalized >= 0.65f

        return ClassificationResult(
            isAd = isAd,
            confidence = normalized,
            reasons = reasons
        )
    }

    /**
     * يفحص محتوى JSON للكشف عن بيانات إعلانية
     * @param jsonContent محتوى JSON كنص
     * @return هل يحتوي على بيانات إعلانية
     */
    fun containsAdJson(jsonContent: String): Boolean {
        val lower = jsonContent.lowercase()
        var matchCount = 0
        for (key in JSON_AD_KEYS) {
            if (lower.contains("\"$key\"") || lower.contains("'$key'")) {
                matchCount++
                if (matchCount >= 2) return true
            }
        }
        return false
    }
}
