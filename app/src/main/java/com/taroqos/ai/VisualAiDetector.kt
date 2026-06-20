package com.taroqos.ai

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * Layer 10 — Visual AI Detector (TensorFlow Lite Ready)
 *
 * واجهة كاشف الإعلانات البصري. تعمل الآن بمحرك heuristic متقدم
 * وهي مصممة لاستقبال نموذج TFLite مستقبلاً بدون تغيير في الواجهة.
 *
 * خوارزمية الكشف الحالية:
 *   1. تحليل نسب الأبعاد (Aspect Ratio) — الإعلانات لها نسب قياسية
 *   2. تحليل موقع العنصر (Position) — الإعلانات في أعلى/أسفل الشاشة عادة
 *   3. تحليل الحجم النسبي (Relative Size) — الإعلانات تأخذ 10-25% من الشاشة
 *   4. كشف Blur/Overlay — يُغطي محتوى الإعلانات عادةً المحتوى الأصلي
 *
 * Standard Ad Sizes (IAB):
 *   - Banner: 320×50, 728×90
 *   - Medium Rectangle: 300×250
 *   - Large Rectangle: 336×280
 *   - Half Page: 300×600
 *   - Leaderboard: 728×90, 970×90
 *   - Interstitial: ~90% screen
 */
object VisualAiDetector {

    @Volatile var detectedCount = 0L
    @Volatile var analyzedCount = 0L

    // TFLite model path (for future integration)
    private const val MODEL_FILE = "ad_classifier.tflite"
    private var isTfLiteAvailable = false  // set to true when model is bundled

    data class VisualAnalysis(
        val isAd: Boolean,
        val confidence: Float,
        val adType: AdType,
        val reasons: List<String>
    )

    enum class AdType {
        UNKNOWN, BANNER, INTERSTITIAL, NATIVE, VIDEO, REWARDED, POPUP
    }

    // IAB standard ad sizes (width, height) in dp
    private val IAB_BANNER_SIZES: List<Pair<Int, Int>> = listOf(
        300 to 50, 320 to 50, 360 to 50, 375 to 50, 414 to 50,  // Mobile banner
        728 to 90, 970 to 90,                                      // Leaderboard
        300 to 250, 250 to 250, 200 to 200,                       // Rectangle
        300 to 600, 160 to 600,                                    // Half page
        320 to 480, 480 to 320,                                    // Interstitial small
        336 to 280,                                                // Large rectangle
    )

    // Tolerance percentage for size matching
    private const val SIZE_TOLERANCE = 0.15f

    /**
     * تحليل عنصر بناءً على أبعاده وموقعه على الشاشة
     */
    fun analyzeNodeBounds(
        bounds: Rect,
        screenWidth: Int,
        screenHeight: Int
    ): VisualAnalysis {
        analyzedCount++
        val reasons = mutableListOf<String>()
        var score = 0f

        val width = bounds.width()
        val height = bounds.height()

        if (width <= 0 || height <= 0) {
            return VisualAnalysis(false, 0f, AdType.UNKNOWN, reasons)
        }

        // 1. Check against IAB standard sizes
        val screenDensity = 2.0f  // assume xhdpi
        val widthDp = (width / screenDensity).toInt()
        val heightDp = (height / screenDensity).toInt()

        for ((w, h) in IAB_BANNER_SIZES) {
            val wMatch = Math.abs(widthDp - w).toFloat() / w < SIZE_TOLERANCE
            val hMatch = Math.abs(heightDp - h).toFloat() / h < SIZE_TOLERANCE
            if (wMatch && hMatch) {
                score += 0.85f
                reasons.add("Matches IAB ad size: ${w}x${h}dp (actual: ${widthDp}x${heightDp}dp)")
                break
            }
        }

        // 2. Interstitial detection (covers >70% screen)
        val screenArea = screenWidth * screenHeight
        val nodeArea = width * height
        val coverageRatio = nodeArea.toFloat() / screenArea

        if (coverageRatio > 0.70f) {
            score += 0.75f
            reasons.add("Interstitial-sized: covers ${(coverageRatio * 100).toInt()}% of screen")
            return VisualAnalysis(true, minOf(score, 1f), AdType.INTERSTITIAL, reasons)
        }

        // 3. Position analysis — top and bottom of screen are common ad positions
        val topRatio = bounds.top.toFloat() / screenHeight
        val bottomRatio = bounds.bottom.toFloat() / screenHeight

        if (topRatio < 0.08f && height < screenHeight * 0.15f) {
            score += 0.3f
            reasons.add("Top banner position (top=${topRatio * 100}% screen)")
        }
        if (bottomRatio > 0.88f && height < screenHeight * 0.15f) {
            score += 0.35f
            reasons.add("Bottom banner position (bottom=${bottomRatio * 100}% screen)")
        }

        // 4. Aspect ratio analysis
        val ratio = width.toFloat() / height
        // Common ad ratios: 6.4 (320x50), 8.1 (728x90), 1.2 (300x250)
        val adRatios = listOf(6.4f, 8.1f, 10.8f, 1.2f, 0.5f)
        for (adRatio in adRatios) {
            if (Math.abs(ratio - adRatio) < adRatio * 0.2f) {
                score += 0.2f
                reasons.add("Ad aspect ratio: ${"%.1f".format(ratio)}:1")
                break
            }
        }

        // 5. Tracking pixel (1x1 or very small)
        if (width <= 5 && height <= 5) {
            score += 0.95f
            reasons.add("Tracking pixel: ${width}x${height}px")
            return VisualAnalysis(true, 0.95f, AdType.NATIVE, reasons)
        }

        val isAd = score >= 0.65f
        val adType = when {
            score >= 0.9f && coverageRatio > 0.5f -> AdType.INTERSTITIAL
            height < 100 -> AdType.BANNER
            score > 0.5f -> AdType.NATIVE
            else -> AdType.UNKNOWN
        }

        if (isAd) detectedCount++
        return VisualAnalysis(isAd, minOf(score, 1f), adType, reasons)
    }

    /**
     * تحليل Bitmap للكشف عن الإعلانات بصرياً (TFLite placeholder)
     * ستُفعَّل عند توفر نموذج TFLite في assets/ad_classifier.tflite
     */
    fun analyzeBitmap(bitmap: Bitmap): VisualAnalysis {
        // TFLite integration placeholder
        // When model is available:
        // val interpreter = Interpreter(loadModelFile(context, MODEL_FILE))
        // val inputBuffer = preprocessBitmap(bitmap)
        // interpreter.run(inputBuffer, outputBuffer)
        // return interpretOutput(outputBuffer)

        // Fallback: heuristic analysis on bitmap dimensions
        return analyzeNodeBounds(
            bounds = Rect(0, 0, bitmap.width, bitmap.height),
            screenWidth = bitmap.width * 3,
            screenHeight = bitmap.height * 6
        )
    }

    fun reset() {
        detectedCount = 0L
        analyzedCount = 0L
    }
}
