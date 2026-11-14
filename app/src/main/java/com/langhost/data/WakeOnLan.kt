package com.langhost.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object WakeOnLan {

    /**
     * Send magic packet to wake device by MAC address.
     * @return true if packet was sent successfully
     */
    suspend fun send(macAddress: String, broadcastIp: String = "255.255.255.255", port: Int = 9): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val macBytes = parseMac(macAddress) ?: return@withContext false
                val packetData = ByteArray(6 + 16 * macBytes.size)

                // First 6 bytes: 0xFF
                for (i in 0 until 6) {
                    packetData[i] = 0xFF.toByte()
                }

                // Repeat MAC 16 times
                for (i in 0 until 16) {
                    System.arraycopy(macBytes, 0, packetData, 6 + i * macBytes.size, macBytes.size)
                }

                val address = InetAddress.getByName(broadcastIp)
                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    val packet = DatagramPacket(packetData, packetData.size, address, port)
                    socket.send(packet)
                }
                true
            } catch (e: Exception) {
                false
            }
        }

    private fun parseMac(mac: String): ByteArray? {
        val clean = mac.replace(":", "").replace("-", "").replace(".", "")
        if (clean.length != 12) return null
        return try {
            ByteArray(6) { i ->
                clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        } catch (e: Exception) {
            null
        }
    }
}
