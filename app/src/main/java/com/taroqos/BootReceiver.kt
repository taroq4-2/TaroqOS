package com.taroqos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (VpnService.prepare(context) == null) {
                context.startForegroundService(
                    Intent(context, TaroqVpnService::class.java).apply {
                        action = TaroqVpnService.ACTION_START
                    }
                )
            }
        }
    }
}
