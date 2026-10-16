package app.mingo.download

import app.mingo.data.AudioBitrate
import app.mingo.data.AudioFormat
import app.mingo.data.DownloadType
import app.mingo.data.VideoContainer
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.util.Locale
import kotlinx.serialization.Serializable

/** One download as the user configured it. Serialized into the worker's input data. */
@Serializable
data class DownloadOptions(
    val url: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val uploader: String? = null,
    val durationSeconds: Int = 0,
    val extractor: String? = null,
    val type: DownloadType,
    /** Maximum video height; null means best available. */
    val videoHeight: Int? = null,
    val container: VideoContainer = VideoContainer.MP4,
    val audioFormat: AudioFormat = AudioFormat.MP3,
    val audioBitrate: AudioBitrate = AudioBitrate.K320,
    val embedThumbnail: Boolean = true,
    val embedMetadata: Boolean = true,
    val embedChapters: Boolean = true,
    val embedSubtitles: Boolean = false,
    val sponsorBlock: Boolean = false,
    /** Raw yt-dlp arguments appended last, so they can override anything above. */
    val extraArgs: List<String> = emptyList(),
) {
    /** Short human summary, e.g. "1080p · MP4" or "MP3 · 320 kbps". */
    val summary: String
        get() = when (type) {
            DownloadType.Video -> "${videoHeight?.let { "${it}p" } ?: "Best"} · ${container.name}"
            DownloadType.Audio ->
                if (audioFormat.lossless) audioFormat.name else "${audioFormat.name} · ${audioBitrate.kbps} kbps"
        }

    fun toRequest(outputDir: File): YoutubeDLRequest = YoutubeDLRequest(url).apply {
        addOption("-o", "${outputDir.absolutePath}/%(title).150B [%(id)s].%(ext)s")
        addOption("--no-playlist")
        addOption("--no-mtime")
        // One progress report per line, so every update reaches the progress callback.
        addOption("--newline")
        // Fetch DASH/HLS fragments in parallel: a large speed-up on YouTube.
        addOption("--concurrent-fragments", 4)
        // Fail instead of hanging forever when a server stops responding. TikTok gets a short
        // timeout because a stalled mirror is retried on the next one (see [attempts]).
        addOption("--socket-timeout", if (isTikTok) 10 else 20)
        addOption("--retries", if (isTikTok) 1 else 3)

        when (type) {
            DownloadType.Video -> {
                addOption("-f", "bv*+ba/b")
                val sort = buildList {
                    videoHeight?.let { add("res:$it") }
                    when (container) {
                        VideoContainer.MP4 -> add("ext:mp4:m4a")
                        VideoContainer.WEBM -> add("ext:webm:webm")
                        VideoContainer.MKV -> Unit
                    }
                }
                if (sort.isNotEmpty()) addOption("-S", sort.joinToString(","))
                addOption("--merge-output-format", container.ext)
                if (embedThumbnail && container != VideoContainer.WEBM) addThumbnail()
                if (embedChapters) addOption("--embed-chapters")
                if (embedSubtitles) {
                    addOption("--embed-subs")
                    addOption("--sub-langs", "${Locale.getDefault().language}.*,en.*")
                }
            }
            DownloadType.Audio -> {
                addOption("-f", "ba/b")
                addOption("-x")
                addOption("--audio-format", audioFormat.ext)
                if (!audioFormat.lossless) addOption("--audio-quality", "${audioBitrate.kbps}K")
                if (embedThumbnail && audioFormat in setOf(AudioFormat.MP3, AudioFormat.M4A)) addThumbnail()
            }
        }
        if (embedMetadata) addOption("--embed-metadata")
        if (sponsorBlock) addOption("--sponsorblock-remove", "sponsor,selfpromo,interaction")
        if (extraArgs.isNotEmpty()) addCommands(extraArgs)
    }

    private val isTikTok: Boolean get() = url.contains("tiktok.com", ignoreCase = true)

    /**
     * Extra arguments for each download attempt, tried in order until one succeeds.
     *
     * TikTok serves every format from two CDN mirrors (format ids ending in -0 and -1), and some
     * networks cannot reach one of them at all, which leaves the download stuck at 0%. Trying each
     * mirror explicitly makes TikTok work wherever at least one mirror is reachable.
     */
    fun attempts(): List<List<String>> =
        if (isTikTok) listOf(listOf("-f", "b[format_id$=-0]"), listOf("-f", "b[format_id$=-1]"), emptyList())
        else listOf(emptyList())

    private fun YoutubeDLRequest.addThumbnail() {
        addOption("--embed-thumbnail")
        addOption("--convert-thumbnails", "jpg")
    }
}
