package dev.codexsoft.codexcine.data.vidzy

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** The API is undocumented beyond these fields, so nothing is assumed to be present or well typed. */
val VidzyJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

@Serializable
data class VidzyAvailabilityDto(
    val available: Boolean? = null,
    val detectedType: String? = null,
    val title: String? = null,
    val year: JsonElement? = null, // A number today; never read, and must not fail the whole response.
    val languages: List<String>? = null,
    val embed: String? = null,
)
