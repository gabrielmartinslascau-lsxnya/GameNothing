package app.gamenative.service.xbox

import android.util.Base64
import org.json.JSONObject

/**
 * Kotlin port of `tools/xbox/droiddeck-xbox-ticket.patch`.
 *
 * Store and licensing services reject delegation-compact tickets in the `d=` form that Xbox Live
 * sign-in needs (`InvalidMsaTicketFormat`) and accept the same value as `t=`.
 * Meanwhile, compact JWE tokens (`eyJ...` 5-part dot-separated JWE) must strip `d=` when sent to
 * `user.auth.xboxlive.com` (`xboxTicket`) while preserving `d=` for opaque RPS tickets.
 */
object XboxTicketParser {

    /**
     * Converts a delegation ticket for Xbox Live `user.auth.xboxlive.com` (`rps_ticket`).
     * Only strips `d=` if the underlying token is a valid compact JWE; preserves `d=` for opaque RPS tickets.
     */
    fun xboxTicket(ticket: String): String {
        val raw = ticket.removePrefix("d=")
        if (raw != ticket && isCompactJwe(raw)) {
            return raw
        }
        return ticket
    }

    /**
     * Converts a delegation ticket for Microsoft Store / Content Licensing services (`t=`).
     */
    fun storeTicket(ticket: String): String {
        val raw = ticket.removePrefix("d=")
        return if (raw != ticket) "t=$raw" else ticket
    }

    /**
     * Checks whether [token] is a 5-part RFC 7516 compact JWE with a valid JSON JOSE header
     * containing non-empty `alg` and `enc` fields.
     */
    fun isCompactJwe(token: String): Boolean {
        if (token.isEmpty() || token.length > 8192) return false
        val parts = token.split('.')
        if (parts.size != 5) return false
        // Header (0), IV (2), Ciphertext (3), and Tag (4) must be non-empty base64url strings
        for (idx in intArrayOf(0, 2, 3, 4)) {
            if (parts[idx].isEmpty() || !isBase64Url(parts[idx])) return false
        }
        if (parts[1].isNotEmpty() && !isBase64Url(parts[1])) return false
        return runCatching {
            val headerBytes = Base64.decode(parts[0], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            val header = JSONObject(String(headerBytes, Charsets.UTF_8))
            header.optString("alg").isNotEmpty() && header.optString("enc").isNotEmpty()
        }.getOrDefault(false)
    }

    private fun isBase64Url(segment: String): Boolean =
        segment.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' }
}
