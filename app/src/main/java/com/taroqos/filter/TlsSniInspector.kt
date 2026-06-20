package com.taroqos.filter

/**
 * Layer 3 — TLS/HTTPS SNI Inspection
 *
 * يستخرج اسم الخادم (Server Name Indication) من حزمة TLS ClientHello
 * دون فك تشفير البيانات.
 *
 * الـ SNI يُرسَل بنص واضح (plaintext) في بداية مصافحة TLS
 * قبل أي تشفير، مما يتيح لنا معرفة الموقع المطلوب
 * حتى مع اتصالات HTTPS.
 *
 * بنية TLS ClientHello:
 *   - Record Layer:    [0x16][ver][len]
 *   - Handshake:       [0x01][len24][ClientHello...]
 *   - SNI Extension:   type=0x0000, list_type=0x00
 */
object TlsSniInspector {

    @Volatile var blockedBySni = 0L
    @Volatile var inspectedCount = 0L

    /**
     * يفحص حزمة TCP payload ويستخرج SNI إن وجد
     * @param payload bytes of the TCP payload
     * @return اسم الخادم أو null إذا لم يكن TLS ClientHello
     */
    fun extractSni(payload: ByteArray): String? {
        if (payload.size < 43) return null

        // TLS Record: ContentType=22 (Handshake)
        if (payload[0] != 0x16.toByte()) return null
        // TLS Version: 3.x
        if (payload[1] != 0x03.toByte()) return null
        // Handshake Type: ClientHello = 1
        if (payload[5] != 0x01.toByte()) return null

        try {
            var pos = 5 + 4  // skip: handshake_type(1) + length(3)

            // Skip: client_version(2) + random(32)
            pos += 2 + 32

            if (pos >= payload.size) return null

            // Session ID
            val sessionLen = payload[pos].toInt() and 0xFF
            pos += 1 + sessionLen

            if (pos + 2 > payload.size) return null

            // Cipher Suites
            val cipherLen = ((payload[pos].toInt() and 0xFF) shl 8) or
                    (payload[pos + 1].toInt() and 0xFF)
            pos += 2 + cipherLen

            if (pos >= payload.size) return null

            // Compression Methods
            val compLen = payload[pos].toInt() and 0xFF
            pos += 1 + compLen

            if (pos + 2 > payload.size) return null

            // Extensions Length
            val extLen = ((payload[pos].toInt() and 0xFF) shl 8) or
                    (payload[pos + 1].toInt() and 0xFF)
            pos += 2

            val extEnd = pos + extLen

            // Iterate through extensions
            while (pos + 4 <= extEnd && pos + 4 <= payload.size) {
                val extType = ((payload[pos].toInt() and 0xFF) shl 8) or
                        (payload[pos + 1].toInt() and 0xFF)
                val extDataLen = ((payload[pos + 2].toInt() and 0xFF) shl 8) or
                        (payload[pos + 3].toInt() and 0xFF)
                pos += 4

                // SNI Extension type = 0x0000
                if (extType == 0x0000) {
                    return parseSniExtension(payload, pos, extDataLen)
                }
                pos += extDataLen
            }
        } catch (_: Exception) {}

        return null
    }

    private fun parseSniExtension(
        payload: ByteArray,
        offset: Int,
        length: Int
    ): String? {
        var pos = offset
        if (pos + 2 > payload.size || pos + 2 > offset + length) return null

        // Server Name List Length
        val listLen = ((payload[pos].toInt() and 0xFF) shl 8) or
                (payload[pos + 1].toInt() and 0xFF)
        pos += 2

        if (pos + listLen > payload.size) return null

        // Server Name Type: 0x00 = host_name
        if (pos >= payload.size || payload[pos] != 0x00.toByte()) return null
        pos += 1

        if (pos + 2 > payload.size) return null

        // Host Name Length
        val nameLen = ((payload[pos].toInt() and 0xFF) shl 8) or
                (payload[pos + 1].toInt() and 0xFF)
        pos += 2

        if (pos + nameLen > payload.size) return null

        return String(payload, pos, nameLen, Charsets.US_ASCII)
    }

    /**
     * يفحص إن كانت الحزمة TLS وإن كان SNI يخص خادم إعلانات
     */
    fun isTlsAdTraffic(payload: ByteArray): Boolean {
        val sni = extractSni(payload) ?: return false
        inspectedCount++
        val isAd = AdBlockList.isBlocked(sni)
        if (isAd) blockedBySni++
        return isAd
    }
}
