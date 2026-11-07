package dev.codexsoft.codexcine.core

sealed class AppResult<out T> {
    data class Success<out T>(val data: T) : AppResult<T>()
    data class Error(val kind: ErrorKind, val message: String? = null) : AppResult<Nothing>()
}

enum class ErrorKind {
    /** The token is empty, or TMDB rejected it with HTTP 401. Retrying cannot help. */
    INVALID_TOKEN,
    NO_CONNECTION,
    GENERIC,
}
