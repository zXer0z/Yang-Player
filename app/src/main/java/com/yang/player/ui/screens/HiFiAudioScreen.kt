package com.yang.player.ui.screens

import android.content.Intent
import android.os.SystemClock
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.yang.player.audio.*
import com.yang.player.model.PlayerState
import com.yang.player.ui.theme.LocalArtworkAccent
import java.util.Locale
import kotlin.math.*

@Composable
fun HiFiAudioScreen(playerState: PlayerState, bottomContentPadding: Dp) {
    val context = LocalContext.current
    val engine = remember { AudioEngineStore.get(context) }
    val yin by remember { YinConnector.get(context) }.state.collectAsState()
    val profiles by engine.profiles.collectAsState()
    val activeRoute by engine.route.collectAsState()
    val signal by engine.telemetry.collectAsState()
    val accent = LocalArtworkAccent.current

    var selectedRoute by remember(activeRoute) { mutableStateOf(activeRoute) }
    var draft by remember(selectedRoute, profiles) { mutableStateOf(profiles.getValue(selectedRoute)) }
    var bandIndex by remember { mutableIntStateOf(4) }
    var advancedEq by remember { mutableStateOf(false) }
    var section by rememberSaveable { mutableIntStateOf(0) }

    fun save(value: EngineProfile = draft) {
        draft = value.valid()
        engine.save(draft)
    }

    // Keep spectrum observer alive for the whole Hi-Fi screen while playing
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(playerState.isPlaying, lifecycle) {
        if (playerState.isPlaying) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                MusicSpectrum.observe()
            }
        }
    }

    // External Yin Engine Mode
    if (yin.active || yin.ready) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = bottomContentPadding + 84.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text("Hi-Fi", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            com.yang.player.ui.components.YinConnectionVisual(yin.ready, playerState.isPlaying, accent)
            StudioAudioMonitor(playerState.isPlaying, accent)
            SoundCard {
                Text(playerState.currentSong?.title ?: "Elige una canción", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                playerState.currentSong?.let { Text(it.audioQualityBadge, color = Color.White.copy(alpha = .55f), fontSize = 13.sp) }
                val outputFormat = yin.output.lineSequence().firstOrNull().orEmpty()
                if (outputFormat.isNotBlank()) Text(outputFormat, color = Color.White.copy(alpha = .7f), fontSize = 13.sp)
                if (!yin.ready) Text(yin.message.ifBlank { "El audio espera a que Yin esté listo." }, color = accent, fontSize = 12.sp)
            }
            SoundCard {
                Text("Ajustes de sonido", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("Yin administra esta reproducción. Abre su panel para ajustar el ecualizador, la ganancia y la salida.", color = Color.White.copy(alpha = .6f), fontSize = 13.sp, lineHeight = 19.sp)
                Button(
                    onClick = { context.packageManager.getLaunchIntentForPackage("local.v60.player")?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color(0xFF111318))
                ) {
                    Text("Abrir controles de Yin", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        return
    }

    // Native Yang Hi-Fi Studio Rack
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 24.dp, bottom = bottomContentPadding + 84.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Hi-Fi", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)

        // DAC Route Selector Rack Bar
        RouteSelectorBar(
            routes = AudioRoute.entries,
            activeRoute = activeRoute,
            selectedRoute = selectedRoute,
            onSelect = { selectedRoute = it },
            accent = accent
        )

        // Selected Route Hardware Status Pill
        val isSelectedActive = selectedRoute == activeRoute
        val activeProfile = profiles.getValue(selectedRoute)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color.White.copy(alpha = 0.035f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isSelectedActive && signal.rate > 0)
                            "Hardware: ${signal.backend} · ${hz(signal.rate.toFloat())} · ${pcmName(signal.encoding)}"
                        else
                            "${activeProfile.backend.label} · ${hz(activeProfile.rate.toFloat())} · ${pcmName(activeProfile.encoding)}",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (isSelectedActive) "Salida activa actualmente" else "Perfil para cuando conectes ${selectedRoute.label}",
                        color = if (isSelectedActive) accent else Color.White.copy(alpha = 0.45f),
                        fontSize = 11.sp
                    )
                }
                if (isSelectedActive) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accent.copy(alpha = 0.18f))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text("EN VIVO", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Unified Studio Master Monitor (FFT RTA Spectrum + Dynamic Range + Dual Peak/RMS)
        StudioAudioMonitor(playerState.isPlaying, accent)

        // 3 Clean Studio Tabs
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Ecualizador", "Dinámica", "Salida").forEachIndexed { index, title ->
                FilterChip(
                    selected = section == index,
                    onClick = { section = index },
                    modifier = Modifier.weight(1f),
                    label = { Text(title, fontSize = 12.sp, maxLines = 1) },
                    colors = soundChipColors()
                )
            }
        }

        // Tab Content
        Crossfade(targetState = section, animationSpec = tween(180), label = "hifiSection") { activeSection ->
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (activeSection) {
                    0 -> {
                        // TAB 0: Ecualizador
                        SoundCard {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Ecualizador", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Switch(
                                    checked = draft.eq,
                                    onCheckedChange = { save(draft.copy(eq = it)) },
                                    colors = SwitchDefaults.colors(checkedTrackColor = accent, checkedThumbColor = Color(0xFF121317))
                                )
                            }

                            // Presets Row
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val presets = listOf("Plano", "Bajos", "Voces", "Agudos", "Acústico")
                                presets.forEach { label ->
                                    OutlinedButton(
                                        onClick = {
                                            val gains = when (label) {
                                                "Bajos" -> listOf(4f, 3.5f, 2f, 0f, -1f, -1f, 0f, 0f, 1f, 1f)
                                                "Voces" -> listOf(-2f, -1.5f, -0.5f, 0f, 2f, 3.5f, 3f, 1f, 0f, 0f)
                                                "Agudos" -> listOf(-1f, -1f, 0f, 0f, 0f, 1f, 2f, 3.5f, 4f, 4.5f)
                                                "Acústico" -> listOf(3f, 2f, 1f, 0f, 0f, 1f, 2f, 2.5f, 3f, 2f)
                                                else -> List(10) { 0f }
                                            }
                                            save(draft.copy(eq = true, bands = defaultBands.mapIndexed { i, band -> band.copy(gain = gains[i]) }))
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(label, fontSize = 11.5.sp)
                                    }
                                }
                            }

                            // 10 Precision Vertical Faders
                            BoxWithConstraints(Modifier.fillMaxWidth()) {
                                val bandWidth = (maxWidth / 10).coerceAtLeast(30.dp)
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                    draft.bands.forEachIndexed { index, band ->
                                        Column(Modifier.width(bandWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = String.format(Locale.ROOT, "%+.1f", band.gain),
                                                color = if (draft.eq && band.gain != 0f) accent else Color.White.copy(alpha = 0.35f),
                                                fontSize = 9.sp,
                                                fontWeight = if (band.gain != 0f) FontWeight.Bold else FontWeight.Normal,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                            VerticalEqFader(
                                                value = band.gain,
                                                enabled = draft.eq,
                                                label = "Banda ${hz(band.hz)}",
                                                modifier = Modifier.fillMaxWidth().height(160.dp),
                                                onValue = { gain ->
                                                    draft = draft.copy(bands = draft.bands.toMutableList().also { it[index] = band.copy(gain = gain) })
                                                    engine.preview(draft)
                                                },
                                                onFinished = { save() }
                                            )
                                            Text(
                                                text = if (band.hz >= 1000) "${decimal(band.hz / 1000).removeSuffix(".0")}k" else band.hz.roundToInt().toString(),
                                                color = Color.White.copy(alpha = 0.55f),
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }

                            // Zone Markers
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Graves", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
                                Text("Medios", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
                                Text("Agudos", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
                            }

                            // Quick Reset to 0 dB
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = {
                                    save(draft.copy(bands = defaultBands.map { it.copy(gain = 0f) }))
                                }) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Poner a 0 dB", color = accent, fontSize = 12.sp)
                                }
                                TextButton(onClick = { advancedEq = !advancedEq }) {
                                    Text("Curva y Q", color = accent, fontSize = 12.sp)
                                    Icon(if (advancedEq) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = accent)
                                }
                            }

                            // Advanced Mode: Biquad Curve & Fine Q Tuning
                            if (advancedEq) {
                                EqResponse(if (selectedRoute == activeRoute && signal.rate > 0) draft.copy(rate = signal.rate) else draft, accent)
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    draft.bands.forEachIndexed { index, band ->
                                        FilterChip(
                                            selected = index == bandIndex,
                                            onClick = { bandIndex = index },
                                            label = { Text(hz(band.hz), fontSize = 11.sp) },
                                            colors = soundChipColors()
                                        )
                                    }
                                }
                                val band = draft.bands[bandIndex]
                                fun updateBand(value: EqBand) {
                                    draft = draft.copy(bands = draft.bands.toMutableList().also { it[bandIndex] = value })
                                }
                                SoundSlider("Ganancia", "${decimal(band.gain)} dB", band.gain, -12f..12f, 95, draft.eq, { updateBand(band.copy(gain = it)) }) { save() }
                                SoundSlider("Frecuencia", hz(band.hz), (ln(band.hz / 20f) / ln(1000f)).coerceIn(0f, 1f), 0f..1f, 0, draft.eq,
                                    { updateBand(band.copy(hz = 20f * 1000f.pow(it))) }) { save() }
                                SoundSlider("Ancho de banda · Q", decimal(band.q), band.q, 0.3f..6f, 56, draft.eq, { updateBand(band.copy(q = it)) }) { save() }
                                Text("Q bajo: ajuste amplio. Q alto: corte preciso.", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
                            }
                        }
                    }
                    1 -> {
                        // TAB 1: Procesamiento (DSP & Dinámica)
                        SoundCard {
                            Text("Ganancia y protección", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            SoundSlider("Preamplificador", "${decimal(draft.preamp)} dB", draft.preamp, -24f..12f, 71, true, {
                                draft = draft.copy(preamp = it)
                                engine.preview(draft)
                            }) { save() }
                            SoundSwitch("Margen automático de EQ (Headroom)", draft.headroom) { save(draft.copy(headroom = it)) }
                            Text("Resta automáticamente el realce del EQ para evitar saturación digital.", color = Color.White.copy(alpha = 0.4f), fontSize = 11.5.sp)
                            SoundSwitch("Limitador de picos", draft.limiter) { save(draft.copy(limiter = it)) }
                            Text("Curva suave (soft-clipping) que redondea transitorios sin distorsión áspera.", color = Color.White.copy(alpha = 0.4f), fontSize = 11.5.sp)
                        }

                        SoundCard {
                            Text("Campo estéreo y volumen", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            SoundSlider(
                                label = "Balance estéreo",
                                value = balanceLabel(draft.balance),
                                amount = draft.balance,
                                range = -1f..1f,
                                steps = 40,
                                enabled = true,
                                onValue = {
                                    val snapped = if (abs(it) < 0.05f) 0f else it
                                    draft = draft.copy(balance = snapped)
                                    engine.preview(draft)
                                },
                                onFinished = { save() }
                            )
                            if (draft.balance != 0f) {
                                TextButton(
                                    onClick = { draft = draft.copy(balance = 0f); save() },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Centrar balance", color = accent, fontSize = 12.sp)
                                }
                            }

                            SoundSwitch("Crossfeed (Acústica de altavoces)", draft.crossfeed) { save(draft.copy(crossfeed = it)) }
                            Text("Mezcla acústica de 700 Hz para auriculares. Disminuye la fatiga estéreo extrema.", color = Color.White.copy(alpha = 0.4f), fontSize = 11.5.sp)

                            SoundSwitch("Normalización de volumen (RMS)", draft.loudnessNorm) { save(draft.copy(loudnessNorm = it)) }
                            Text("Nivela dinámicamente canciones con volúmenes de grabación desiguales.", color = Color.White.copy(alpha = 0.4f), fontSize = 11.5.sp)
                        }

                    }
                    2 -> {
                        // TAB 2: Salida & Driver
                        SoundCard {
                            Text("Motor de salida · ${selectedRoute.label}", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            SoundChoice("Driver de audio", draft.backend.label, OutputBackend.entries.map { it.label }) { selected ->
                                save(draft.copy(backend = OutputBackend.entries[selected]))
                            }
                            val rates = listOf(44100, 48000, 88200, 96000, 176400, 192000).filter { it <= selectedRoute.ceiling }
                            SoundChoice("Frecuencia", hz(draft.rate.toFloat()), rates.map { hz(it.toFloat()) }) {
                                save(draft.copy(rate = rates[it]))
                            }
                            val encodings = when (selectedRoute) {
                                AudioRoute.SPEAKER -> listOf(2)
                                AudioRoute.BLUETOOTH -> listOf(2, 21)
                                else -> listOf(2, 21, 22, 4)
                            }
                            SoundChoice("Formato PCM", pcmName(draft.encoding), encodings.map(::pcmName)) {
                                save(draft.copy(encoding = encodings[it]))
                            }
                            val buffers = listOf(40, 80, 100, 200, 400, 800)
                            SoundChoice("Búfer", "${draft.bufferMs} ms", buffers.map { "$it ms" }) {
                                save(draft.copy(bufferMs = buffers[it]))
                            }
                            Text("Un búfer menor reduce la latencia; un búfer mayor resiste mejor las interrupciones.", color = Color.White.copy(alpha = 0.4f), fontSize = 11.5.sp)
                        }

                        // Live Telemetry
                        SoundCard {
                            Text("Telemetría de señal en vivo", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            if (signal.rate > 0) {
                                Text(signal.backend, color = accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("${signal.device} · ${hz(signal.rate.toFloat())} · ${pcmName(signal.encoding)}", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                                Text("Solicitado: ${hz(signal.requestedRate.toFloat())} · ${pcmName(signal.requestedEncoding)}", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
                                Text("Búfer: ${signal.bufferFrames} muestras (${signal.bufferFrames * 1000 / signal.rate} ms)" +
                                        if (signal.underruns >= 0) " · ${signal.underruns} cortes" else "", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                                Text("PCM decodificado: ${hz(signal.inputRate.toFloat())} · ${pcmName(signal.inputEncoding)}", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                                if (signal.decoder.isNotBlank()) Text("Decodificador: ${signal.decoder}", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
                                if (signal.bitrate > 0) Text("Bitrate de origen: ${signal.bitrate / 1000} kbps", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
                                if (signal.note.isNotBlank()) Text(signal.note, color = accent.copy(alpha = 0.8f), fontSize = 11.5.sp)
                            } else {
                                Text("Inicia la reproducción para inspeccionar la señal del hardware.", color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp)
                            }
                        }

                        // Reset Profile
                        TextButton(
                            onClick = { save(EngineProfile(selectedRoute)) },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("Restablecer este perfil a valores predeterminados", color = accent, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

/** Studio Route Selector Pill Bar */
@Composable
private fun RouteSelectorBar(
    routes: List<AudioRoute>,
    activeRoute: AudioRoute,
    selectedRoute: AudioRoute,
    onSelect: (AudioRoute) -> Unit,
    accent: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        routes.forEach { route ->
            val isCurrentActive = route == activeRoute
            val isSelected = route == selectedRoute
            val pillBg = when {
                isSelected -> accent.copy(alpha = 0.20f)
                isCurrentActive -> Color.White.copy(alpha = 0.08f)
                else -> Color.White.copy(alpha = 0.035f)
            }
            val borderCol = when {
                isSelected -> accent.copy(alpha = 0.75f)
                isCurrentActive -> accent.copy(alpha = 0.35f)
                else -> Color.Transparent
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = pillBg,
                modifier = Modifier
                    .border(1.dp, borderCol, RoundedCornerShape(12.dp))
                    .clickable { onSelect(route) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isCurrentActive) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
                    }
                    Text(
                        text = route.label,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                    if (isCurrentActive) {
                        Text(
                            text = "ACTIVO",
                            color = accent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioAudioMonitor(
    isPlaying: Boolean,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val spectrum = MusicSpectrum.frame.collectAsState()
    val measuredLevels by remember(spectrum) { derivedStateOf { spectrum.value.levels } }
    val levels = measuredLevels.takeIf { isPlaying }
    var drawTrigger by remember { mutableLongStateOf(0L) }
    val animBands = remember { FloatArray(24) }
    val peakBands = remember { FloatArray(24) }
    val peakDecay = remember { FloatArray(24) }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            animBands.fill(0f)
            peakBands.fill(0f)
            peakDecay.fill(0f)
            drawTrigger++
            return@LaunchedEffect
        }
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nowNanos ->
                val frame = MusicSpectrum.frame.value
                val bands = frame.bands

                if (lastNanos != 0L) {
                    val dt = ((nowNanos - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                    for (i in 0 until 24) {
                        val target = if (i < bands.size) bands[i].coerceIn(0f, 1f) else 0f
                        val speed = if (target > animBands[i]) 32f else 11f
                        animBands[i] += (target - animBands[i]) * (speed * dt).coerceIn(0f, 1f)

                        if (animBands[i] >= peakBands[i]) {
                            peakBands[i] = animBands[i]
                            peakDecay[i] = 0f
                        } else {
                            peakDecay[i] += dt * 0.85f
                            peakBands[i] = (peakBands[i] - peakDecay[i] * dt).coerceAtLeast(animBands[i])
                        }
                    }
                }
                lastNanos = nowNanos
                drawTrigger = nowNanos
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
            Text("Monitor de Audio", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(accent.copy(alpha = 0.18f))
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            ) {
                Text(
                    text = levels?.let { String.format(Locale.getDefault(), "Cresta %.1f dB", it.crestDb) } ?: "Cresta —",
                    color = if (levels != null) accent else Color.White.copy(alpha = 0.45f),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Text("Cresta = pico − RMS · PCM de entrada en bloques de 400 ms", color = Color.White.copy(alpha = 0.45f), fontSize = 11.5.sp)

        // 24-Band FFT Spectrum Analyzer Canvas
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
        ) {
            val _tick = drawTrigger
            val width = size.width
            val height = size.height
            val numBands = 24
            val spacing = 2.5.dp.toPx()
            val totalSpacing = spacing * (numBands - 1)
            val barWidth = ((width - totalSpacing) / numBands).coerceAtLeast(1f)

            // Horizontal reference lines (-12dB, -24dB, -36dB)
            listOf(0.25f, 0.50f, 0.75f).forEach { step ->
                val y = height * (1f - step)
                drawLine(
                    color = Color.White.copy(alpha = 0.05f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Frequency bars
            for (i in 0 until numBands) {
                val x = i * (barWidth + spacing)
                val level = animBands[i].coerceIn(0f, 1f)
                val barHeight = (height * level).coerceAtLeast(2.dp.toPx())
                val y = height - barHeight

                val brush = Brush.verticalGradient(
                    colors = listOf(accent, accent.copy(alpha = 0.28f)),
                    startY = y,
                    endY = height
                )

                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )

                val peak = peakBands[i].coerceIn(0f, 1f)
                if (peak > 0.04f) {
                    val peakY = height - (height * peak).coerceAtLeast(2.dp.toPx())
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.85f),
                        topLeft = Offset(x, peakY),
                        size = Size(barWidth, 2.dp.toPx()),
                        cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                    )
                }
            }
        }

        // Frequency Labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("32Hz", "64", "125", "250", "500", "1k", "2k", "4k", "8k", "16k").forEach { label ->
                Text(label, color = Color.White.copy(alpha = 0.35f), fontSize = 9.5.sp)
            }
        }

        val peakDbfs = levels?.peakDbfs
        val rmsDbfs = levels?.rmsDbfs
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Pico", color = Color.White.copy(alpha = 0.40f), fontSize = 10.5.sp)
                    Text(
                        text = peakDbfs?.let { String.format(Locale.getDefault(), "%.1f dBFS", it) } ?: "—",
                        color = Color.White.copy(alpha = 0.70f),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                val peakProgress = (((peakDbfs ?: -60f) + 60f) / 60f).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { peakProgress },
                    modifier = Modifier.fillMaxWidth().height(3.5.dp).padding(top = 3.dp).clip(CircleShape),
                    color = if (peakDbfs != null && peakDbfs > -1f) Color(0xFFEF4444) else accent,
                    trackColor = Color.White.copy(alpha = 0.08f)
                )
            }
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("RMS", color = Color.White.copy(alpha = 0.40f), fontSize = 10.5.sp)
                    Text(
                        text = rmsDbfs?.let { String.format(Locale.getDefault(), "%.1f dBFS", it) } ?: "—",
                        color = Color.White.copy(alpha = 0.70f),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                val rmsProgress = (((rmsDbfs ?: -60f) + 60f) / 60f).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { rmsProgress },
                    modifier = Modifier.fillMaxWidth().height(3.5.dp).padding(top = 3.dp).clip(CircleShape),
                    color = accent.copy(alpha = 0.8f),
                    trackColor = Color.White.copy(alpha = 0.08f)
                )
            }
        }
    }
}

private fun balanceLabel(balance: Float): String {
    val rounded = (balance * 100).roundToInt()
    return when {
        abs(rounded) <= 2 -> "Centro"
        rounded < 0 -> "Izq ${abs(rounded)}%"
        else -> "Der ${rounded}%"
    }
}

@Composable
private fun SoundCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
        content = content
    )
}

@Composable
private fun SoundChoice(label: String, value: String, values: List<String>, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White.copy(alpha = 0.75f), fontSize = 13.5.sp, modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { open = true }) {
                Text(value, color = LocalArtworkAccent.current, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Color(0xFF202127)) {
                values.forEachIndexed { index, text ->
                    DropdownMenuItem(
                        text = { Text(text, color = if (text == value) LocalArtworkAccent.current else Color.White) },
                        onClick = { open = false; onSelect(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SoundSwitch(label: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = enabled,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = LocalArtworkAccent.current, checkedThumbColor = Color(0xFF121317))
        )
    }
}

@Composable
private fun SoundSlider(
    label: String,
    value: String,
    amount: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean,
    onValue: (Float) -> Unit,
    onFinished: () -> Unit
) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(value, color = LocalArtworkAccent.current, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Slider(
            value = amount,
            onValueChange = onValue,
            onValueChangeFinished = onFinished,
            valueRange = range,
            steps = steps,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = LocalArtworkAccent.current,
                activeTrackColor = LocalArtworkAccent.current,
                inactiveTrackColor = Color.White.copy(alpha = 0.1f),
                activeTickColor = Color(0xFF121317).copy(alpha = 0.6f),
                inactiveTickColor = LocalArtworkAccent.current.copy(alpha = 0.3f),
                disabledThumbColor = Color.White.copy(alpha = 0.3f),
                disabledActiveTrackColor = Color.White.copy(alpha = 0.15f),
                disabledInactiveTrackColor = Color.White.copy(alpha = 0.06f),
                disabledActiveTickColor = Color.Transparent,
                disabledInactiveTickColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun soundChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = LocalArtworkAccent.current.copy(alpha = 0.18f),
    selectedLabelColor = LocalArtworkAccent.current,
    labelColor = Color.White.copy(alpha = 0.55f)
)

@Composable
private fun VerticalEqFader(
    value: Float,
    enabled: Boolean,
    label: String,
    modifier: Modifier,
    onValue: (Float) -> Unit,
    onFinished: () -> Unit
) {
    val accent = LocalArtworkAccent.current
    val latestChange by rememberUpdatedState(onValue)
    val latestFinished by rememberUpdatedState(onFinished)
    Canvas(
        modifier
            .semantics {
                contentDescription = label
                stateDescription = "${decimal(value)} decibelios"
                progressBarRangeInfo = ProgressBarRangeInfo(value, -12f..12f, 95)
                setProgress { requested ->
                    if (enabled) {
                        latestChange((requested * 4).roundToInt().div(4f).coerceIn(-12f, 12f))
                        latestFinished()
                        true
                    } else false
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val inset = 12.dp.toPx()
                    fun change(y: Float) {
                        val gain = (12 - ((y - inset) / (size.height - inset * 2)).coerceIn(0f, 1f) * 24)
                        latestChange((gain * 4).roundToInt() / 4f)
                    }
                    down.consume()
                    change(down.position.y)
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!pointer.pressed) {
                                pointer.consume()
                                break
                            }
                            change(pointer.position.y)
                            pointer.consume()
                        }
                    } finally {
                        latestFinished()
                    }
                }
            }
    ) {
        val x = size.width / 2
        val inset = 12.dp.toPx()
        val center = size.height / 2
        val thumb = inset + (12 - value) / 24 * (size.height - inset * 2)
        val tint = if (enabled) accent else Color.White.copy(alpha = 0.18f)
        drawLine(Color.White.copy(alpha = 0.1f), Offset(x, inset), Offset(x, size.height - inset), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.22f), Offset(x - 5.dp.toPx(), center), Offset(x + 5.dp.toPx(), center), 1.dp.toPx())
        drawLine(tint.copy(alpha = 0.7f), Offset(x, center), Offset(x, thumb), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(tint, radius = 6.dp.toPx(), center = Offset(x, thumb))
        drawCircle(if (enabled) Color.White else tint, radius = 2.dp.toPx(), center = Offset(x, thumb))
    }
}

private fun decimal(value: Float) = String.format(Locale.ROOT, "%.1f", value)
private fun hz(value: Float) = if (value >= 1000) "${String.format(Locale.ROOT, "%.1f", value / 1000)} kHz" else "${value.roundToInt()} Hz"

@Composable
private fun EqResponse(profile: EngineProfile, accent: Color) {
    val curve = remember(profile.bands, profile.eq, profile.rate) {
        (0..160).map { point ->
            val frequency = 20.0 * 1000.0.pow(point / 160.0)
            var db = 0.0
            if (profile.eq && frequency < profile.rate / 2) {
                profile.bands.filter { it.hz < profile.rate * 0.48 }.forEach { band ->
                    val a = 10.0.pow(band.gain / 40.0)
                    val w = 2 * PI * band.hz / profile.rate
                    val alpha = sin(w) / (2 * band.q)
                    val a0 = 1 + alpha / a
                    val b0 = (1 + alpha * a) / a0
                    val b1 = -2 * cos(w) / a0
                    val b2 = (1 - alpha * a) / a0
                    val a1 = -2 * cos(w) / a0
                    val a2 = (1 - alpha / a) / a0
                    val f = 2 * PI * frequency / profile.rate
                    val nr = b0 + b1 * cos(f) + b2 * cos(2 * f)
                    val ni = -b1 * sin(f) - b2 * sin(2 * f)
                    val dr = 1 + a1 * cos(f) + a2 * cos(2 * f)
                    val di = -a1 * sin(f) - a2 * sin(2 * f)
                    db += 10 * log10(((nr * nr + ni * ni) / (dr * dr + di * di)).coerceAtLeast(1e-12))
                }
            }
            db.coerceIn(-24.0, 24.0).toFloat()
        }
    }
    Canvas(Modifier.fillMaxWidth().height(130.dp)) {
        for (line in -2..2) {
            val y = size.height / 2 - line * size.height / 4
            drawLine(Color.White.copy(alpha = if (line == 0) 0.16f else 0.06f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
        val path = Path()
        curve.forEachIndexed { i, value ->
            val x = size.width * i / 160
            val y = size.height / 2 - value * size.height / 48
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, accent.copy(alpha = 0.12f), style = Stroke(10.dp.toPx()))
        drawPath(path, accent, style = Stroke(2.dp.toPx()))
    }
}
