package com.taroqos.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Layer 9 — Auto Hide Engine
 *
 * يُنفّذ إجراءات الإخفاء على عناصر الإعلان التي رصدتها الطبقات 6/7/8.
 *
 * استراتيجيات الإخفاء (بالترتيب من الأقل تدخلاً للأكثر):
 *   1. ACTION_SCROLL_FORWARD — التمرير فوق الإعلان
 *   2. ACTION_COLLAPSE — طي العنصر (إن دعمه)
 *   3. Gesture scroll — محاكاة سحب الإصبع
 *   4. ACTION_DISMISS — إغلاق (لـ dialogs وoverlays)
 */
class AutoHideEngine(private val service: AccessibilityService) {

    companion object {
        @Volatile var hiddenCount = 0L
        @Volatile var attemptCount = 0L

        fun reset() {
            hiddenCount = 0L
            attemptCount = 0L
        }
    }

    /**
     * إخفاء عنصر إعلان بأفضل استراتيجية متاحة
     */
    fun hide(adNode: AccessibilityNodeInfo): Boolean {
        attemptCount++

        // Strategy 1: Scroll past the ad
        if (adNode.isScrollable) {
            if (adNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                hiddenCount++
                return true
            }
        }

        // Strategy 2: Collapse if supported
        if (adNode.isCollapsed.not() && adNode.actionList.any {
                it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_COLLAPSE.id
            }) {
            if (adNode.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_COLLAPSE.id)) {
                hiddenCount++
                return true
            }
        }

        // Strategy 3: Find scrollable parent and scroll past
        val scrollParent = findScrollableParent(adNode)
        if (scrollParent != null) {
            if (scrollParent.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                hiddenCount++
                return true
            }
        }

        // Strategy 4: Get bounds and perform gesture scroll
        val bounds = Rect()
        adNode.getBoundsInScreen(bounds)
        if (!bounds.isEmpty && bounds.height() > 0) {
            val scrolled = performScrollGesture(bounds)
            if (scrolled) {
                hiddenCount++
                return true
            }
        }

        // Strategy 5: Dismiss (for overlays)
        if (adNode.performAction(AccessibilityNodeInfo.ACTION_DISMISS)) {
            hiddenCount++
            return true
        }

        return false
    }

    /**
     * إخفاء عنصر بأبوه المناسب
     */
    fun hideWithParent(adNode: AccessibilityNodeInfo): Boolean {
        val target = UiTreeAnalyzer.findBestHideTarget(adNode) ?: adNode
        return hide(target)
    }

    /**
     * إخفاء overlay إعلاني (مثل إعلانات ملء الشاشة)
     */
    fun dismissOverlay(node: AccessibilityNodeInfo): Boolean {
        // Try close button first (common pattern: ✕ or X or Close)
        val closeButton = findCloseButton(node)
        if (closeButton != null) {
            if (closeButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                hiddenCount++
                return true
            }
        }

        // Try dismiss action
        if (node.performAction(AccessibilityNodeInfo.ACTION_DISMISS)) {
            hiddenCount++
            return true
        }

        // Back button as last resort for full-screen ads
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        hiddenCount++
        return true
    }

    private fun findCloseButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val closeLabels = setOf("close", "dismiss", "skip", "x", "✕", "×", "إغلاق", "تخطي")
        return findNodeByText(root, closeLabels, 0)
    }

    private fun findNodeByText(
        node: AccessibilityNodeInfo,
        labels: Set<String>,
        depth: Int
    ): AccessibilityNodeInfo? {
        if (depth > 8) return null

        val text = node.text?.toString()?.lowercase()?.trim()
        val desc = node.contentDescription?.toString()?.lowercase()?.trim()
        if ((text != null && text in labels) || (desc != null && desc in labels)) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByText(child, labels, depth + 1)
            if (found != null) return found
            child.recycle()
        }
        return null
    }

    private fun findScrollableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node.parent
        var level = 0
        while (current != null && level < 10) {
            if (current.isScrollable) return current
            val next = current.parent
            current = next
            level++
        }
        return null
    }

    private fun performScrollGesture(bounds: Rect): Boolean {
        return try {
            val startX = bounds.centerX().toFloat()
            val startY = bounds.bottom.toFloat()
            val endY = (bounds.top - bounds.height()).toFloat()

            val path = Path()
            path.moveTo(startX, startY)
            path.lineTo(startX, endY)

            val stroke = GestureDescription.StrokeDescription(path, 0, 300)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            service.dispatchGesture(gesture, null, null)
        } catch (_: Exception) { false }
    }
}

// Extension to avoid crash
private val AccessibilityNodeInfo.isCollapsed: Boolean
    get() = try {
        !this.isExpanded
    } catch (_: Exception) { false }
