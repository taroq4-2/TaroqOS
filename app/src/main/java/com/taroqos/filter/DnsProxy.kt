package com.taroqos.filter

import android.net.VpnService
import com.taroqos.ai.AiAdClassifier
import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Layer 1 + 2 — DNS Proxy
 *
 * يعالج كل استعلام DNS عبر سلسلة من الطبقات:
 *   1. DNS Blocklist         — قائمة 500+ نطاق معروف (Layer 1)
 *   2. DoH Domain Detection  — كشف خوادم DoH (Layer 2)
 *   3. AI/ML Classifier      — للنطاقات غير المعروفة (Layer 16)
 *   4. Forward to upstream   — إرسال للـ DNS الحقيقي إن لم يُحجب
 */
class DnsProxy(private val vpnService: VpnService) : Closeable {

    companion object {
        private val UPSTREAM_DNS = InetAddress.getByName("8.8.8.8")
        private const val DNS_PORT   = 53
        private const val TIMEOUT_MS = 3000
    }

    private val socket: DatagramSocket = DatagramSocket().also { vpnService.protect(it) }

    var blockedCount = 0L
    var allowedCount = 0L

    fun handleDnsPacketWithAi(packet: ByteArray, len: Int): ByteArray? {
        val ipHeaderLen = PacketUtils.getIpHeaderLen(packet)
        val dnsPayload  = PacketUtils.extractDnsPayload(packet, ipHeaderLen)

        if (!PacketUtils.isQueryPacket(dnsPayload)) return null

        val domain = PacketUtils.extractQueryDomain(dnsPayload) ?: return null

        // Layer 1: DNS blocklist
        if (AdBlockList.isBlocked(domain)) {
            blockedCount++
            return PacketUtils.buildNxdomainResponse(packet, ipHeaderLen)
        }

        // Layer 2: DoH domain bypass check
        if (DohProtection.isDoHDomain(domain)) {
            blockedCount++
            return PacketUtils.buildNxdomainResponse(packet, ipHeaderLen)
        }

        // Layer 16: AI/ML classifier for unknown domains
        val aiResult = AiAdClassifier.classify(domain)
        if (aiResult.isAd && aiResult.confidence >= 0.75f) {
            blockedCount++
            return PacketUtils.buildNxdomainResponse(packet, ipHeaderLen)
        }

        allowedCount++
        return forwardToUpstream(packet, ipHeaderLen, dnsPayload)
    }

    fun handleDnsPacket(packet: ByteArray, len: Int): ByteArray? =
        handleDnsPacketWithAi(packet, len)

    private fun forwardToUpstream(
        originalPacket: ByteArray,
        ipHeaderLen: Int,
        dnsPayload: ByteArray
    ): ByteArray? {
        return try {
            socket.soTimeout = TIMEOUT_MS
            socket.send(DatagramPacket(dnsPayload, dnsPayload.size, UPSTREAM_DNS, DNS_PORT))

            val respBuf = ByteArray(4096)
            val respPacket = DatagramPacket(respBuf, respBuf.size)
            socket.receive(respPacket)

            val responsePayload = respBuf.copyOf(respPacket.length)
            val srcIp   = PacketUtils.getDestIp(originalPacket)
            val dstIp   = PacketUtils.getSrcIp(originalPacket)
            val srcPort = PacketUtils.getDestPort(originalPacket, ipHeaderLen)
            val dstPort = PacketUtils.getSrcPort(originalPacket, ipHeaderLen)
            PacketUtils.buildUdpIpPacket(srcIp, dstIp, srcPort, dstPort, responsePayload)
        } catch (_: Exception) { null }
    }

    override fun close() { runCatching { socket.close() } }
}
