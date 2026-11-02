package com.yang.player.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

enum class OutputBackend(val label: String) { HI_RES("Yang Hi-Res"), AUDIO_TRACK("AudioTrack"), AAUDIO("AAudio"), OPENSL("OpenSL ES") }
enum class AudioRoute(val label: String, val ceiling: Int) { SPEAKER("Altavoz", 48000), BLUETOOTH("Bluetooth", 96000), WIRED("Jack / AUX", 192000), USB("USB", 192000) }
// PCM codes match Android's AudioFormat constants.
fun pcmName(encoding: Int) = when (encoding) { 2 -> "PCM 16 bits"; 21 -> "PCM 24 bits"; 22 -> "PCM 32 bits"; 4 -> "Float 32 bits"; else -> "Sin datos" }
data class EqBand(val hz: Float, val gain: Float = 0f, val q: Float = 1.4f)
val defaultBands = listOf(31f, 63f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f).map(::EqBand)
data class EngineProfile(val route: AudioRoute, val backend: OutputBackend = if (route == AudioRoute.WIRED || route == AudioRoute.USB) OutputBackend.HI_RES else OutputBackend.AUDIO_TRACK,
    val rate: Int = route.ceiling, val encoding: Int = when(route) { AudioRoute.SPEAKER -> 2; AudioRoute.BLUETOOTH -> 21; else -> 4 },
    val bufferMs: Int = 200, val eq: Boolean = false, val preamp: Float = 0f, val headroom: Boolean = true, val limiter: Boolean = true, val bands: List<EqBand> = defaultBands) {
    fun valid(): EngineProfile = copy(rate = rate.coerceIn(8000, route.ceiling),
        encoding = when (route) { AudioRoute.SPEAKER -> 2; AudioRoute.BLUETOOTH -> if (encoding == 2) 2 else 21; else -> encoding.takeIf { it in listOf(2, 21, 22, 4) } ?: 4 },
        bufferMs = bufferMs.coerceIn(40, 800), preamp = preamp.coerceIn(-24f, 12f),
        bands = bands.take(10).map { it.copy(hz = it.hz.coerceIn(20f, 20000f), gain = it.gain.coerceIn(-12f, 12f), q = it.q.coerceIn(0.3f, 6f)) })
}
data class EngineTelemetry(val backend: String = "Sin reproducción", val device: String = "", val route: AudioRoute = AudioRoute.SPEAKER,
    val requestedRate: Int = 0, val requestedEncoding: Int = 0, val rate: Int = 0, val encoding: Int = 0,
    val channels: Int = 0, val bufferFrames: Int = 0, val underruns: Int = 0, val inputRate: Int = 0, val inputEncoding: Int = 0,
    val decoder: String = "", val sourceMime: String = "", val bitrate: Int = 0, val note: String = "", val exclusive: Boolean = false)

class AudioEngineStore private constructor(context: Context) {
    private val preferences = context.getSharedPreferences("yang_audio_engine_v1", Context.MODE_PRIVATE)
    private val manager = context.getSystemService(AudioManager::class.java)
    private val _profiles = MutableStateFlow(AudioRoute.entries.associateWith(::load))
    val profiles = _profiles.asStateFlow()
    private val _route = MutableStateFlow(routeOf(predictedDevice()))
    val route = _route.asStateFlow()
    private val _telemetry = MutableStateFlow(EngineTelemetry())
    val telemetry = _telemetry.asStateFlow()
    @Volatile var revision = 0L; private set
    init {
        manager.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) { refreshRoute() }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) { refreshRoute() }
        }, Handler(Looper.getMainLooper()))
    }
    fun predictedDevice(): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT >= 33) runCatching {
            manager.getAudioDevicesForAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build()).firstOrNull()
        }.getOrNull()?.let { return it }
        return manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).maxByOrNull { when(routeOf(it)) { AudioRoute.USB -> 4; AudioRoute.WIRED -> 3; AudioRoute.BLUETOOTH -> 2; else -> if (it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) 1 else -1 } }
    }
    @Synchronized private fun refreshRoute() { _route.value = routeOf(predictedDevice()); revision++ }
    fun profile() = _profiles.value.getValue(_route.value)
    @Synchronized fun preview(profile: EngineProfile) {
        val valid = profile.valid()
        _profiles.value = _profiles.value + (valid.route to valid)
        revision++
    }
    @Synchronized fun save(profile: EngineProfile) {
        val valid = profile.valid()
        _profiles.value = _profiles.value + (valid.route to valid)
        val json = JSONObject().put("backend", valid.backend.name).put("rate", valid.rate).put("encoding", valid.encoding).put("buffer", valid.bufferMs)
            .put("eq", valid.eq).put("preamp", valid.preamp).put("headroom", valid.headroom).put("limiter", valid.limiter)
            .put("bands", JSONArray().apply { valid.bands.forEach { put(JSONObject().put("hz", it.hz).put("gain", it.gain).put("q", it.q)) } })
        preferences.edit().putString(valid.route.name, json.toString()).apply(); revision++
    }
    fun report(value: EngineTelemetry) { _telemetry.update { value.copy(decoder = it.decoder, sourceMime = it.sourceMime, bitrate = it.bitrate) } }
    fun decoder(name: String) { _telemetry.update { it.copy(decoder = name) } }
    fun source(mime: String, bitrate: Int) { _telemetry.update { it.copy(sourceMime = mime, bitrate = bitrate.coerceAtLeast(0)) } }
    private fun load(route: AudioRoute): EngineProfile = runCatching {
        val j = JSONObject(preferences.getString(route.name, "{}") ?: "{}")
        val base = EngineProfile(route)
        base.copy(backend = OutputBackend.valueOf(j.optString("backend", base.backend.name)), rate = j.optInt("rate", base.rate), encoding = j.optInt("encoding", base.encoding), bufferMs = j.optInt("buffer", 200),
            eq = j.optBoolean("eq"), preamp = j.optDouble("preamp", 0.0).toFloat(), headroom = j.optBoolean("headroom", true), limiter = j.optBoolean("limiter", true),
            bands = j.optJSONArray("bands")?.takeIf { it.length() == 10 }?.let { a -> (0..9).map { i -> a.getJSONObject(i).let { EqBand(it.getDouble("hz").toFloat(), it.getDouble("gain").toFloat(), it.getDouble("q").toFloat()) } } } ?: defaultBands).valid()
    }.getOrDefault(EngineProfile(route))
    companion object {
        @Volatile private var instance: AudioEngineStore? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: AudioEngineStore(context.applicationContext).also { instance = it } }
        fun routeOf(device: AudioDeviceInfo?) = when(device?.type) {
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_LINE_ANALOG -> AudioRoute.WIRED
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> AudioRoute.BLUETOOTH
            AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY -> AudioRoute.USB
            else -> AudioRoute.SPEAKER
        }
    }
}
