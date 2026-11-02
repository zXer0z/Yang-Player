package com.yang.player.audio

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import java.nio.ByteBuffer

/** Sends untouched decoded PCM to Yin only after its output has acknowledged OPEN. */
@UnstableApi
class YinAudioSink private constructor(context: Context, private val local: YangAudioSink) : AudioSink by local {
    constructor(context: Context) : this(context, YangAudioSink(context))
    private val connector = YinConnector.get(context)
    private var channel: YinChannel? = null
    private var format: Format? = null
    private var listener: AudioSink.Listener? = null
    private var playing = false
    private var volume = 1f
    private var anchor = C.TIME_UNSET
    private var ended = false
    private var localConfigured = false
    private var currentBuffer: ByteBuffer? = null
    private var bufferTimestamp = C.TIME_UNSET
    private var bufferStart = 0
    override fun setListener(listener: AudioSink.Listener) { this.listener = listener; local.setListener(listener) }
    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        MusicSpectrum.reset()
        if (format != inputFormat || ended) closeRemote()
        format = inputFormat; ended = false
        local.configure(inputFormat, specifiedBufferSize, outputChannels); localConfigured = true
    }
    private fun closeRemote() { channel?.close(); channel = null; connector.stopped(); anchor = C.TIME_UNSET }
    private fun failed(error: Exception) {
        android.util.Log.w("YinBridge", "Fallo de sesión: ${error.message}")
        closeRemote(); connector.failed(error.message ?: "Yin no respondió")
        // Never bypass a missing preamp, limiter or other Yin processing.
        local.pause()
        local.flush()
        listener?.onPositionDiscontinuity()
    }
    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        val input = format ?: return false
        if (currentBuffer !== buffer || bufferTimestamp != presentationTimeUs) {
            currentBuffer = buffer; bufferTimestamp = presentationTimeUs; bufferStart = buffer.position()
        }
        if (channel?.eos == true) {
            try { if (!channel!!.status().getBoolean("drained")) return false } catch (error: Exception) { failed(error) }
            closeRemote(); ended = false
        }
        if (channel == null && connector.canOpen()) {
            // Finish the local queued audio before handing the DAC to the external engine.
            if (!local.drainForHandoff()) return false
            local.flush()
            try {
                channel = connector.open(input.sampleRate, input.channelCount, input.pcmEncoding, volume, playing)
                val frameBytes = input.channelCount * when(input.pcmEncoding) { 3 -> 1; 2 -> 2; 21 -> 3; else -> 4 }
                anchor = presentationTimeUs + (buffer.position() - bufferStart).coerceAtLeast(0).toLong() / frameBytes * 1_000_000L / input.sampleRate
                channel?.state(playing, volume)
                listener?.onPositionDiscontinuity()
            } catch (error: Exception) { failed(error); return false }
        }
        val remote = channel
        if (remote != null) {
            return try {
                val start = buffer.position()
                val consumed = remote.write(buffer)
                MusicSpectrum.offer(buffer, start, buffer.position(), input.sampleRate, input.channelCount, input.pcmEncoding)
                consumed
            } catch (error: Exception) { failed(error); false }
        }
        if (connector.mustWaitForYin()) return false
        if (!localConfigured) { local.configure(input, 0, null); localConfigured = true }
        if (playing) local.play()
        val start = buffer.position()
        val consumed = local.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        MusicSpectrum.offer(buffer, start, buffer.position(), input.sampleRate, input.channelCount, input.pcmEncoding)
        return consumed
    }
    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        val remote = channel ?: return local.getCurrentPositionUs(sourceEnded)
        return try { if (anchor == C.TIME_UNSET) AudioSink.CURRENT_POSITION_NOT_SET else anchor + remote.status().getLong("positionUs") }
        catch (error: Exception) { failed(error); AudioSink.CURRENT_POSITION_NOT_SET }
    }
    override fun hasPendingData(): Boolean {
        val remote = channel ?: return local.hasPendingData()
        return try {
            val state = remote.status()
            if (remote.eos) !state.getBoolean("drained") else {
                val input = format ?: return false
                val width = when(input.pcmEncoding) { 3 -> 1; 2 -> 2; 21 -> 3; else -> 4 }
                state.getLong("positionUs") + 1000 < remote.written * 1_000_000L / (input.sampleRate.toLong() * input.channelCount * width)
            }
        } catch (error: Exception) { failed(error); false }
    }
    override fun playToEndOfStream() {
        val remote = channel
        if (remote == null) { local.playToEndOfStream(); return }
        try { remote.finish(); ended = true } catch (error: Exception) { failed(error); ended = true }
    }
    override fun isEnded(): Boolean = if (channel != null) ended && !hasPendingData() else if (ended) !local.hasPendingData() else local.isEnded
    override fun play() { playing = true; val remote = channel; if (remote == null) { if (!connector.mustWaitForYin()) local.play() } else try { remote.state(true, volume) } catch (error: Exception) { failed(error) } }
    override fun pause() { playing = false; local.pause(); channel?.let { try { it.state(false, volume) } catch (error: Exception) { failed(error) } } }
    override fun setVolume(volume: Float) {
        this.volume = volume; local.setVolume(volume)
        channel?.let { try { it.state(playing, volume) } catch (error: Exception) { failed(error) } }
    }
    override fun handleDiscontinuity() { MusicSpectrum.reset(); local.handleDiscontinuity(); listener?.onPositionDiscontinuity() }
    override fun flush() { MusicSpectrum.reset(); closeRemote(); local.flush(); ended = false }
    override fun reset() { MusicSpectrum.reset(); closeRemote(); local.reset(); format = null; localConfigured = false; ended = false; currentBuffer = null; bufferTimestamp = C.TIME_UNSET }
    override fun release() { reset(); local.release() }
}
