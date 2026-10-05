package com.causalguard.data.tracker

import java.net.IDN
import java.util.Locale

/** Normalizes a DNS domain hint without resolving it or interpreting its meaning. */
object DomainNormalizer {

    private const val MAX_DOMAIN_LENGTH = 253
    private const val MAX_LABEL_LENGTH = 63

    fun normalize(raw: String?): String? {
        var value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        value = value.lowercase(Locale.ROOT)
        if (value.endsWith('.')) {
            value = value.dropLast(1)
        }
        if (value.isEmpty() || value.any(Char::isWhitespace) || isIpLiteral(value)) {
            return null
        }

        val ascii = runCatching {
            IDN.toASCII(value, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
        }.getOrNull() ?: return null

        if (ascii.length > MAX_DOMAIN_LENGTH) return null
        val labels = ascii.split('.')
        if (labels.any { it.isEmpty() || it.length > MAX_LABEL_LENGTH }) return null
        if (labels.any { !isValidLabel(it) }) return null
        return ascii
    }

    private fun isValidLabel(label: String): Boolean {
        if (!label.first().isLetterOrDigit() || !label.last().isLetterOrDigit()) return false
        return label.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }
    }

    private fun isIpLiteral(value: String): Boolean {
        if (value.contains(':')) return true
        val octets = value.split('.')
        if (octets.size != 4 || octets.any { it.isEmpty() || !it.all(Char::isDigit) }) {
            return false
        }
        return octets.all { it.toIntOrNull()?.let { octet -> octet in 0..255 } == true }
    }
}
