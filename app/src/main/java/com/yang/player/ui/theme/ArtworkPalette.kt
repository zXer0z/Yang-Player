package com.yang.player.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import coil.imageLoader
import coil.request.ImageRequest
import com.yang.player.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

data class ArtworkPalette(
    val topColor: Color = Color(0xFF485260),
    val midColor: Color = Color(0xFF2E3642),
    val bottomColor: Color = Color(0xFF1A2028),
    val accentColor: Color = Color(0xFFB4BECF),
    val ambientMesh: ImageBitmap? = null
)

private val artworkPalettes = android.util.LruCache<String, ArtworkPalette>(24)

@Composable
fun rememberArtworkPalette(song: Song?): ArtworkPalette {
    val context = LocalContext.current.applicationContext
    var palette by remember { mutableStateOf(ArtworkPalette()) }
    LaunchedEffect(song?.id, song?.artworkUri) {
        val current = song
        if (current == null) palette = ArtworkPalette() else {
            val key = "${current.id}:${current.artworkUri ?: current.mediaUri}"
            val cached = synchronized(artworkPalettes) { artworkPalettes.get(key) }
            if (cached != null) palette = cached else {
                val cover = withContext(Dispatchers.IO) { loadSongCoverBitmap(context, current) }
                val loaded = cover?.let { withContext(Dispatchers.Default) { buildAppleMusicAmbientPalette(it) } }
                if (loaded != null) {
                    synchronized(artworkPalettes) { artworkPalettes.put(key, loaded) }
                    palette = loaded
                } else palette = ArtworkPalette()
            }
        }
    }

    return palette
}

/**
 * Loads the song's album cover bitmap reliably from:
 * 1) Fast L2 disk cache (art_thumbs_v2/cover_<id>.jpg)
 * 2) Coil ImageLoader (L1 memory / custom AudioCoverFetcher)
 * 3) Direct MediaMetadataRetriever fallback on file path or MediaStore URI
 */
private suspend fun loadSongCoverBitmap(context: Context, song: Song): Bitmap? {
    // A downloaded cover takes priority over thumbnails extracted from the audio.
    song.artworkUri?.let { value ->
        val uri = Uri.parse(value)
        if (uri.scheme == "file" && uri.path?.contains("/completed_artwork/") == true) {
            BitmapFactory.decodeFile(uri.path)?.let { return it }
        }
    }
    // 1. Check L2 pre-warmed disk thumbnail first
    try {
        val cacheDir = File(context.cacheDir, "art_thumbs_v2")
        val diskFile = File(cacheDir, "cover_${song.id}.jpg")
        if (diskFile.exists() && diskFile.length() > 0) {
            val bmp = BitmapFactory.decodeFile(diskFile.absolutePath)
            if (bmp != null) return bmp
        }
    } catch (_: Exception) {}

    // 2. Try Coil ImageLoader with artworkUri or mediaUri
    try {
        val modelData = song.artworkUri?.takeIf { it.isNotBlank() } ?: song.mediaUri
        val req = ImageRequest.Builder(context)
            .data(Uri.parse(modelData))
            .size(128, 128)
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(req)
        val bmp = (result.drawable as? BitmapDrawable)?.bitmap
        if (bmp != null) return bmp
    } catch (_: Exception) {}

    // 3. Direct MediaMetadataRetriever extraction fallback
    val mmr = MediaMetadataRetriever()
    try {
        if (song.dataPath.isNotBlank() && File(song.dataPath).exists()) {
            mmr.setDataSource(song.dataPath)
        } else {
            mmr.setDataSource(context, Uri.parse(song.mediaUri))
        }
        val pic = mmr.embeddedPicture
        if (pic != null) {
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            return BitmapFactory.decodeByteArray(pic, 0, pic.size, opts)
        }
    } catch (_: Exception) {
    } finally {
        try { mmr.release() } catch (_: Exception) {}
    }

    return null
}

/**
 * Generates an Apple Music style 2D blurred ambient mesh + multi-stop palette
 * directly from the song's cover art bitmap.
 */
private fun buildAppleMusicAmbientPalette(source: Bitmap): ArtworkPalette {
    val gridSize = 64
    val small = Bitmap.createScaledBitmap(source, gridSize, gridSize, true)
    val pixels = IntArray(gridSize * gridSize)
    small.getPixels(pixels, 0, gridSize, 0, 0, gridSize, gridSize)

    val hsl = FloatArray(3)
    val bucketWeight = FloatArray(12)
    val bucketHueSum = FloatArray(12)
    val bucketSatSum = FloatArray(12)

    // Pass 1: Find dominant vibrant hue so neutral/white covers still take on the artwork's accent atmosphere
    for (px in pixels) {
        ColorUtils.colorToHSL(px, hsl)
        val s = hsl[1]
        val l = hsl[2]
        if (s > 0.10f && l in 0.10f..0.90f) {
            val b = ((hsl[0] / 30f).toInt()).coerceIn(0, 11)
            val w = s * (1f - abs(l - 0.5f))
            bucketWeight[b] += w
            bucketHueSum[b] += hsl[0] * w
            bucketSatSum[b] += s * w
        }
    }

    val bestBucket = bucketWeight.indices.maxByOrNull { bucketWeight[it] } ?: 0
    val dominantHue = if (bucketWeight[bestBucket] > 0f) {
        bucketHueSum[bestBucket] / bucketWeight[bestBucket]
    } else {
        215f // Cool slate fallback for purely monochrome B&W covers
    }
    val dominantSat = if (bucketWeight[bestBucket] > 0f) {
        (bucketSatSum[bestBucket] / bucketWeight[bestBucket]).coerceIn(0.20f, 0.65f)
    } else {
        0.18f
    }

    // Pass 2: Normalize luminance and saturation across the 64x64 grid so the entire canvas
    // has rich, atmospheric Apple Music tones without ever washing out white text or turning pitch black
    for (y in 0 until gridSize) {
        val vFrac = y.toFloat() / (gridSize - 1)
        // Top half stays slightly brighter to frame the cover art; bottom half deepens smoothly for controls
        val minL = 0.24f - vFrac * 0.10f   // 0.24 at top -> 0.14 at bottom
        val maxL = 0.44f - vFrac * 0.16f   // 0.44 at top -> 0.28 at bottom

        for (x in 0 until gridSize) {
            val idx = y * gridSize + x
            ColorUtils.colorToHSL(pixels[idx], hsl)

            if (hsl[1] < 0.08f) {
                // Infuse pure white/black/grey background pixels with a subtle tint of the cover's dominant hue
                hsl[0] = dominantHue
                hsl[1] = dominantSat * 0.55f
            } else {
                hsl[1] = (hsl[1] * 1.28f).coerceIn(0.18f, 0.82f)
            }

            hsl[2] = (minL + hsl[2] * (maxL - minL)).coerceIn(minL, maxL)
            pixels[idx] = ColorUtils.HSLToColor(hsl)
        }
    }

    // Pass 3: Multi-pass separable 2D box blur (radius = 12 on 64x64) -> ultra-silky Gaussian cloud mesh
    val temp = IntArray(gridSize * gridSize)
    repeat(4) {
        boxBlurHorizontal(pixels, temp, gridSize, gridSize, radius = 12)
        boxBlurVertical(temp, pixels, gridSize, gridSize, radius = 12)
    }

    val blurredBitmap = Bitmap.createBitmap(gridSize, gridSize, Bitmap.Config.ARGB_8888)
    blurredBitmap.setPixels(pixels, 0, gridSize, 0, 0, gridSize, gridSize)

    val topSample = pixels[(gridSize * 0.15f).toInt() * gridSize + gridSize / 2]
    val midSample = pixels[(gridSize * 0.50f).toInt() * gridSize + gridSize / 2]
    val botSample = pixels[(gridSize * 0.85f).toInt() * gridSize + gridSize / 2]

    return ArtworkPalette(
        accentColor = Color(ColorUtils.HSLToColor(floatArrayOf(
            dominantHue, dominantSat.coerceIn(0.30f, 0.65f), 0.74f
        ))),
        topColor = Color(topSample),
        midColor = Color(midSample),
        bottomColor = Color(botSample),
        ambientMesh = blurredBitmap.asImageBitmap()
    )
}

private fun boxBlurHorizontal(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int) {
    for (y in 0 until h) {
        val rowOffset = y * w
        for (x in 0 until w) {
            var r = 0
            var g = 0
            var b = 0
            var count = 0
            for (dx in -radius..radius) {
                val nx = (x + dx).coerceIn(0, w - 1)
                val c = src[rowOffset + nx]
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
                count++
            }
            dst[rowOffset + x] = (0xFF shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
        }
    }
}

private fun boxBlurVertical(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int) {
    for (y in 0 until h) {
        for (x in 0 until w) {
            var r = 0
            var g = 0
            var b = 0
            var count = 0
            for (dy in -radius..radius) {
                val ny = (y + dy).coerceIn(0, h - 1)
                val c = src[ny * w + x]
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
                count++
            }
            dst[y * w + x] = (0xFF shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
        }
    }
}
