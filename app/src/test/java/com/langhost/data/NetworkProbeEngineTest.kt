package com.langhost.data

import com.langhost.data.model.PortAuditStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger

class NetworkProbeEngineTest {
    @Test
    fun securityProbeRecordsOpenPortAndChecksNetworkScope() = runBlocking {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            val checkpoints = AtomicInteger()
            val engine = NetworkProbeEngine(
                scanTimeoutMs = { 500 },
                ensureCurrentNetworkScope = { scope ->
                    assertEquals("test-network", scope)
                    checkpoints.incrementAndGet()
                }
            )

            val evidence = engine.scanSecurityPortEvidence(
                ip = "127.0.0.1",
                ports = listOf(server.localPort),
                expectedNetworkScope = "test-network"
            )

            assertEquals(1, evidence.size)
            assertEquals(server.localPort, evidence.single().port)
            assertEquals(PortAuditStatus.OPEN, evidence.single().status)
            assertTrue("Expected batch and per-probe scope checkpoints", checkpoints.get() >= 2)
        }
    }

    @Test(expected = CancellationException::class)
    fun securityProbePropagatesNetworkScopeCancellation() = runBlocking {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            val engine = NetworkProbeEngine(
                scanTimeoutMs = { 500 },
                ensureCurrentNetworkScope = { throw CancellationException("Network changed") }
            )

            engine.scanSecurityPortEvidence(
                ip = "127.0.0.1",
                ports = listOf(server.localPort),
                expectedNetworkScope = "old-network"
            )
        }
    }

    @Test
    fun manualProbeReturnsOnlyOpenPortsAndIgnoresInvalidDuplicates() = runBlocking {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            val engine = NetworkProbeEngine(
                scanTimeoutMs = { 500 },
                ensureCurrentNetworkScope = {}
            )

            val result = engine.scanPorts(
                ip = "127.0.0.1",
                ports = listOf(server.localPort, server.localPort, 0, 65536),
                expectedNetworkScope = "test-network"
            )

            assertEquals(listOf(server.localPort), result)
        }
    }
}
