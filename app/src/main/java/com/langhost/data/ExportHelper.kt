package com.langhost.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.langhost.data.model.Device
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportHelper {

    fun exportDevicesAsText(context: Context, devices: List<Device>): Intent? {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault())
            val timeStr = sdf.format(Date())
            val fileName = "langhost_devices_$timeStr.txt"

            val content = buildString {
                appendLine("LanGhost — отчёт по устройствам")
                appendLine("Дата: ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())}")
                appendLine("Всего устройств: ${devices.size}")
                appendLine("=".repeat(40))
                appendLine()

                devices.forEachIndexed { index, device ->
                    appendLine("${index + 1}. ${device.displayName}")
                    appendLine("   IP:      ${device.ip}")
                    appendLine("   MAC:     ${device.mac.ifBlank { "—" }}")
                    appendLine("   Vendor:  ${device.vendor.ifBlank { "—" }}")
                    appendLine("   Host:    ${device.hostname.ifBlank { "—" }}")
                    appendLine("   OS:      ${device.osGuess.ifBlank { "—" }}")
                    if (device.openPorts.isNotEmpty()) {
                        appendLine("   Ports:   ${device.openPorts.joinToString(", ")}")
                    }
                    if (device.notes.isNotBlank()) {
                        appendLine("   Notes:   ${device.notes}")
                    }
                    appendLine()
                }
            }

            val file = File(context.cacheDir, fileName)
            file.writeText(content)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "LanGhost — устройства")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            null
        }
    }

    internal fun escapeCsvCell(value: String): String {
        val first = value.firstOrNull()
        val dangerous = first == '=' || first == '+' || first == '@' ||
            first == '\\t' || first == '\\r' ||
            (first == '-' && value.getOrNull(1)?.isDigit() != true)
        val safe = if (dangerous) "'$value" else value
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }

    fun exportDevicesAsCsv(context: Context, devices: List<Device>): Intent? {
        return try {
            val timeStr = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())
            val fileName = "langhost_devices_$timeStr.csv"

            val content = buildString {
                appendLine("IP,MAC,Hostname,Vendor,OS,Nickname,Notes,OpenPorts,IsGateway,IsLocal")
                devices.forEach { d ->
                    appendLine(
                        listOf(
                            d.ip,
                            d.mac,
                            d.hostname,
                            d.vendor,
                            d.osGuess,
                            d.nickname,
                            d.notes.replace(",", ";"),
                            d.openPorts.joinToString(";"),
                            d.isGateway.toString(),
                            d.isLocalDevice.toString()
                        ).joinToString(",") { escapeCsvCell(it) }
                    )
                }
            }

            val file = File(context.cacheDir, fileName)
            file.writeText(content)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "LanGhost devices CSV")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            null
        }
    }
}
