package com.langhost.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanCoverageTest {
    @Test
    fun calculatesCoverageWithoutRoundingUp() {
        assertEquals(1, ScanCoverage(checkedAddresses = 1024, totalUsableAddresses = 65534).coveragePercent)
        assertEquals(100, ScanCoverage(checkedAddresses = 10, totalUsableAddresses = 10).coveragePercent)
    }

    @Test
    fun handlesUnknownAndEmptyRanges() {
        assertEquals(0, ScanCoverage().coveragePercent)
        assertEquals(0, ScanCoverage(checkedAddresses = 4, totalUsableAddresses = 0).coveragePercent)
    }
}
