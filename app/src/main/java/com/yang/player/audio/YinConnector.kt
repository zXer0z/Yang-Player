package com.yang.player.audio

import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.yang.bridge.YinProtocol
import com.yang.player.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.IOException
import java.nio.ByteBuffer
import java.security.MessageDigest

data class YinState(val installed: Boolean = false, val ready: Boolean = false, val active: Boolean = false, val message: String = "", val output: String = "", val enabled: Boolean? = null)

/** Explicit authenticated binding; installed/running alone never disables Yang's output. */
class YinConnector private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(YinState(installed = trusted()))
    val state = _state.asStateFlow()
    @Volatile private var remote: IBinder? = null
    @Volatile private var retryAfter = 0L
    @Volatile private var protectedSession = false
    private var bound = false
    private var poll: Job? = null
    @Volatile private var source = Bundle()
    fun updateSource(value: Bundle) { source = Bundle(value) }
    internal fun sourceSnapshot(): Bundle = source
    private val reconnect = Runnable { bind() }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            if (!trusted()) { disconnect(); return }
            remote = service
            runCatching { service.linkToDeath({ main.post { disconnect() } }, 0) }
            poll?.cancel()
            poll = scope.launch {
                while (isActive && remote === service) {
                    try {
                        val result = YinProtocol.call(service, YinProtocol.HELLO, null)
                        acceptHello(result)
                    } catch (_: Exception) { main.post { disconnect() }; break }
                    delay(1000)
                }
            }
        }
        override fun onServiceDisconnected(name: ComponentName) { disconnect() }
        override fun onBindingDied(name: ComponentName) { disconnect() }
        override fun onNullBinding(name: ComponentName) { disconnect() }
    }
    init { main.post { bind() } }
    private fun trusted(): Boolean = runCatching {
        val info = context.packageManager.getPackageInfo(YinProtocol.PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES)
        info.signingInfo?.apkContentsSigners?.any { signature -> MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) } == BuildConfig.YIN_CERT_SHA256 } == true
    }.getOrDefault(false)
    private fun bind() {
        if (bound) return
        main.removeCallbacks(reconnect)
        val intent = Intent().setComponent(ComponentName(YinProtocol.PACKAGE, YinProtocol.SERVICE))
        if (trusted() && context.packageManager.resolveService(intent, 0) != null) {
            bound = runCatching { context.bindService(intent, connection, Context.BIND_AUTO_CREATE) }.getOrDefault(false)
            _state.update { it.copy(installed = true) }
        } else _state.value = YinState()
        if (!bound) main.postDelayed(reconnect, 5000)
    }
    private fun disconnect() {
        poll?.cancel(); remote = null
        if (bound) runCatching { context.unbindService(connection) }
        bound = false
        _state.update { it.copy(ready = false, active = protectedSession, message = "Yin se desconectó · esperando sin reproducir audio sin efectos") }
        main.removeCallbacks(reconnect); main.postDelayed(reconnect, 1500)
    }
    fun canOpen() = _state.value.ready && SystemClock.elapsedRealtime() >= retryAfter && remote?.isBinderAlive == true
    /** Only an explicitly disabled Yin may release its protected output to Yang. */
    fun mustWaitForYin() = (protectedSession || _state.value.installed) && _state.value.enabled != false
    private fun acceptHello(result: Bundle) {
        val enabled = if (result.containsKey("enabled")) result.getBoolean("enabled") else if (result.getBoolean("ready")) true else _state.value.enabled
        if (enabled == false) { protectedSession = false; retryAfter = 0 }
        val ready = result.getInt("version") == YinProtocol.VERSION && result.getBoolean("ready") && SystemClock.elapsedRealtime() >= retryAfter
        _state.update { it.copy(installed = true, enabled = enabled, ready = ready, active = if(enabled == false) false else it.active,
            message = if (SystemClock.elapsedRealtime() < retryAfter) it.message else result.getString("message").orEmpty()) }
    }
    fun open(rate: Int, channels: Int, encoding: Int, volume: Float, playing: Boolean): YinChannel {
        val service = remote?.takeIf { canOpen() } ?: throw IOException("Yin no está listo")
        val token = Binder()
        val args = Bundle().apply { putInt("version", YinProtocol.VERSION); putInt("rate", rate); putInt("channels", channels); putInt("encoding", encoding); putFloat("volume", volume); putBoolean("playing", playing); putBinder("client", token) }
        args.putBundle("source", sourceSnapshot())
        val result = YinProtocol.call(service, YinProtocol.OPEN, args)
        if (!result.getBoolean("ok")) {
            runCatching { result.getParcelable<ParcelFileDescriptor>("fd")?.close() }
            runCatching { YinProtocol.call(service, YinProtocol.CLOSE, Bundle().apply { putString("session", result.getString("session")) }) }
            throw IOException(result.getString("message", "Yin no pudo abrir su motor"))
        }
        val fd = result.getParcelable<ParcelFileDescriptor>("fd") ?: throw IOException("Yin no entregó el canal PCM")
        try {
            Os.fcntlInt(fd.fileDescriptor, OsConstants.F_SETFL, Os.fcntlInt(fd.fileDescriptor, OsConstants.F_GETFL, 0) or OsConstants.O_NONBLOCK)
            return YinChannel(service, token, fd, result, this, rate, channels, encoding).also {
                val output = "${result.getInt("rate") / 1000f} kHz · ${pcmName(result.getInt("encoding"))}\n${result.getString("message").orEmpty()}"
                protectedSession = true
                _state.update { it.copy(active = true, enabled = true, output = output) }
            }
        } catch (failure: Exception) {
            runCatching { YinProtocol.call(service, YinProtocol.CLOSE, Bundle().apply { putString("session", result.getString("session")) }) }
            fd.close(); throw failure
        }
    }
    fun stopped() { _state.update { it.copy(active = protectedSession && it.enabled != false) } }
    fun failed(reason: String) {
        retryAfter = SystemClock.elapsedRealtime() + 300
        _state.update { it.copy(ready = false, active = protectedSession, message = "$reason · esperando a Yin en silencio") }
        scope.launch {
            delay(320)
            remote?.let { service -> runCatching { acceptHello(YinProtocol.call(service, YinProtocol.HELLO, null)) } }
        }
    }
    companion object {
        @Volatile private var instance: YinConnector? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: YinConnector(context.applicationContext).also { instance = it } }
    }
}

class YinChannel internal constructor(private val remote: IBinder, @Suppress("unused") private val lifetime: IBinder,
    private val fd: ParcelFileDescriptor, initial: Bundle, private val owner: YinConnector, private val inputRate: Int, private val inputChannels: Int, inputEncoding: Int) {
    private val frameBytes = inputChannels * when(inputEncoding) { 3 -> 1; 2 -> 2; 21 -> 3; else -> 4 }
    private val session = initial.getString("session") ?: throw IOException("Sesión de Yin inválida")
    private var cached = initial
    private var polledAt = 0L
    private var changedAt = SystemClock.elapsedRealtime()
    private var previousPosition = 0L
    var written = 0L; private set
    var eos = false; private set
    private var closed = false
    private var playing = false
    private var partialFrame = 0
    private var sentSource: Bundle? = null
    private fun args() = Bundle().apply { putString("session", session) }
    fun status(force: Boolean = false): Bundle {
        if (closed || !remote.isBinderAlive || !lifetime.isBinderAlive) throw IOException("Yin se desconectó")
        val now = SystemClock.elapsedRealtime()
        if (force || now - polledAt > 50) {
            val source = owner.sourceSnapshot()
            val request = args().apply { if (sentSource !== source) putBundle("source", source) }
            cached = YinProtocol.call(remote, YinProtocol.STATUS, request); polledAt = now; sentSource = source
            if (!cached.getBoolean("ok")) throw IOException(cached.getString("message", "Falló el motor Yin"))
            // Seal on a complete PCM frame. Already accepted data still runs through
            // the old graph; the caller retains the next buffer for the new graph.
            if (cached.getBoolean("reconfigure") && partialFrame == 0 && !eos) finish()
            val position = cached.getLong("positionUs")
            if (position != previousPosition || !playing || position + 20_000 >= written * 1_000_000L / (inputRate.toLong() * frameBytes) || cached.getBoolean("drained")) { previousPosition = position; changedAt = now }
            if (playing && written > 0 && now - changedAt > 5000) throw IOException("El motor Yin dejó de avanzar")
        }
        return cached
    }
    fun write(buffer: ByteBuffer): Boolean {
        val state = status()
        if (eos) return false
        val start = buffer.position()
        val transitioning = state.getBoolean("reconfigure")
        val playedBytes = state.getLong("positionUs") * inputRate / 1_000_000L * frameBytes
        val inFlight = (written - playedBytes).coerceAtLeast(0)
        val budget = inputRate.toLong() * frameBytes * 400 / 1000 - inFlight
        if (!transitioning && budget < frameBytes && partialFrame == 0) return false
        val frameBudget = (budget / frameBytes).coerceAtLeast(1L).coerceAtMost(Int.MAX_VALUE.toLong() / frameBytes).toInt()
        val countLimit = minOf(buffer.remaining() - partialFrame,
            if (transitioning) frameBytes - partialFrame else frameBudget * frameBytes - partialFrame)
        if (countLimit <= 0) return !buffer.hasRemaining()
        val slice = buffer.duplicate().apply { position(start + partialFrame); limit(position() + countLimit) }
        try {
            val count = Os.write(fd.fileDescriptor, slice)
            val accepted = partialFrame + count
            buffer.position(start + accepted / frameBytes * frameBytes)
            partialFrame = accepted % frameBytes
            written += count
            if (transitioning && partialFrame == 0) finish()
        } catch (error: ErrnoException) { if (error.errno != OsConstants.EAGAIN) throw IOException("Se cerró el PCM de Yin", error) }
        return !buffer.hasRemaining()
    }
    fun state(play: Boolean, volume: Float) {
        playing = play; changedAt = SystemClock.elapsedRealtime()
        val response = YinProtocol.call(remote, YinProtocol.STATE, args().apply { putBoolean("playing", play); putFloat("volume", volume) })
        if (!response.getBoolean("ok")) throw IOException(response.getString("message", "Yin perdió la sesión"))
    }
    fun finish() { if (!eos) { Os.shutdown(fd.fileDescriptor, OsConstants.SHUT_WR); eos = true; YinProtocol.call(remote, YinProtocol.EOS, args()) } }
    fun close() {
        if (closed) return
        closed = true
        runCatching { YinProtocol.call(remote, YinProtocol.CLOSE, args()) }
        runCatching { fd.close() }; owner.stopped()
    }
}
