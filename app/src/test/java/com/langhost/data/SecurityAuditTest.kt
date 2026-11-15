package com.langhost.data

import com.langhost.data.model.Device
import com.langhost.data.model.WifiNetwork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityAuditTest {

    @Test
    fun reportsTelnetAndPossibleEvilTwinWithTransparentScore() {
        val device = Device(
            ip = "192.168.1.10",
            openPorts = listOf(23),
            lastSeen = System.currentTimeMillis()
        )
        val nearby = listOf(
            WifiNetwork("Home", "AA:AA:AA:AA:AA:AA", -45, 2412, 1, "WPA2"),
            WifiNetwork("Guest", "BB:BB:BB:BB:BB:BB", -60, 2437, 6, "Open")
        )

        val report = SecurityAudit.analyze(
            devices = listOf(device),
            currentSsid = "Home",
            currentBssid = "00:00:00:00:00:00",
            nearby = nearby
        )

        assertEquals(2, report.findings.count { it.severity == SecurityFinding.Severity.HIGH })
        assertEquals(1, report.findings.count { it.severity == SecurityFinding.Severity.LOW })
        assertEquals(46, report.score)
        assertEquals(1, report.evilTwins.size)
    }

    @Test
    fun emptyEvidenceDoesNotInventSecurityFindings() {
        val report = SecurityAudit.analyze(emptyList(), "", "", emptyList())

        assertEquals(100, report.score)
        assertEquals(1, report.findings.size)
        assertEquals(SecurityFinding.Severity.INFO, report.findings.single().severity)
        assertTrue(report.evilTwins.isEmpty())
    }
}
