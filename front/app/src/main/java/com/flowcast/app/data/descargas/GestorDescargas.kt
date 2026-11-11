package com.flowcast.app.data.descargas

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import java.io.File
import java.util.concurrent.Executors

@UnstableApi
class GestorDescargas(contexto: Context) {
    private val baseDatos = StandaloneDatabaseProvider(contexto)

    val cache: SimpleCache = SimpleCache(File(contexto.filesDir, "descargas"), NoOpCacheEvictor(), baseDatos)

    val fabricaHttp: DefaultHttpDataSource.Factory = DefaultHttpDataSource.Factory()
        .setUserAgent(USER_AGENT)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(30_000)

    fun fabricaReproduccion(userAgent: String): CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory().setUserAgent(userAgent).setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(20_000).setReadTimeoutMs(60_000))
        .setCacheWriteDataSinkFactory(null)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    val gestor: DownloadManager = DownloadManager(
        contexto,
        baseDatos,
        cache,
        fabricaHttp,
        Executors.newFixedThreadPool(2),
    ).apply { maxParallelDownloads = 2 }

    companion object {
        const val USER_AGENT = "FlowCast/0.1"
    }
}
