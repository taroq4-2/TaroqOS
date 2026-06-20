package com.taroqos

import java.nio.ByteBuffer

object SniFilter {

    fun extractSni(data: ByteArray, offset: Int, length: Int): String? {
        return try {
            if (length < 5) return null
            val recordType = data[offset].toInt() and 0xFF
            if (recordType != 0x16) return null
            val handshakeType = data[offset + 5].toInt() and 0xFF
            if (handshakeType != 0x01) return null
            var pos = offset + 5 + 4 + 2 + 32
            if (pos >= offset + length) return null
            val sessionIdLen = data[pos].toInt() and 0xFF
            pos += 1 + sessionIdLen
            if (pos + 2 >= offset + length) return null
            val cipherSuitesLen = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
            pos += 2 + cipherSuitesLen
            if (pos >= offset + length) return null
            val compressionLen = data[pos].toInt() and 0xFF
            pos += 1 + compressionLen
            if (pos + 2 >= offset + length) return null
            val extensionsLen = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
            pos += 2
            val extensionsEnd = pos + extensionsLen
            while (pos + 4 <= extensionsEnd && pos + 4 <= offset + length) {
                val extType = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
                val extLen = ((data[pos + 2].toInt() and 0xFF) shl 8) or (data[pos + 3].toInt() and 0xFF)
                pos += 4
                if (extType == 0x0000) {
                    var p = pos
                    val listLen = ((data[p].toInt() and 0xFF) shl 8) or (data[p + 1].toInt() and 0xFF)
                    p += 2
                    val nameType = data[p].toInt() and 0xFF
                    p += 1
                    if (nameType == 0x00) {
                        val nameLen = ((data[p].toInt() and 0xFF) shl 8) or (data[p + 1].toInt() and 0xFF)
                        p += 2
                        return String(data, p, nameLen, Charsets.US_ASCII)
                    }
                }
                pos += extLen
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
