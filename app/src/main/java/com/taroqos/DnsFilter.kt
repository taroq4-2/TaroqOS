package com.taroqos

import java.net.DatagramPacket
import java.net.InetAddress
import java.nio.ByteBuffer

object DnsFilter {

    private const val DNS_PORT = 53

    fun isDnsQuery(buffer: ByteBuffer, offset: Int, length: Int): Boolean {
        if (length < 12) return false
        val flags = ((buffer.get(offset + 2).toInt() and 0xFF) shl 8) or
                (buffer.get(offset + 3).toInt() and 0xFF)
        val qr = (flags shr 15) and 1
        return qr == 0
    }

    fun extractQueryDomain(data: ByteArray, offset: Int, length: Int): String? {
        return try {
            if (length < 12) return null
            val sb = StringBuilder()
            var pos = offset + 12
            while (pos < offset + length) {
                val labelLen = data[pos].toInt() and 0xFF
                if (labelLen == 0) break
                if (labelLen >= 0xC0) break
                if (sb.isNotEmpty()) sb.append('.')
                for (i in 1..labelLen) {
                    if (pos + i >= offset + length) return null
                    sb.append(data[pos + i].toChar())
                }
                pos += labelLen + 1
            }
            sb.toString().ifEmpty { null }
        } catch (e: Exception) {
            null
        }
    }

    fun buildNxdomainResponse(queryData: ByteArray, queryOffset: Int, queryLength: Int): ByteArray {
        val response = queryData.copyOfRange(queryOffset, queryOffset + queryLength)
        response[2] = (0x81).toByte()
        response[3] = (0x83).toByte()
        response[6] = 0
        response[7] = 0
        response[8] = 0
        response[9] = 0
        response[10] = 0
        response[11] = 0
        return response
    }
}
