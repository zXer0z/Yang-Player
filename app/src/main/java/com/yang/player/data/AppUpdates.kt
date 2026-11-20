package com.yang.player.data

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.yang.player.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

internal data class YangRelease(val version: String, val notes: String, val size: Long, val sha256: String) {
    val fileName = "Yang-Player-$version.apk"
    val url = URI("https://github.com/zXer0z/Yang-Player/releases/download/v$version/$fileName")
}

internal class AppUpdates(private val context: Context) {
    private val prefs = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)

    suspend fun check(force: Boolean): YangRelease? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val lastCheck = prefs.getLong("last_check", 0L)
        if (!force && now - lastCheck in 0L until 24 * 60 * 60 * 1000L) return@withContext null
        val response = connect(URI("https://api.github.com/repos/zXer0z/Yang-Player/releases/latest"), api = true)
        val release = try {
            response.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8_192)
                while (true) {
                    coroutineContext.ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 512 * 1024) throw IOException("Respuesta demasiado grande.")
                    output.write(buffer, 0, count)
                }
                JSONObject(output.toByteArray().toString(Charsets.UTF_8))
            }
        } finally {
            response.disconnect()
        }
        prefs.edit().putLong("last_check", now).apply()
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) return@withContext null
        val version = release.optString("tag_name").takeIf { it.startsWith("v") }?.drop(1)
            ?: return@withContext null
        if (!isNewerYangVersion(version, BuildConfig.VERSION_NAME)) return@withContext null
        val assets = release.getJSONArray("assets")
        val asset = (0 until assets.length()).asSequence().map(assets::getJSONObject).singleOrNull {
            it.optString("name") == "Yang-Player-$version.apk" && it.optString("state") == "uploaded"
        } ?: throw IOException("La publicación todavía no tiene un APK.")
        val size = asset.optLong("size")
        val hash = asset.optString("digest").removePrefix("sha256:")
        if (size !in 1..MAX_APK_BYTES || !hash.matches(Regex("[0-9a-f]{64}"))) {
            throw IOException("La publicación no trae un APK verificable.")
        }
        YangRelease(version, release.optString("body").trim().take(1_500), size, hash)
    }

    suspend fun download(release: YangRelease, progress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val directory = context.cacheDir.resolve("app-updates")
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("No se pudo preparar la descarga.")
        val target = directory.resolve(release.fileName)
        val partial = File.createTempFile("update-", ".apk", directory)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val connection = connect(release.url)
            try {
                connection.inputStream.use { input ->
                    partial.outputStream().buffered().use { output ->
                        val buffer = ByteArray(128 * 1024)
                        var bytes = 0L
                        var lastProgress = -1
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            bytes += count
                            if (bytes > release.size) throw IOException("El tamaño del APK no coincide.")
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            val percent = (bytes * 100 / release.size).toInt()
                            if (percent != lastProgress) {
                                progress(percent)
                                lastProgress = percent
                            }
                        }
                        if (bytes != release.size || digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } != release.sha256) {
                            throw IOException("La integridad del APK no coincide con GitHub.")
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
            verify(partial, release.version)
            if (!partial.renameTo(target)) throw IOException("No se pudo preparar el APK.")
            directory.listFiles()?.filter { it != target && it.name.startsWith("Yang-Player-") && it.extension == "apk" }
                ?.forEach(File::delete)
            target
        } finally {
            partial.delete()
        }
    }

    @Suppress("DEPRECATION")
    private fun verify(file: File, version: String) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val manager = context.packageManager
        val installed = manager.getPackageInfo(context.packageName, flags)
        val apk = manager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw IOException("Android no pudo leer el APK.")
        fun PackageInfo.code() = if (Build.VERSION.SDK_INT >= 28) longVersionCode else versionCode.toLong()
        fun PackageInfo.signers() = if (Build.VERSION.SDK_INT >= 28) {
            signingInfo?.apkContentsSigners?.toSet().orEmpty()
        } else signatures?.toSet().orEmpty()
        if (apk.packageName != installed.packageName || apk.versionName != version || apk.code() <= installed.code() ||
            installed.signers().isEmpty() || apk.signers() != installed.signers()
        ) throw IOException("La versión o la firma del APK no coincide con Yang.")
    }

    private fun connect(initial: URI, api: Boolean = false): HttpURLConnection {
        var url = initial
        repeat(6) {
            val hosts = if (api) setOf("api.github.com") else setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
            if (url.scheme != "https" || url.host !in hosts || url.userInfo != null || url.port !in listOf(-1, 443)) {
                throw IOException("GitHub devolvió un destino no válido.")
            }
            val connection = url.toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 12_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("User-Agent", "YangPlayer/${BuildConfig.VERSION_NAME}")
            if (api) connection.setRequestProperty("Accept", "application/vnd.github+json")
            try {
                val status = connection.responseCode
                if (status == 200) return connection
                if (status !in 300..399) throw IOException("GitHub respondió con HTTP $status.")
                url = url.resolve(connection.getHeaderField("Location") ?: throw IOException("Redirección incompleta."))
            } catch (error: Exception) {
                connection.disconnect()
                throw error
            }
            connection.disconnect()
        }
        throw IOException("Demasiadas redirecciones al consultar GitHub.")
    }

    companion object {
        private const val MAX_APK_BYTES = 128L * 1024 * 1024
    }
}

internal fun isNewerYangVersion(candidate: String, installed: String): Boolean {
    val pattern = Regex("""(?:0|[1-9][0-9]{0,8})(?:\.(?:0|[1-9][0-9]{0,8})){2}""")
    if (!pattern.matches(candidate) || !pattern.matches(installed)) return false
    val current = installed.split('.').map(String::toInt)
    val offered = candidate.split('.').map(String::toInt)
    for (index in current.indices) {
        if (offered[index] != current[index]) return offered[index] > current[index]
    }
    return false
}
