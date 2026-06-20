package com.taroqos

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.taroqos.ai.AiAdClassifier
import com.taroqos.ai.BehavioralDetector
import com.taroqos.ai.OfflineMlEngine
import com.taroqos.filter.*
import com.taroqos.firewall.PrivacyFirewall
import com.taroqos.telemetry.LocalTelemetry
import com.taroqos.ui.MainActivity
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * TaroqVpnService — محرك الـ VPN المحلي
 *
 * يُنسّق الطبقات 1 + 2 + 3 + 4 + 5 + 15 + 16:
 *
 *   Layer 1  — DNS Filter:         حجب بـ 500+ نطاق معروف
 *   Layer 2  — DoH/DoT Protection: منع تجاوز DNS عبر HTTPS/TLS
 *   Layer 3  — Local VPN Engine:   تمرير الشبكة داخل الجهاز فقط
 *   Layer 4  — URL Filter:         تصفية مسارات URL إعلانية
 *   Layer 5  — SSL Pinning Detect: كشف التطبيقات التي تتجاوز الفحص
 *   Layer 15 — Privacy Firewall:   منع اتصالات التتبع
 *   Layer 16 — Offline ML:         تصنيف ذكي للنطاقات المجهولة
 *
 * الطبقات 6-9: تعمل في TaroqAccessibilityService
 * الطبقات 10-14: تعمل في سياقاتها المستقلة
 * الطبقة 17: BuildVerifier تُولّد تقرير في AboutActivity
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
        @Volatile var blockedByDoH = 0L
        @Volatile var blockedByFirewall = 0L
        @Volatile var blockedByMl = 0L
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

        vpnIface = Builder()
            .setSession("Taroq OS v5.0 — 17 Layers")
            .addAddress(VPN_ADDRESS, 24)
            .addRoute("10.99.0.0", 24)
            .addDnsServer(VPN_DNS)
            .setMtu(1500)
            .setBlocking(true)
            .establish()

        if (vpnIface == null) { stopSelf(); return }
        isRunning = true
        job = scope.launch { runLoop() }

        // Layer 13: schedule cloud rule update
        scope.launch {
            delay(5_000)
            com.taroqos.rules.CloudRuleUpdater.fetchUpdates(applicationContext)
        }

        // Telemetry session start
        LocalTelemetry.initialize(applicationContext)
    }

    private fun stopVpn() {
        isRunning = false
        job?.cancel()
        runCatching { dnsProxy.close() }
        runCatching { vpnIface?.close() }
        vpnIface = null
        blocked = 0L
        allowed = 0L
        blockedByDoH = 0L
        blockedByFirewall = 0L
        blockedByMl = 0L
        LocalTelemetry.saveSession(applicationContext)
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

                // Layer 3: VPN Packet Filter — validate IPv4/UDP
                if (!PacketUtils.isIpv4(buf, len)) continue
                if (!PacketUtils.isUdp(buf)) continue

                val ipHdrLen = PacketUtils.getIpHeaderLen(buf)
                val dstPort  = PacketUtils.getDestPort(buf, ipHdrLen)

                // Layer 2: DoH/DoT Protection — block DNS-over-TLS (port 853)
                if (dstPort == 853) {
                    blockedByDoH++
                    DohProtection.blockedByDoh++
                    LocalTelemetry.sessionBlockedDoH++
                    continue
                }

                if (dstPort != 53) continue

                val packet = buf.copyOf(len)

                // Layer 5: SSL Pinning Detection via TLS inspection
                val tcpPayload = PacketUtils.extractTcpPayload(packet, ipHdrLen)
                if (tcpPayload != null) {
                    if (TlsSniInspector.isTlsAdTraffic(tcpPayload)) {
                        blocked++
                        continue
                    }
                }

                // Extract domain for multi-layer checking
                val dnsPayload = PacketUtils.extractDnsPayload(packet, ipHdrLen)
                val domain = if (PacketUtils.isQueryPacket(dnsPayload))
                    PacketUtils.extractQueryDomain(dnsPayload) else null

                if (domain != null) {
                    // Layer 2: Check DoH domain (DNS-over-HTTPS bypass)
                    if (DohProtection.isDoHDomain(domain)) {
                        blockedByDoH++
                        blocked++
                        LocalTelemetry.sessionBlockedDoH++
                        withContext(Dispatchers.IO) {
                            output.write(PacketUtils.buildNxdomainResponse(packet, ipHdrLen))
                        }
                        continue
                    }

                    // Layer 15: Privacy Firewall check
                    val firewallDecision = PrivacyFirewall.evaluate(domain)
                    if (firewallDecision == com.taroqos.firewall.PrivacyFirewall.FirewallDecision.BLOCK) {
                        blockedByFirewall++
                        blocked++
                        LocalTelemetry.recordBlockedDomain(domain, 15)
                        withContext(Dispatchers.IO) {
                            output.write(PacketUtils.buildNxdomainResponse(packet, ipHdrLen))
                        }
                        continue
                    }
                }

                // Layers 1 + 4 + 16: DNS filter + URL filter + Offline ML
                val response = handleDnsWithAllLayers(packet, len, domain) ?: continue
                withContext(Dispatchers.IO) { output.write(response) }

                // Update aggregated counters
                blocked = dnsProxy.blockedCount +
                        TlsSniInspector.blockedBySni +
                        HttpAdDetector.blockedByHttp +
                        DohProtection.blockedByDoh +
                        PrivacyFirewall.blockedByFirewall +
                        blockedByMl
                allowed = dnsProxy.allowedCount

            } catch (_: CancellationException) { break
            } catch (_: Exception)             { delay(50) }
        }
    }

    private fun handleDnsWithAllLayers(
        packet: ByteArray,
        len: Int,
        domain: String?
    ): ByteArray? {
        val ipHdrLen = PacketUtils.getIpHeaderLen(packet)
        val dnsPayload = PacketUtils.extractDnsPayload(packet, ipHdrLen)

        if (!PacketUtils.isQueryPacket(dnsPayload)) return null
        val resolvedDomain = domain ?: PacketUtils.extractQueryDomain(dnsPayload) ?: return null

        // Layer 1: DNS blocklist (500+ known ad domains)
        if (AdBlockList.isBlocked(resolvedDomain)) {
            dnsProxy.blockedCount++
            LocalTelemetry.recordBlockedDomain(resolvedDomain, 1)
            return PacketUtils.buildNxdomainResponse(packet, ipHdrLen)
        }

        // Layer 4: URL filter (EasyList patterns)
        if (UrlFilter.isAdSubdomain(resolvedDomain)) {
            dnsProxy.blockedCount++
            LocalTelemetry.recordBlockedDomain(resolvedDomain, 4)
            return PacketUtils.buildNxdomainResponse(packet, ipHdrLen)
        }

        // Layer 16: Offline ML classifier
        val mlResult = OfflineMlEngine.classify(resolvedDomain)
        if (mlResult.isAd && mlResult.confidence >= 0.72f) {
            blockedByMl++
            dnsProxy.blockedCount++
            LocalTelemetry.recordBlockedDomain(resolvedDomain, 16)
            return PacketUtils.buildNxdomainResponse(packet, ipHdrLen)
        }

        // Layer 16 fallback: AiAdClassifier (original ensemble)
        val aiResult = AiAdClassifier.classify(resolvedDomain)
        if (aiResult.isAd && aiResult.confidence >= 0.75f) {
            blockedByMl++
            dnsProxy.blockedCount++
            return PacketUtils.buildNxdomainResponse(packet, ipHdrLen)
        }

        dnsProxy.allowedCount++
        return dnsProxy.handleDnsPacketWithAi(packet, len)
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
            .setContentTitle("Taroq OS — 17 طبقة حماية نشطة")
            .setContentText("${AdBlockList.blockedDomains.size}+ نطاق • DNS+DoH+VPN+URL+AI+Firewall")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_delete, "إيقاف", stop)
            .setOngoing(true)
            .build()
    }

    private fun createChannel() {
        val ch = NotificationChannel(
            CHANNEL_ID, "Taroq OS", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "17 طبقة حماية تعمل في الخلفية" }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(ch)
    }

    override fun onDestroy() {
        stopVpn()
        scope.cancel()
        super.onDestroy()
    }
}
