package com.yang.player.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class ArtistImageRepository private constructor(private val context: Context) {
    private val photosDir = File(context.cacheDir, "artist_photos").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("artist_photo_cache_v1", Context.MODE_PRIVATE)
    private val memCache = ConcurrentHashMap<String, String>()
    private val _artistPhotos = MutableStateFlow<Map<String, String>>(emptyMap())
    val artistPhotos: StateFlow<Map<String, String>> = _artistPhotos.asStateFlow()

    private val fetchSemaphore = Semaphore(3)

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun isUnknown(name: String): Boolean {
        val norm = LibraryGrouping.normalized(name)
        return norm in setOf("", "<unknown>", "unknown", "desconocido", "artista desconocido", "various artists", "varios artistas")
    }

    /**
     * Cleans up collaboration tags (feat., ft., &, with) to get the primary artist name for search.
     */
    fun cleanArtistQuery(name: String): String {
        var clean = name.trim()
        val featRegex = Regex("\\s*\\(?\\b(?:feat\\.?|ft\\.?|featuring)\\b.*", RegexOption.IGNORE_CASE)
        clean = clean.replace(featRegex, "").trim()
        val withRegex = Regex("\\s*\\(?\\b(?:with|con)\\b.*", RegexOption.IGNORE_CASE)
        clean = clean.replace(withRegex, "").trim()
        return clean.ifBlank { name.trim() }
    }

    /**
     * Returns the cached local file URI if the artist photo is already downloaded.
     */
    fun getCachedPhotoUri(artistName: String): String? {
        val key = LibraryGrouping.normalized(artistName)
        if (key.isBlank() || isUnknown(key)) return null

        memCache[key]?.let { return it }

        val hash = md5(key)
        val file = File(photosDir, "artist_$hash.jpg")
        if (file.exists() && file.length() > 0) {
            val uri = Uri.fromFile(file).toString()
            memCache[key] = uri
            _artistPhotos.update { it + (key to uri) }
            return uri
        }
        return null
    }

    /**
     * Searches Deezer first (1000x1000 HD), then Wikipedia fallback.
     * Downloads and caches the photo locally so it works 100% offline subsequently.
     */
    suspend fun fetchArtistPhoto(artistName: String): String? = withContext(Dispatchers.IO) {
        val key = LibraryGrouping.normalized(artistName)
        if (key.isBlank() || isUnknown(key)) return@withContext null

        getCachedPhotoUri(artistName)?.let { return@withContext it }

        // Negative cache check (retry after 3 days)
        val cachedStatus = prefs.getString(key, null)
        if (cachedStatus != null && cachedStatus.startsWith("NOT_FOUND:")) {
            val timestamp = cachedStatus.substringAfter("NOT_FOUND:").toLongOrNull() ?: 0L
            if (System.currentTimeMillis() - timestamp < 3 * 24 * 3600 * 1000L) {
                return@withContext null
            }
        }

        fetchSemaphore.withPermit {
            // Re-check after acquiring semaphore in case another thread fetched it
            getCachedPhotoUri(artistName)?.let { return@withContext it }

            val cleanQuery = cleanArtistQuery(artistName)
            val photoUrl = searchDeezer(cleanQuery)
                ?: if (cleanQuery != artistName.trim()) searchDeezer(artistName.trim()) else null
                ?: searchWikipedia(cleanQuery)

            if (photoUrl.isNullOrBlank()) {
                prefs.edit().putString(key, "NOT_FOUND:${System.currentTimeMillis()}").apply()
                return@withContext null
            }

            val hash = md5(key)
            val targetFile = File(photosDir, "artist_$hash.jpg")
            val downloaded = downloadImageToFile(photoUrl, targetFile)
            if (downloaded && targetFile.exists() && targetFile.length() > 0) {
                val uri = Uri.fromFile(targetFile).toString()
                prefs.edit().putString(key, targetFile.name).apply()
                memCache[key] = uri
                _artistPhotos.update { it + (key to uri) }
                return@withContext uri
            }
            null
        }
    }

    private fun searchDeezer(query: String): String? {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://api.deezer.com/search/artist?q=$encoded")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 8000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "YangPlayer/1.0 (Android; Audiophile Music Player)")
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (conn.responseCode != 200) return null
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(jsonStr)
                val data = obj.optJSONArray("data") ?: return null
                if (data.length() == 0) return null

                val targetNorm = LibraryGrouping.normalized(query)

                // 1. Exact match with valid picture
                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    val itemName = item.optString("name")
                    if (LibraryGrouping.normalized(itemName) == targetNorm) {
                        val pic = extractDeezerPic(item)
                        if (pic != null) return pic
                    }
                }

                // 2. Similar name match
                for (i in 0 until minOf(data.length(), 6)) {
                    val item = data.optJSONObject(i) ?: continue
                    val itemName = item.optString("name")
                    val itemNorm = LibraryGrouping.normalized(itemName)
                    if (itemNorm.contains(targetNorm) || targetNorm.contains(itemNorm)) {
                        val pic = extractDeezerPic(item)
                        if (pic != null) return pic
                    }
                }

                // 3. Fallback to first item with a valid picture
                for (i in 0 until minOf(data.length(), 3)) {
                    val item = data.optJSONObject(i) ?: continue
                    val pic = extractDeezerPic(item)
                    if (pic != null) return pic
                }
                null
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractDeezerPic(item: JSONObject): String? {
        val candidates = listOf(
            item.optString("picture_xl"),
            item.optString("picture_big"),
            item.optString("picture_medium")
        )
        for (url in candidates) {
            if (url.isNotBlank() && !url.contains("/artist//") && !url.contains("/images/artist//")) {
                return url
            }
        }
        return null
    }

    private fun searchWikipedia(query: String): String? {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://en.wikipedia.org/api/rest_v1/page/summary/$encoded")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 6000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "YangPlayer/1.0 (Android; Audiophile Music Player)")
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (conn.responseCode != 200) return null
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(jsonStr)
                val original = obj.optJSONObject("originalimage")?.optString("source")
                val thumb = obj.optJSONObject("thumbnail")?.optString("source")
                original?.takeIf { it.isNotBlank() } ?: thumb?.takeIf { it.isNotBlank() }
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun downloadImageToFile(urlStr: String, destFile: File): Boolean {
        val tempFile = File(destFile.parentFile, "${destFile.name}.${System.currentTimeMillis()}.tmp")
        return try {
            val url = URL(urlStr)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 12000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "YangPlayer/1.0 (Android)")
            }
            try {
                if (conn.responseCode == 200) {
                    conn.inputStream.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (tempFile.length() > 0) {
                        if (destFile.exists()) destFile.delete()
                        tempFile.renameTo(destFile)
                    } else {
                        tempFile.delete()
                        false
                    }
                } else {
                    tempFile.delete()
                    false
                }
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            tempFile.delete()
            false
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: ArtistImageRepository? = null

        fun get(context: Context): ArtistImageRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ArtistImageRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

/**
 * Composable helper to get the artist photo URI reactively.
 * Reads memory/disk cache synchronously on first composition (instant, 0 ms),
 * and triggers background Deezer fetch if not yet cached.
 */
@Composable
fun rememberArtistPhoto(artistName: String?): String? {
    if (artistName.isNullOrBlank()) return null
    val context = LocalContext.current
    val repository = remember { ArtistImageRepository.get(context) }
    val normName = remember(artistName) { LibraryGrouping.normalized(artistName) }
    val photosMap by repository.artistPhotos.collectAsState()

    var localPhoto by remember(normName) {
        mutableStateOf(repository.getCachedPhotoUri(artistName))
    }

    LaunchedEffect(normName) {
        if (localPhoto == null) {
            val fetched = repository.fetchArtistPhoto(artistName)
            if (fetched != null) {
                localPhoto = fetched
            }
        }
    }

    return localPhoto ?: photosMap[normName]
}
