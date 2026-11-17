package com.langhost.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTypeClassifierTest {
    @Test
    fun gatewayIsClassifiedWithExplicitHighConfidence() {
        val result = DeviceTypeClassifier.classify("", "", emptyList(), isGateway = true)

        assertEquals(DeviceType.ROUTER, result.type)
        assertEquals(100, result.confidence)
        assertEquals(listOf("gateway"), result.signals)
    }

    @Test
    fun combinesHostnameVendorAndServicePorts() {
        val phone = DeviceTypeClassifier.classify("iPhone-Roman", "Apple", emptyList(), false)
        val nas = DeviceTypeClassifier.classify("diskstation", "Synology", listOf(5001, 445), false)
        val printer = DeviceTypeClassifier.classify("", "Brother", listOf(9100), false)
        val television = DeviceTypeClassifier.classify("", "", listOf(8008), false)

        assertEquals(DeviceType.PHONE, phone.type)
        assertTrue(phone.confidence >= 45)
        assertEquals(DeviceType.NAS, nas.type)
        assertEquals(DeviceType.PRINTER, printer.type)
        assertEquals(DeviceType.TV, television.type)
    }

    @Test
    fun weakVendorOrGenericPortsDoNotForceAType() {
        val vendorOnly = DeviceTypeClassifier.classify("", "TP-Link", emptyList(), false)
        val smbOnly = DeviceTypeClassifier.classify("", "", listOf(445), false)
        val noSignals = DeviceTypeClassifier.classify("unknown-host", "Unknown", emptyList(), false)

        assertEquals(DeviceType.UNKNOWN, vendorOnly.type)
        assertEquals(DeviceType.UNKNOWN, smbOnly.type)
        assertEquals(DeviceType.UNKNOWN, noSignals.type)
        assertEquals(0, noSignals.confidence)
    }

    @Test
    fun contradictoryHostnameSignalsRemainUnknown() {
        val result = DeviceTypeClassifier.classify("iphone-tv", "", emptyList(), false)

        assertEquals(DeviceType.UNKNOWN, result.type)
        assertEquals(0, result.confidence)
    }

    @Test
    fun deviceSpecificHttpFingerprintCanIdentifyNasOrCamera() {
        val nas = DeviceTypeClassifier.classify("", "", emptyList(), false, "Synology DiskStation")
        val camera = DeviceTypeClassifier.classify("", "", emptyList(), false, "Hikvision Web Components")

        assertEquals(DeviceType.NAS, nas.type)
        assertEquals(DeviceType.CAMERA, camera.type)
    }
}
