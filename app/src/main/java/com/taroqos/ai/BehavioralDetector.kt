package com.taroqos.ai

/**
 * Layer 11 — Behavioral Detection (Custom Heuristic Engine)
 *
 * يكتشف الإعلانات بتحليل السلوك والسياق بدلاً من الأنماط الثابتة:
 *   - كلمات "Install Now / Shop Now / Sign Up" في سياق CTA
 *   - أنماط URL-in-text (روابط مضمّنة في محتوى مرئي)
 *   - تسلسل عناصر واجهة مميز للإعلانات (صورة + نص + زر CTA)
 *   - تكرار نفس النطاق مرات كثيرة (network-based detection)
 *   - Keywords المميزة لـ RTB وOpenRTB
 */
object BehavioralDetector {

    @Volatile var detectedBehavioral = 0L

    // CTA (Call-to-Action) phrases that ONLY appear in ads
    private val HARD_CTA_PHRASES: Set<String> = setOf(
        // English CTAs
        "install now", "install app", "get the app", "download app",
        "download free", "download now", "get it free", "free download",
        "shop now", "buy now", "order now", "get deal", "get offer",
        "claim offer", "see offer", "view deal", "limited offer",
        "sign up free", "try free", "start free", "free trial",
        "learn more", "find out more", "discover more", "explore now",
        "play now", "play free", "play for free", "start playing",
        "join now", "register now", "create account", "open account",
        "get quote", "request quote", "get estimate", "book now",
        "apply now", "apply today", "apply online",
        "visit now", "visit site", "go to site", "see more",
        "follow us", "like our page", "subscribe now",
        // Arabic CTAs
        "احصل على التطبيق", "تحميل مجاني", "حمّل الآن", "ثبّت الآن",
        "تسوق الآن", "اشترِ الآن", "اطلب الآن", "سجّل مجاناً",
        "جرّب مجاناً", "تعلم المزيد", "اعرف أكثر", "اكتشف المزيد",
        "العب الآن", "انضم الآن", "سجّل الآن", "احجز الآن",
        "قدّم الآن", "زر الموقع",
    )

    // Behavioral patterns — sequences that indicate an ad unit
    private val BEHAVIORAL_PATTERNS: List<Regex> = listOf(
        // "Sponsored" or "Ad" followed by brand name
        Regex("(?i)(sponsored|إعلان|ممول)\\s+.{1,50}\\s+(install|تحميل|shop|تسوق)"),
        // Price + CTA pattern
        Regex("(?i)(\\$|€|£|﷼|ريال|دولار)\\s*\\d+[.,]?\\d*\\s*(?:per|/|لكل)?"),
        // "X% off" promotional patterns
        Regex("(?i)\\d+%\\s*(?:off|discount|خصم|تخفيض)"),
        // "Limited time" urgency patterns
        Regex("(?i)(limited time|limited offer|ends soon|offer expires|عرض محدود|ينتهي قريباً)"),
        // Brand + "ad" pattern
        Regex("(?i)\\b\\w+\\s+(?:official ad|official advertisement|إعلان رسمي)"),
        // App store style rating + install
        Regex("(?i)\\d+\\.\\d+\\s*(?:stars?|★|⭐).*(?:install|تحميل|download)"),
    )

    // Network request patterns from behavioral analysis
    private val SUSPICIOUS_URL_PATHS: Set<String> = setOf(
        "/impression", "/click", "/beacon", "/pixel", "/track",
        "/event", "/analytics", "/collect", "/hit", "/log",
        "/conversion", "/attribution", "/postback",
        "/bid", "/auction", "/rtb", "/vast", "/vpaid",
        "/openrtb", "/prebid", "/hb", "/header-bid",
    )

    data class BehavioralResult(
        val isAd: Boolean,
        val confidence: Float,
        val ctaFound: String?,
        val patternMatched: String?,
        val behavioralScore: Float
    )

    /**
     * تحليل نص الواجهة للكشف عن أنماط CTA الإعلانية
     */
    fun analyzeText(text: String, context: String = ""): BehavioralResult {
        val lower = text.lowercase().trim()
        val contextLower = context.lowercase()
        var score = 0f
        var ctaFound: String? = null
        var patternMatched: String? = null

        // 1. Hard CTA check (very high confidence)
        for (cta in HARD_CTA_PHRASES) {
            if (lower.contains(cta)) {
                score += 0.85f
                ctaFound = cta
                break
            }
        }

        // 2. Behavioral pattern matching
        for (pattern in BEHAVIORAL_PATTERNS) {
            val combined = "$lower $contextLower"
            if (pattern.containsMatchIn(combined)) {
                score += 0.70f
                patternMatched = pattern.pattern
                break
            }
        }

        // 3. Context boost — if context has "Sponsored" or "إعلان"
        if (contextLower.contains("sponsored") || contextLower.contains("إعلان") ||
            contextLower.contains("ممول") || contextLower.contains("promoted")) {
            score += 0.50f
        }

        // 4. Multiple CTAs in same text (stronger signal)
        val ctaCount = HARD_CTA_PHRASES.count { lower.contains(it) }
        if (ctaCount > 1) score += (ctaCount - 1) * 0.15f

        val isAd = score >= 0.65f
        if (isAd) detectedBehavioral++

        return BehavioralResult(
            isAd = isAd,
            confidence = minOf(score, 1f),
            ctaFound = ctaFound,
            patternMatched = patternMatched,
            behavioralScore = score
        )
    }

    /**
     * تحليل مسار URL الشبكي للكشف عن طلبات الإعلانات
     */
    fun analyzeNetworkPath(path: String): Boolean {
        val lower = path.lowercase()
        return SUSPICIOUS_URL_PATHS.any { lower.startsWith(it) || lower.contains("$it/") }
    }

    /**
     * كشف أنماط تتبع المستخدم السلوكية في URLs
     */
    fun isTrackingRequest(url: String): Boolean {
        val lower = url.lowercase()
        // High-confidence tracking patterns
        return lower.contains("?utm_") ||
               lower.contains("&utm_") ||
               lower.contains("fbclid=") ||
               lower.contains("gclid=") ||
               lower.contains("msclkid=") ||
               lower.contains("ttclid=") ||
               lower.contains("li_fat_id=") ||
               lower.contains("clickid=") ||
               lower.contains("click_id=")
    }

    fun reset() {
        detectedBehavioral = 0L
    }
}
