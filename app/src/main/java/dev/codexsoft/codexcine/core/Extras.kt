package dev.codexsoft.codexcine.core

/** Intent extra keys. Screens only ever exchange primitives, never domain objects. */
object Extras {
    const val MEDIA_ID = "extra_media_id"
    const val MEDIA_TYPE = "extra_media_type"
    const val TITLE = "extra_title"
    const val SEASON_NUMBER = "extra_season_number"
    const val EPISODE_NUMBER = "extra_episode_number"
    const val CATEGORY = "extra_category"

    /** Value used when an optional Int extra (season or episode number) is absent. */
    const val NO_VALUE = -1
}
