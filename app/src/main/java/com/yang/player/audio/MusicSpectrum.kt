package com.yang.player.audio

import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.*

data class SpectrumFrame(val bands: FloatArray = FloatArray(24), val capturedAt: Long = 0L, val bassPulse: Float = 0f, val detailBands: FloatArray = FloatArray(32))

/** Read-only PCM tap. Two fixed windows bound work and memory; FFT never runs on the audio thread. */
object MusicSpectrum {
    private const val N = 2048
    private const val HOP = 512
    private data class Window(val samples: FloatArray = FloatArray(N), var rate: Int = 44100, var at: Long = 0)
    private val spare = AtomicReference<Window?>(Window())
    private val pending = AtomicReference<Window?>(null)
    private var capture = Window()
    private val history = FloatArray(N)
    private var historyPosition = 0
    private var sinceWindow = 0
    private var filled = 0
    private var sourceRate = 0
    @Volatile private var enabled = false
    @Volatile private var generation = 0
    private var seenGeneration = -1
    private val _frame = MutableStateFlow(SpectrumFrame())
    val frame = _frame.asStateFlow()

    private val consumers = MutableStateFlow(0)
    private val analysisScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val analysisJob = analysisScope.launch {
        consumers.map { it > 0 }.distinctUntilChanged().collectLatest { requested ->
            if (requested) analyse()
        }
    }

    /** Multiple visible clients share one FFT consumer, including HiFi behind the player sheet. */
    suspend fun observe(): Nothing {
        consumers.update { it + 1 }
        try { awaitCancellation() }
        finally { consumers.update { (it - 1).coerceAtLeast(0) } }
    }

    fun reset() { generation++ }

    /** Called only with bytes actually consumed by the sink; absolute reads leave buffer state intact. */
    fun offer(buffer: ByteBuffer, start: Int, end: Int, rate: Int, channels: Int, encoding: Int) {
        if (!enabled || rate <= 0 || channels <= 0) return
        val width = when (encoding) { 3 -> 1; 2 -> 2; 21 -> 3; 22, 4 -> 4; else -> return }
        val stride = width * channels
        if (seenGeneration != generation || sourceRate != rate) {
            filled = 0; historyPosition = 0; sinceWindow = 0; sourceRate = rate; seenGeneration = generation
        }
        var position = start
        while (position + stride <= end) {
            var sample = 0f
            for (channel in 0 until channels) {
                val offset = position + channel * width
                fun byte(index: Int) = buffer.get(offset + index).toInt() and 255
                sample += when (encoding) {
                    3 -> (byte(0) - 128) / 128f
                    2 -> ((byte(0) or (byte(1) shl 8)).toShort()) / 32768f
                    21 -> ((byte(0) shl 8) or (byte(1) shl 16) or (byte(2) shl 24)) / 2147483648f
                    22 -> (byte(0) or (byte(1) shl 8) or (byte(2) shl 16) or (byte(3) shl 24)) / 2147483648f
                    else -> Float.fromBits(byte(0) or (byte(1) shl 8) or (byte(2) shl 16) or (byte(3) shl 24))
                }
            }
            history[historyPosition] = (sample / channels).let { if (it.isFinite()) it.coerceIn(-1f, 1f) else 0f }
            historyPosition = (historyPosition + 1) and (N - 1)
            if (filled < N) filled++
            sinceWindow++
            if (filled == N && sinceWindow >= HOP) {
                sinceWindow = 0
                // Overlapping windows follow short transients. Never wait for the FFT consumer.
                val next = spare.getAndSet(null)
                if (next != null) {
                    System.arraycopy(history, historyPosition, capture.samples, 0, N - historyPosition)
                    System.arraycopy(history, 0, capture.samples, N - historyPosition, historyPosition)
                    capture.rate = rate; capture.at = SystemClock.elapsedRealtime()
                    pending.set(capture); capture = next
                }
            }
            position += stride
        }
    }

    /** One lifecycle-owned consumer, present only while the HiFi visual is visible and playing. */
    private suspend fun analyse(): Nothing = withContext(Dispatchers.Default) {
        val real = DoubleArray(N)
        val imaginary = DoubleArray(N)
        val hann = DoubleArray(N) { .5 - .5 * cos(2 * PI * it / (N - 1)) }
        var bassAverage = 0.0
        var previousPulse = 0f
        var previousCapture = 0L
        generation++; enabled = true
        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                val window = pending.getAndSet(null)
                if (window != null) {
                    try {
                        for (i in 0 until N) { real[i] = window.samples[i] * hann[i]; imaginary[i] = 0.0 }
                        var j = 0
                        for (i in 1 until N) {
                            var bit = N shr 1
                            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
                            j = j xor bit
                            if (i < j) {
                                val swap = real[i]; real[i] = real[j]; real[j] = swap
                            }
                        }
                        var length = 2
                        while (length <= N) {
                            val angle = -2 * PI / length
                            val cosine = cos(angle); val sine = sin(angle)
                            var start = 0
                            while (start < N) {
                                var wr = 1.0; var wi = 0.0
                                for (offset in 0 until length / 2) {
                                    val a = start + offset; val z = a + length / 2
                                    val tr = wr * real[z] - wi * imaginary[z]
                                    val ti = wr * imaginary[z] + wi * real[z]
                                    real[z] = real[a] - tr; imaginary[z] = imaginary[a] - ti
                                    real[a] += tr; imaginary[a] += ti
                                    val next = wr * cosine - wi * sine
                                    wi = wr * sine + wi * cosine; wr = next
                                }
                                start += length
                            }
                            length *= 2
                        }
                        val high = min(20000.0, window.rate * .48)
                        val bands = FloatArray(24) { band ->
                            val lowHz = 30 * (high / 30).pow(band / 24.0)
                            val highHz = 30 * (high / 30).pow((band + 1) / 24.0)
                            val from = max(1, (lowHz * N / window.rate).toInt()).coerceAtMost(N / 2 - 1)
                            val to = max(from + 1, ceil(highHz * N / window.rate).toInt()).coerceAtMost(N / 2)
                            var power = 0.0
                            for (bin in from until to) power = max(power, real[bin] * real[bin] + imaginary[bin] * imaginary[bin])
                            val db = 20 * log10(max(1e-9, sqrt(power) * 4 / N))
                            ((db + 72) / 72).coerceIn(0.0, 1.0).toFloat()
                        }
                        val details = FloatArray(32) { band ->
                            val lowHz = 30 * (high / 30).pow(band / 32.0)
                            val highHz = 30 * (high / 30).pow((band + 1) / 32.0)
                            val from = max(1, (lowHz * N / window.rate).toInt()).coerceAtMost(N / 2 - 1)
                            val to = max(from + 1, ceil(highHz * N / window.rate).toInt()).coerceAtMost(N / 2)
                            var power = 0.0
                            for (bin in from until to) power = max(power, real[bin] * real[bin] + imaginary[bin] * imaginary[bin])
                            val db = 20 * log10(max(1e-9, sqrt(power) * 4 / N))
                            ((db + 60) / 60).coerceIn(0.0, 1.0).toFloat()
                        }
                        // Relative low-frequency onset, rather than a timer or a BPM guess.
                        var bassPower = 0.0
                        val bassEnd = ceil(220.0 * N / window.rate).toInt().coerceIn(2, N / 2)
                        for (bin in 1 until bassEnd) bassPower += real[bin] * real[bin] + imaginary[bin] * imaginary[bin]
                        val bass = sqrt(bassPower) * 2 / N
                        val elapsed = if (previousCapture == 0L) .016 else ((window.at - previousCapture) / 1000.0).coerceIn(.008, .2)
                        if (previousCapture == 0L) bassAverage = bass
                        val onset = ((bass - bassAverage * 1.05) / (bassAverage * .65 + .003)).coerceIn(0.0, 1.0).toFloat()
                        bassAverage += (bass - bassAverage) * (1 - exp(-elapsed / .6))
                        previousPulse = max(onset, previousPulse * exp(-elapsed / .12).toFloat())
                        previousCapture = window.at
                        _frame.value = SpectrumFrame(bands, window.at, previousPulse, details)
                    } finally { spare.set(window) }
                }
                delay(16)
            }
            @Suppress("UNREACHABLE_CODE") error("Spectrum stopped")
        } finally {
            enabled = false; generation++
            pending.getAndSet(null)?.let { spare.set(it) }
            _frame.value = SpectrumFrame()
        }
    }
}
