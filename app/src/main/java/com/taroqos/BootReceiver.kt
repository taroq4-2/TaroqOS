package com.taroqos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val vpnIntent = VpnService.prepare(context)
            if (vpnIntent == null) {
                val serviceIntent = Intent(context, TaroqVpnService::class.java).apply {
                    action = TaroqVpnService.ACTION_START
                }
                context.startForegroundService(serviceIntent)
            }
        }
    }
}
