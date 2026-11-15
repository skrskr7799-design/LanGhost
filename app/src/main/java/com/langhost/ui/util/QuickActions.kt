package com.langhost.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.langhost.data.model.Device

object QuickActions {

    fun openHttpAdmin(context: Context, device: Device) {
        val ports = device.openPorts.toSet()
        val url = when {
            443 in ports -> "https://${device.ip}/"
            8443 in ports -> "https://${device.ip}:8443/"
            80 in ports -> "http://${device.ip}/"
            8080 in ports -> "http://${device.ip}:8080/"
            device.isGateway -> "http://${device.ip}/"
            else -> null
        }
        if (url == null) {
            Toast.makeText(context, "Нет известного HTTP-порта", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(context, "Не удалось открыть браузер", Toast.LENGTH_SHORT).show()
        }
    }

    fun openSsh(context: Context, device: Device) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("ssh://${device.ip}")))
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("ssh://${device.ip}:22")))
            } catch (_: Exception) {
                Toast.makeText(context, "Нет SSH-клиента. Установите Termius / JuiceSSH", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun openRdp(context: Context, device: Device) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("rdp://${device.ip}")))
        } catch (_: Exception) {
            Toast.makeText(context, "Нет RDP-клиента. Установите Microsoft RD Client", Toast.LENGTH_LONG).show()
        }
    }
}
