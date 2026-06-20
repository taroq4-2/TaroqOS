package com.taroqos.filter

import android.net.VpnService
import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class DnsProxy(private val vpnService: VpnService) : Closeable {

    companion object {
        private val UPSTREAM_DNS = InetAddress.getByName("8.8.8.8")
        private const val DNS_PORT   = 53
        private const val TIMEOUT_MS = 3000
    }

    // Reuse a single protected socket for all forwarded DNS queries (performance fix)
    private val socket: DatagramSocket = DatagramSocket().also { vpnService.protect(it) }

    var blockedCount = 0L
        private set
    var allowedCount = 0L
        private set

    fun handleDnsPacket(packet: ByteArray, len: Int): ByteArray? {
        val ipHeaderLen  = PacketUtils.getIpHeaderLen(packet)
        val dnsPayload   = PacketUtils.extractDnsPayload(packet, ipHeaderLen)

        if (!PacketUtils.isQueryPacket(dnsPayload)) return null

        val domain = PacketUtils.extractQueryDomain(dnsPayload) ?: return null

        return if (AdBlockList.isBlocked(domain)) {
            blockedCount++
            PacketUtils.buildNxdomainResponse(packet, ipHeaderLen)
        } else {
            allowedCount++
            forwardToUpstream(packet, ipHeaderLen, dnsPayload)
        }
    }

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

    override fun close() {
        runCatching { socket.close() }
    }
}
