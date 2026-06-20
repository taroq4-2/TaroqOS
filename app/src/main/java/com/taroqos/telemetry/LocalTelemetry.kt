package com.taroqos.telemetry

import android.content.Context
import android.content.SharedPreferences

/**
 * Layer 14 — Local Telemetry (Disabled by Default)
 *
 * يجمع إحصائيات الأداء المحلية فقط — لا يُرسل أي شيء للخارج.
 *
 * ما يُجمع (محلياً فقط):
 *   - عدد النطاقات المحجوبة لكل طبقة
 *   - عدد الإعلانات المخفية في UI
 *   - وقت التشغيل الإجمالي
 *   - النطاقات الأكثر حجباً
 *
 * ما لا يُجمع أبداً:
 *   ❌ محتوى الطلبات أو الردود
 *   ❌ بيانات المستخدم الشخصية
 *   ❌ سجل التصفح
 *   ❌ أي شيء يُرسل خارج الجهاز
 *
 * مُعطَّل افتراضياً — يجب على المستخدم تفعيله يدوياً.
 */
object LocalTelemetry {

    private const val PREF_NAME = "taroq_telemetry"
    private const val KEY_ENABLED = "telemetry_enabled"
    private const val KEY_SESSION_START = "session_start"
    private const val KEY_TOTAL_BLOCKED = "total_blocked_all_time"
    private const val KEY_TOTAL_SESSIONS = "total_sessions"

    @Volatile var isEnabled = false
        private set

    // Session counters (in-memory)
    @Volatile var sessionBlockedDns = 0L
    @Volatile var sessionBlockedTls = 0L
    @Volatile var sessionBlockedHttp = 0L
    @Volatile var sessionBlockedUrl = 0L
    @Volatile var sessionHiddenUi = 0L
    @Volatile var sessionBlockedDoH = 0L
    @Volatile var sessionClassifiedByAi = 0L
    @Volatile var sessionStartTime = 0L

    // Top blocked domains (local tracking)
    private val domainHitMap = mutableMapOf<String, Int>()
    private const val MAX_TRACKED_DOMAINS = 100

    fun initialize(context: Context) {
        val prefs = getPrefs(context)
        isEnabled = prefs.getBoolean(KEY_ENABLED, false)  // DISABLED BY DEFAULT
        if (isEnabled) {
            sessionStartTime = System.currentTimeMillis()
            val sessions = prefs.getInt(KEY_TOTAL_SESSIONS, 0)
            prefs.edit().putInt(KEY_TOTAL_SESSIONS, sessions + 1).apply()
        }
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        isEnabled = enabled
        getPrefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) {
            sessionStartTime = System.currentTimeMillis()
        } else {
            resetSession()
        }
    }

    fun recordBlockedDomain(domain: String, layer: Int) {
        if (!isEnabled) return
        when (layer) {
            1, 2 -> sessionBlockedDns++
            3    -> sessionBlockedTls++
            4    -> sessionBlockedHttp++
            else -> sessionBlockedDns++
        }
        // Track domain frequency (capped)
        if (domainHitMap.size < MAX_TRACKED_DOMAINS) {
            domainHitMap[domain] = (domainHitMap[domain] ?: 0) + 1
        }
    }

    fun recordHiddenUi() {
        if (!isEnabled) return
        sessionHiddenUi++
    }

    fun recordAiClassification() {
        if (!isEnabled) return
        sessionClassifiedByAi++
    }

    fun getSessionSummary(): Map<String, Any> {
        val sessionDuration = if (sessionStartTime > 0)
            (System.currentTimeMillis() - sessionStartTime) / 1000 else 0L

        return mapOf(
            "enabled" to isEnabled,
            "session_duration_sec" to sessionDuration,
            "blocked_dns" to sessionBlockedDns,
            "blocked_tls" to sessionBlockedTls,
            "blocked_http" to sessionBlockedHttp,
            "blocked_url" to sessionBlockedUrl,
            "hidden_ui" to sessionHiddenUi,
            "blocked_doh" to sessionBlockedDoH,
            "ai_classified" to sessionClassifiedByAi,
            "total_blocked" to getTotalBlocked(),
        )
    }

    fun getTotalBlocked(): Long =
        sessionBlockedDns + sessionBlockedTls + sessionBlockedHttp +
        sessionBlockedUrl + sessionHiddenUi + sessionBlockedDoH

    fun getTopBlockedDomains(limit: Int = 10): List<Pair<String, Int>> {
        return domainHitMap.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key to it.value }
    }

    fun saveSession(context: Context) {
        if (!isEnabled) return
        val prefs = getPrefs(context)
        val allTime = prefs.getLong(KEY_TOTAL_BLOCKED, 0L)
        prefs.edit().putLong(KEY_TOTAL_BLOCKED, allTime + getTotalBlocked()).apply()
    }

    fun getAllTimeBlocked(context: Context): Long {
        return getPrefs(context).getLong(KEY_TOTAL_BLOCKED, 0L)
    }

    private fun resetSession() {
        sessionBlockedDns = 0L
        sessionBlockedTls = 0L
        sessionBlockedHttp = 0L
        sessionBlockedUrl = 0L
        sessionHiddenUi = 0L
        sessionBlockedDoH = 0L
        sessionClassifiedByAi = 0L
        domainHitMap.clear()
    }

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
