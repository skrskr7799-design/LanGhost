package com.langhost.data

import com.langhost.data.model.Device
import com.langhost.data.model.ScanCoverage
import com.langhost.data.model.WifiNetwork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityAuditTest {

    @Test
    fun doesNotCalculateScoreBeforeDeviceScan() {
        val report = SecurityAudit.analyze(
            devices = emptyList(),
            currentSsid = "Home",
            currentBssid = "AA:AA:AA:AA:AA:AA",
            nearby = emptyList(),
            currentSecurity = "WPA3-SAE"
        )

        assertNull(report.score)
        assertFalse(report.deviceScanCompleted)
        assertTrue(report.findings.any { it.title == "Недостаточно данных для общей оценки" })
    }

    @Test
    fun sameSsidOnAnotherBssidIsNotAutomaticallyAnEvilTwin() {
        val nearby = listOf(
            WifiNetwork("Home", "BB:BB:BB:BB:BB:BB", -45, 2412, 1, "WPA2")
        )
        val report = SecurityAudit.analyze(
            devices = emptyList(),
            currentSsid = "Home",
            currentBssid = "AA:AA:AA:AA:AA:AA",
            nearby = nearby,
            currentSecurity = "WPA2",
            scanCoverage = ScanCoverage("192.168.1.0/24", 254, 254)
        )

        assertTrue(report.securityMismatches.isEmpty())
        assertEquals(100, report.score)
    }

    @Test
    fun reportsKnownSecurityMismatchAsLowConfidenceSignalNotEvilTwin() {
        val nearby = listOf(
            WifiNetwork("Home", "BB:BB:BB:BB:BB:BB", -45, 2412, 1, "WPA2")
        )
        val report = SecurityAudit.analyze(
            devices = emptyList(),
            currentSsid = "Home",
            currentBssid = "AA:AA:AA:AA:AA:AA",
            nearby = nearby,
            currentSecurity = "WPA3-SAE",
            scanCoverage = ScanCoverage("192.168.1.0/24", 254, 254),
            nearbyScanCompleted = true
        )

        assertEquals(1, report.securityMismatches.size)
        assertTrue(report.findings.any { it.severity == SecurityFinding.Severity.LOW })
        assertFalse(report.findings.any { it.title.contains("Evil Twin") })
    }

    @Test
    fun openCurrentNetworkReducesScoreButOpenNeighborDoesNot() {
        val coverage = ScanCoverage("192.168.1.0/24", 254, 254)
        val openCurrent = SecurityAudit.analyze(
            devices = emptyList(),
            currentSsid = "Home",
            currentBssid = "AA:AA:AA:AA:AA:AA",
            nearby = emptyList(),
            currentSecurity = "Open",
            scanCoverage = coverage
        )
        val openNeighborOnly = SecurityAudit.analyze(
            devices = emptyList(),
            currentSsid = "Home",
            currentBssid = "AA:AA:AA:AA:AA:AA",
            nearby = listOf(WifiNetwork("Cafe", "BB:BB:BB:BB:BB:BB", -50, 2412, 1, "Open")),
            currentSecurity = "WPA2",
            scanCoverage = coverage
        )

        assertEquals(75, openCurrent.score)
        assertEquals(100, openNeighborOnly.score)
    }

    @Test
    fun reportsTelnetAndIncludesItInScore() {
        val device = Device(
            ip = "192.168.1.10",
            openPorts = listOf(23),
            lastSeen = System.currentTimeMillis()
        )
        val report = SecurityAudit.analyze(
            devices = listOf(device),
            currentSsid = "Home",
            currentBssid = "AA:AA:AA:AA:AA:AA",
            nearby = emptyList(),
            currentSecurity = "WPA2",
            scanCoverage = ScanCoverage("192.168.1.0/24", 254, 254)
        )

        assertEquals(75, report.score)
        assertTrue(report.findings.any { it.title == "Доступен Telnet" })
    }

    @Test
    fun genericPskDoesNotTriggerUnsupportedSecurityMismatch() {
        val nearby = listOf(
            WifiNetwork("Home", "BB:BB:BB:BB:BB:BB", -45, 2412, 1, "WPA3")
        )
        val report = SecurityAudit.analyze(
            devices = emptyList(),
            currentSsid = "Home",
            currentBssid = "AA:AA:AA:AA:AA:AA",
            nearby = nearby,
            currentSecurity = "WPA-PSK",
            scanCoverage = ScanCoverage("192.168.1.0/24", 254, 254)
        )

        assertTrue(report.securityMismatches.isEmpty())
    }
}
