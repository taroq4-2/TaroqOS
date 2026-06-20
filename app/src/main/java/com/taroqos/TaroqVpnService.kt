package com.taroqos

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream

class TaroqVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.taroqos.START_VPN"
        const val ACTION_STOP  = "com.taroqos.STOP_VPN"
        const val CHANNEL_ID   = "taroq_vpn_channel"
        const val NOTIF_ID     = 1
        @Volatile var isRunning = false
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var serviceJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP -> { stopVpn(); START_NOT_STICKY }
            else        -> { startVpn(); START_STICKY }
        }
    }

    private fun startVpn() {
        if (isRunning) return
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())

        vpnInterface = Builder()
            .setSession("Taroq OS")
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("10.0.0.1")
            .setMtu(1500)
            .setBlocking(false)
            .establish()
            ?: run { stopSelf(); return }

        isRunning = true
        serviceJob = scope.launch { runPacketLoop() }
    }

    private fun stopVpn() {
        isRunning = false
        serviceJob?.cancel()
        vpnInterface?.close()
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun runPacketLoop() = coroutineScope {
        val vpnFd = vpnInterface ?: return@coroutineScope
        val input  = FileInputStream(vpnFd.fileDescriptor)
        val output = FileOutputStream(vpnFd.fileDescriptor)
        val buffer = ByteArray(32767)

        while (isRunning && isActive) {
            try {
                val len = input.read(buffer)
                if (len <= 0) { delay(10); continue }
                val processed = processPacket(buffer.copyOf(len), len)
                if (processed != null) output.write(processed)
            } catch (e: CancellationException) {
                break
            } catch (e: Exception) {
                if (!isRunning) break
                delay(50)
            }
        }
    }

    private fun processPacket(packet: ByteArray, length: Int): ByteArray? {
        if (length < 20) return packet
        val version      = (packet[0].toInt() and 0xFF) shr 4
        if (version != 4) return packet
        val protocol     = packet[9].toInt() and 0xFF
        val ipHeaderLen  = (packet[0].toInt() and 0x0F) * 4
        return when (protocol) {
            17   -> processUdp(packet, length, ipHeaderLen)
            6    -> processTcp(packet, length, ipHeaderLen)
            else -> packet
        }
    }

    private fun processUdp(packet: ByteArray, length: Int, ipHeaderLen: Int): ByteArray? {
        if (length < ipHeaderLen + 8) return packet
        val destPort = ((packet[ipHeaderLen + 2].toInt() and 0xFF) shl 8) or
                       (packet[ipHeaderLen + 3].toInt() and 0xFF)
        if (destPort != 53) return packet
        val dnsOffset = ipHeaderLen + 8
        val dnsLength = length - dnsOffset
        if (dnsLength < 12) return packet
        val domain = DnsFilter.extractQueryDomain(packet, dnsOffset, dnsLength) ?: return packet
        return if (AdBlockList.isBlocked(domain))
            buildBlockedDnsResponse(packet, length, ipHeaderLen, dnsOffset, dnsLength)
        else packet
    }

    private fun processTcp(packet: ByteArray, length: Int, ipHeaderLen: Int): ByteArray? {
        if (length < ipHeaderLen + 20) return packet
        val destPort = ((packet[ipHeaderLen + 2].toInt() and 0xFF) shl 8) or
                       (packet[ipHeaderLen + 3].toInt() and 0xFF)
        if (destPort != 443) return packet
        val tcpHeaderLen = ((packet[ipHeaderLen + 12].toInt() and 0xFF) shr 4) * 4
        val payloadOffset = ipHeaderLen + tcpHeaderLen
        val payloadLength = length - payloadOffset
        if (payloadLength < 5) return packet
        val sni = SniFilter.extractSni(packet, payloadOffset, payloadLength) ?: return packet
        return if (AdBlockList.isBlocked(sni)) null else packet
    }

    private fun buildBlockedDnsResponse(
        original: ByteArray, length: Int, ipHeaderLen: Int,
        dnsOffset: Int, dnsLength: Int
    ): ByteArray {
        val dnsResponse = DnsFilter.buildNxdomainResponse(original, dnsOffset, dnsLength)
        val response = original.copyOf(length)
        val srcIp = original.copyOfRange(12, 16)
        val dstIp = original.copyOfRange(16, 20)
        System.arraycopy(dstIp, 0, response, 12, 4)
        System.arraycopy(srcIp, 0, response, 16, 4)
        val srcPort = ((original[ipHeaderLen].toInt() and 0xFF) shl 8) or
                      (original[ipHeaderLen + 1].toInt() and 0xFF)
        val dstPort = ((original[ipHeaderLen + 2].toInt() and 0xFF) shl 8) or
                      (original[ipHeaderLen + 3].toInt() and 0xFF)
        response[ipHeaderLen]     = (dstPort shr 8).toByte()
        response[ipHeaderLen + 1] = dstPort.toByte()
        response[ipHeaderLen + 2] = (srcPort shr 8).toByte()
        response[ipHeaderLen + 3] = srcPort.toByte()
        System.arraycopy(dnsResponse, 0, response, dnsOffset, dnsLength)
        return response
    }

    private fun buildNotification(): Notification {
        val stopIntent  = Intent(this, TaroqVpnService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val openPending = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Taroq OS نشط")
            .setContentText("الإعلانات محجوبة")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_delete, "إيقاف", stopPending)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Taroq OS VPN",
            NotificationManager.IMPORTANCE_LOW).apply {
            description = "يعمل حاجب الإعلانات في الخلفية"
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    override fun onDestroy() {
        stopVpn()
        scope.cancel()
        super.onDestroy()
    }
}
