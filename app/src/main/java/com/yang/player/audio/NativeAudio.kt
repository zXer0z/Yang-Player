package com.yang.player.audio

import java.nio.ByteBuffer

/** All handles have one playback-thread owner. Audio callbacks never call Java. */
object NativeAudio {
    init { System.loadLibrary("yang_audio") }
    external fun create(inputRate: Int, inputChannels: Int, inputEncoding: Int, outputRate: Int, outputChannels: Int, outputEncoding: Int): Long
    external fun destroy(handle: Long)
    external fun process(handle: Long, input: ByteBuffer, offset: Int, frames: Int, output: ByteBuffer): Int
    external fun dsp(handle: Long, frequencies: FloatArray, gains: FloatArray, q: FloatArray, preamp: Float, eq: Boolean, headroom: Boolean, limiter: Boolean, volume: Float)
    external fun openOutput(backend: Int, rate: Int, channels: Int, encoding: Int, bufferMs: Int, deviceId: Int, exclusive: Boolean): Long
    external fun outputInfo(handle: Long): IntArray
    external fun write(handle: Long, data: ByteBuffer, offset: Int, frames: Int): Int
    external fun playing(handle: Long, play: Boolean): Int
    external fun position(handle: Long): Long
    external fun closeOutput(handle: Long)
}
