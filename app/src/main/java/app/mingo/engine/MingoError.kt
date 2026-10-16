package app.mingo.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.StringRes
import androidx.core.content.getSystemService
import app.mingo.R

/** The reasons a link can fail, each with a short title and a hint on what to do about it. */
enum class ErrorKind(@StringRes val title: Int, @StringRes val hint: Int) {
    Offline(R.string.err_offline, R.string.err_offline_hint),
    Network(R.string.err_network, R.string.err_network_hint),
    Unsupported(R.string.err_unsupported, R.string.err_unsupported_hint),
    Private(R.string.err_private, R.string.err_private_hint),
    AgeRestricted(R.string.err_age, R.string.err_age_hint),
    Unavailable(R.string.err_unavailable, R.string.err_unavailable_hint),
    GeoBlocked(R.string.err_geo, R.string.err_geo_hint),
    RateLimited(R.string.err_rate, R.string.err_rate_hint),
    Format(R.string.err_format, R.string.err_format_hint),
    Storage(R.string.err_storage, R.string.err_storage_hint),
    Live(R.string.err_live, R.string.err_live_hint),
    Engine(R.string.err_engine, R.string.err_engine_hint),
    Unknown(R.string.err_unknown, R.string.err_unknown_hint),
}

/** A classified failure; [details] keeps yt-dlp's own wording for the "Show details" view. */
data class MingoError(val kind: ErrorKind, val details: String = "")

class EngineException(val error: MingoError) : Exception(error.details)

/** Turns whatever went wrong into a [MingoError], checking connectivity first. */
fun Context.toMingoError(throwable: Throwable?): MingoError {
    if (throwable is EngineException) return throwable.error
    val details = readableError(throwable?.message)
    val kind = classifyError(throwable?.message)
    return MingoError(if (kind == ErrorKind.Network && !isOnline()) ErrorKind.Offline else kind, details)
}

fun Context.isOnline(): Boolean {
    val manager = getSystemService<ConnectivityManager>() ?: return true
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

/** Maps yt-dlp's error text to a [ErrorKind]. Order matters: more specific causes are checked first. */
fun classifyError(raw: String?): ErrorKind {
    val text = raw.orEmpty().lowercase()
    fun has(vararg needles: String) = needles.any { it in text }
    return when {
        text.isBlank() -> ErrorKind.Unknown
        has("no space left", "enospc", "disk full") -> ErrorKind.Storage
        has("http error 429", "too many requests", "rate-limit", "rate limit") -> ErrorKind.RateLimited
        has("confirm your age", "age-restricted", "age restricted", "inappropriate for some users") -> ErrorKind.AgeRestricted
        has("not available in your country", "geo restrict", "geo-restrict", "in your country", "from your location") ->
            ErrorKind.GeoBlocked
        has("live event will begin", "premieres in", "is currently live", "this live stream") -> ErrorKind.Live
        has(
            "private video", "video is private", "login required", "log in to", "sign in to", "requires authentication",
            "members-only", "join this channel", "--cookies", "account is private",
        ) -> ErrorKind.Private
        has("requested format is not available", "requested format not available") -> ErrorKind.Format
        has("unsupported url", "is not a valid url", "no video in this post", "there is no video") -> ErrorKind.Unsupported
        has(
            "unavailable", "has been removed", "does not exist", "http error 404", "404: not found",
            "has been deleted", "account has been terminated", "no longer available", "not available",
        ) -> ErrorKind.Unavailable
        has(
            "no address associated", "name or service not known", "temporary failure in name resolution",
            "network is unreachable", "getaddrinfo", "timed out", "connection reset", "connection refused",
            "connection aborted", "remote end closed", "unable to download webpage", "transporterror",
            "giving up after", "ssl", "eof occurred",
        ) -> ErrorKind.Network
        has("engine is not available") -> ErrorKind.Engine
        else -> ErrorKind.Unknown
    }
}

/** yt-dlp writes warnings and errors to the same stream; surface the actual error lines only. */
fun readableError(raw: String?): String {
    val lines = raw.orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }
    val errors = lines.filter { it.startsWith("ERROR:") }.map { it.removePrefix("ERROR:").trim() }
    return errors.joinToString("\n").ifEmpty { lines.lastOrNull { !it.startsWith("WARNING:") }.orEmpty() }
}
