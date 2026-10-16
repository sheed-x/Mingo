package app.mingo.data

import kotlinx.serialization.Serializable

enum class ThemeMode { System, Light, Dark }

@Serializable
enum class DownloadType { Video, Audio }

@Serializable
enum class VideoQuality(val height: Int?, val label: String) {
    Best(null, "Best"),
    P2160(2160, "4K"),
    P1440(1440, "1440p"),
    P1080(1080, "1080p"),
    P720(720, "720p"),
    P480(480, "480p"),
    P360(360, "360p"),
}

@Serializable
enum class VideoContainer(val ext: String) { MP4("mp4"), WEBM("webm"), MKV("mkv") }

@Serializable
enum class AudioFormat(val ext: String, val lossless: Boolean) {
    MP3("mp3", false),
    M4A("m4a", false),
    OPUS("opus", false),
    FLAC("flac", true),
    WAV("wav", true),
}

@Serializable
enum class AudioBitrate(val kbps: Int) {
    K128(128), K192(192), K256(256), K320(320);

    /** "320 kbps", isolated so right-to-left languages don't flip it into "kbps 320". */
    val label: String get() = "⁦$kbps kbps⁩"
}

/** Everything the user can configure. Download-related fields are the defaults the link panel starts from. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val amoledBlack: Boolean = false,
    val dynamicColor: Boolean = true,
    val defaultType: DownloadType = DownloadType.Video,
    val videoQuality: VideoQuality = VideoQuality.P1080,
    val videoContainer: VideoContainer = VideoContainer.MP4,
    val audioFormat: AudioFormat = AudioFormat.MP3,
    val audioBitrate: AudioBitrate = AudioBitrate.K320,
    val embedThumbnail: Boolean = true,
    val embedMetadata: Boolean = true,
    val embedChapters: Boolean = true,
    val embedSubtitles: Boolean = false,
    val sponsorBlock: Boolean = false,
    val autoPasteLinks: Boolean = true,
    val autoAnalyze: Boolean = true,
    /** Sharing a link downloads it straight away with these defaults instead of showing the options sheet. */
    val instantShareDownload: Boolean = false,
)
