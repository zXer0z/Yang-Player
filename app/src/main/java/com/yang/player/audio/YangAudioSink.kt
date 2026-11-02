package com.yang.player.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Media3 decodes/demuxes; Yang owns PCM processing, conversion, buffering and output. */
@UnstableApi
class YangAudioSink(context: Context) : AudioSink {
    private val store = AudioEngineStore.get(context)
    private var listener: AudioSink.Listener? = null
    private var format: Format? = null
    private var activeFormat: Format? = null
    private var profile: EngineProfile? = null
    private var revision = -1L
    private var attributes = AudioAttributes.DEFAULT
    private var requestedDevice: AudioDeviceInfo? = null
    private var sessionId = 0
    private var track: AudioTrack? = null
    private var nativeOutput = 0L
    private var dsp = 0L
    private var outputRate = 48000
    private var outputChannels = 2
    private var outputEncoding = 2
    private var bufferFrames = 0
    private var backendName = ""
    private var exclusive = false
    private var playing = false
    private var outputStarted = false
    private var volume = 1f
    private var eos = false
    private var tailQueued = false
    private var discontinuity = false
    private var firstTimestamp = C.TIME_UNSET
    private var submitted = 0L
    private var lastHead = 0L
    private var wraps = 0L
    private var lastPosition = 0L
    private var lastReport = 0L
    private var note = ""
    private var nativeFailure: String? = null
    private var fallbackKey: String? = null
    private var fallbackReason = ""
    private var lastRequestKey: String? = null
    private var transitionKey: String? = null
    private var transitionAt = 0L
    private var handoffAt = 0L
    fun useCompatibleOutput(reason: String) { fallbackKey = requestKey(store.profile()); fallbackReason = reason; flush() }
    private var progressHead = 0L
    private var progressAt = 0L
    private fun requestKey(settings: EngineProfile) = "${settings.route}:${settings.backend}:${settings.rate}:${settings.encoding}:${settings.bufferMs}"
    private var pending = ByteBuffer.allocateDirect(1024 * 1024).order(ByteOrder.LITTLE_ENDIAN).apply { limit(0) }
    private val silence = ByteBuffer.allocateDirect(64 * 8 * 4).order(ByteOrder.LITTLE_ENDIAN)
    private val inputCopy = ByteBuffer.allocateDirect(1024 * 8 * 4).order(ByteOrder.LITTLE_ENDIAN)

    override fun setListener(listener: AudioSink.Listener) { this.listener = listener }
    override fun supportsFormat(format: Format) = getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED
    override fun getFormatSupport(format: Format): Int = if (format.sampleMimeType == MimeTypes.AUDIO_RAW &&
        format.channelCount in 1..8 && format.pcmEncoding in listOf(C.ENCODING_PCM_8BIT, C.ENCODING_PCM_16BIT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT)) AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY else AudioSink.SINK_FORMAT_UNSUPPORTED
    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        if (!supportsFormat(inputFormat) || inputFormat.sampleRate !in 8000..768000) throw AudioSink.ConfigurationException("PCM no compatible con el motor Yang", inputFormat)
        // Standard mono/stereo maps are retained. Multichannel is downmixed in C.
        if (outputChannels != null && outputChannels.indices.any { outputChannels[it] != it }) throw AudioSink.ConfigurationException("Mapa de canales no compatible", inputFormat)
        format = inputFormat
        eos = false; tailQueued = false
    }
    private fun width(encoding: Int) = when (encoding) { C.ENCODING_PCM_8BIT -> 1; C.ENCODING_PCM_16BIT -> 2; C.ENCODING_PCM_24BIT -> 3; else -> 4 }
    private fun closeOutput() {
        track?.let { runCatching { it.pause(); it.flush() }; it.release() }; track = null
        if (nativeOutput != 0L) { NativeAudio.closeOutput(nativeOutput); nativeOutput = 0 }
        if (dsp != 0L) { NativeAudio.destroy(dsp); dsp = 0 }
        submitted = 0; wraps = 0; lastHead = 0; lastPosition = 0
        outputStarted = false; handoffAt = 0
        progressHead = 0; progressAt = SystemClock.elapsedRealtime(); nativeFailure = null
        pending.clear(); pending.limit(0)
    }
    private fun open(input: Format, settings: EngineProfile) {
        closeOutput()
        val key = requestKey(settings)
        if (lastRequestKey != null && lastRequestKey != key) { fallbackKey = null; fallbackReason = "" }
        lastRequestKey = key
        outputChannels = min(2, input.channelCount)
        val device = requestedDevice ?: store.predictedDevice()
        var last: Exception? = null
        val candidates = if (fallbackKey == requestKey(settings)) listOf("AudioTrack") else when(settings.backend) {
            OutputBackend.HI_RES -> listOf("AAudio exclusivo", "AAudio", "AudioTrack")
            OutputBackend.AAUDIO -> listOf("AAudio", "AudioTrack")
            OutputBackend.OPENSL -> listOf("OpenSL ES", "AudioTrack")
            else -> listOf("AudioTrack")
        }
        var opened = false
        outer@ for (backend in candidates) {
            val rates = if (backend == "OpenSL ES") listOf(min(settings.rate, 48000), 48000, 44100).distinct()
                else if (settings.backend == OutputBackend.HI_RES && backend.startsWith("AAudio")) listOf(settings.rate)
                else listOf(settings.rate, 48000, 44100).distinct().filter { it <= settings.route.ceiling }
            val encodings = if (backend == "OpenSL ES") listOf(2)
                else if (settings.backend == OutputBackend.HI_RES && backend.startsWith("AAudio")) listOf(settings.encoding)
                else when(settings.route) {
                AudioRoute.SPEAKER -> listOf(2)
                AudioRoute.BLUETOOTH -> listOf(settings.encoding, 2).distinct()
                else -> listOf(settings.encoding, 4, 2).distinct()
            }
            for (rate in rates) for (encoding in encodings) {
                if (Build.VERSION.SDK_INT < 31 && encoding in listOf(21, 22)) continue
                try {
                    if (backend.startsWith("AAudio") || backend == "OpenSL ES") {
                        nativeOutput = NativeAudio.openOutput(if (backend == "OpenSL ES") 2 else 1, rate, outputChannels, encoding, settings.bufferMs,
                            if (backend == "OpenSL ES") 0 else device?.id ?: 0, backend == "AAudio exclusivo")
                        check(nativeOutput != 0L) { "Salida nativa rechazada" }
                        val info = NativeAudio.outputInfo(nativeOutput)
                        if (backend.startsWith("AAudio") && device != null && info[6] > 0 && info[6] != device.id) {
                            NativeAudio.closeOutput(nativeOutput); nativeOutput = 0
                            continue
                        }
                        outputRate = info[0]; outputChannels = info[1]; outputEncoding = info[2]; bufferFrames = info[3]; exclusive = info[4] != 0
                        if (outputRate > settings.route.ceiling || (settings.route == AudioRoute.BLUETOOTH && outputEncoding !in listOf(2, 21))) {
                            NativeAudio.closeOutput(nativeOutput); nativeOutput = 0; continue
                        }
                    } else {
                        val mask = if (outputChannels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
                        val minimum = AudioTrack.getMinBufferSize(rate, mask, encoding)
                        check(minimum > 0) { "Formato AudioTrack no compatible" }
                        val frames = max((minimum + outputChannels * width(encoding) - 1) / (outputChannels * width(encoding)), rate * settings.bufferMs / 1000)
                        val builder = AudioTrack.Builder().setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(attributes.usage).setContentType(attributes.contentType).build())
                            .setAudioFormat(AudioFormat.Builder().setEncoding(encoding).setSampleRate(rate).setChannelMask(mask).build())
                            .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(frames * outputChannels * width(encoding))
                            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_NONE)
                        if (sessionId > 0) builder.setSessionId(sessionId)
                        val candidate = builder.build()
                        if (candidate.state != AudioTrack.STATE_INITIALIZED) { candidate.release(); error("AudioTrack no inicializado") }
                        if (device != null && !candidate.setPreferredDevice(device)) note = "Android eligió la ruta de salida."
                        track = candidate
                        outputRate = candidate.sampleRate; outputEncoding = candidate.audioFormat; outputChannels = candidate.channelCount
                        bufferFrames = candidate.bufferSizeInFrames; exclusive = false
                    }
                    backendName = if (backend.startsWith("AAudio")) if (exclusive) "AAudio · exclusivo" else "AAudio · compartido" else backend
                    opened = true; break@outer
                } catch (error: Exception) { last = error; closeOutput() }
            }
        }
        if (!opened) throw AudioSink.InitializationException(0, settings.rate, outputChannels, 0, input, false, last)
        note = buildList {
            if (fallbackKey == requestKey(settings)) add("$fallbackReason Se usa AudioTrack para mantener la reproducción.")
            if (settings.backend == OutputBackend.HI_RES && !exclusive) add("No se obtuvo acceso exclusivo; se usa $backendName.")
            if (outputRate != settings.rate || outputEncoding != settings.encoding) add("Formato adaptado a lo que aceptó la salida.")
            if (backendName == "OpenSL ES") add("OpenSL ES usa PCM de 16 bits hasta 48 kHz en esta implementación.")
            if (settings.route == AudioRoute.BLUETOOTH) add("PCM de la app; el códec Bluetooth lo negocia Android.")
        }.joinToString(" ")
        dsp = NativeAudio.create(input.sampleRate, input.channelCount, input.pcmEncoding, outputRate, outputChannels, outputEncoding)
        if (dsp == 0L) { closeOutput(); throw AudioSink.InitializationException(0, outputRate, outputChannels, 0, input, false, IllegalStateException("No se pudo crear el DSP")) }
        activeFormat = input; profile = settings; revision = store.revision
        applyDsp(settings)
        if (playing) startOutput()
        android.util.Log.i("YangAudio", "Opened ${settings.backend} -> $backendName rate=$outputRate encoding=$outputEncoding playing=$playing device=${device?.id}")
        report()
    }
    private fun applyDsp(settings: EngineProfile) {
        if (dsp != 0L) NativeAudio.dsp(dsp, settings.bands.map { it.hz }.toFloatArray(), settings.bands.map { it.gain }.toFloatArray(), settings.bands.map { it.q }.toFloatArray(), settings.preamp,
            settings.eq, settings.headroom, settings.limiter, volume)
    }
    private fun startOutput() {
        if (outputStarted) return
        if (nativeOutput != 0L) {
            if (backendName == "OpenSL ES" && submitted == 0L) return
            // Start native streams even when a driver accepts no pre-start writes.
            val result = NativeAudio.playing(nativeOutput, true)
            if (result < 0) { nativeFailure = "La salida nativa rechazó el arranque ($result)."; return }
        } else {
            if (track == null || submitted == 0L) return
            track?.play()
        }
        progressAt = SystemClock.elapsedRealtime(); outputStarted = true
    }
    override fun play() { playing = true; startOutput() }
    override fun pause() { playing = false; outputStarted = false; progressAt = SystemClock.elapsedRealtime(); track?.pause(); if (nativeOutput != 0L) NativeAudio.playing(nativeOutput, false) }
    private fun head(): Long {
        val count = track?.let {
            val current = it.playbackHeadPosition.toLong() and 0xffffffffL
            if (current < lastHead) wraps++
            lastHead = current
            current + (wraps shl 32)
        } ?: if (nativeOutput != 0L) NativeAudio.position(nativeOutput) else 0
        return count.coerceIn(0, submitted)
    }
    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        if (firstTimestamp == C.TIME_UNSET) return AudioSink.CURRENT_POSITION_NOT_SET
        val position = firstTimestamp + head() * 1_000_000L / outputRate
        lastPosition = max(lastPosition, position)
        if (SystemClock.elapsedRealtime() - lastReport > 500) report()
        checkNativeProgress()
        return lastPosition
    }
    private fun checkNativeProgress() {
        if (nativeOutput == 0L || !playing || nativeFailure != null) return
        val now = SystemClock.elapsedRealtime()
        val consumed = head()
        if (consumed != progressHead || (submitted == consumed && !pending.hasRemaining())) {
            progressHead = consumed; progressAt = now
        }
        val state = NativeAudio.outputInfo(nativeOutput).getOrNull(7) ?: -1
        if (state < 0) nativeFailure = "La salida nativa se desconectó."
        else if (now - progressAt > max(1500L, (profile?.bufferMs ?: 200) * 4L)) nativeFailure = "La salida nativa dejó de consumir audio."
    }
    private fun recoverNative(input: Format, settings: EngineProfile, timestamp: Long) {
        val reason = nativeFailure ?: return
        fallbackKey = requestKey(settings); fallbackReason = reason
        android.util.Log.w("YangAudio", "$reason Switching to AudioTrack")
        open(input, settings)
        firstTimestamp = timestamp; lastPosition = timestamp
        listener?.onPositionDiscontinuity()
    }
    private fun drainPending(): Boolean {
        if (!pending.hasRemaining()) return true
        val bytes = if (track != null) track!!.write(pending, pending.remaining(), AudioTrack.WRITE_NON_BLOCKING) else {
            val frames = NativeAudio.write(nativeOutput, pending, pending.position(), pending.remaining() / (outputChannels * width(outputEncoding)))
            if (frames > 0) pending.position(pending.position() + frames * outputChannels * width(outputEncoding))
            if (frames < 0) frames else frames * outputChannels * width(outputEncoding)
        }
        if (bytes < 0) {
            if (nativeOutput != 0L) { nativeFailure = "La salida nativa rechazó el audio ($bytes)."; return false }
            throw AudioSink.WriteException(bytes, checkNotNull(format), true)
        }
        submitted += bytes / (outputChannels * width(outputEncoding))
        if (bytes > 0 && playing) startOutput()
        return nativeFailure == null && !pending.hasRemaining()
    }
    /** Complete a partly written DSP block before handing the device to Yin. */
    fun drainForHandoff(): Boolean {
        val drained = drainPending() && !hasPendingData()
        if (drained || !playing) { handoffAt = 0; return true }
        val now = SystemClock.elapsedRealtime()
        if (handoffAt == 0L) handoffAt = now
        return now - handoffAt >= max(500L, (profile?.bufferMs ?: 200) * 2L)
    }
    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        val input = format ?: return false
        val settings = store.profile()
        checkNativeProgress()
        if (nativeFailure != null) recoverNative(input, settings, presentationTimeUs)
        val structuralChange = activeFormat != input || profile?.let { it.backend != settings.backend || it.rate != settings.rate || it.encoding != settings.encoding || it.bufferMs != settings.bufferMs || it.route != settings.route } != false
        if (dsp == 0L || structuralChange) {
            val key = requestKey(settings)
            if (transitionKey != key) { transitionKey = key; transitionAt = SystemClock.elapsedRealtime() }
            // A starving/paused track may retain a final partial burst forever. A hot
            // switch must never wait indefinitely for that exact last frame count.
            val drained = drainPending() && !hasPendingData()
            val budget = max(500L, (profile?.bufferMs ?: 200) * 2L)
            if (dsp != 0L && !drained && playing && profile?.route == settings.route && SystemClock.elapsedRealtime() - transitionAt < budget) return false
            val anchor = if (firstTimestamp == C.TIME_UNSET) presentationTimeUs else firstTimestamp + head() * 1_000_000L / outputRate
            android.util.Log.i("YangAudio", "Switch ${profile?.backend} -> ${settings.backend}, drained=$drained playing=$playing submitted=$submitted head=${head()}")
            open(input, settings)
            firstTimestamp = anchor; lastPosition = anchor; transitionKey = null
            listener?.onPositionDiscontinuity()
        } else {
            transitionKey = null
            if (!drainPending()) return false
            if (store.revision != revision) { applyDsp(settings); revision = store.revision; profile = settings }
        }
        if (discontinuity) { firstTimestamp = presentationTimeUs - submitted * 1_000_000L / outputRate; lastPosition = firstTimestamp; discontinuity = false; listener?.onPositionDiscontinuity() }
        if (!buffer.hasRemaining()) return true
        val frameBytes = input.channelCount * width(input.pcmEncoding)
        while (buffer.hasRemaining()) {
            val frames = min(1024, buffer.remaining() / frameBytes)
            if (frames == 0) throw AudioSink.WriteException(-1, input, false)
            val nativeInput = if (buffer.isDirect) buffer else inputCopy.apply {
                clear(); put(buffer.duplicate().apply { limit(position() + frames * frameBytes) }); flip()
            }
            val bytes = NativeAudio.process(dsp, nativeInput, if (buffer.isDirect) buffer.position() else 0, frames, pending)
            if (bytes < 0) throw AudioSink.WriteException(-1, input, false)
            buffer.position(buffer.position() + frames * frameBytes)
            pending.position(0); pending.limit(bytes)
            if (!drainPending()) return !buffer.hasRemaining()
        }
        return true
    }
    override fun playToEndOfStream() {
        checkNativeProgress()
        if (nativeFailure != null) {
            val anchor = if (firstTimestamp == C.TIME_UNSET) 0 else firstTimestamp + head() * 1_000_000L / outputRate
            format?.let { recoverNative(it, store.profile(), anchor) }
            eos = true; return
        }
        if (!drainPending()) return
        val input = activeFormat
        if (!tailQueued && input != null && dsp != 0L && input.sampleRate != outputRate) {
            silence.clear()
            val zero = if (input.pcmEncoding == C.ENCODING_PCM_8BIT) 128.toByte() else 0.toByte()
            while (silence.hasRemaining()) silence.put(zero)
            val bytes = NativeAudio.process(dsp, silence, 0, 64, pending)
            if (bytes < 0) throw AudioSink.WriteException(-1, input, false)
            pending.position(0); pending.limit(bytes); tailQueued = true
            if (!drainPending()) return
        }
        eos = true
    }
    override fun isEnded() = eos && !hasPendingData()
    override fun hasPendingData() = pending.hasRemaining() || submitted > head()
    override fun handleDiscontinuity() { discontinuity = true }
    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) { /* Speed/pitch are currently fixed at unity. */ }
    override fun getPlaybackParameters(): PlaybackParameters = PlaybackParameters.DEFAULT
    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) { listener?.onSkipSilenceEnabledChanged(false) }
    override fun getSkipSilenceEnabled() = false
    override fun setAudioAttributes(audioAttributes: AudioAttributes) { attributes = audioAttributes }
    override fun getAudioAttributes() = attributes
    override fun setAudioSessionId(audioSessionId: Int) { sessionId = audioSessionId }
    override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) {}
    override fun setPreferredDevice(audioDeviceInfo: AudioDeviceInfo?) { requestedDevice = audioDeviceInfo; track?.setPreferredDevice(audioDeviceInfo) }
    override fun enableTunnelingV21() { throw IllegalStateException("El DSP Yang no usa tunneling") }
    override fun disableTunneling() {}
    override fun setVolume(volume: Float) { this.volume = volume.coerceIn(0f, 1f); profile?.let(::applyDsp) }
    override fun flush() { closeOutput(); activeFormat = null; firstTimestamp = C.TIME_UNSET; eos = false; tailQueued = false; discontinuity = false }
    override fun reset() { flush(); format = null }
    override fun release() { reset() }
    private fun report() {
        lastReport = SystemClock.elapsedRealtime()
        val input = activeFormat ?: return
        val settings = profile ?: return
        val nativeInfo = if (nativeOutput != 0L) NativeAudio.outputInfo(nativeOutput) else null
        val device = track?.routedDevice ?: store.predictedDevice()
        store.report(EngineTelemetry(backendName, device?.productName?.toString() ?: settings.route.label, settings.route,
            settings.rate, settings.encoding, outputRate, outputEncoding, outputChannels, bufferFrames,
            track?.underrunCount ?: nativeInfo?.getOrNull(5) ?: -1, input.sampleRate, input.pcmEncoding, note = note, exclusive = exclusive))
    }
}
