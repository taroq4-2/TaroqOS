package com.taroqos.accessibility

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Layer 7 — OCR Engine (Text Extraction)
 *
 * يستخرج النص من عناصر الواجهة دون الحاجة لـ Tesseract OCR الثقيل.
 * يستخدم AccessibilityNodeInfo.getText() كبديل طبيعي يعطي نفس النتيجة:
 * استخراج كل النصوص المرئية للمستخدم من الشاشة.
 *
 * المزايا على Tesseract:
 *  - لا يحتاج مكتبات native ضخمة (25MB+)
 *  - أسرع بكثير (لا معالجة صور)
 *  - أدق في التطبيقات لأنه يقرأ النص قبل الرسم
 *  - يدعم اللغة العربية بالكامل
 *
 * هذا النهج مستخدم في AdGuard وBlockBear للأسباب ذاتها.
 */
object OcrEngine {

    @Volatile var textExtractionCount = 0L

    data class ExtractedText(
        val text: String,
        val contentDesc: String,
        val nodeClass: String,
        val depth: Int,
        val bounds: android.graphics.Rect?
    )

    /**
     * استخراج كل النصوص من شجرة العناصر
     */
    fun extractAllText(root: AccessibilityNodeInfo, maxDepth: Int = 15): List<ExtractedText> {
        val results = mutableListOf<ExtractedText>()
        extractRecursive(root, 0, maxDepth, results)
        textExtractionCount++
        return results
    }

    private fun extractRecursive(
        node: AccessibilityNodeInfo,
        depth: Int,
        maxDepth: Int,
        results: MutableList<ExtractedText>
    ) {
        if (depth > maxDepth) return

        val text = node.text?.toString()?.trim() ?: ""
        val contentDesc = node.contentDescription?.toString()?.trim() ?: ""
        val nodeClass = node.className?.toString() ?: ""

        if (text.isNotEmpty() || contentDesc.isNotEmpty()) {
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            results.add(
                ExtractedText(
                    text = text,
                    contentDesc = contentDesc,
                    nodeClass = nodeClass,
                    depth = depth,
                    bounds = bounds
                )
            )
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                extractRecursive(child, depth + 1, maxDepth, results)
            } finally {
                child.recycle()
            }
        }
    }

    /**
     * استخراج كل النصوص المرئية كنص واحد مسطّح
     */
    fun extractFlatText(root: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        extractFlatRecursive(root, sb, 0)
        return sb.toString()
    }

    private fun extractFlatRecursive(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
        if (depth > 15) return

        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty()) {
            if (sb.isNotEmpty()) sb.append(" | ")
            sb.append(text)
        }

        val desc = node.contentDescription?.toString()?.trim()
        if (!desc.isNullOrEmpty() && desc != text) {
            if (sb.isNotEmpty()) sb.append(" | ")
            sb.append(desc)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                extractFlatRecursive(child, sb, depth + 1)
            } finally {
                child.recycle()
            }
        }
    }

    /**
     * فحص إن كان النص يحتوي على مؤشر إعلاني
     */
    fun containsAdText(text: String): Boolean {
        return AdTextMatcher.isAdLabel(text)
    }

    fun reset() {
        textExtractionCount = 0L
    }
}

/**
 * مطابق النصوص الإعلانية — مُعرَّف هنا لإعادة الاستخدام بين الطبقات
 */
internal object AdTextMatcher {

    private val AD_LABELS_EXACT: Set<String> = setOf(
        // English
        "sponsored", "promoted", "advertisement", "ad", "paid",
        "suggested", "suggested for you", "promoted post",
        "paid partnership", "paid promotion", "advertiser content",
        "ad content", "why am i seeing this", "why am i seeing this ad",
        "shop now", "learn more", "sign up", "install now",
        "get offer", "book now", "contact us", "apply now",
        "download", "get started", "subscribe", "get app",
        "buy now", "order now", "see offer", "visit site",
        // Arabic
        "إعلان", "ممول", "مدفوع", "مُعلَن", "محتوى إعلاني",
        "محتوى مدفوع", "مقترح لك", "مُقترح", "إعلانات",
        "برعاية", "مدفوع الأجر", "اشتر الآن", "اطلب الآن",
        "تسوق الآن", "احصل على العرض", "سجّل الآن", "تنزيل",
    )

    private val AD_CONTENT_PATTERNS: List<Regex> = listOf(
        Regex("(?i)^sponsored\\b"),
        Regex("(?i)^promoted\\b"),
        Regex("(?i)^advertisement\\b"),
        Regex("(?i)^إعلان\\b"),
        Regex("(?i)^ممول\\b"),
        Regex("(?i)paid partnership"),
        Regex("(?i)paid promotion"),
        Regex("(?i)^ad \\d"),
        Regex("(?i)\\bsponsored by\\b"),
        Regex("(?i)\\bpromoted by\\b"),
    )

    fun isAdLabel(text: String): Boolean {
        if (text.isEmpty()) return false
        val lower = text.lowercase().trim()
        if (lower in AD_LABELS_EXACT) return true
        return AD_CONTENT_PATTERNS.any { it.containsMatchIn(lower) }
    }
}
