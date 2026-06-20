package com.taroqos.ui

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.taroqos.R
import com.taroqos.TaroqVpnService
import com.taroqos.accessibility.AutoHideEngine
import com.taroqos.accessibility.TaroqAccessibilityService
import com.taroqos.ai.BehavioralDetector
import com.taroqos.ai.OfflineMlEngine
import com.taroqos.ai.VisualAiDetector
import com.taroqos.databinding.ActivityMainBinding
import com.taroqos.filter.AdBlockList
import com.taroqos.filter.DohProtection
import com.taroqos.filter.HttpAdDetector
import com.taroqos.filter.SslPinningDetector
import com.taroqos.filter.TlsSniInspector
import com.taroqos.filter.UrlFilter
import com.taroqos.firewall.PrivacyFirewall
import com.taroqos.rules.AppRulesManager
import com.taroqos.rules.CloudRuleUpdater
import com.taroqos.telemetry.LocalTelemetry
import com.taroqos.verification.BuildVerifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    private val vpnLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) launchVpn()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100
            )
        }

        b.btnToggle.setOnClickListener {
            if (TaroqVpnService.isRunning) stopVpn() else askVpnPermission()
        }

        b.btnAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        b.btnEnableAccessibility.setOnClickListener {
            openAccessibilitySettings()
        }

        b.tvDomainCount.text = "${AdBlockList.blockedDomains.size}+ نطاق محجوب"
        b.tvVersion.text = "v${BuildVerifier.APP_VERSION} • ${BuildVerifier.LAYER_COUNT} طبقة"

        lifecycleScope.launch {
            while (isActive) { refresh(); delay(800) }
        }
    }

    private fun askVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) vpnLauncher.launch(intent) else launchVpn()
    }

    private fun launchVpn() {
        startForegroundService(
            Intent(this, TaroqVpnService::class.java).apply {
                action = TaroqVpnService.ACTION_START
            }
        )
    }

    private fun stopVpn() {
        startService(
            Intent(this, TaroqVpnService::class.java).apply {
                action = TaroqVpnService.ACTION_STOP
            }
        )
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun isAccessibilityEnabled(): Boolean {
        val service = "${packageName}/${TaroqAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            if (splitter.next().equals(service, ignoreCase = true)) return true
        }
        return false
    }

    private fun refresh() {
        val vpnOn    = TaroqVpnService.isRunning
        val accessOn = isAccessibilityEnabled()

        // ─── Shield status ───────────────────────────────────────────────────
        b.statusDot.setBackgroundResource(
            if (vpnOn) R.drawable.dot_green else R.drawable.dot_red
        )
        b.tvStatusLabel.text = if (vpnOn) "الحماية مفعّلة — 17 طبقة" else "الحماية متوقفة"
        b.btnToggle.text     = if (vpnOn) "إيقاف الحماية" else "تفعيل الحماية"

        val tintColor = if (vpnOn) getColor(R.color.stop_red) else getColor(R.color.start_green)
        b.btnToggle.backgroundTintList = ColorStateList.valueOf(tintColor)
        b.tvShieldIcon.text = if (vpnOn) "🛡️" else "🔓"

        // ─── Total stats ─────────────────────────────────────────────────────
        if (vpnOn) {
            val totalBlocked = TaroqVpnService.blocked +
                    TlsSniInspector.blockedBySni +
                    HttpAdDetector.blockedByHttp +
                    TaroqAccessibilityService.hiddenByAccessibility +
                    DohProtection.blockedByDoh +
                    PrivacyFirewall.blockedByFirewall
            b.tvBlockedCount.text = "$totalBlocked"
            b.tvAllowedCount.text = "${TaroqVpnService.allowed}"
        } else {
            b.tvBlockedCount.text = "—"
            b.tvAllowedCount.text = "—"
        }

        // ─── 17 Layer statuses ───────────────────────────────────────────────
        fun st(active: Boolean, detail: String = "") =
            if (active) "🟢 نشط${if (detail.isNotEmpty()) " ($detail)" else ""}"
            else "⚪ متوقف"
        fun stErr(msg: String) = "🔴 $msg"

        b.tvLayer01Status.text = st(vpnOn, "${TaroqVpnService.blocked} محجوب")
        b.tvLayer02Status.text = st(vpnOn, "${DohProtection.blockedByDoh} DoH")
        b.tvLayer03Status.text = st(vpnOn, "VPN نشط")
        b.tvLayer04Status.text = st(vpnOn, "${UrlFilter.blockedByUrl} URL")
        b.tvLayer05Status.text = st(vpnOn, "${SslPinningDetector.detectedPinningCount} كشف")
        b.tvLayer06Status.text = if (accessOn) st(true, "${TaroqAccessibilityService.eventsProcessed} حدث")
                                 else stErr("يحتاج تفعيل")
        b.tvLayer07Status.text = if (accessOn) st(true, "${com.taroqos.accessibility.OcrEngine.textExtractionCount} استخراج")
                                 else stErr("يحتاج تفعيل")
        b.tvLayer08Status.text = if (accessOn) st(true, "${com.taroqos.accessibility.UiTreeAnalyzer.adNodesFound} عقدة")
                                 else stErr("يحتاج تفعيل")
        b.tvLayer09Status.text = if (accessOn) st(true, "${AutoHideEngine.hiddenCount} مخفي")
                                 else stErr("يحتاج تفعيل")
        b.tvLayer10Status.text = st(vpnOn || accessOn, "${VisualAiDetector.detectedCount} كشف")
        b.tvLayer11Status.text = st(vpnOn || accessOn, "${BehavioralDetector.detectedBehavioral} نمط")
        b.tvLayer12Status.text = st(true, "${AppRulesManager.loadedRules} قاعدة")
        b.tvLayer13Status.text = if (CloudRuleUpdater.lastUpdateTime > 0)
            st(true, "${CloudRuleUpdater.totalDownloadedDomains} نطاق")
            else "🔵 جاهز"
        b.tvLayer14Status.text = if (LocalTelemetry.isEnabled) st(true, "${LocalTelemetry.getTotalBlocked()} مسجّل")
                                  else "⚫ معطّل (افتراضي)"
        b.tvLayer15Status.text = st(vpnOn, "${PrivacyFirewall.blockedByFirewall} محجوب")
        b.tvLayer16Status.text = st(vpnOn || accessOn, "${OfflineMlEngine.adClassifications} تصنيف")
        b.tvLayer17Status.text = st(true, "مفتوح المصدر")

        // ─── Accessibility prompt ─────────────────────────────────────────────
        b.cardAccessibilityPrompt.visibility =
            if (!accessOn) android.view.View.VISIBLE else android.view.View.GONE
    }
}
