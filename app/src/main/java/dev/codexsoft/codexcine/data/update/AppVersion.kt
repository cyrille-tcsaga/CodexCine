package dev.codexsoft.codexcine.data.update

/** A release number such as 1.2.0 (a leading "v", as in a Git tag, is allowed). */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)

    override fun toString() = "$major.$minor.$patch"

    companion object {
        private val PATTERN = Regex("""v?(\d{1,3})\.(\d{1,2})(?:\.(\d{1,2}))?""")

        fun parse(text: String?): AppVersion? {
            val match = PATTERN.matchEntire(text.orEmpty().trim()) ?: return null
            val (major, minor, patch) = match.destructured
            return AppVersion(major.toInt(), minor.toInt(), patch.ifEmpty { "0" }.toInt())
        }
    }
}

/** A published release that carries an APK. */
data class AvailableUpdate(
    val version: AppVersion,
    val apkUrl: String,
    val apkSizeBytes: Long,
    val notes: String,
)

/** What a GitHub "latest release" answer means for this app. Pure, so that it can be tested. */
object UpdateRules {

    /** An APK larger than this is not ours: the app is about 4 MB. */
    const val MAX_APK_BYTES = 100L * 1024 * 1024
    private const val MAX_NOTES = 600

    /** Only https downloads from GitHub's own hosts are followed. */
    private val ALLOWED_DOWNLOAD = Regex("""https://(github\.com|objects\.githubusercontent\.com)/.+\.apk""")

    fun newerThan(installed: AppVersion?, release: GitHubRelease): AvailableUpdate? {
        if (release.draft || release.prerelease) return null
        val version = AppVersion.parse(release.tagName) ?: return null
        if (installed != null && version <= installed) return null
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk") && ALLOWED_DOWNLOAD.matches(it.downloadUrl) }
            ?: return null
        if (apk.size <= 0 || apk.size > MAX_APK_BYTES) return null
        return AvailableUpdate(version, apk.downloadUrl, apk.size, release.body.orEmpty().trim().take(MAX_NOTES))
    }
}
