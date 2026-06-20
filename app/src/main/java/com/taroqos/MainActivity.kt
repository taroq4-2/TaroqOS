package com.taroqos

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.taroqos.databinding.ActivityMainBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            launchVpnService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                100
            )
        }

        binding.btnToggle.setOnClickListener {
            if (TaroqVpnService.isRunning) {
                stopVpn()
            } else {
                requestVpnPermission()
            }
        }

        updateUi()
        startUiRefresh()
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            launchVpnService()
        }
    }

    private fun launchVpnService() {
        val intent = Intent(this, TaroqVpnService::class.java).apply {
            action = TaroqVpnService.ACTION_START
        }
        startForegroundService(intent)
        lifecycleScope.launch {
            delay(500)
            updateUi()
        }
    }

    private fun stopVpn() {
        val intent = Intent(this, TaroqVpnService::class.java).apply {
            action = TaroqVpnService.ACTION_STOP
        }
        startService(intent)
        lifecycleScope.launch {
            delay(500)
            updateUi()
        }
    }

    private fun startUiRefresh() {
        lifecycleScope.launch {
            while (true) {
                updateUi()
                delay(1000)
            }
        }
    }

    private fun updateUi() {
        val running = TaroqVpnService.isRunning
        binding.tvStatus.text = if (running) "نشط — الإعلانات محجوبة ✓" else "متوقف"
        binding.tvStatusDot.setBackgroundResource(
            if (running) R.drawable.dot_green else R.drawable.dot_red
        )
        binding.btnToggle.text = if (running) "إيقاف الحماية" else "تشغيل الحماية"
        binding.btnToggle.setBackgroundColor(
            if (running)
                getColor(R.color.color_stop)
            else
                getColor(R.color.color_start)
        )
    }
}
