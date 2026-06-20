package com.taroqos.rules

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Layer 12 — App Rules Manager (JSON Rules Database)
 *
 * يدير قواعد مخصصة لكل تطبيق تُحدّد:
 *   - النطاقات المحجوبة خصيصاً لهذا التطبيق
 *   - مسارات URL المحجوبة
 *   - عناصر UI المستهدفة (resource IDs)
 *   - سلوك الطبقة 9 (autoHide vs scroll vs dismiss)
 *
 * القواعد محفوظة في assets/app_rules.json وقابلة للتحديث عبر Layer 13.
 */
object AppRulesManager {

    @Volatile var loadedRules = 0
    @Volatile var rulesApplied = 0L

    data class AppRule(
        val packageName: String,
        val blockedDomains: Set<String>,
        val blockedUrlPatterns: List<Regex>,
        val targetResourceIds: Set<String>,
        val targetClassNames: Set<String>,
        val hideStrategy: HideStrategy,
        val enabled: Boolean
    )

    enum class HideStrategy { SCROLL, COLLAPSE, DISMISS, GESTURE }

    // Built-in rules for major apps (always available, no download needed)
    val BUILTIN_RULES: Map<String, AppRule> = mapOf(

        "com.instagram.android" to AppRule(
            packageName = "com.instagram.android",
            blockedDomains = setOf(
                "ads.instagram.com", "analytics.instagram.com",
                "pixel.instagram.com", "graph.instagram.com",
                "an.facebook.com", "ads.facebook.com"
            ),
            blockedUrlPatterns = listOf(
                Regex("(?i)/ads/"),
                Regex("(?i)/sponsored/"),
                Regex("(?i)/iab_ad_"),
                Regex("(?i)fbadnw"),
            ),
            targetResourceIds = setOf(
                "com.instagram.android:id/ads_label",
                "com.instagram.android:id/promoted_label",
                "com.instagram.android:id/sponsored_label",
                "com.instagram.android:id/media_sponsored_label",
                "com.instagram.android:id/native_ad_badge",
            ),
            targetClassNames = setOf(
                "com.instagram.android.ads.NativeAdView",
                "com.instagram.android.ads.IgBannerView",
            ),
            hideStrategy = HideStrategy.SCROLL,
            enabled = true
        ),

        "com.zhiliaoapp.musically" to AppRule(  // TikTok
            packageName = "com.zhiliaoapp.musically",
            blockedDomains = setOf(
                "ads.tiktok.com", "analytics.tiktok.com", "ad.tiktok.com",
                "ads-api.tiktok.com", "mon.musical.ly", "log-va.tiktokv.com"
            ),
            blockedUrlPatterns = listOf(
                Regex("(?i)/adserver/"),
                Regex("(?i)/open_ad/"),
                Regex("(?i)/ad_spark/"),
                Regex("(?i)/feed_ad"),
            ),
            targetResourceIds = setOf(
                "com.zhiliaoapp.musically:id/sponsored_tag",
                "com.zhiliaoapp.musically:id/ad_badge",
                "com.zhiliaoapp.musically:id/promote_tag",
            ),
            targetClassNames = emptySet(),
            hideStrategy = HideStrategy.SCROLL,
            enabled = true
        ),

        "com.snapchat.android" to AppRule(
            packageName = "com.snapchat.android",
            blockedDomains = setOf(
                "ads.snapchat.com", "tr.snapchat.com",
                "businesshelp.snapchat.com", "sc-static.net"
            ),
            blockedUrlPatterns = listOf(
                Regex("(?i)/snap_ads/"),
                Regex("(?i)/story_ads"),
            ),
            targetResourceIds = setOf(
                "com.snapchat.android:id/ad_badge",
                "com.snapchat.android:id/sponsored_label",
            ),
            targetClassNames = emptySet(),
            hideStrategy = HideStrategy.SCROLL,
            enabled = true
        ),

        "com.google.android.youtube" to AppRule(
            packageName = "com.google.android.youtube",
            blockedDomains = setOf(
                "googleads.g.doubleclick.net",
                "pagead2.googlesyndication.com",
                "imasdk.googleapis.com",
            ),
            blockedUrlPatterns = listOf(
                Regex("(?i)/pagead/"),
                Regex("(?i)/videoplayback.*&ctier="),
                Regex("(?i)/api/stats/ads"),
                Regex("(?i)/ptracking"),
            ),
            targetResourceIds = setOf(
                "com.google.android.youtube:id/ad_badge",
                "com.google.android.youtube:id/skip_ad_button",
                "com.google.android.youtube:id/ad_progress_text",
            ),
            targetClassNames = emptySet(),
            hideStrategy = HideStrategy.DISMISS,
            enabled = true
        ),

        "com.twitter.android" to AppRule(
            packageName = "com.twitter.android",
            blockedDomains = setOf(
                "ads.twitter.com", "analytics.twitter.com",
                "ads-twitter.com", "syndication.twitter.com"
            ),
            blockedUrlPatterns = listOf(
                Regex("(?i)/promoted/"),
                Regex("(?i)/ad_engagement"),
                Regex("(?i)/scribe"),
            ),
            targetResourceIds = setOf(
                "com.twitter.android:id/promoted_indicator",
                "com.twitter.android:id/tweet_ad_label",
            ),
            targetClassNames = emptySet(),
            hideStrategy = HideStrategy.SCROLL,
            enabled = true
        ),

        "com.reddit.frontpage" to AppRule(
            packageName = "com.reddit.frontpage",
            blockedDomains = setOf(
                "ads.reddit.com", "redd.it",
                "reddit-marketing.pro"
            ),
            blockedUrlPatterns = listOf(
                Regex("(?i)/promoted"),
                Regex("(?i)/advertising"),
            ),
            targetResourceIds = setOf(
                "com.reddit.frontpage:id/promoted_label",
                "com.reddit.frontpage:id/ad_tag",
            ),
            targetClassNames = emptySet(),
            hideStrategy = HideStrategy.SCROLL,
            enabled = true
        ),
    )

    private var customRules: Map<String, AppRule> = emptyMap()

    /**
     * تحميل قواعد مخصصة من JSON (يُستدعى من Layer 13)
     */
    fun loadCustomRules(json: String) {
        try {
            val parsed = parseRulesJson(json)
            customRules = parsed
            loadedRules = BUILTIN_RULES.size + parsed.size
        } catch (_: Exception) {
            loadedRules = BUILTIN_RULES.size
        }
    }

    /**
     * الحصول على قاعدة تطبيق محدد
     */
    fun getRuleFor(packageName: String): AppRule? {
        return customRules[packageName] ?: BUILTIN_RULES[packageName]
    }

    fun isTargetPackage(packageName: String): Boolean {
        return packageName in customRules || packageName in BUILTIN_RULES
    }

    fun getAllPackages(): Set<String> {
        return BUILTIN_RULES.keys + customRules.keys
    }

    private fun parseRulesJson(json: String): Map<String, AppRule> {
        val result = mutableMapOf<String, AppRule>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val pkg = obj.getString("package")

                val domains = mutableSetOf<String>()
                val domainsArr = obj.optJSONArray("blocked_domains")
                if (domainsArr != null) {
                    for (j in 0 until domainsArr.length()) domains.add(domainsArr.getString(j))
                }

                val patterns = mutableListOf<Regex>()
                val patternsArr = obj.optJSONArray("blocked_url_patterns")
                if (patternsArr != null) {
                    for (j in 0 until patternsArr.length()) {
                        runCatching { patterns.add(Regex(patternsArr.getString(j))) }
                    }
                }

                val resourceIds = mutableSetOf<String>()
                val idsArr = obj.optJSONArray("target_resource_ids")
                if (idsArr != null) {
                    for (j in 0 until idsArr.length()) resourceIds.add(idsArr.getString(j))
                }

                val strategy = when (obj.optString("hide_strategy", "SCROLL").uppercase()) {
                    "COLLAPSE" -> AppRulesManager.HideStrategy.COLLAPSE
                    "DISMISS"  -> AppRulesManager.HideStrategy.DISMISS
                    "GESTURE"  -> AppRulesManager.HideStrategy.GESTURE
                    else       -> AppRulesManager.HideStrategy.SCROLL
                }

                result[pkg] = AppRule(
                    packageName = pkg,
                    blockedDomains = domains,
                    blockedUrlPatterns = patterns,
                    targetResourceIds = resourceIds,
                    targetClassNames = emptySet(),
                    hideStrategy = strategy,
                    enabled = obj.optBoolean("enabled", true)
                )
            }
        } catch (_: Exception) {}
        return result
    }

    init {
        loadedRules = BUILTIN_RULES.size
    }
}
