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
import com.taroqos.accessibility.TaroqAccessibilityService
import com.taroqos.ai.AiAdClassifier
import com.taroqos.databinding.ActivityMainBinding
import com.taroqos.filter.AdBlockList
import com.taroqos.filter.HttpAdDetector
import com.taroqos.filter.TlsSniInspector
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
        return TextUtils.SimpleStringSplitter(':').also { it.setString(enabledServices) }
            .asSequence().any { it.equals(service, ignoreCase = true) }
    }

    private fun refresh() {
        val vpnOn = TaroqVpnService.isRunning
        val accessOn = isAccessibilityEnabled()

        // Shield status
        b.statusDot.setBackgroundResource(
            if (vpnOn) R.drawable.dot_green else R.drawable.dot_red
        )
        b.tvStatusLabel.text = if (vpnOn) "الحماية مفعّلة" else "الحماية متوقفة"
        b.btnToggle.text     = if (vpnOn) "إيقاف الحماية" else "تفعيل الحماية"

        val tintColor = if (vpnOn) getColor(R.color.stop_red) else getColor(R.color.start_green)
        b.btnToggle.backgroundTintList = ColorStateList.valueOf(tintColor)
        b.tvShieldIcon.text = if (vpnOn) "🛡️" else "🔓"

        // Stats
        if (vpnOn) {
            val totalBlocked = TaroqVpnService.blocked +
                    TlsSniInspector.blockedBySni +
                    HttpAdDetector.blockedByHttp +
                    TaroqAccessibilityService.hiddenByAccessibility
            b.tvBlockedCount.text = "$totalBlocked"
            b.tvAllowedCount.text = "${TaroqVpnService.allowed}"
        } else {
            b.tvBlockedCount.text = "—"
            b.tvAllowedCount.text = "—"
        }

        // Layer statuses
        b.tvLayer1Status.text = if (vpnOn) "🟢 نشط (${TaroqVpnService.blocked})" else "⚪ متوقف"
        b.tvLayer2Status.text = if (vpnOn) "🟢 نشط" else "⚪ متوقف"
        b.tvLayer3Status.text = if (vpnOn) "🟢 نشط (${TlsSniInspector.blockedBySni})" else "⚪ متوقف"
        b.tvLayer4Status.text = if (vpnOn) "🟢 نشط (${HttpAdDetector.blockedByHttp})" else "⚪ متوقف"
        b.tvLayer5Status.text = when {
            accessOn -> "🟢 نشط (${TaroqAccessibilityService.hiddenByAccessibility})"
            else     -> "🔴 غير مفعّل"
        }
        // Layer 6 (AI) runs passively inside VPN
        b.tvLayer6Status.text = if (vpnOn) "🟢 نشط (${TlsSniInspector.inspectedCount} فحص)" else "⚪ متوقف"

        // Accessibility prompt
        b.cardAccessibilityPrompt.visibility = if (!accessOn)
            android.view.View.VISIBLE else android.view.View.GONE
    }
}
