package com.taroqos.filter

import com.taroqos.ai.AiAdClassifier

/**
 * Layer 4 — JSON & HTTP Ad Detection
 *
 * يفحص محتوى HTTP (غير المشفر) ويكتشف الحمولات الإعلانية
 * عبر تحليل:
 *  1. ترويسات HTTP (Host, Referer, User-Agent)
 *  2. مسار URL وبارامتراته
 *  3. محتوى JSON في الطلبات والردود
 *
 * ملاحظة: يعمل على حركة HTTP (منفذ 80) فقط.
 * حركة HTTPS مغطاة بـ Layer 3 (TLS SNI).
 */
object HttpAdDetector {

    @Volatile var blockedByHttp = 0L

    // ترويسات HTTP المميزة للإعلانات
    private val AD_HEADERS = setOf(
        "x-ad-unit", "x-ad-type", "x-ad-format",
        "x-advertiser-id", "x-campaign-id", "x-impression-id",
        "x-click-track", "x-beacon", "x-openrtb-version",
        "x-prebid", "x-bid-price",
    )

    // مسارات URL إعلانية شائعة
    private val AD_PATH_PATTERNS = listOf(
        Regex("(?i)/(?:ads?|advert(?:isement)?)/"),
        Regex("(?i)/(?:banner|interstitial|popup)/"),
        Regex("(?i)/(?:sponsored|promoted)/"),
        Regex("(?i)/(?:tracking|tracker|beacon|pixel)/"),
        Regex("(?i)/(?:impression|click|conversion)/"),
        Regex("(?i)/openrtb/"),
        Regex("(?i)/prebid/"),
        Regex("(?i)/bid(?:der|ding)?/"),
    )

    // بارامترات URL إعلانية
    private val AD_QUERY_PARAMS = setOf(
        "adid", "ad_id", "adtype", "ad_type",
        "adunit", "ad_unit", "adpos", "ad_pos",
        "adformat", "ad_format", "bid", "cpm", "ecpm",
        "campaign", "cmpid", "impression", "impr",
        "clickid", "click_id", "convid", "conv_id",
        "afid", "aff_id", "affiliate",
    )

    /**
     * يحلل حزمة HTTP ويحدد هل هي إعلانية
     * @param payload محتوى TCP payload
     * @return true إذا كانت الحزمة تحمل محتوى إعلاني
     */
    fun isHttpAdTraffic(payload: ByteArray): Boolean {
        val text = try {
            String(payload, Charsets.ISO_8859_1)
        } catch (_: Exception) { return false }

        if (!text.startsWith("GET ") &&
            !text.startsWith("POST ") &&
            !text.startsWith("HTTP/")
        ) return false

        val lines = text.lines()
        if (lines.isEmpty()) return false

        // فحص مسار URL
        val firstLine = lines[0]
        if (hasAdPath(firstLine)) {
            blockedByHttp++
            return true
        }

        // فحص ترويسات HTTP
        if (hasAdHeaders(lines)) {
            blockedByHttp++
            return true
        }

        // فحص محتوى JSON
        val bodyStart = text.indexOf("\r\n\r\n")
        if (bodyStart != -1) {
            val body = text.substring(bodyStart + 4)
            if (body.trimStart().startsWith("{") || body.trimStart().startsWith("[")) {
                if (AiAdClassifier.containsAdJson(body)) {
                    blockedByHttp++
                    return true
                }
            }
        }

        return false
    }

    private fun hasAdPath(requestLine: String): Boolean {
        // "GET /path?query HTTP/1.1"
        val parts = requestLine.split(" ")
        if (parts.size < 2) return false
        val pathAndQuery = parts[1]

        // فحص المسار
        for (pattern in AD_PATH_PATTERNS) {
            if (pattern.containsMatchIn(pathAndQuery)) return true
        }

        // فحص بارامترات Query
        val queryIdx = pathAndQuery.indexOf('?')
        if (queryIdx != -1) {
            val query = pathAndQuery.substring(queryIdx + 1).lowercase()
            for (param in AD_QUERY_PARAMS) {
                if (query.contains("$param=")) return true
            }
        }

        return false
    }

    private fun hasAdHeaders(lines: List<String>): Boolean {
        for (line in lines.drop(1)) {
            if (line.isEmpty()) break
            val lower = line.lowercase()
            for (header in AD_HEADERS) {
                if (lower.startsWith("$header:")) return true
            }
        }
        return false
    }
}
