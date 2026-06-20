package com.taroqos.filter

import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

object PacketUtils {

    fun isIpv4(buf: ByteArray, len: Int): Boolean = len >= 20 && ((buf[0].toInt() and 0xFF) shr 4) == 4

    fun getProtocol(buf: ByteArray): Int = buf[9].toInt() and 0xFF

    fun getIpHeaderLen(buf: ByteArray): Int = (buf[0].toInt() and 0x0F) * 4

    fun isUdp(buf: ByteArray): Boolean = getProtocol(buf) == 17

    fun getDestPort(buf: ByteArray, ipHeaderLen: Int): Int =
        ((buf[ipHeaderLen + 2].toInt() and 0xFF) shl 8) or (buf[ipHeaderLen + 3].toInt() and 0xFF)

    fun getSrcPort(buf: ByteArray, ipHeaderLen: Int): Int =
        ((buf[ipHeaderLen].toInt() and 0xFF) shl 8) or (buf[ipHeaderLen + 1].toInt() and 0xFF)

    fun getDestIp(buf: ByteArray): ByteArray = buf.copyOfRange(16, 20)

    fun getSrcIp(buf: ByteArray): ByteArray = buf.copyOfRange(12, 16)

    fun extractDnsPayload(buf: ByteArray, ipHeaderLen: Int): ByteArray {
        val udpPayloadStart = ipHeaderLen + 8
        return buf.copyOfRange(udpPayloadStart, buf.size)
    }

    fun buildUdpIpPacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val totalLen = 20 + 8 + payload.size
        val buf = ByteBuffer.allocate(totalLen).order(ByteOrder.BIG_ENDIAN)

        // IPv4 header
        buf.put(0x45.toByte())                                  // Version + IHL
        buf.put(0x00)                                           // DSCP/ECN
        buf.putShort(totalLen.toShort())                        // Total length
        buf.putShort(0x0000)                                    // ID
        buf.putShort(0x4000)                                    // Flags (Don't Fragment)
        buf.put(0x40)                                           // TTL = 64
        buf.put(0x11)                                           // Protocol = UDP
        buf.putShort(0x0000)                                    // Checksum placeholder
        buf.put(srcIp)                                          // Source IP
        buf.put(dstIp)                                          // Destination IP

        // Compute IP checksum
        val ipChecksum = computeChecksum(buf.array(), 0, 20)
        buf.putShort(10, ipChecksum)

        // UDP header
        buf.putShort(srcPort.toShort())
        buf.putShort(dstPort.toShort())
        buf.putShort((8 + payload.size).toShort())
        buf.putShort(0x0000)                                    // UDP checksum (optional)

        // Payload
        buf.put(payload)

        return buf.array()
    }

    private fun computeChecksum(buf: ByteArray, offset: Int, len: Int): Short {
        var sum = 0
        var i = offset
        while (i < offset + len - 1) {
            sum += ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            i += 2
        }
        if ((len and 1) != 0) {
            sum += (buf[offset + len - 1].toInt() and 0xFF) shl 8
        }
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toShort()
    }

    fun buildNxdomainResponse(queryPacket: ByteArray, ipHeaderLen: Int): ByteArray {
        val dnsPayload = extractDnsPayload(queryPacket, ipHeaderLen)
        val response = dnsPayload.copyOf()
        // Set QR=1, AA=1, RCODE=3 (NXDOMAIN)
        if (response.size >= 4) {
            response[2] = (0x81).toByte()
            response[3] = (0x83).toByte()
            // Zero out answer/authority/additional counts
            if (response.size >= 12) {
                response[6] = 0; response[7] = 0
                response[8] = 0; response[9] = 0
                response[10] = 0; response[11] = 0
            }
        }
        val srcIp = getDestIp(queryPacket)
        val dstIp = getSrcIp(queryPacket)
        val srcPort = getDestPort(queryPacket, ipHeaderLen)
        val dstPort = getSrcPort(queryPacket, ipHeaderLen)
        return buildUdpIpPacket(srcIp, dstIp, srcPort, dstPort, response)
    }

    fun extractQueryDomain(dnsPayload: ByteArray): String? {
        return try {
            if (dnsPayload.size < 12) return null
            val sb = StringBuilder()
            var pos = 12
            while (pos < dnsPayload.size) {
                val labelLen = dnsPayload[pos].toInt() and 0xFF
                if (labelLen == 0) break
                if (labelLen >= 0xC0) break
                if (sb.isNotEmpty()) sb.append('.')
                for (i in 1..labelLen) {
                    if (pos + i >= dnsPayload.size) return null
                    sb.append(dnsPayload[pos + i].toChar())
                }
                pos += labelLen + 1
            }
            sb.toString().lowercase().ifEmpty { null }
        } catch (_: Exception) { null }
    }

    fun isQueryPacket(dnsPayload: ByteArray): Boolean {
        if (dnsPayload.size < 4) return false
        return ((dnsPayload[2].toInt() and 0xFF) shr 7) == 0
    }
}
