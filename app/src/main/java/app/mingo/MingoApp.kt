package app.mingo

import android.app.Application
import app.mingo.data.LibraryRepository
import app.mingo.data.SettingsRepository
import app.mingo.download.DownloadManager
import app.mingo.download.DownloadNotifications
import app.mingo.engine.MingoEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MingoApp : Application() {

    /** Lives as long as the process; used for work that must outlive any single screen. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var engine: MingoEngine
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var downloadManager: DownloadManager
        private set
    lateinit var library: LibraryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this, appScope)
        engine = MingoEngine(this, appScope)
        downloadManager = DownloadManager(this)
        library = LibraryRepository(this, appScope)
        DownloadNotifications.createChannels(this)
        engine.initialize()
    }
}
