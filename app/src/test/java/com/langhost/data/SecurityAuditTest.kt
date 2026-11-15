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
        assertFalse(report.serviceScanCompleted)
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
            scanCoverage = ScanCoverage("192.168.1.0/24", 254, 254),
            nearbyScanCompleted = true,
            serviceScanCompleted = true
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
            scanCoverage = coverage,
            nearbyScanCompleted = true,
            serviceScanCompleted = true
        )
        val openNeighborOnly = SecurityAudit.analyze(
            devices = emptyList(),
            currentSsid = "Home",
            currentBssid = "AA:AA:AA:AA:AA:AA",
            nearby = listOf(WifiNetwork("Cafe", "BB:BB:BB:BB:BB:BB", -50, 2412, 1, "Open")),
            currentSecurity = "WPA2",
            scanCoverage = coverage,
            nearbyScanCompleted = true,
            serviceScanCompleted = true
        )

        assertEquals(75, openCurrent.score)
        assertEquals(100, openNeighborOnly.score)
    }

    @Test
    fun reportsTelnetAndIncludesItInScore() {
        val device = Device(
            ip = "192.168.1.10",
            openPorts = listOf(22, 80, 443),
            auditedPorts = listOf(23),
            lastSeen = System.currentTimeMillis()
        )
        val report = SecurityAudit.analyze(
            devices = listOf(device),
            currentSsid = "Home",
            currentBssid = "AA:AA:AA:AA:AA:AA",
            nearby = emptyList(),
            currentSecurity = "WPA2",
            scanCoverage = ScanCoverage("192.168.1.0/24", 254, 254),
            nearbyScanCompleted = true,
            serviceScanCompleted = true
        )

        assertEquals(75, report.score)
        assertTrue(report.findings.any { it.title == "Доступен Telnet" })
    }


    @Test
    fun doesNotUseScanEvidenceFromAnotherNetwork() {
        val now = System.currentTimeMillis()
        val report = SecurityAudit.analyze(
            devices = emptyList(),
            currentSsid = "Office",
            currentBssid = "CC:CC:CC:CC:CC:CC",
            nearby = emptyList(),
            currentSecurity = "WPA2",
            scanCoverage = ScanCoverage(
                subnetCidr = "192.168.1.0/24",
                checkedAddresses = 254,
                totalUsableAddresses = 254,
                scannedAt = now,
                networkScope = "home-scope"
            ),
            nearbyScanCompleted = true,
            serviceScanCompleted = true,
            currentNetworkScope = "office-scope",
            nearbyScanTime = now,
            serviceScanTime = now,
            now = now
        )

        assertNull(report.score)
        assertFalse(report.deviceScanCompleted)
        assertTrue(report.findings.any { it.title == "Недостаточно данных для общей оценки" })
    }

    @Test
    fun expiresDeviceScanEvidenceAfterFiveMinutes() {
        val now = 1_000_000L
        val report = SecurityAudit.analyze(
            devices = listOf(Device(ip = "192.168.1.10", openPorts = listOf(23), lastSeen = System.currentTimeMillis())),
            currentSsid = "Home",
            currentBssid = "AA:AA:AA:AA:AA:AA",
            nearby = emptyList(),
            currentSecurity = "WPA2",
            scanCoverage = ScanCoverage(
                subnetCidr = "192.168.1.0/24",
                checkedAddresses = 254,
                totalUsableAddresses = 254,
                scannedAt = now - 6 * 60 * 1000L,
                networkScope = "home-scope"
            ),
            nearbyScanCompleted = true,
            serviceScanCompleted = true,
            currentNetworkScope = "home-scope",
            nearbyScanTime = now,
            serviceScanTime = now,
            now = now
        )

        assertNull(report.score)
        assertFalse(report.deviceScanCompleted)
        assertFalse(report.serviceScanCompleted)
        assertFalse(report.findings.any { it.title == "Доступен Telnet" })
    }

    @Test
    fun hidesPortAndNearbyFindingsWhenTheirEvidenceIsStale() {
        val now = 1_000_000L
        val report = SecurityAudit.analyze(
            devices = listOf(Device(ip = "192.168.1.10", openPorts = listOf(23), lastSeen = System.currentTimeMillis())),
            currentSsid = "Home",
            currentBssid = "AA:AA:AA:AA:AA:AA",
            nearby = listOf(WifiNetwork("Home", "BB:BB:BB:BB:BB:BB", -45, 2412, 1, "Open")),
            currentSecurity = "WPA3-SAE",
            scanCoverage = ScanCoverage(
                subnetCidr = "192.168.1.0/24",
                checkedAddresses = 254,
                totalUsableAddresses = 254,
                scannedAt = now,
                networkScope = "home-scope"
            ),
            nearbyScanCompleted = true,
            serviceScanCompleted = true,
            currentNetworkScope = "home-scope",
            nearbyScanTime = now - 6 * 60 * 1000L,
            serviceScanTime = now - 6 * 60 * 1000L,
            now = now
        )

        assertNull(report.score)
        assertTrue(report.deviceScanCompleted)
        assertFalse(report.serviceScanCompleted)
        assertTrue(report.securityMismatches.isEmpty())
        assertFalse(report.findings.any { it.title == "Доступен Telnet" })
    }

    @Test
    fun withholdsScoreWhenCurrentWifiSecurityIsUnknown() {
        val report = SecurityAudit.analyze(
            devices = emptyList(),
            currentSsid = "Home",
            currentBssid = "AA:AA:AA:AA:AA:AA",
            nearby = emptyList(),
            currentSecurity = "—",
            scanCoverage = ScanCoverage("192.168.1.0/24", 254, 254),
            nearbyScanCompleted = true,
            serviceScanCompleted = true
        )

        assertNull(report.score)
        assertTrue(report.findings.any { it.title == "Не удалось определить режим защиты текущей сети" })
    }

    @Test
    fun doesNotCalculateScoreUntilServiceScanCompletes() {
        val report = SecurityAudit.analyze(
            devices = emptyList(),
            currentSsid = "Home",
            currentBssid = "AA:AA:AA:AA:AA:AA",
            nearby = emptyList(),
            currentSecurity = "WPA2",
            scanCoverage = ScanCoverage("192.168.1.0/24", 254, 254),
            nearbyScanCompleted = true,
            serviceScanCompleted = false
        )

        assertNull(report.score)
        assertFalse(report.serviceScanCompleted)
        assertTrue(report.findings.any { it.title == "Проверка сервисов не выполнена или устарела" })
    }


    @Test
    fun ignoresGeneralDevicePortsWhenDedicatedServiceScanHasNoTelnetEvidence() {
        val device = Device(
            ip = "192.168.1.10",
            openPorts = listOf(23, 80),
            auditedPorts = emptyList(),
            lastSeen = System.currentTimeMillis()
        )
        val report = SecurityAudit.analyze(
            devices = listOf(device),
            currentSsid = "Home",
            currentBssid = "AA:AA:AA:AA:AA:AA",
            nearby = emptyList(),
            currentSecurity = "WPA2",
            scanCoverage = ScanCoverage("192.168.1.0/24", 254, 254),
            nearbyScanCompleted = true,
            serviceScanCompleted = true
        )

        assertEquals(100, report.score)
        assertFalse(report.findings.any { it.title == "Доступен Telnet" })
    }

    @Test
    fun doesNotCalculateScoreUntilNearbyScanCompletes() {
        val report = SecurityAudit.analyze(
            devices = emptyList(),
            currentSsid = "Home",
            currentBssid = "AA:AA:AA:AA:AA:AA",
            nearby = emptyList(),
            currentSecurity = "WPA2",
            scanCoverage = ScanCoverage("192.168.1.0/24", 254, 254),
            nearbyScanCompleted = false,
            serviceScanCompleted = true
        )

        assertNull(report.score)
    }

    @Test
    fun transitionModeIsCompatibleWithItsWpa2AndWpa3Members() {
        val nearby = listOf(
            WifiNetwork("Home", "BB:BB:BB:BB:BB:BB", -45, 2412, 1, "WPA2/WPA3")
        )
        val report = SecurityAudit.analyze(
            devices = emptyList(),
            currentSsid = "Home",
            currentBssid = "AA:AA:AA:AA:AA:AA",
            nearby = nearby,
            currentSecurity = "WPA2",
            scanCoverage = ScanCoverage("192.168.1.0/24", 254, 254),
            nearbyScanCompleted = true
        )

        assertTrue(report.securityMismatches.isEmpty())
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
