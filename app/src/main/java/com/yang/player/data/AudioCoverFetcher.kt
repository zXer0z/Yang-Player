package com.yang.player.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import com.yang.player.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Collections

/**
 * High-performance HD cover art fetcher:
 * Tier 1: L1 In-Memory LRU Cache
 * Tier 2: L2 Fast Disk Cache (Pre-scaled 600x600 HD JPEGs in cacheDir/art_thumbs_v2)
 * Negative Cache: Memory & disk tracking for files with no artwork
 * Tier 3: Native extraction with downsampling to 600x600 ARGB_8888 for crystal-clear HD
 */
class AudioCoverFetcher(
    private val uri: Uri,
    private val context: Context,
    private val options: Options
) : Fetcher {

    companion object {
        private fun cacheSongId(uri: Uri): String {
            if (uri.authority == "media" && uri.lastPathSegment?.all(Char::isDigit) == true) return uri.lastPathSegment!!
            return "uri_" + java.security.MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray())
                .joinToString("") { "%02x".format(it.toInt() and 255) }
        }
        // High-speed L1 memory cache
        private val l1Cache = object : LruCache<String, Bitmap>(80) {
            override fun sizeOf(key: String, value: Bitmap): Int = 1
        }

        // Negative memory cache: tracks songs known to have no artwork
        private val negativeCache = Collections.synchronizedSet(HashSet<String>())

        /**
         * Pre-warms the cache in the background during/after scanning
         * Decodes at 600x600 HD resolution so Hero cards and modal look crystal clear
         */
        suspend fun preWarmCache(context: Context, songs: List<Song>) = withContext(Dispatchers.IO) {
            val cacheDir = File(context.cacheDir, "art_thumbs_v2").apply { if (!exists()) mkdirs() }
            val mmr = MediaMetadataRetriever()

            for (song in songs) {
                val songId = cacheSongId(Uri.parse(song.mediaUri))
                val cacheKey = "cover_$songId"
                if (negativeCache.contains(cacheKey) || l1Cache.get(cacheKey) != null) continue

                val diskFile = File(cacheDir, "$cacheKey.jpg")
                val noArtFile = File(cacheDir, "$cacheKey.no_art")

                if (diskFile.exists() || noArtFile.exists()) continue

                try {
                    if (song.dataPath.isNotBlank() && File(song.dataPath).exists()) {
                        mmr.setDataSource(song.dataPath)
                    } else {
                        mmr.setDataSource(context, Uri.parse(song.mediaUri))
                    }

                    val picture = mmr.embeddedPicture
                    if (picture != null) {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(picture, 0, picture.size, bounds)

                        var sample = 1
                        while (bounds.outWidth / (sample * 2) >= 600 && bounds.outHeight / (sample * 2) >= 600) {
                            sample *= 2
                        }

                        val decodeOpts = BitmapFactory.Options().apply {
                            inSampleSize = sample
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                        val bitmap = BitmapFactory.decodeByteArray(picture, 0, picture.size, decodeOpts)
                        if (bitmap != null) {
                            l1Cache.put(cacheKey, bitmap)
                            FileOutputStream(diskFile).use { out ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                            }
                        }
                    } else {
                        negativeCache.add(cacheKey)
                        noArtFile.createNewFile()
                    }
                } catch (_: Exception) {
                    negativeCache.add(cacheKey)
                    try { noArtFile.createNewFile() } catch (_: Exception) {}
                }
            }
            try { mmr.release() } catch (_: Exception) {}
        }
    }

    override suspend fun fetch(): FetchResult? {
        val songId = cacheSongId(uri)
        val cacheKey = "cover_$songId"

        // 0. Negative memory cache check (0.0001ms)
        if (negativeCache.contains(cacheKey)) {
            return null
        }

        // 1. Tier 1: Instant L1 memory cache hit (0.001ms)
        val memBitmap = l1Cache.get(cacheKey)
        if (memBitmap != null && !memBitmap.isRecycled) {
            return DrawableResult(
                drawable = BitmapDrawable(context.resources, memBitmap),
                isSampled = false,
                dataSource = DataSource.MEMORY
            )
        }

        val cacheDir = File(context.cacheDir, "art_thumbs_v2").apply { if (!exists()) mkdirs() }
        val diskFile = File(cacheDir, "$cacheKey.jpg")
        val noArtFile = File(cacheDir, "$cacheKey.no_art")

        // 0b. Negative disk cache check
        if (noArtFile.exists()) {
            negativeCache.add(cacheKey)
            return null
        }

        // 2. Tier 2: Instant L2 local disk file hit (~0.5ms)
        if (diskFile.exists() && diskFile.length() > 0) {
            try {
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bitmap = BitmapFactory.decodeFile(diskFile.absolutePath, opts)
                if (bitmap != null) {
                    l1Cache.put(cacheKey, bitmap)
                    return DrawableResult(
                        drawable = BitmapDrawable(context.resources, bitmap),
                        isSampled = false,
                        dataSource = DataSource.DISK
                    )
                }
            } catch (_: Exception) {}
        }

        // 3. Tier 3: Extract picture directly from audio file at 600x600 HD target
        val mmr = MediaMetadataRetriever()
        try {
            val path = uri.path
            if (uri.scheme == "file" && path != null && File(path).exists()) {
                mmr.setDataSource(path)
            } else {
                mmr.setDataSource(context, uri)
            }

            val picture = mmr.embeddedPicture
            if (picture != null) {
                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(picture, 0, picture.size, boundsOptions)

                val target = 600
                var sampleSize = 1
                while (boundsOptions.outWidth / (sampleSize * 2) >= target &&
                    boundsOptions.outHeight / (sampleSize * 2) >= target
                ) {
                    sampleSize *= 2
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bitmap = BitmapFactory.decodeByteArray(picture, 0, picture.size, decodeOptions)

                if (bitmap != null) {
                    l1Cache.put(cacheKey, bitmap)
                    try {
                        FileOutputStream(diskFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                        }
                    } catch (_: Exception) {}

                    return DrawableResult(
                        drawable = BitmapDrawable(context.resources, bitmap),
                        isSampled = true,
                        dataSource = DataSource.DISK
                    )
                }
            } else {
                negativeCache.add(cacheKey)
                try { noArtFile.createNewFile() } catch (_: Exception) {}
            }
        } catch (_: Exception) {
            negativeCache.add(cacheKey)
            try { noArtFile.createNewFile() } catch (_: Exception) {}
        } finally {
            try { mmr.release() } catch (_: Exception) {}
        }

        return null
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val scheme = data.scheme ?: ""
            val path = (data.path ?: "").lowercase()

            val isAudioContent = scheme == "content" && (path.contains("audio") || path.contains("media") || data.authority?.endsWith(".documents") == true)
            val isAudioExtension = path.endsWith(".flac") || path.endsWith(".dsf") ||
                    path.endsWith(".dff") || path.endsWith(".alac") || path.endsWith(".m4a") ||
                    path.endsWith(".wav") || path.endsWith(".mp3")
            val isAudioFile = scheme == "file" && isAudioExtension

            return if (isAudioContent || isAudioFile) {
                AudioCoverFetcher(data, context, options)
            } else {
                null // Return null so Coil's built-in file decoder handles JPEGs and PNGs directly!
            }
        }
    }
}
