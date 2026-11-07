package dev.codexsoft.codexcine.ui.playback.web

/**
 * Hosts whose requests are answered with an empty response. Only known ad and tracking hosts are
 * listed, because the host that serves the video itself is unknown and must never be blocked.
 */
class AdHostBlocklist private constructor(private val hosts: Set<String>) {

    /** True when [host] or any of its parent domains is listed. */
    fun matches(host: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        var candidate = host.lowercase().trimEnd('.')
        while (true) {
            if (candidate in hosts) return true
            val dot = candidate.indexOf('.')
            if (dot < 0) return false
            candidate = candidate.substring(dot + 1)
        }
    }

    companion object {
        const val ASSET_NAME = "blocklist.txt"

        /** One host per line. Blank lines and `#` comments, whole-line or trailing, are ignored. */
        fun parse(lines: Sequence<String>) = AdHostBlocklist(
            lines.map { it.substringBefore('#').trim().lowercase() }
                .filter { it.isNotEmpty() }
                .toHashSet(),
        )
    }
}
