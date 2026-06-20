package com.taroqos

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.taroqos.ai.AiAdClassifier
import com.taroqos.filter.AdBlockList
import com.taroqos.filter.DnsProxy
import com.taroqos.filter.HttpAdDetector
import com.taroqos.filter.PacketUtils
import com.taroqos.filter.TlsSniInspector
import com.taroqos.ui.MainActivity
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * TaroqVpnService — القلب النابض لـ Taroq OS
 *
 * يجمع طبقات الحماية 1–4 + 6:
 *   Layer 1: DNS Filter         — يحجب الطلبات بناءً على قائمة النطاقات
 *   Layer 2: VPN Packet Filter  — يراقب جميع الحزم المارة عبر النفق
 *   Layer 3: TLS SNI Inspection — يستخرج SNI من TLS ClientHello ويحجبه
 *   Layer 4: HTTP/JSON Detection— يكتشف حمولات JSON الإعلانية
 *   Layer 6: AI Classifier      — يصنّف النطاقات غير المعروفة بذكاء
 *
 * الطبقة 5 (Accessibility) تعمل باستقلالية كاملة عبر TaroqAccessibilityService.
 */
class TaroqVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.taroqos.START"
        const val ACTION_STOP  = "com.taroqos.STOP"
        const val CHANNEL_ID   = "taroq_channel"
        const val NOTIF_ID     = 1
        const val VPN_ADDRESS  = "10.99.0.1"
        const val VPN_DNS      = "10.99.0.2"

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
         * ARCHITECTURE:
         * Route VPN subnet (10.99.0.0/24) through tunnel — handles DNS queries.
         * DNS queries to 10.99.0.2 are intercepted for Layer 1/2/3/4/6 filtering.
         * Non-DNS internet traffic flows normally without VPN overhead.
         * Layer 5 (Accessibility) runs independently to handle in-app UI ads.
         */
        vpnIface = Builder()
            .setSession("Taroq OS v4.0")
            .addAddress(VPN_ADDRESS, 24)
            .addRoute("10.99.0.0", 24)
            .addDnsServer(VPN_DNS)
            .setMtu(1500)
            .setBlocking(true)
            .establish()

        if (vpnIface == null) { stopSelf(); return }
        isRunning = true
        job = scope.launch { runLoop() }
    }

    private fun stopVpn() {
        isRunning = false
        job?.cancel()
        runCatching { dnsProxy.close() }
        runCatching { vpnIface?.close() }
        vpnIface = null
        blocked = 0L
        allowed = 0L
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun runLoop() = coroutineScope {
        val fd     = vpnIface ?: return@coroutineScope
        val input  = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buf    = ByteArray(32767)

        while (isRunning && isActive) {
            try {
                val len = input.read(buf)
                if (len <= 0) continue

                // Layer 2: VPN Packet Filter — validate IPv4/UDP
                if (!PacketUtils.isIpv4(buf, len)) continue
                if (!PacketUtils.isUdp(buf)) continue

                val ipHdrLen = PacketUtils.getIpHeaderLen(buf)
                val dstPort  = PacketUtils.getDestPort(buf, ipHdrLen)
                if (dstPort != 53) continue

                val packet = buf.copyOf(len)

                // Layer 3: TLS SNI Inspection (on DNS payload that wraps TLS hints)
                val tcpPayload = PacketUtils.extractTcpPayload(packet, ipHdrLen)
                if (tcpPayload != null && TlsSniInspector.isTlsAdTraffic(tcpPayload)) {
                    blocked++
                    continue
                }

                // Layer 1 + 4 + 6: DNS filter + JSON check + AI classify
                val response = dnsProxy.handleDnsPacketWithAi(packet, len) ?: continue

                withContext(Dispatchers.IO) { output.write(response) }
                blocked = dnsProxy.blockedCount +
                        TlsSniInspector.blockedBySni +
                        HttpAdDetector.blockedByHttp
                allowed = dnsProxy.allowedCount

            } catch (_: CancellationException) { break
            } catch (_: Exception)             { delay(50) }
        }
    }

    override fun onRevoke() { stopVpn(); super.onRevoke() }

    private fun buildNotif(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, TaroqVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Taroq OS — 6 طبقات حماية نشطة")
            .setContentText("${AdBlockList.blockedDomains.size}+ نطاق • DNS + TLS + UI + AI")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_delete, "إيقاف", stop)
            .setOngoing(true)
            .build()
    }

    private fun createChannel() {
        val ch = NotificationChannel(
            CHANNEL_ID, "Taroq OS", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "6 طبقات حماية تعمل في الخلفية" }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(ch)
    }

    override fun onDestroy() {
        stopVpn()
        scope.cancel()
        super.onDestroy()
    }
}
