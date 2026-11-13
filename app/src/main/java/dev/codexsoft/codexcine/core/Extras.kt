package dev.codexsoft.codexcine.core

/** Intent extra keys. Screens only ever exchange primitives, never domain objects. */
object Extras {
    const val MEDIA_ID = "extra_media_id"
    const val MEDIA_TYPE = "extra_media_type"
    const val TITLE = "extra_title"
    const val SEASON_NUMBER = "extra_season_number"
    const val EPISODE_NUMBER = "extra_episode_number"
    const val CATEGORY = "extra_category"
    const val AUTO_RESUME = "extra_auto_resume"
    const val START_OVER = "extra_start_over"

    // Debug test harness only. PlaybackActivity ignores them in release builds.
    const val DEBUG_EMBED_URL = "extra_debug_embed_url"
    const val DEBUG_FORCE_BASIC_MODE = "extra_debug_force_basic_mode"

    /** Value used when an optional Int extra (season or episode number) is absent. */
    const val NO_VALUE = -1
}
