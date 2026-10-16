package app.mingo.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.settingsStore by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context, scope: CoroutineScope) {

    private val store = context.applicationContext.settingsStore

    val settings: StateFlow<AppSettings> = store.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            themeMode = enumOr(Keys.themeMode, d.themeMode),
            amoledBlack = this[Keys.amoledBlack] ?: d.amoledBlack,
            dynamicColor = this[Keys.dynamicColor] ?: d.dynamicColor,
            defaultType = enumOr(Keys.defaultType, d.defaultType),
            videoQuality = enumOr(Keys.videoQuality, d.videoQuality),
            videoContainer = enumOr(Keys.videoContainer, d.videoContainer),
            audioFormat = enumOr(Keys.audioFormat, d.audioFormat),
            audioBitrate = enumOr(Keys.audioBitrate, d.audioBitrate),
            embedThumbnail = this[Keys.embedThumbnail] ?: d.embedThumbnail,
            embedMetadata = this[Keys.embedMetadata] ?: d.embedMetadata,
            embedChapters = this[Keys.embedChapters] ?: d.embedChapters,
            embedSubtitles = this[Keys.embedSubtitles] ?: d.embedSubtitles,
            sponsorBlock = this[Keys.sponsorBlock] ?: d.sponsorBlock,
            autoPasteLinks = this[Keys.autoPasteLinks] ?: d.autoPasteLinks,
            autoAnalyze = this[Keys.autoAnalyze] ?: d.autoAnalyze,
            instantShareDownload = this[Keys.instantShareDownload] ?: d.instantShareDownload,
        )
    }

    private fun MutablePreferences.write(s: AppSettings) {
        this[Keys.themeMode] = s.themeMode.name
        this[Keys.amoledBlack] = s.amoledBlack
        this[Keys.dynamicColor] = s.dynamicColor
        this[Keys.defaultType] = s.defaultType.name
        this[Keys.videoQuality] = s.videoQuality.name
        this[Keys.videoContainer] = s.videoContainer.name
        this[Keys.audioFormat] = s.audioFormat.name
        this[Keys.audioBitrate] = s.audioBitrate.name
        this[Keys.embedThumbnail] = s.embedThumbnail
        this[Keys.embedMetadata] = s.embedMetadata
        this[Keys.embedChapters] = s.embedChapters
        this[Keys.embedSubtitles] = s.embedSubtitles
        this[Keys.sponsorBlock] = s.sponsorBlock
        this[Keys.autoPasteLinks] = s.autoPasteLinks
        this[Keys.autoAnalyze] = s.autoAnalyze
        this[Keys.instantShareDownload] = s.instantShareDownload
    }

    private inline fun <reified E : Enum<E>> Preferences.enumOr(key: Preferences.Key<String>, default: E): E =
        this[key]?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val amoledBlack = booleanPreferencesKey("amoled_black")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val defaultType = stringPreferencesKey("default_type")
        val videoQuality = stringPreferencesKey("video_quality")
        val videoContainer = stringPreferencesKey("video_container")
        val audioFormat = stringPreferencesKey("audio_format")
        val audioBitrate = stringPreferencesKey("audio_bitrate")
        val embedThumbnail = booleanPreferencesKey("embed_thumbnail")
        val embedMetadata = booleanPreferencesKey("embed_metadata")
        val embedChapters = booleanPreferencesKey("embed_chapters")
        val embedSubtitles = booleanPreferencesKey("embed_subtitles")
        val sponsorBlock = booleanPreferencesKey("sponsorblock")
        val autoPasteLinks = booleanPreferencesKey("auto_paste_links")
        val autoAnalyze = booleanPreferencesKey("auto_analyze")
        val instantShareDownload = booleanPreferencesKey("instant_share_download")
    }
}
