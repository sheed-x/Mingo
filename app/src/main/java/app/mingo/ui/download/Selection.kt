package app.mingo.ui.download

import app.mingo.data.AppSettings
import app.mingo.data.AudioBitrate
import app.mingo.data.AudioFormat
import app.mingo.data.DownloadType
import app.mingo.data.VideoContainer
import app.mingo.download.DownloadOptions
import app.mingo.engine.MediaInfo

/** What the user picked in the options panel for an analyzed link. */
data class Selection(
    val type: DownloadType,
    /** Maximum video height; null means best available. */
    val height: Int?,
    val container: VideoContainer,
    val audioFormat: AudioFormat,
    val audioBitrate: AudioBitrate,
    val embedThumbnail: Boolean,
    val embedMetadata: Boolean,
    val embedChapters: Boolean,
    val embedSubtitles: Boolean,
    val sponsorBlock: Boolean,
) {
    fun toOptions(info: MediaInfo) = DownloadOptions(
        url = info.url,
        title = info.title,
        thumbnailUrl = info.thumbnailUrl,
        uploader = info.uploader,
        durationSeconds = info.durationSeconds,
        extractor = info.extractor,
        type = type,
        videoHeight = height,
        container = container,
        audioFormat = audioFormat,
        audioBitrate = audioBitrate,
        embedThumbnail = embedThumbnail,
        embedMetadata = embedMetadata,
        embedChapters = embedChapters,
        embedSubtitles = embedSubtitles,
        sponsorBlock = sponsorBlock,
    )

    /** Adjusts the defaults to what this media actually offers (e.g. no 4K, or audio only). */
    fun fitTo(info: MediaInfo): Selection {
        if (!info.hasVideo) return copy(type = DownloadType.Audio)
        val target = height ?: return this
        // Highest available height not above the preferred one; Best if everything is above it.
        return copy(height = info.videoHeights.firstOrNull { it <= target })
    }

    companion object {
        fun from(s: AppSettings) = Selection(
            type = s.defaultType,
            height = s.videoQuality.height,
            container = s.videoContainer,
            audioFormat = s.audioFormat,
            audioBitrate = s.audioBitrate,
            embedThumbnail = s.embedThumbnail,
            embedMetadata = s.embedMetadata,
            embedChapters = s.embedChapters,
            embedSubtitles = s.embedSubtitles,
            sponsorBlock = s.sponsorBlock,
        )
    }
}

/** Heights this media is actually offered in, preceded by null for "Best". */
val MediaInfo.qualityOptions: List<Int?>
    get() = listOf<Int?>(null) + videoHeights.filter { it >= 144 }

fun qualityLabel(height: Int?, best: String): String = when {
    height == null -> best
    height >= 4320 -> "8K"
    height >= 2160 -> "4K"
    else -> "${height}p"
}
