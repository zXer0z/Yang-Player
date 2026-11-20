package com.yang.player.audio

import android.os.SystemClock
import androidx.media3.common.C
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.*

data class SpectrumFrame(
    val bands: FloatArray = FloatArray(24),
    val capturedAt: Long = 0L,
    val bassPulse: Float = 0f,
    val detailBands: FloatArray = FloatArray(32),
    val levels: PcmLevels? = null
)

data class PcmLevels(val peakDbfs: Float, val rmsDbfs: Float) {
    val crestDb: Float get() = peakDbfs - rmsDbfs
}

/** Read-only PCM tap. Pre-allocated window pool bounds work and memory; FFT never runs on the audio thread. */
object MusicSpectrum {
    private const val N = 2048
    private const val HOP = 512
    private const val METER_WINDOW_MS = 400
    private data class Window(
        val samples: FloatArray = FloatArray(N),
        var rate: Int = 44100,
        var at: Long = 0,
        var generation: Int = 0,
        var meterBlock: Int = 0,
        var peak: Float = 0f,
        var meanSquare: Double = 0.0
    )

    private val pool = ArrayBlockingQueue<Window>(3).apply {
        add(Window())
        add(Window())
        add(Window())
    }
    private val pending = AtomicReference<Window?>(null)
    private var capture = Window()
    private val history = FloatArray(N)
    private var historyPosition = 0
    private var sinceWindow = 0
    private var filled = 0
    private var sourceRate = 0
    private var sourceChannels = 0
    private var sourceEncoding = C.ENCODING_INVALID
    private val generation = AtomicInteger()
    private var seenGeneration = -1
    private var meterFrames = 0
    private var meterPeak = 0f
    private var meterSumSquares = 0.0
    private var completedBlock = 0
    private var completedPeak = 0f
    private var completedMeanSquare = 0.0
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

    fun reset() { generation.incrementAndGet() }

    /** Called only with bytes actually consumed by the sink; absolute reads leave buffer state intact. */
    fun offer(buffer: ByteBuffer, start: Int, end: Int, rate: Int, channels: Int, encoding: Int) {
        if (consumers.value <= 0 || rate <= 0 || channels <= 0) return
        val width = when (encoding) {
            C.ENCODING_PCM_8BIT -> 1
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_24BIT -> 3
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
            else -> return
        }
        val stride = width * channels
        val currentGeneration = generation.get()
        if (seenGeneration != currentGeneration || sourceRate != rate || sourceChannels != channels || sourceEncoding != encoding) {
            seenGeneration = if (seenGeneration == currentGeneration) generation.incrementAndGet() else currentGeneration
            filled = 0
            historyPosition = 0
            sinceWindow = 0
            sourceRate = rate
            sourceChannels = channels
            sourceEncoding = encoding
            meterFrames = 0
            meterPeak = 0f
            meterSumSquares = 0.0
            completedBlock = 0
            completedPeak = 0f
            completedMeanSquare = 0.0
        }
        val framesPerMeterBlock = (rate.toLong() * METER_WINDOW_MS / 1_000).toInt().coerceAtLeast(1)
        var position = start
        while (position + stride <= end) {
            var sample = 0f
            for (channel in 0 until channels) {
                val offset = position + channel * width
                fun byte(index: Int) = buffer.get(offset + index).toInt() and 255
                val decoded = when (encoding) {
                    C.ENCODING_PCM_8BIT -> (byte(0) - 128) / 128f
                    C.ENCODING_PCM_16BIT -> ((byte(0) or (byte(1) shl 8)).toShort()) / 32768f
                    C.ENCODING_PCM_24BIT -> ((byte(0) shl 8) or (byte(1) shl 16) or (byte(2) shl 24)) / 2147483648f
                    C.ENCODING_PCM_FLOAT -> Float.fromBits(byte(0) or (byte(1) shl 8) or (byte(2) shl 16) or (byte(3) shl 24))
                    else -> (byte(0) or (byte(1) shl 8) or (byte(2) shl 16) or (byte(3) shl 24)) / 2147483648f
                }
                val channelSample = if (decoded.isFinite()) decoded else 0f
                sample += channelSample
                meterPeak = max(meterPeak, abs(channelSample))
                meterSumSquares += channelSample.toDouble() * channelSample
            }
            meterFrames++
            if (meterFrames == framesPerMeterBlock) {
                // Meter each channel before summing to mono for the FFT.
                completedBlock++
                completedPeak = meterPeak
                completedMeanSquare = meterSumSquares / (meterFrames.toDouble() * channels)
                meterFrames = 0
                meterPeak = 0f
                meterSumSquares = 0.0
            }
            history[historyPosition] = (sample / channels).let { if (it.isFinite()) it.coerceIn(-1f, 1f) else 0f }
            historyPosition = (historyPosition + 1) and (N - 1)
            if (filled < N) filled++
            sinceWindow++
            if (filled == N && sinceWindow >= HOP) {
                sinceWindow = 0
                val next = pool.poll()
                if (next != null) {
                    System.arraycopy(history, historyPosition, capture.samples, 0, N - historyPosition)
                    System.arraycopy(history, 0, capture.samples, N - historyPosition, historyPosition)
                    capture.rate = rate
                    capture.at = SystemClock.elapsedRealtime()
                    capture.generation = seenGeneration
                    capture.meterBlock = completedBlock
                    capture.peak = completedPeak
                    capture.meanSquare = completedMeanSquare
                    val oldPending = pending.getAndSet(capture)
                    if (oldPending != null) pool.offer(oldPending)
                    capture = next
                }
            }
            position += stride
        }
    }

    /** One lifecycle-owned consumer, active while visible clients observe. */
    private suspend fun analyse(): Nothing = withContext(Dispatchers.Default) {
        val real = DoubleArray(N)
        val imaginary = DoubleArray(N)
        val hann = DoubleArray(N) { .5 - .5 * cos(2 * PI * it / (N - 1)) }
        var bassAverage = 0.0
        var previousPulse = 0f
        var previousCapture = 0L
        var levels: PcmLevels? = null
        var analysedBlock = 0
        var analysedGeneration = -1
        var lastWindowAt = SystemClock.elapsedRealtime()
        reset()
        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                val currentGeneration = generation.get()
                if (analysedGeneration != currentGeneration) {
                    analysedGeneration = currentGeneration
                    analysedBlock = 0
                    levels = null
                    previousCapture = 0L
                    previousPulse = 0f
                    _frame.value = SpectrumFrame()
                }
                val window = pending.getAndSet(null)
                if (window != null) {
                    lastWindowAt = SystemClock.elapsedRealtime()
                    try {
                        if (window.generation != currentGeneration) continue
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
                        // Relative low-frequency onset
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

                        if (analysedBlock != window.meterBlock) {
                            analysedBlock = window.meterBlock
                            levels = if (window.meanSquare > 0.0) PcmLevels(
                                peakDbfs = (20 * log10(window.peak.toDouble())).toFloat(),
                                rmsDbfs = (10 * log10(window.meanSquare)).toFloat()
                            ) else null
                        }

                        if (currentGeneration == generation.get()) {
                            _frame.value = SpectrumFrame(
                                bands = bands,
                                capturedAt = window.at,
                                bassPulse = previousPulse,
                                detailBands = details,
                                levels = levels
                            )
                        }
                    } finally {
                        pool.offer(window)
                    }
                } else {
                    val idleMs = SystemClock.elapsedRealtime() - lastWindowAt
                    val prev = _frame.value
                    val visibleLevels = if (idleMs >= METER_WINDOW_MS) null else prev.levels
                    if (idleMs > 40L && (prev.bands.any { it > 0.005f } || prev.detailBands.any { it > 0.005f })) {
                        _frame.value = prev.copy(
                            bands = FloatArray(24) { prev.bands[it] * 0.85f },
                            detailBands = FloatArray(32) { prev.detailBands[it] * 0.85f },
                            bassPulse = prev.bassPulse * 0.82f,
                            levels = visibleLevels
                        )
                    } else if (prev.levels != visibleLevels) {
                        _frame.value = prev.copy(levels = visibleLevels)
                    }
                }
                delay(12)
            }
            @Suppress("UNREACHABLE_CODE") error("Spectrum stopped")
        } finally {
            reset()
            while (true) {
                val rem = pending.getAndSet(null) ?: break
                pool.offer(rem)
            }
            _frame.value = SpectrumFrame()
        }
    }
}
