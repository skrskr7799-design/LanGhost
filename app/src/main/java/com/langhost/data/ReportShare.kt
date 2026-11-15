package com.langhost.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.langhost.data.model.Device
import com.langhost.data.model.NetworkInfo
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportShare {

    fun buildTextReport(network: NetworkInfo, devices: List<Device>, score: Int?): String {
        val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
        return buildString {
            appendLine("LanGhost — отчёт о сети")
            appendLine(date)
            appendLine("—".repeat(28))
            appendLine("SSID: ${network.ssid}")
            appendLine("BSSID: ${network.bssid}")
            appendLine("Шлюз: ${network.gateway}")
            appendLine("Канал: ${network.channel} · ${network.signalDbm} dBm")
            if (score != null) appendLine("Оценка безопасности: $score/100")
            appendLine("Устройств: ${devices.size} (онлайн ${devices.count { it.isOnline }})")
            appendLine()
            devices.take(30).forEach { d ->
                appendLine("• ${d.displayName} — ${d.ip}${if (d.mac.isNotBlank()) " (${d.mac})" else ""}")
            }
        }
    }

    fun shareText(context: Context, text: String): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "LanGhost — отчёт о сети")
            putExtra(Intent.EXTRA_TEXT, text)
        }
    }

    fun shareImageCard(context: Context, network: NetworkInfo, devices: List<Device>, score: Int?): Intent? {
        return try {
            val bmp = renderCard(network, devices, score)
            val file = ShareFileStore.create(context, "langhost_report_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "LanGhost — отчёт")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun renderCard(network: NetworkInfo, devices: List<Device>, score: Int?): Bitmap {
        val w = 1080
        val h = 1400
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.parseColor("#0D0D12"))

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 64f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B0B0C0")
            textSize = 40f
        }
        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#5B8CFF")
            textSize = 48f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        var y = 120f
        c.drawText("LanGhost", 80f, y, titlePaint)
        y += 80f
        c.drawText(network.ssid.ifBlank { "Сеть" }, 80f, y, accent)
        y += 70f
        c.drawText("Канал ${network.channel} · ${network.signalDbm} dBm", 80f, y, body)
        y += 55f
        c.drawText("Шлюз ${network.gateway}", 80f, y, body)
        y += 55f
        if (score != null) {
            c.drawText("Безопасность: $score/100", 80f, y, body)
            y += 55f
        }
        c.drawText("Устройств: ${devices.size} · онлайн ${devices.count { it.isOnline }}", 80f, y, body)
        y += 90f

        val line = Paint().apply { color = Color.parseColor("#2A2A3A"); strokeWidth = 2f }
        c.drawLine(80f, y, w - 80f, y, line)
        y += 60f

        devices.take(12).forEach { d ->
            c.drawText("${d.displayName.take(22)}  ${d.ip}", 80f, y, body)
            y += 50f
        }

        val footer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#606070")
            textSize = 32f
        }
        c.drawText(SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date()), 80f, h - 60f, footer)
        return bmp
    }
}
