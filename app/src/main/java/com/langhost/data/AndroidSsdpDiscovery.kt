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
import com.langhost.data.model.ProtocolEvidence
import com.langhost.data.model.ProtocolEvidenceSource

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

        val socket = DatagramSocket(null)
        runSsdpTransportSession(
            results = results,
            deadlineNanos = System.nanoTime() + WINDOW_NANOS,
            maxHosts = MAX_HOSTS,
            maxPacketBytes = MAX_PACKET_BYTES,
            prepare = {
                socket.reuseAddress = false
                socket.bind(InetSocketAddress(0))
                activeNetwork.bindSocket(socket)
                socket.soTimeout = RECEIVE_TIMEOUT_MS
            },
            sendSearch = {
                socket.send(DatagramPacket(request, request.size, target, SSDP_PORT))
            },
            receive = { buffer ->
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                SsdpReceivedDatagram(packet.address, packet.data, packet.length)
            },
            close = socket::close
        )
        DiscoverySourceResult(
            value = results.snapshot(),
            status = results.status(),
            protocolEvidenceByIp = results.protocolEvidenceByIp()
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


/** A received datagram view; its byte array may be reused by the transport. */
internal data class SsdpReceivedDatagram(
    val address: InetAddress,
    val data: ByteArray,
    val length: Int
)

/**
 * Platform-independent receive-loop boundary. The injected clock and receiver
 * make timeout, malformed-packet and mid-window failure behavior deterministic in tests.
 */
internal suspend fun runSsdpReceiveLoop(
    results: SsdpDiscoveryResults,
    deadlineNanos: Long,
    maxHosts: Int = 256,
    maxPacketBytes: Int = 4_096,
    nowNanos: () -> Long = System::nanoTime,
    receive: (ByteArray) -> SsdpReceivedDatagram
) {
    val buffer = ByteArray(maxPacketBytes)
    while (nowNanos() < deadlineNanos && results.snapshot().size < maxHosts) {
        currentCoroutineContext().ensureActive()
        try {
            val packet = receive(buffer)
            val host = packet.address as? Inet4Address ?: continue
            val evidence = SsdpResponseParser.parseResponse(packet.data, packet.length)
            if (evidence != null) host.hostAddress?.let { results.addResponder(it, evidence) }
        } catch (_: SocketTimeoutException) {
            // A receive timeout is expected while waiting for unicast M-SEARCH replies.
            continue
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            results.markOperationFailed()
            return
        }
    }
}

/**
 * Owns the complete transport lifecycle. Keeping preparation, send, receive
 * and close explicit lets unit tests verify ordering and cancellation cleanup
 * without pretending to exercise Android's Network.bindSocket implementation.
 */
internal suspend fun runSsdpTransportSession(
    results: SsdpDiscoveryResults,
    deadlineNanos: Long,
    maxHosts: Int = 256,
    maxPacketBytes: Int = 4_096,
    prepare: () -> Unit,
    sendSearch: () -> Unit,
    receive: (ByteArray) -> SsdpReceivedDatagram,
    close: () -> Unit
) {
    try {
        prepare()
        sendSearch()
        runSsdpReceiveLoop(
            results = results,
            deadlineNanos = deadlineNanos,
            maxHosts = maxHosts,
            maxPacketBytes = maxPacketBytes,
            receive = receive
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        results.markOperationFailed()
    } finally {
        runCatching(close)
    }
}


internal object SsdpResponseParser {
    fun isValidResponse(packet: ByteArray, length: Int): Boolean =
        parseResponse(packet, length) != null

    fun parseResponse(packet: ByteArray, length: Int): ProtocolEvidence? {
        if (length <= 0 || length > packet.size) return null
        val lines = String(packet, 0, length, Charsets.US_ASCII)
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        val status = lines.firstOrNull().orEmpty().split(Regex("\\s+"))
        if (
            status.size < 2 ||
            (status[0] != "HTTP/1.1" && status[0] != "HTTP/1.0") ||
            status[1] != "200"
        ) return null

        val headers = lines.drop(1).mapNotNull { line ->
            val separator = line.indexOf(':')
            if (separator <= 0) null else line.substring(0, separator).trim().lowercase() to
                line.substring(separator + 1).trim()
        }.toMap()
        val deviceType = headers["st"]?.takeIf { it.isNotBlank() } ?: return null
        val usn = headers["usn"].orEmpty()
        val location = headers["location"].orEmpty()
        if (usn.isBlank() && location.isBlank()) return null

        // LOCATION is intentionally never retained: URLs may contain credentials or tokens.
        return ProtocolEvidence(
            source = ProtocolEvidenceSource.SSDP,
            friendlyName = ProtocolEvidence.sanitize(usn.substringBefore("::").removePrefix("uuid:"), 72),
            server = ProtocolEvidence.sanitize(headers["server"], 96),
            deviceType = ProtocolEvidence.sanitize(deviceType, 96)
        )
    }
}
