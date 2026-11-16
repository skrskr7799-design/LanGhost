package com.langhost.data

/**
 * Presentation-only masking for network identifiers.
 * Raw values remain untouched for discovery, diagnostics and network actions.
 */
object PrivacyFormatter {
    fun ip(value: String, enabled: Boolean): String {
        if (!enabled || value.isBlank() || value == "—") return value
        return if (value.contains('.')) {
            val parts = value.split('.')
            if (parts.size == 4) "${parts[0]}.${parts[1]}.••.••" else "••••"
        } else if (value.contains(':')) {
            val parts = value.split(':')
            if (parts.size >= 3) parts.take(2).joinToString(":") + ":••••:••••" else "••••"
        } else "••••"
    }

    fun mac(value: String, enabled: Boolean): String {
        if (!enabled || value.isBlank() || value == "—") return value
        val separator = if (value.contains('-')) "-" else ":"
        val parts = value.split(separator)
        return if (parts.size == 6) "••$separator••$separator••$separator••$separator••$separator${parts.last()}" else "••:••:••:••:••:••"
    }

    fun bssid(value: String, enabled: Boolean): String = mac(value, enabled)
}
