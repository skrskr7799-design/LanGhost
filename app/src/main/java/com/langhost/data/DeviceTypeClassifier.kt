package com.langhost.data

import com.langhost.data.model.DeviceType

/**
 * Conservative, explainable device fingerprinting.
 *
 * Signals are weighted rather than treated as definitive. A type is emitted only
 * when the leading candidate has enough evidence and a useful margin over the runner-up.
 */
internal object DeviceTypeClassifier {
    data class Result(
        val type: DeviceType,
        val confidence: Int,
        val signals: List<String>
    )

    fun classify(
        hostname: String,
        vendor: String,
        ports: List<Int>,
        isGateway: Boolean,
        httpFingerprint: String = ""
    ): Result {
        if (isGateway) return Result(DeviceType.ROUTER, 100, listOf("gateway"))

        val host = hostname.lowercase()
        val maker = vendor.lowercase()
        val http = httpFingerprint.lowercase()
        val open = ports.toSet()
        val scores = DeviceType.values().associateWith { 0 }.toMutableMap()
        val evidence = DeviceType.values().associateWith { mutableListOf<String>() }

        fun add(type: DeviceType, points: Int, signal: String) {
            scores[type] = (scores[type] ?: 0) + points
            evidence.getValue(type).add(signal)
        }

        fun hostMatch(type: DeviceType, points: Int, signal: String, vararg tokens: String) {
            if (tokens.any { token -> host.contains(token) }) add(type, points, signal)
        }

        hostMatch(DeviceType.PHONE, 52, "hostname:phone",
            "iphone", "android", "pixel", "oneplus", "galaxy-s", "redmi", "phone")
        hostMatch(DeviceType.TABLET, 55, "hostname:tablet",
            "ipad", "tablet", "galaxy-tab", "lenovo-tab", "fire-tablet")
        hostMatch(DeviceType.TV, 55, "hostname:tv",
            "smart-tv", "smarttv", "webos", "bravia", "chromecast", "firetv", "roku", "apple-tv", "shield-tv")
        hostMatch(DeviceType.LAPTOP, 52, "hostname:laptop",
            "laptop", "notebook", "macbook", "thinkpad")
        hostMatch(DeviceType.PC, 48, "hostname:computer",
            "desktop", "workstation", "windows-pc", "gaming-pc")
        hostMatch(DeviceType.NAS, 58, "hostname:nas",
            "synology", "diskstation", "qnap", "terramaster", "nas")
        hostMatch(DeviceType.PRINTER, 58, "hostname:printer",
            "printer", "print-server", "laserjet", "officejet", "mfc-")
        hostMatch(DeviceType.CAMERA, 55, "hostname:camera",
            "ipcam", "camera", "webcam", "nvr", "hikvision", "dahua", "reolink")
        hostMatch(DeviceType.IOT, 48, "hostname:iot",
            "tuya", "tasmota", "shelly", "smart-plug", "smartbulb", "esp32", "esp8266")

        when {
            "synology" in maker || "qnap" in maker || "terramaster" in maker ->
                add(DeviceType.NAS, 48, "vendor:nas")
            listOf("hikvision", "dahua", "reolink", "axis communications").any { it in maker } ->
                add(DeviceType.CAMERA, 45, "vendor:camera")
            listOf("brother", "epson", "canon").any { it in maker } ->
                add(DeviceType.PRINTER, 38, "vendor:printer")
            listOf("mikrotik", "ubiquiti", "tp-link", "netgear", "asus", "cisco", "linksys", "d-link").any { it in maker } ->
                add(DeviceType.ROUTER, 24, "vendor:network")
        }

        if (9100 in open) add(DeviceType.PRINTER, 62, "port:9100")
        if (631 in open) add(DeviceType.PRINTER, 42, "port:631")
        if (515 in open) add(DeviceType.PRINTER, 30, "port:515")

        if (8008 in open || 8009 in open) add(DeviceType.TV, 48, "port:cast")
        if (7000 in open) add(DeviceType.TV, 22, "port:7000")

        if (5000 in open || 5001 in open) add(DeviceType.NAS, 34, "port:nas-web")
        if (2049 in open || 548 in open) add(DeviceType.NAS, 24, "port:file-sharing")

        if (554 in open || 8554 in open) add(DeviceType.CAMERA, 38, "port:rtsp")
        if (37777 in open || 8000 in open) add(DeviceType.CAMERA, 22, "port:camera-control")

        if (3389 in open) add(DeviceType.PC, 38, "port:rdp")
        if (5985 in open || 5986 in open) add(DeviceType.PC, 32, "port:winrm")
        if (445 in open || 139 in open) add(DeviceType.PC, 18, "port:smb")

        if (22 in open && (80 in open || 443 in open)) add(DeviceType.SERVER, 20, "port:ssh-web")
        if ("raspberry pi" in maker) add(DeviceType.SERVER, 28, "vendor:raspberry-pi")

        when {
            listOf("synology", "diskstation").any { it in http } ->
                add(DeviceType.NAS, 52, "http:synology")
            "qnap" in http ->
                add(DeviceType.NAS, 52, "http:qnap")
            listOf("hikvision", "dahua", "reolink").any { it in http } ->
                add(DeviceType.CAMERA, 48, "http:camera")
            listOf("webos", "samsung smart tv", "roku", "chromecast").any { it in http } ->
                add(DeviceType.TV, 42, "http:tv")
        }

        val ranked = scores.entries
            .filter { it.key != DeviceType.UNKNOWN && it.value > 0 }
            .sortedByDescending { it.value }
        val best = ranked.firstOrNull() ?: return Result(DeviceType.UNKNOWN, 0, emptyList())
        val runnerUp = ranked.getOrNull(1)?.value ?: 0
        val margin = best.value - runnerUp

        if (best.value < MINIMUM_SCORE || margin < MINIMUM_MARGIN) {
            return Result(DeviceType.UNKNOWN, 0, emptyList())
        }

        val confidence = (42 + best.value / 2 + margin / 4).coerceIn(45, 96)
        return Result(best.key, confidence, evidence.getValue(best.key).distinct())
    }

    private const val MINIMUM_SCORE = 40
    private const val MINIMUM_MARGIN = 12
}
