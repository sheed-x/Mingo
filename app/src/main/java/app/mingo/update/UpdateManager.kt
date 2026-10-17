package app.mingo.update

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.edit
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A newer version published on GitHub Releases, with the APK that fits this phone. */
data class AppRelease(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val apkName: String,
    val apkSize: Long,
    /** Hex SHA-256 when GitHub reports one for the asset. */
    val sha256: String?,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Downloading(val release: AppRelease, val progress: Float) : UpdateState
    /** Android needs the user to allow installs from Mingo once before the installer can open. */
    data class NeedsPermission(val release: AppRelease, val file: File) : UpdateState
    data class ReadyToInstall(val release: AppRelease, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Checks GitHub Releases for a newer Mingo and installs it. Android never installs silently:
 * the system installer always asks the user, and the APK must be signed with the same key.
 */
class UpdateManager(context: Context, private val scope: CoroutineScope) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private var downloadJob: Job? = null

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val currentVersion: String =
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName.orEmpty().substringBefore('-')

    /** Debug builds have a different package and can't be updated by a release APK. */
    private val isDebugBuild = appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    /** Automatic check on launch, at most every [CHECK_INTERVAL_MS]. */
    fun checkIfDue() {
        if (isDebugBuild) return
        if (System.currentTimeMillis() - prefs.getLong(KEY_LAST_CHECK, 0) < CHECK_INTERVAL_MS) return
        check(userInitiated = false)
    }

    fun check(userInitiated: Boolean = true) {
        if (_state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Checking
        scope.launch(Dispatchers.IO) {
            _state.value = runCatching { fetchLatest() }
                .onSuccess { prefs.edit { putLong(KEY_LAST_CHECK, System.currentTimeMillis()) } }
                .fold(
                    onSuccess = { release ->
                        when {
                            release == null -> if (userInitiated) UpdateState.UpToDate else UpdateState.Idle
                            !userInitiated && prefs.getString(KEY_SKIPPED, null) == release.version -> UpdateState.Idle
                            else -> UpdateState.Available(release)
                        }
                    },
                    onFailure = {
                        Log.w(TAG, "Update check failed", it)
                        if (userInitiated) UpdateState.Failed(it.message ?: it.javaClass.simpleName) else UpdateState.Idle
                    },
                )
        }
    }

    /** "Later": stop offering this version automatically; a manual check still shows it. */
    fun dismiss() {
        (_state.value as? UpdateState.Available)?.let { prefs.edit { putString(KEY_SKIPPED, it.release.version) } }
        downloadJob?.cancel()
        _state.value = UpdateState.Idle
    }

    fun download(release: AppRelease) {
        downloadJob?.cancel()
        _state.value = UpdateState.Downloading(release, 0f)
        downloadJob = scope.launch(Dispatchers.IO) {
            val dir = File(appContext.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
            val file = File(dir, release.apkName)
            try {
                val connection = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 20_000
                    readTimeout = 30_000
                    setRequestProperty("User-Agent", "Mingo/$currentVersion")
                }
                val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
                val digest = MessageDigest.getInstance("SHA-256")
                connection.inputStream.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read = 0L
                        while (isActive) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            digest.update(buffer, 0, n)
                            read += n
                            if (total > 0) _state.value = UpdateState.Downloading(release, (read.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                if (release.sha256 != null && !release.sha256.equals(hash, ignoreCase = true)) {
                    file.delete()
                    _state.value = UpdateState.Failed("Downloaded file is corrupted")
                    return@launch
                }
                _state.value = if (canInstall()) UpdateState.ReadyToInstall(release, file)
                else UpdateState.NeedsPermission(release, file)
            } catch (e: Exception) {
                if (isActive) _state.value = UpdateState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || appContext.packageManager.canRequestPackageInstalls()

    /** Opens the system screen where the user allows Mingo to install apps. */
    fun permissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${appContext.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Called when the user returns from the permission screen. */
    fun onPermissionResult() {
        val s = _state.value as? UpdateState.NeedsPermission ?: return
        if (canInstall()) _state.value = UpdateState.ReadyToInstall(s.release, s.file)
    }

    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    @Serializable
    private data class GhAsset(
        val name: String,
        val size: Long = 0,
        @SerialName("browser_download_url") val url: String,
        val digest: String? = null,
    )

    @Serializable
    private data class GhRelease(
        @SerialName("tag_name") val tag: String,
        val body: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<GhAsset> = emptyList(),
    )

    /** Returns the latest release if it is newer than this app, otherwise null. */
    private fun fetchLatest(): AppRelease? {
        val connection = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Mingo/$currentVersion")
        }
        if (connection.responseCode == HttpURLConnection.HTTP_NOT_FOUND) return null
        val release = json.decodeFromString<GhRelease>(connection.inputStream.bufferedReader().use { it.readText() })
        if (release.draft || release.prerelease) return null
        val version = release.tag.removePrefix("v")
        if (!isNewer(version, currentVersion)) return null

        // Prefer the APK built for this phone's processor; fall back to the universal one.
        val asset = Build.SUPPORTED_ABIS.firstNotNullOfOrNull { abi ->
            release.assets.firstOrNull { it.name.endsWith("-$abi.apk") }
        } ?: release.assets.firstOrNull { it.name.endsWith("-universal.apk") }
            ?: return null

        return AppRelease(
            version = version,
            notes = cleanNotes(release.body.orEmpty()),
            apkUrl = asset.url,
            apkName = asset.name,
            apkSize = asset.size,
            sha256 = asset.digest?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:"),
        )
    }

    companion object {
        private const val TAG = "UpdateManager"
        private const val LATEST_URL = "https://api.github.com/repos/sheed-x/Mingo/releases/latest"
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_SKIPPED = "skipped_version"
        private const val CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000

        /** Compares dotted versions numerically: 1.10.0 is newer than 1.9.2. */
        fun isNewer(candidate: String, current: String): Boolean {
            val a = candidate.split('.').map { it.toIntOrNull() ?: 0 }
            val b = current.split('.').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }

        /**
         * Keeps the human part of the release notes: everything before the download table and
         * checksums, with Markdown symbols removed.
         */
        fun cleanNotes(body: String): String = body
            .substringBefore("### 📥")
            .substringBefore("### 🔐")
            .lines()
            .filterNot { it.trimStart().startsWith("|") || it.trimStart().startsWith("```") }
            .joinToString("\n") { line ->
                line.replace(Regex("^#{1,6}\\s*"), "")
                    .replace(Regex("^\\s*[-*]\\s+"), "• ")
                    .replace("*", "")
                    .replace("`", "")
            }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
}
