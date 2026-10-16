package app.mingo.ui

import java.util.Locale

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return ""
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    val text = if (unit >= 2) String.format(Locale.US, "%.1f %s", value, units[unit]) else "${value.toInt()} ${units[unit]}"
    // Isolate the number and unit so right-to-left languages keep "2.4 MB" in that order.
    return "⁦$text⁩"
}

fun formatDuration(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = totalSeconds % 3600 / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private val URL_REGEX = Regex("""https?://\S+""")

/** Apps often share "Check this out https://…", so pull out the URL itself. */
fun findUrl(text: String): String? = URL_REGEX.find(text)?.value
