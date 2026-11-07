package dev.codexsoft.codexcine.data.remote

import kotlinx.serialization.json.Json

/** Lenient on purpose: TMDB adds fields over time and sends explicit nulls for absent values. */
val TmdbJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}
