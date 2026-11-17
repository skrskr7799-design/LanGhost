package com.langhost.data

import android.net.Network
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import com.langhost.data.model.DiscoverySourceStatus

/**
 * Bounded SSDP/UPnP discovery. M-SEARCH replies are unicast to this socket,
 * so no multicast receive lock is needed. The socket is bound to the captured
 * active Android Network before sending anything.
 */
internal class AndroidSsdpDiscovery {
    suspend fun discover(activeNetwork: Network?): DiscoverySourceResult<Set<String>> = withContext(Dispatchers.IO) {
        if (activeNetwork == null) return@withContext DiscoverySourceResult(emptySet(), DiscoverySourceStatus.UNAVAILABLE)

        val results = SsdpDiscoveryResults()
        val request = M_SEARCH.toByteArray(Charsets.US_ASCII)
        val target = InetAddress.getByName(SSDP_MULTICAST_ADDRESS)

        DatagramSocket(null).use { socket ->
            try {
                socket.reuseAddress = false
                socket.bind(InetSocketAddress(0))
                activeNetwork.bindSocket(socket)
                socket.soTimeout = RECEIVE_TIMEOUT_MS
                socket.send(DatagramPacket(request, request.size, target, SSDP_PORT))

                val deadline = System.nanoTime() + WINDOW_NANOS
                val buffer = ByteArray(MAX_PACKET_BYTES)
                while (System.nanoTime() < deadline && addresses.size < MAX_HOSTS) {
                    currentCoroutineContext().ensureActive()
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    val host = packet.address as? Inet4Address ?: continue
                    if (SsdpResponseParser.isValidResponse(packet.data, packet.length)) {
                        host.hostAddress?.let(results::addResponder)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // SSDP is optional; failures must not discard TCP, ARP or DNS-SD evidence.
                results.markOperationFailed()
            }
        }
        DiscoverySourceResult(
            value = results.snapshot(),
            status = results.status()
        )
    }

    private companion object {
        const val SSDP_MULTICAST_ADDRESS = "239.255.255.250"
        const val SSDP_PORT = 1900
        const val RECEIVE_TIMEOUT_MS = 200
        const val MAX_PACKET_BYTES = 4_096
        const val MAX_HOSTS = 256
        const val WINDOW_NANOS = 1_500_000_000L
        const val M_SEARCH = "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: 239.255.255.250:1900\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: 1\r\n" +
            "ST: ssdp:all\r\n\r\n"
    }
}

internal object SsdpResponseParser {
    fun isValidResponse(packet: ByteArray, length: Int): Boolean {
        if (length <= 0 || length > packet.size) return false
        val lines = String(packet, 0, length, Charsets.US_ASCII)
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        val status = lines.firstOrNull().orEmpty().split(' ')
        if (
            status.size < 2 ||
            (status[0] != "HTTP/1.1" && status[0] != "HTTP/1.0") ||
            status[1] != "200"
        ) return false

        val headers = lines.drop(1).mapNotNull { line ->
            val separator = line.indexOf(':')
            if (separator <= 0) null else line.substring(0, separator).trim().lowercase() to
                line.substring(separator + 1).trim()
        }.toMap()
        return !headers["st"].isNullOrBlank() &&
            (!headers["usn"].isNullOrBlank() || !headers["location"].isNullOrBlank())
    }
}
