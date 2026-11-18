package com.langhost.data.model

/** Bounded, sanitized metadata learned from a local network discovery protocol. */
enum class ProtocolEvidenceSource {
    DNS_SD,
    SSDP
}

data class ProtocolEvidence(
    val source: ProtocolEvidenceSource,
    val serviceType: String = "",
    val serviceName: String = "",
    val friendlyName: String = "",
    val manufacturer: String = "",
    val model: String = "",
    val server: String = "",
    val deviceType: String = ""
) {
    companion object {
        private const val MAX_FIELD_LENGTH = 96

        /** Removes control characters and caps untrusted protocol strings before they reach UI/state. */
        fun sanitize(value: String?, maxLength: Int = MAX_FIELD_LENGTH): String {
            if (value.isNullOrBlank() || maxLength <= 0) return ""
            return value.asSequence()
                .filterNot { Character.isISOControl(it) }
                .joinToString("")
                .trim()
                .take(maxLength)
        }
    }
}
