package com.langhost.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

object DeviceProbe {

    /** NetBIOS Name Service query (UDP 137) */
    suspend fun netBiosName(ip: String): String = withContext(Dispatchers.IO) {
        withTimeoutOrNull(800L) {
            try {
                DatagramSocket().use { socket ->
                    socket.soTimeout = 700
                    val query = buildNetBiosQuery()
                    val addr = InetAddress.getByName(ip)
                    socket.send(DatagramPacket(query, query.size, addr, 137))
                    val buf = ByteArray(512)
                    val resp = DatagramPacket(buf, buf.size)
                    socket.receive(resp)
                    parseNetBiosResponse(buf, resp.length)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ""
            }
        } ?: ""
    }

    private fun buildNetBiosQuery(): ByteArray {
        // Minimal NBSTAT query for *
        val bb = ByteBuffer.allocate(50).order(ByteOrder.BIG_ENDIAN)
        bb.putShort(0x0001.toShort()) // transaction id
        bb.putShort(0x0000.toShort()) // flags
        bb.putShort(0x0001.toShort()) // questions
        bb.putShort(0x0000.toShort()) // answers
        bb.putShort(0x0000.toShort())
        bb.putShort(0x0000.toShort())
        // name: 32-byte encoded "*"
        bb.put(0x20)
        val name = "CKAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" // encoded *
        bb.put(name.toByteArray(Charsets.US_ASCII))
        bb.put(0x00)
        bb.putShort(0x0021.toShort()) // NBSTAT
        bb.putShort(0x0001.toShort()) // IN
        return bb.array().copyOf(bb.position())
    }

    internal fun parseNetBiosResponse(data: ByteArray, length: Int): String {
        if (length < 12 || length > data.size) return ""

        fun readU16(offset: Int): Int {
            if (offset < 0 || offset + 1 >= length) return -1
            return ((data[offset].toInt() and 0xFF) shl 8) or
                (data[offset + 1].toInt() and 0xFF)
        }

        fun skipDnsName(startOffset: Int): Int? {
            var offset = startOffset
            while (offset < length) {
                val labelLength = data[offset].toInt() and 0xFF
                if ((labelLength and 0xC0) == 0xC0) {
                    return if (offset + 1 < length) offset + 2 else null
                }
                if (labelLength == 0) return offset + 1
                if ((labelLength and 0xC0) != 0) return null
                offset++
                if (offset + labelLength > length) return null
                offset += labelLength
            }
            return null
        }

        val questionCount = readU16(4)
        val answerCount = readU16(6)
        if (questionCount < 0 || answerCount <= 0) return ""

        var offset = 12
        repeat(questionCount) {
            offset = skipDnsName(offset) ?: return ""
            offset += 4 // QTYPE + QCLASS
            if (offset > length) return ""
        }

        val candidates = mutableListOf<Pair<Int, String>>()
        repeat(answerCount) {
            offset = skipDnsName(offset) ?: return@repeat
            if (offset + 10 > length) return@repeat

            val type = readU16(offset)
            val recordClass = readU16(offset + 2)
            val dataLength = readU16(offset + 8)
            val dataStart = offset + 10
            val dataEnd = dataStart + dataLength
            if (dataLength < 0 || dataEnd > length) return@repeat

            if (type == 0x0021 && recordClass == 0x0001 && dataLength >= 1) {
                val nameCount = data[dataStart].toInt() and 0xFF
                var nameOffset = dataStart + 1
                repeat(nameCount) {
                    if (nameOffset + 18 > dataEnd) return@repeat
                    val rawName = data.copyOfRange(nameOffset, nameOffset + 15)
                    val name = String(rawName, Charsets.US_ASCII)
                        .trim()
                        .trimEnd('\u0000')
                    val suffix = data[nameOffset + 15].toInt() and 0xFF
                    val flags = readU16(nameOffset + 16)
                    val isGroup = (flags and 0x8000) != 0
                    if (!isGroup && name.isNotBlank() && name.any { it.isLetterOrDigit() }) {
                        // Prefer server (0x20) and workstation (0x00) names over other suffixes.
                        val priority = when (suffix) {
                            0x20 -> 0
                            0x00 -> 1
                            else -> 2
                        }
                        candidates += priority to name
                    }
                    nameOffset += 18
                }
            }
            offset = dataEnd
        }

        return candidates.minByOrNull { it.first }?.second.orEmpty()
    }
    /** Plain HTTP fingerprints are limited to private IPv4 targets; HTTPS retains platform TLS validation. */
    suspend fun httpFingerprint(ip: String, port: Int = 80, https: Boolean = false): String =
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(1500L) {
                try {
                    if (!https) {
                        require(isPrivateIpv4(ip)) { "Plain HTTP is allowed only for private IPv4 targets" }
                        fingerprintPlainHttp(ip, port)
                    } else {
                        fingerprintHttps(ip, port)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    ""
                }
            } ?: ""
        }

    internal fun isPrivateIpv4(value: String): Boolean {
        val parts = value.split(".")
        if (parts.size != 4 || parts.any { it.length > 1 && it.startsWith('0') }) return false
        val octets = parts.map { it.toIntOrNull()?.takeIf { n -> n in 0..255 } ?: return false }
        val first = octets[0]
        val second = octets[1]
        return first == 10 ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 168) ||
            (first == 169 && second == 254)
    }

    private fun fingerprintPlainHttp(ip: String, port: Int): String {
        require(port in 1..65535)
        Socket().use { socket ->
            socket.connect(InetSocketAddress(InetAddress.getByAddress(ip.split('.').map { it.toInt().toByte() }.toByteArray()), port), 800)
            socket.soTimeout = 800
            socket.getOutputStream().write(
                "GET / HTTP/1.1\r\nHost: $ip\r\nUser-Agent: LanGhost/0.4\r\nConnection: close\r\n\r\n"
                    .toByteArray(Charsets.US_ASCII)
            )
            val response = socket.getInputStream().use {
                readBounded(it, 32 * 1024, 800) { remaining -> socket.soTimeout = remaining }
            }
            return summarizeHttpResponse(response)
        }
    }

    private fun fingerprintHttps(ip: String, port: Int): String {
        require(port in 1..65535)
        val connection = (URL("https://$ip:$port/").openConnection() as HttpURLConnection).apply {
            connectTimeout = 800
            readTimeout = 800
            instanceFollowRedirects = false
            useCaches = false
            requestMethod = "GET"
            setRequestProperty("User-Agent", "LanGhost/0.4")
            setRequestProperty("Connection", "close")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..399) connection.inputStream else connection.errorStream
            val body = stream?.use {
                readBounded(it, 32 * 1024, 800) { remaining -> connection.readTimeout = remaining }
            }.orEmpty()
            val server = connection.getHeaderField("Server").orEmpty()
            return summarizeHttpResponse("HTTP $status\r\nServer: $server\r\n\r\n$body")
        } finally {
            connection.disconnect()
        }
    }

    /** Reads a bounded response with one total read budget, not a fresh timeout per chunk. */
    private fun readBounded(
        input: java.io.InputStream,
        limit: Int,
        timeoutMs: Int,
        updateReadTimeout: (Int) -> Unit
    ): String {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs.coerceAtLeast(1).toLong())
        var total = 0
        while (total < limit) {
            val remainingNanos = deadline - System.nanoTime()
            if (remainingNanos <= 0L) break
            val remainingMs = TimeUnit.NANOSECONDS.toMillis(remainingNanos).coerceAtLeast(1L).toInt()
            updateReadTimeout(remainingMs)
            val count = try {
                input.read(buffer, 0, minOf(buffer.size, limit - total))
            } catch (_: SocketTimeoutException) {
                break
            }
            if (count < 0) break
            output.write(buffer, 0, count)
            total += count
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun summarizeHttpResponse(response: String): String {
        val headerEnd = response.indexOf("\r\n\r\n")
        val headers = if (headerEnd >= 0) response.substring(0, headerEnd) else response
        val body = if (headerEnd >= 0) response.substring(headerEnd + 4) else ""
        val status = Regex("^HTTP/\\S+\\s+(\\d{3})", RegexOption.IGNORE_CASE)
            .find(headers)?.groupValues?.getOrNull(1)
        val server = Regex("(?im)^Server:\\s*(.+)$")
            .find(headers)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val title = Regex("<title[^>]*>([^<]+)</title>", RegexOption.IGNORE_CASE)
            .find(body)?.groupValues?.getOrNull(1)?.trim()?.take(60).orEmpty()
        return listOfNotNull(
            server.takeIf { it.isNotBlank() }?.let { "Server: $it" },
            title.takeIf { it.isNotBlank() }?.let { "Title: $it" },
            status?.let { "HTTP $it" }
        ).joinToString(" · ")
    }

    suspend fun enrichHostnamesAndFingerprint(ip: String, openPorts: List<Int>): Pair<String, String> {
        val netbios = netBiosName(ip)
        var http = ""
        when {
            80 in openPorts -> http = httpFingerprint(ip, 80, false)
            8080 in openPorts -> http = httpFingerprint(ip, 8080, false)
            443 in openPorts -> http = httpFingerprint(ip, 443, true)
            8443 in openPorts -> http = httpFingerprint(ip, 8443, true)
        }
        return netbios to http
    }

    suspend fun tcpPing(ip: String, port: Int = 80, timeoutMs: Int = 500): Long? =
        withContext(Dispatchers.IO) {
            val start = System.nanoTime()
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(ip, port), timeoutMs)
                }
                (System.nanoTime() - start) / 1_000_000
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
}
