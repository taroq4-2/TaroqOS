package com.taroqos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // Only auto-start if VPN permission was already granted in a previous session
        // prepare() returns null if permission is already granted, non-null Intent otherwise
        val prepare = VpnService.prepare(context)
        if (prepare == null) {
            // Permission granted — safe to start
            context.startForegroundService(
                Intent(context, TaroqVpnService::class.java).apply {
                    action = TaroqVpnService.ACTION_START
                }
            )
        }
        // If prepare != null, user has not granted VPN permission yet — do nothing
        // The user must open the app manually to grant permission first
    }
}
