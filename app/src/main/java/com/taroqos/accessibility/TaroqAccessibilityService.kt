package com.taroqos.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Layer 5 — UI Accessibility Ad Detection
 *
 * يراقب عناصر الواجهة في التطبيقات الأخرى ويخفي
 * الإعلانات المحتواة فيها دون الحاجة لاعتراض الشبكة.
 * يعمل حتى مع إعلانات Instagram وSnapchat وTikTok
 * التي تأتي من نفس خادم المحتوى.
 */
class TaroqAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var isRunning = false
        @Volatile var hiddenByAccessibility = 0L

        private val AD_LABELS = setOf(
            // English
            "sponsored", "promoted", "advertisement", "ad", "suggested",
            "suggested for you", "promoted post", "paid partnership",
            "paid promotion", "advertiser content", "ad content",
            "why am i seeing this", "why am i seeing this ad",
            // Arabic
            "إعلان", "ممول", "محتوى مدفوع", "منشور ممول",
            "مقترح لك", "شراكة مدفوعة", "إعلانات",
            // Social media specific labels
            "shop now", "learn more", "sign up", "install now",
            "get offer", "book now", "contact us", "apply now",
            "download", "get started", "subscribe"
        )

        private val AD_CONTENT_PATTERNS = listOf(
            Regex("(?i)^sponsored\\b"),
            Regex("(?i)^promoted\\b"),
            Regex("(?i)^advertisement\\b"),
            Regex("(?i)إعلان\\b"),
            Regex("(?i)ممول\\b"),
            Regex("(?i)paid partnership"),
        )

        // Package names of apps where we apply accessibility filtering
        private val TARGET_PACKAGES = setOf(
            "com.instagram.android",
            "com.snapchat.android",
            "com.zhiliaoapp.musically",   // TikTok
            "com.ss.android.ugc.trill",   // TikTok alternative
            "com.facebook.katana",        // Facebook
            "com.facebook.lite",
            "com.twitter.android",
            "com.x.android",
            "com.reddit.frontpage",
            "com.linkedin.android",
            "com.youtube.android",
            "com.google.android.youtube",
            "tv.twitch.android.app",
        )
    }

    override fun onServiceConnected() {
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_SCROLLED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100L
            packageNames = TARGET_PACKAGES.toTypedArray()
        }
        isRunning = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg !in TARGET_PACKAGES) return

        val root = rootInActiveWindow ?: return
        try {
            scanAndHideAds(root)
        } finally {
            root.recycle()
        }
    }

    private fun scanAndHideAds(node: AccessibilityNodeInfo) {
        val text = node.text?.toString()?.trim() ?: ""
        val contentDesc = node.contentDescription?.toString()?.trim() ?: ""

        if (isAdLabel(text) || isAdLabel(contentDesc)) {
            hideAdContainer(node)
            return
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                scanAndHideAds(child)
            } finally {
                child.recycle()
            }
        }
    }

    private fun isAdLabel(text: String): Boolean {
        if (text.isEmpty()) return false
        val lower = text.lowercase().trim()
        if (lower in AD_LABELS) return true
        return AD_CONTENT_PATTERNS.any { it.containsMatchIn(lower) }
    }

    /**
     * يحاول إخفاء حاوية الإعلان بالانتقال إلى العنصر الأب
     * حتى يجد حاوية ScrollView أو RecyclerView لإخفائها.
     */
    private fun hideAdContainer(adNode: AccessibilityNodeInfo) {
        var target: AccessibilityNodeInfo? = adNode.parent
        var depth = 0
        while (target != null && depth < 6) {
            val cls = target.className?.toString() ?: ""
            if (cls.contains("RecyclerView") ||
                cls.contains("ScrollView") ||
                cls.contains("ListView") ||
                cls.contains("FrameLayout") && depth >= 2
            ) {
                performHide(target)
                hiddenByAccessibility++
                return
            }
            val next = target.parent
            target.recycle()
            target = next
            depth++
        }
        target?.recycle()
        // fallback: scroll past the ad
        adNode.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        adNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        hiddenByAccessibility++
    }

    private fun performHide(node: AccessibilityNodeInfo) {
        // Scroll past instead of collapsing (more reliable than ACTION_COLLAPSE)
        node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    override fun onInterrupt() {
        isRunning = false
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }
}
