package app.mingo.engine

import com.yausername.youtubedl_android.mapper.VideoInfo

data class MediaInfo(
    val url: String,
    val title: String,
    val uploader: String?,
    val durationSeconds: Int,
    val thumbnailUrl: String?,
    val extractor: String?,
    /** Distinct video heights offered by the site, highest first. Empty for audio-only sources. */
    val videoHeights: List<Int>,
    /** Rough download size in bytes for each height (best video at that height + best audio). */
    val sizeByHeight: Map<Int, Long>,
    /** Rough size of the best audio stream in bytes, or null when the site does not report it. */
    val audioSize: Long?,
) {
    val hasVideo: Boolean get() = videoHeights.isNotEmpty()

    companion object {
        fun from(url: String, info: VideoInfo): MediaInfo {
            val formats = info.formats.orEmpty()
            fun size(f: com.yausername.youtubedl_android.mapper.VideoFormat) =
                f.fileSize.takeIf { it > 0 } ?: f.fileSizeApproximate.takeIf { it > 0 }

            val audioOnly = formats.filter { it.vcodec == "none" && it.acodec != null && it.acodec != "none" }
            val bestAudioSize = audioOnly.mapNotNull(::size).maxOrNull()

            val video = formats.filter { it.height > 0 && it.vcodec != null && it.vcodec != "none" }
            val heights = video.map { it.height }.distinct().sortedDescending()
            val sizes = heights.associateWith { h ->
                val bestVideo = video.filter { it.height == h }.mapNotNull(::size).maxOrNull() ?: 0L
                bestVideo + (bestAudioSize ?: 0L)
            }.filterValues { it > 0 }

            return MediaInfo(
                url = url,
                title = info.title ?: info.fulltitle.orEmpty(),
                uploader = info.uploader,
                durationSeconds = info.duration,
                thumbnailUrl = info.thumbnail,
                extractor = info.extractorKey,
                videoHeights = heights,
                sizeByHeight = sizes,
                audioSize = bestAudioSize,
            )
        }
    }
}
