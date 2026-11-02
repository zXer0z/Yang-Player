package com.yang.player.data

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import org.json.JSONObject

data class SourcePcmInfo(val rate: Int = 0, val bits: Int = 0)

/** Cache real extractor metadata; a codec/extension is never a sample-rate measurement. */
class AudioSourceMetadata(private val context: Context) {
    private val file = context.cacheDir.resolve("audio_source_metadata_v1.json")
    private val entries = runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject())
    fun get(uri: Uri, stamp: String): SourcePcmInfo {
        val entry = entries.optJSONObject(uri.toString())
        if (entry?.optString("stamp") == stamp) return SourcePcmInfo(entry.optInt("rate"), entry.optInt("bits"))
        return read(context, uri).also { entries.put(uri.toString(), JSONObject().put("stamp", stamp).put("rate", it.rate).put("bits", it.bits)) }
    }
    fun save() { runCatching { file.writeText(entries.toString()) } }
    companion object {
        fun read(context: Context, uri: Uri): SourcePcmInfo {
            val extractor = MediaExtractor()
            return try {
                extractor.setDataSource(context, uri, null)
                var result = SourcePcmInfo()
                for (index in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(index)
                    if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") != true) continue
                    fun number(key: String) = if (format.containsKey(key)) runCatching { format.getInteger(key) }.getOrDefault(0) else 0
                    val bits = number("bits-per-sample").takeIf { it > 0 } ?: number("bits-per-raw-sample").takeIf { it > 0 }
                        ?: when(number(MediaFormat.KEY_PCM_ENCODING)) { 2 -> 16; 3 -> 8; 4, 22 -> 32; 21 -> 24; else -> 0 }
                    result = SourcePcmInfo(number(MediaFormat.KEY_SAMPLE_RATE), bits)
                    break
                }
                result
            } catch (_: Exception) { SourcePcmInfo() }
            finally { extractor.release() }
        }
    }
}
