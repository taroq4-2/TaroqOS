package com.taroqos.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.taroqos.ai.BehavioralDetector
import com.taroqos.ai.VisualAiDetector
import com.taroqos.rules.AppRulesManager
import com.taroqos.telemetry.LocalTelemetry

/**
 * Layers 6 + 7 + 8 + 9 — Unified Accessibility Pipeline
 *
 *   Layer 6 — Accessibility Service: يستقبل أحداث الواجهة
 *   Layer 7 — OCR Engine: يستخرج النص من AccessibilityNodeInfo
 *   Layer 8 — UI Tree Analyzer: يحلل شجرة العناصر
 *   Layer 9 — Auto Hide Engine: يُخفي الإعلانات المكتشفة
 */
class TaroqAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var isRunning = false
        @Volatile var hiddenByAccessibility = 0L
        @Volatile var eventsProcessed = 0L
        @Volatile var currentPackage = ""

        private val TARGET_PACKAGES = setOf(
            "com.instagram.android",
            "com.snapchat.android",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.facebook.katana",
            "com.facebook.lite",
            "com.twitter.android",
            "com.x.android",
            "com.reddit.frontpage",
            "com.linkedin.android",
            "com.youtube.android",
            "com.google.android.youtube",
            "tv.twitch.android.app",
            "com.pinterest",
        )
    }

    private lateinit var autoHideEngine: AutoHideEngine

    override fun onServiceConnected() {
        autoHideEngine = AutoHideEngine(this)
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_SCROLLED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 80L
            packageNames = (TARGET_PACKAGES + AppRulesManager.getAllPackages()).toTypedArray()
        }
        isRunning = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg !in TARGET_PACKAGES && !AppRulesManager.isTargetPackage(pkg)) return

        currentPackage = pkg
        eventsProcessed++

        val root = rootInActiveWindow ?: return
        try {
            processWindow(root, pkg)
        } finally {
            root.recycle()
        }
    }

    private fun processWindow(root: AccessibilityNodeInfo, pkg: String) {
        val appRule = AppRulesManager.getRuleFor(pkg)
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels

        // === Layer 8: UI Tree Analyzer ===
        val adNodes = UiTreeAnalyzer.findAdNodes(root, pkg)
        for (adNode in adNodes) {
            if (adNode.confidence >= 0.75f) {
                val hidden = autoHideEngine.hideWithParent(adNode.node)
                if (hidden) { hiddenByAccessibility++; LocalTelemetry.recordHiddenUi() }
            }
        }

        // === Layer 7: OCR text extraction + Layer 6 label check + Layer 11 behavioral ===
        val extractedTexts = OcrEngine.extractAllText(root)
        for (extracted in extractedTexts) {
            val isAdByLabel = AdTextMatcher.isAdLabel(extracted.text) ||
                              AdTextMatcher.isAdLabel(extracted.contentDesc)
            val behavioralResult = BehavioralDetector.analyzeText(extracted.text, extracted.contentDesc)

            if (isAdByLabel || (behavioralResult.isAd && behavioralResult.confidence >= 0.75f)) {
                val textToFind = extracted.text.ifEmpty { extracted.contentDesc }
                if (textToFind.isEmpty()) continue
                val nodes = root.findAccessibilityNodeInfosByText(textToFind)
                val node = nodes.firstOrNull() ?: continue
                val target = UiTreeAnalyzer.findBestHideTarget(node) ?: node
                val hidden = autoHideEngine.hide(target)
                if (hidden) { hiddenByAccessibility++; LocalTelemetry.recordHiddenUi() }
            }

            // === Layer 10: Visual AI bounds analysis ===
            val bounds = extracted.bounds
            if (bounds != null && !bounds.isEmpty) {
                val visual = VisualAiDetector.analyzeNodeBounds(bounds, screenW, screenH)
                if (visual.isAd && visual.confidence >= 0.85f) {
                    if (extracted.text.isEmpty()) continue
                    val textToFind = extracted.text
                    val nodes = root.findAccessibilityNodeInfosByText(textToFind)
                    val node = nodes.firstOrNull() ?: continue
                    val hidden = autoHideEngine.hideWithParent(node)
                    if (hidden) { hiddenByAccessibility++; LocalTelemetry.recordHiddenUi() }
                }
            }
        }

        // === Layer 12: App-specific resource ID rules ===
        if (appRule != null && appRule.enabled) {
            for (resourceId in appRule.targetResourceIds) {
                val nodes = root.findAccessibilityNodeInfosByViewId(resourceId)
                for (node in nodes) {
                    val target = UiTreeAnalyzer.findBestHideTarget(node) ?: node
                    val hidden = when (appRule.hideStrategy) {
                        AppRulesManager.HideStrategy.DISMISS -> autoHideEngine.dismissOverlay(target)
                        else -> autoHideEngine.hide(target)
                    }
                    if (hidden) { hiddenByAccessibility++; LocalTelemetry.recordHiddenUi() }
                    node.recycle()
                }
            }
        }
    }

    override fun onInterrupt() { isRunning = false }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }
}
