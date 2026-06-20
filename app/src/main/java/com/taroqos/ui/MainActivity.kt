package com.taroqos.ui

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.taroqos.R
import com.taroqos.TaroqVpnService
import com.taroqos.databinding.ActivityMainBinding
import com.taroqos.filter.AdBlockList
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    private val vpnLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { if (it.resultCode == Activity.RESULT_OK) launchVpn() }

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
        startService(Intent(this, TaroqVpnService::class.java).apply {
            action = TaroqVpnService.ACTION_STOP
        })
    }

    private fun refresh() {
        val on = TaroqVpnService.isRunning
        b.statusDot.setBackgroundResource(
            if (on) R.drawable.dot_green else R.drawable.dot_red
        )
        b.tvStatusLabel.text = if (on) "الحماية مفعّلة" else "الحماية متوقفة"
        b.btnToggle.text = if (on) "إيقاف الحماية" else "تفعيل الحماية"
        b.btnToggle.setBackgroundColor(
            if (on) getColor(R.color.stop_red) else getColor(R.color.start_green)
        )
        b.tvShieldIcon.text = if (on) "🛡️" else "🔓"
        if (on) {
            b.tvBlockedCount.text = "${TaroqVpnService.blocked}"
            b.tvAllowedCount.text = "${TaroqVpnService.allowed}"
        } else {
            b.tvBlockedCount.text = "—"
            b.tvAllowedCount.text = "—"
        }
    }
}
