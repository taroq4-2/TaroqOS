package com.taroqos.filter

/**
 * Layer 5 — SSL Pinning Detection
 *
 * يكتشف التطبيقات التي تستخدم SSL Pinning لمنع فحص الحزم.
 * يُحلل أنماط TLS للتعرف على:
 *   - TLS fingerprinting (JA3/JA4)
 *   - Certificate pinning indicators
 *   - HPKP / Certificate Transparency bypass attempts
 *
 * ملاحظة: هذه الطبقة تعمل في وضع المراقبة فقط — لا تحجب
 * الاتصالات الشرعية، بل تسجّل التطبيقات التي تتجاوز الفحص.
 */
object SslPinningDetector {

    @Volatile var detectedPinningCount = 0L
    @Volatile var inspectedTlsCount = 0L

    // Apps known to use SSL pinning aggressively
    private val PINNING_APP_PACKAGES: Set<String> = setOf(
        "com.instagram.android",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.snapchat.android",
        "com.zhiliaoapp.musically",   // TikTok
        "com.ss.android.ugc.trill",
        "com.twitter.android",
        "com.x.android",
        "com.linkedin.android",
        "com.pinterest",
        "com.netflix.mediaclient",
        "com.spotify.music",
        "com.whatsapp",
        "org.telegram.messenger",
        "com.discord",
    )

    // TLS extensions that indicate aggressive cert pinning
    // Extension 0x0012 = signed_certificate_timestamp (CT)
    // Extension 0x0015 = padding
    // Extension 0xFF01 = renegotiation_info
    private val PINNING_TLS_EXTENSIONS: Set<Int> = setOf(
        0x0012, // signed_certificate_timestamp
        0x0011, // status_request (OCSP stapling)
    )

    // Cipher suites common in pinning-heavy apps (GREASE + strong suites)
    private val GREASE_VALUES: Set<Int> = setOf(
        0x0A0A, 0x1A1A, 0x2A2A, 0x3A3A, 0x4A4A,
        0x5A5A, 0x6A6A, 0x7A7A, 0x8A8A, 0x9A9A,
        0xAAAA, 0xBABA, 0xCACA, 0xDADA, 0xEAEA, 0xFAFA
    )

    data class PinningAnalysis(
        val appPackage: String?,
        val hasPinning: Boolean,
        val confidence: Float,
        val indicators: List<String>
    )

    /**
     * تحليل TLS ClientHello لاكتشاف SSL pinning
     */
    fun analyzeTlsClientHello(payload: ByteArray, appPackage: String? = null): PinningAnalysis {
        inspectedTlsCount++
        val indicators = mutableListOf<String>()
        var score = 0f

        try {
            // Check if known pinning app
            if (appPackage != null && appPackage in PINNING_APP_PACKAGES) {
                score += 0.5f
                indicators.add("Known SSL-pinning app: $appPackage")
            }

            if (payload.size < 43) {
                return PinningAnalysis(appPackage, false, 0f, indicators)
            }

            // Validate TLS ClientHello structure
            if (payload[0] != 0x16.toByte()) {
                return PinningAnalysis(appPackage, false, 0f, indicators)
            }

            // Parse TLS version
            val tlsVersion = ((payload[1].toInt() and 0xFF) shl 8) or (payload[2].toInt() and 0xFF)
            if (tlsVersion >= 0x0303) {
                // TLS 1.2+ often used with pinning
                score += 0.1f
            }

            // Look for GREASE values in extensions (Chrome/modern apps use GREASE)
            var pos = 5 + 4 + 2 + 32  // Skip to session ID
            if (pos >= payload.size) return PinningAnalysis(appPackage, score > 0.5f, score, indicators)

            val sessionLen = payload[pos].toInt() and 0xFF
            pos += 1 + sessionLen

            if (pos + 2 > payload.size) return PinningAnalysis(appPackage, score > 0.5f, score, indicators)

            val cipherLen = ((payload[pos].toInt() and 0xFF) shl 8) or (payload[pos + 1].toInt() and 0xFF)
            pos += 2

            // Check cipher suites for GREASE
            var i = 0
            while (i < cipherLen - 1 && pos + i + 1 < payload.size) {
                val suite = ((payload[pos + i].toInt() and 0xFF) shl 8) or (payload[pos + i + 1].toInt() and 0xFF)
                if (suite in GREASE_VALUES) {
                    score += 0.2f
                    indicators.add("GREASE cipher suite detected (modern TLS fingerprint)")
                    break
                }
                i += 2
            }
            pos += cipherLen

            if (pos >= payload.size) return PinningAnalysis(appPackage, score > 0.5f, score, indicators)
            val compLen = payload[pos].toInt() and 0xFF
            pos += 1 + compLen

            // Check extensions
            if (pos + 2 <= payload.size) {
                val extLen = ((payload[pos].toInt() and 0xFF) shl 8) or (payload[pos + 1].toInt() and 0xFF)
                pos += 2
                val extEnd = minOf(pos + extLen, payload.size)

                while (pos + 4 <= extEnd) {
                    val extType = ((payload[pos].toInt() and 0xFF) shl 8) or (payload[pos + 1].toInt() and 0xFF)
                    val extDataLen = ((payload[pos + 2].toInt() and 0xFF) shl 8) or (payload[pos + 3].toInt() and 0xFF)
                    pos += 4

                    if (extType in PINNING_TLS_EXTENSIONS) {
                        score += 0.15f
                        indicators.add("Pinning extension: 0x${extType.toString(16)}")
                    }

                    // GREASE extension types
                    if (extType in GREASE_VALUES) {
                        score += 0.1f
                        indicators.add("GREASE extension type")
                    }

                    pos += extDataLen
                }
            }

        } catch (_: Exception) {}

        val hasPinning = score >= 0.5f
        if (hasPinning) detectedPinningCount++

        return PinningAnalysis(appPackage, hasPinning, minOf(score, 1f), indicators)
    }

    /**
     * إرجاع قائمة التطبيقات المعروفة باستخدام SSL Pinning
     */
    fun getKnownPinningApps(): Set<String> = PINNING_APP_PACKAGES

    fun isKnownPinningApp(packageName: String): Boolean = packageName in PINNING_APP_PACKAGES

    fun reset() {
        detectedPinningCount = 0L
        inspectedTlsCount = 0L
    }
}
