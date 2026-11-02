package com.yang.player.online

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@UnstableApi
object OnlinePlayback {
    @Volatile private var cache: SimpleCache? = null
    private val warming = ThreadPoolExecutor(1, 1, 15, TimeUnit.SECONDS, ArrayBlockingQueue(1), ThreadPoolExecutor.DiscardOldestPolicy())
    private fun cache(context: Context): SimpleCache = cache ?: synchronized(this) {
        cache ?: SimpleCache(context.applicationContext.cacheDir.resolve("online_audio_v1"),
            LeastRecentlyUsedCacheEvictor(256L * 1024 * 1024), StandaloneDatabaseProvider(context.applicationContext))
            .also { cache = it }
    }

    private fun onlineFactory(context: Context): CacheDataSource.Factory {
        val http = DefaultHttpDataSource.Factory().setUserAgent(OnlineAudio.UA)
            .setConnectTimeoutMs(12_000).setReadTimeoutMs(20_000)
        val resolving = ResolvingDataSource.Factory(http) { spec ->
            if (OnlineAudio.isOnline(spec.uri)) spec.withUri(Uri.parse(OnlineAudio.resolve(spec.uri).url)) else spec
        }
        return CacheDataSource.Factory().setCache(cache(context)).setUpstreamDataSourceFactory(resolving)
            .setCacheKeyFactory { spec -> if (OnlineAudio.isOnline(spec.uri)) "yt:${spec.uri.lastPathSegment}" else spec.key ?: spec.uri.toString() }
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun factory(context: Context): DataSource.Factory {
        val app = context.applicationContext
        return DataSource.Factory {
            object : DataSource {
                private var source: DataSource? = null
                private val listeners = mutableListOf<TransferListener>()
                override fun addTransferListener(listener: TransferListener) { listeners.add(listener); source?.addTransferListener(listener) }
                override fun open(spec: DataSpec): Long {
                    val online = OnlineAudio.isOnline(spec.uri)
                    fun create() = (if (online) onlineFactory(app) else DefaultDataSource.Factory(app)).createDataSource().also { ds ->
                        listeners.forEach(ds::addTransferListener)
                        source = ds
                    }
                    return try { create().open(spec) } catch (error: HttpDataSource.InvalidResponseCodeException) {
                        if (!online || error.responseCode !in listOf(403, 410)) throw error
                        source?.close()
                        OnlineAudio.invalidate(spec.uri)
                        create().open(spec)
                    }
                }
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int = checkNotNull(source).read(buffer, offset, length)
                override fun getUri(): Uri? = source?.uri
                override fun getResponseHeaders(): Map<String, List<String>> = source?.responseHeaders.orEmpty()
                override fun close() { source?.close(); source = null }
            }
        }
    }

    fun prefetch(context: Context, uri: String) {
        if (!uri.startsWith("yangyoutube://")) return
        warming.execute {
            runCatching {
                val source = onlineFactory(context).createDataSource()
                try {
                    CacheWriter(source, DataSpec.Builder().setUri(uri).setLength(128L * 1024).build(), ByteArray(16 * 1024), null).cache()
                } finally { source.close() }
            }
        }
    }
}
