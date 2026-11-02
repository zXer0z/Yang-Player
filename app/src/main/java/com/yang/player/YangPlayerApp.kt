package com.yang.player

import android.app.Application
import android.graphics.Bitmap
import androidx.media3.common.util.UnstableApi
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.yang.player.data.AudioCoverFetcher
import kotlinx.coroutines.Dispatchers

@UnstableApi
class YangPlayerApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        com.yang.player.audio.YinConnector.get(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(AudioCoverFetcher.Factory(this@YangPlayerApp))
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizeBytes(40 * 1024 * 1024)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("album_art_cache"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .bitmapConfig(Bitmap.Config.ARGB_8888)
            // Software bitmaps allow Skia to batch render into a single shared texture atlas,
            // preventing the Adreno 650 driver from creating hundreds of individual GraphicBuffers during fast flings.
            .allowHardware(false)
            .dispatcher(Dispatchers.IO)
            .respectCacheHeaders(false)
            .build()
    }
}
