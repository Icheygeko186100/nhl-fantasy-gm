package com.nhlfantasygm

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.nhlfantasygm.data.local.NhlFantasyDatabase
import com.nhlfantasygm.data.local.SecureCredentialStore
import com.nhlfantasygm.data.local.SettingsDataStore
import com.nhlfantasygm.data.remote.NhlRemoteDataSource
import com.nhlfantasygm.data.repository.NhlFantasyRepository
import com.nhlfantasygm.multiplayer.MultiplayerCoordinator
import com.nhlfantasygm.worker.NhlDataSyncWorker

class NhlFantasyApplication : Application(), ImageLoaderFactory {

    lateinit var database: NhlFantasyDatabase
        private set

    lateinit var repository: NhlFantasyRepository
        private set

    lateinit var multiplayerCoordinator: MultiplayerCoordinator
        private set

    override fun onCreate() {
        super.onCreate()
        database = NhlFantasyDatabase.getInstance(this)
        val settingsStore = SettingsDataStore(this)
        val credentialStore = SecureCredentialStore(this)
        val remoteDataSource = NhlRemoteDataSource()

        repository = NhlFantasyRepository(
            database = database,
            dao = database.dao(),
            remote = remoteDataSource,
            settingsStore = settingsStore,
            credentialStore = credentialStore
        )

        multiplayerCoordinator = MultiplayerCoordinator(repository = repository)

        runCatching {
            NhlDataSyncWorker.schedulePeriodicSync(this)
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(SvgDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.22)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("nhl_image_cache"))
                    .maxSizeBytes(80L * 1024L * 1024L) // 80 MB offline headshot & SVG logo cache
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()
    }
}
