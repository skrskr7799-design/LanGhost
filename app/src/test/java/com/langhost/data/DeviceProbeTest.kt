package com.langhost.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DeviceProbeTest {

    @Test
    fun parsesNetBiosServerNameFromNbstatAnswer() {
        val response = nbstatResponse(listOf("NAS-SERVER" to 0x0020))

        assertEquals("NAS-SERVER", DeviceProbe.parseNetBiosResponse(response, response.size))
    }

    @Test
    fun ignoresGroupNamesAndPrefersServerName() {
        val response = nbstatResponse(
            listOf(
                "WORKGROUP" to 0x8000,
                "WORKSTATION" to 0x0000,
                "NAS-SERVER" to 0x0020
            )
        )

        assertEquals("NAS-SERVER", DeviceProbe.parseNetBiosResponse(response, response.size))
    }

    @Test
    fun rejectsTruncatedAndNonNbstatResponses() {
        val response = nbstatResponse(listOf("NAS-SERVER" to 0x0020))

        assertTrue(DeviceProbe.parseNetBiosResponse(response, 20).isEmpty())
        assertTrue(DeviceProbe.parseNetBiosResponse(ByteArray(12), 12).isEmpty())
    }

    private fun nbstatResponse(names: List<Pair<String, Int>>): ByteArray {
        val encodedName = "CKAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA".toByteArray(Charsets.US_ASCII)
        val question = ByteBuffer.allocate(1 + encodedName.size + 1 + 4).order(ByteOrder.BIG_ENDIAN).apply {
            put(0x20)
            put(encodedName)
            put(0)
            putShort(0x0021)
            putShort(0x0001)
        }.array()

        val answerDataLength = 1 + names.size * 18
        val answer = ByteBuffer.allocate(12 + answerDataLength).order(ByteOrder.BIG_ENDIAN).apply {
            putShort(0xC00C.toShort()) // compressed owner name
            putShort(0x0021) // NBSTAT
            putShort(0x0001) // IN
            putInt(0) // TTL
            putShort(answerDataLength.toShort())
            put(names.size.toByte())
            names.forEach { (name, flags) ->
                val bytes = name.toByteArray(Charsets.US_ASCII).copyOf(15)
                put(bytes)
                put(if (name == "NAS-SERVER") 0x20.toByte() else 0x00.toByte())
                putShort(flags.toShort())
            }
        }.array()

        return ByteBuffer.allocate(12 + question.size + answer.size).order(ByteOrder.BIG_ENDIAN).apply {
            putShort(0x0001)
            putShort(0x8400.toShort())
            putShort(1)
            putShort(1)
            putShort(0)
            putShort(0)
            put(question)
            put(answer)
        }.array()
    }

    @Test
    fun plainHttpTargetMustBePrivateIpv4() {
        assertTrue(DeviceProbe.isPrivateIpv4("192.168.1.20"))
        assertTrue(DeviceProbe.isPrivateIpv4("10.0.0.2"))
        assertTrue(DeviceProbe.isPrivateIpv4("172.16.0.1"))
        assertTrue(DeviceProbe.isPrivateIpv4("172.31.255.254"))
        assertTrue(DeviceProbe.isPrivateIpv4("169.254.1.1"))
        assertFalse(DeviceProbe.isPrivateIpv4("8.8.8.8"))
        assertFalse(DeviceProbe.isPrivateIpv4("172.32.0.1"))
        assertFalse(DeviceProbe.isPrivateIpv4("192.169.1.1"))
        assertFalse(DeviceProbe.isPrivateIpv4("localhost"))
        assertFalse(DeviceProbe.isPrivateIpv4("192.168.1.999"))
    }
}
