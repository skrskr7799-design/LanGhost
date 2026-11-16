package com.langhost.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.langhost.data.model.Device
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportHelper {

    fun exportDevicesAsText(context: Context, devices: List<Device>, maskIdentifiers: Boolean = false): Intent? {
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
                    appendLine("   IP:      ${PrivacyFormatter.ip(device.ip, maskIdentifiers)}")
                    appendLine("   MAC:     ${PrivacyFormatter.mac(device.mac.ifBlank { "—" }, maskIdentifiers)}")
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

            val file = ShareFileStore.create(context, fileName)
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
        val formulaCandidate = value.dropWhile { it.isWhitespace() || it == '\uFEFF' }
        val firstCandidate = formulaCandidate.firstOrNull()
        val negativeNumber = firstCandidate == '-' &&
            formulaCandidate.drop(1).toDoubleOrNull() != null
        val dangerous = first == '\t' || first == '\r' || first == '\n' ||
            firstCandidate == '=' || firstCandidate == '+' || firstCandidate == '@' ||
            (firstCandidate == '-' && !negativeNumber)
        val safe = if (dangerous) "'$value" else value
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }

    fun exportDevicesAsCsv(context: Context, devices: List<Device>, maskIdentifiers: Boolean = false): Intent? {
        return try {
            val timeStr = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())
            val fileName = "langhost_devices_$timeStr.csv"

            val content = buildString {
                appendLine("IP,MAC,Hostname,Vendor,OS,Nickname,Notes,OpenPorts,IsGateway,IsLocal")
                devices.forEach { d ->
                    appendLine(
                        listOf(
                            PrivacyFormatter.ip(d.ip, maskIdentifiers),
                            PrivacyFormatter.mac(d.mac, maskIdentifiers),
                            d.hostname,
                            d.vendor,
                            d.osGuess,
                            d.nickname,
                            d.notes,
                            d.openPorts.joinToString(";"),
                            d.isGateway.toString(),
                            d.isLocalDevice.toString()
                        ).joinToString(",") { escapeCsvCell(it) }
                    )
                }
            }

            val file = ShareFileStore.create(context, fileName)
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
