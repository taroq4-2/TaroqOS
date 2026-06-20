package com.taroqos

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.taroqos.filter.AdBlockList
import com.taroqos.filter.DnsProxy
import com.taroqos.filter.PacketUtils
import com.taroqos.ui.MainActivity
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream

class TaroqVpnService : VpnService() {

    companion object {
        const val ACTION_START  = "com.taroqos.START"
        const val ACTION_STOP   = "com.taroqos.STOP"
        const val CHANNEL_ID    = "taroq_channel"
        const val NOTIF_ID      = 1
        // VPN subnet — only DNS traffic routed here, internet unaffected
        const val VPN_ADDRESS   = "10.99.0.1"
        const val VPN_DNS       = "10.99.0.2"

        @Volatile var isRunning  = false
        @Volatile var blocked    = 0L
        @Volatile var allowed    = 0L
    }

    private var vpnIface: ParcelFileDescriptor? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var dnsProxy: DnsProxy

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP -> { stopVpn(); START_NOT_STICKY }
            else        -> { startVpn(); START_STICKY    }
        }
    }

    private fun startVpn() {
        if (isRunning) return
        dnsProxy = DnsProxy(this)
        createChannel()
        startForeground(NOTIF_ID, buildNotif())

        /*
         * CRITICAL ARCHITECTURE:
         * We route ONLY 10.99.0.0/24 (our VPN subnet) through the tunnel.
         * Regular internet traffic (0.0.0.0/0) is NOT intercepted → no internet cut.
         * Android sends DNS queries to VPN_DNS (10.99.0.2) which is in our subnet.
         * We intercept those DNS queries, filter ad domains, forward allowed ones.
         */
        vpnIface = Builder()
            .setSession("Taroq OS")
            .addAddress(VPN_ADDRESS, 24)
            .addRoute("10.99.0.0", 24)      // Only our subnet — NOT all traffic
            .addDnsServer(VPN_DNS)
            .setMtu(1500)
            .setBlocking(false)
            .establish()

        if (vpnIface == null) { stopSelf(); return }

        isRunning = true
        job = scope.launch { runLoop() }
    }

    private fun stopVpn() {
        isRunning = false
        job?.cancel()
        vpnIface?.close()
        vpnIface = null
        blocked = 0L
        allowed = 0L
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun runLoop() = coroutineScope {
        val fd    = vpnIface ?: return@coroutineScope
        val input  = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buf    = ByteArray(32767)

        while (isRunning && isActive) {
            try {
                val len = input.read(buf)
                if (len < 20) { delay(5); continue }

                if (!PacketUtils.isIpv4(buf, len)) continue
                if (!PacketUtils.isUdp(buf)) continue

                val ipHdrLen = PacketUtils.getIpHeaderLen(buf)
                val dstPort  = PacketUtils.getDestPort(buf, ipHdrLen)
                if (dstPort != 53) continue

                val packet = buf.copyOf(len)
                val response = dnsProxy.handleDnsPacket(packet, len)
                if (response != null) {
                    withContext(Dispatchers.IO) { output.write(response) }
                }
                blocked = dnsProxy.blockedCount
                allowed = dnsProxy.allowedCount

            } catch (_: CancellationException) { break
            } catch (_: Exception) { delay(10) }
        }
    }

    private fun buildNotif(): Notification {
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 0,
            Intent(this, TaroqVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Taroq OS — نشط")
            .setContentText("حماية مفعّلة • ${AdBlockList.blockedDomains.size}+ نطاق محجوب")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_delete, "إيقاف", stop)
            .setOngoing(true)
            .build()
    }

    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "Taroq OS",
            NotificationManager.IMPORTANCE_LOW).apply {
            description = "حاجب الإعلانات يعمل في الخلفية"
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
    }

    override fun onDestroy() { stopVpn(); scope.cancel(); super.onDestroy() }
}
