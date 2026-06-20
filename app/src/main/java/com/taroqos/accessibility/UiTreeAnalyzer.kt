package com.taroqos.accessibility

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Layer 8 — UI Tree Analyzer
 *
 * يُحلّل شجرة AccessibilityNodeInfo بعمق للكشف عن:
 *   - عناصر الإعلانات المخفية وراء containers
 *   - أنماط layouts المميزة للإعلانات (WebView داخل FrameLayout)
 *   - عناصر WebView التي تحتوي إعلانات JavaScript
 *   - Scrollable containers التي تحوي "Sponsored" أو "إعلان"
 *
 * يُستخدم نتائجه لتغذية Layer 9 (AutoHideEngine) بالعناصر المستهدفة.
 */
object UiTreeAnalyzer {

    @Volatile var analyzedCount = 0L
    @Volatile var adNodesFound = 0L

    data class AdNode(
        val node: AccessibilityNodeInfo,
        val confidence: Float,
        val reason: String,
        val parentClass: String,
        val depth: Int
    )

    // تسميات الـ resource IDs المرتبطة بالإعلانات
    private val AD_RESOURCE_ID_PATTERNS: List<Regex> = listOf(
        Regex("(?i):id/.*ad(?:_|\\b)"),
        Regex("(?i):id/.*sponsor"),
        Regex("(?i):id/.*promot"),
        Regex("(?i):id/.*banner"),
        Regex("(?i):id/.*dfp"),
        Regex("(?i):id/.*admob"),
        Regex("(?i):id/.*native_ad"),
        Regex("(?i):id/.*ad_container"),
        Regex("(?i):id/.*ad_frame"),
        Regex("(?i):id/.*ad_view"),
        Regex("(?i):id/.*ad_unit"),
        Regex("(?i):id/.*promoted"),
        Regex("(?i)com\\.google\\.android\\.gms"),
        Regex("(?i)com\\.facebook\\.ads"),
        Regex("(?i)com\\.unity3d\\.ads"),
        Regex("(?i)com\\.ironsource"),
        Regex("(?i)com\\.applovin"),
        Regex("(?i)com\\.chartboost"),
        Regex("(?i)com\\.vungle"),
    )

    // Classes that commonly wrap ads
    private val AD_WRAPPER_CLASSES: Set<String> = setOf(
        "com.google.android.gms.ads.AdView",
        "com.google.android.gms.ads.NativeAdView",
        "com.google.android.gms.ads.AdFrame",
        "com.facebook.ads.AdView",
        "com.facebook.ads.NativeAd",
        "com.unity3d.ads.UnityAds",
        "com.applovin.sdk.AppLovinAdView",
        "com.chartboost.sdk.Chartboost",
        "com.ironsource.mediationsdk.IronSource",
        "com.vungle.warren.Vungle",
        "com.mopub.mobileads.MoPubView",
        "com.inmobi.ads.InMobiAdSet",
        "com.startapp.android.publish.StartAppAd",
    )

    /**
     * تحليل شامل للشجرة وإيجاد عناصر الإعلان
     */
    fun findAdNodes(root: AccessibilityNodeInfo, packageName: String): List<AdNode> {
        val results = mutableListOf<AdNode>()
        analyzeRecursive(root, 0, results, packageName)
        analyzedCount++
        adNodesFound += results.size
        return results
    }

    private fun analyzeRecursive(
        node: AccessibilityNodeInfo,
        depth: Int,
        results: MutableList<AdNode>,
        packageName: String
    ) {
        if (depth > 20) return

        val className = node.className?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""
        val text = node.text?.toString()?.trim() ?: ""
        val contentDesc = node.contentDescription?.toString()?.trim() ?: ""

        var score = 0f
        val reasons = mutableListOf<String>()

        // 1. Check class name directly against known ad SDKs
        if (className in AD_WRAPPER_CLASSES) {
            score += 0.95f
            reasons.add("Ad SDK class: $className")
        }

        // 2. Check resource ID for ad patterns
        if (viewId.isNotEmpty()) {
            for (pattern in AD_RESOURCE_ID_PATTERNS) {
                if (pattern.containsMatchIn(viewId)) {
                    score += 0.85f
                    reasons.add("Ad resource ID: $viewId")
                    break
                }
            }
        }

        // 3. Check text content for ad labels
        if (AdTextMatcher.isAdLabel(text) || AdTextMatcher.isAdLabel(contentDesc)) {
            score += 0.80f
            reasons.add("Ad label text: '${text.ifEmpty { contentDesc }}'")
        }

        // 4. Check for WebView wrapping (common ad injection method)
        if (className.contains("WebView", ignoreCase = true)) {
            score += 0.4f
            reasons.add("WebView detected (potential ad container)")
        }

        // 5. Check bounds — very small elements often tracking pixels
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.width() in 1..5 && bounds.height() in 1..5) {
            score += 0.7f
            reasons.add("Tracking pixel dimensions: ${bounds.width()}x${bounds.height()}")
        }

        if (score >= 0.6f) {
            val parentClass = try {
                node.parent?.className?.toString() ?: ""
            } catch (_: Exception) { "" }
            results.add(
                AdNode(
                    node = node,
                    confidence = minOf(score, 1f),
                    reason = reasons.joinToString("; "),
                    parentClass = parentClass,
                    depth = depth
                )
            )
        }

        // Recurse into children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                analyzeRecursive(child, depth + 1, results, packageName)
            } finally {
                // Don't recycle here — we store references in AdNode
                // AutoHideEngine will recycle after use
            }
        }
    }

    /**
     * إيجاد container مناسب للإخفاء بالصعود في الشجرة
     */
    fun findBestHideTarget(adNode: AccessibilityNodeInfo, maxLevels: Int = 8): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = adNode.parent
        var level = 0

        while (current != null && level < maxLevels) {
            val cls = current.className?.toString() ?: ""
            val isGoodContainer = cls.contains("RecyclerView", ignoreCase = true) ||
                    cls.contains("ListView", ignoreCase = true) ||
                    cls.contains("ScrollView", ignoreCase = true) ||
                    (cls.contains("FrameLayout", ignoreCase = true) && level >= 2) ||
                    (cls.contains("LinearLayout", ignoreCase = true) && level >= 3) ||
                    (cls.contains("RelativeLayout", ignoreCase = true) && level >= 3) ||
                    (cls.contains("ConstraintLayout", ignoreCase = true) && level >= 3)

            if (isGoodContainer) return current

            val next = current.parent
            current = next
            level++
        }
        return null
    }

    fun reset() {
        analyzedCount = 0L
        adNodesFound = 0L
    }
}
