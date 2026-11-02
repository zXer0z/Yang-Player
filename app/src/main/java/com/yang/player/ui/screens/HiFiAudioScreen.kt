package com.yang.player.ui.screens

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.tween
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val routePager = rememberPagerState(initialPage = activeRoute.ordinal, pageCount = { AudioRoute.entries.size })
    val selectedRoute = AudioRoute.entries[routePager.currentPage]
    LaunchedEffect(activeRoute) {
        if (!routePager.isScrollInProgress && routePager.currentPage != activeRoute.ordinal) {
            routePager.animateScrollToPage(activeRoute.ordinal, animationSpec = tween(240))
        }
    }
    var draft by remember(selectedRoute, profiles) { mutableStateOf(profiles.getValue(selectedRoute)) }
    var bandIndex by remember { mutableIntStateOf(4) }
    var advancedEq by remember { mutableStateOf(false) }
    var section by rememberSaveable { mutableIntStateOf(0) }
    fun save(value: EngineProfile = draft) { draft = value.valid(); engine.save(draft) }
    if (yin.active) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
            .padding(top = 24.dp, bottom = bottomContentPadding + 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("Hi-Fi", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("Motor y salida", color = Color.White.copy(alpha = .5f), fontSize = 14.sp)
            SoundCard {
                Text(if (yin.ready) "Yin · conectado" else "Yin · esperando conexión", color = accent, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(playerState.currentSong?.title ?: "Elige una canción", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                playerState.currentSong?.let { Text(it.audioQualityBadge, color = Color.White.copy(alpha = .55f), fontSize = 13.sp) }
                if (yin.output.isNotBlank()) Text(yin.output, color = Color.White.copy(alpha = .7f), fontSize = 14.sp)
                if (!yin.ready) Text(yin.message.ifBlank { "El audio espera a que Yin esté listo." }, color = accent, fontSize = 13.sp)
            }
            SoundCard {
                Text("Ajustes de sonido", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text("Yin administra esta reproducción. Abre su panel para ajustar el ecualizador, la ganancia y la salida.", color = Color.White.copy(alpha = .6f), fontSize = 14.sp, lineHeight = 21.sp)
                Button(onClick = { context.packageManager.getLaunchIntentForPackage("local.v60.player")?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color(0xFF111318))) { Text("Abrir controles de Yin") }
            }
        }
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
        .padding(top = 24.dp, bottom = bottomContentPadding), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Hi-Fi", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("Tu sonido, a tu manera", color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
            com.yang.player.ui.components.HiFiWave(playerState.isPlaying, accent)
        }
        HorizontalPager(state = routePager, key = { AudioRoute.entries[it].name },
            pageSpacing = 12.dp, modifier = Modifier.fillMaxWidth()) { page ->
            val route = AudioRoute.entries[page]
            val profile = profiles.getValue(route)
            val isActive = route == activeRoute
            SoundCard(Modifier.heightIn(min = 210.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(if (isActive) "SALIDA ACTIVA" else "PERFIL GUARDADO", color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text(route.label, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    }

                }
                Text("${profile.backend.label} · ${hz(profile.rate.toFloat())} · ${pcmName(profile.encoding)}",
                    color = Color.White.copy(alpha = .65f), fontSize = 13.sp)
                Text(if (isActive && signal.rate > 0 && signal.route == route)
                    "Flujo abierto: ${hz(signal.rate.toFloat())} · ${pcmName(signal.encoding)}"
                    else if (isActive) "Sus ajustes se usan al reproducir por esta conexión."
                    else "Configura este perfil para cuando conectes esta salida.",
                    color = Color.White.copy(alpha = .45f), fontSize = 12.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Desliza para cambiar de perfil", color = Color.White.copy(alpha = .4f), fontSize = 12.sp, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AudioRoute.entries.forEach { route ->
                    Box(Modifier.size(if (route == selectedRoute) 8.dp else 6.dp).clip(CircleShape)
                        .background(if (route == selectedRoute) accent else Color.White.copy(alpha = .18f)))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Sonido", "Salida", "Detalles").forEachIndexed { index, title ->
                FilterChip(selected = section == index, onClick = { section = index },
                    modifier = Modifier.weight(1f), label = { Text(title) }, colors = soundChipColors())
            }
        }
        androidx.compose.animation.Crossfade(targetState = section, animationSpec = androidx.compose.animation.core.tween(180), label = "hifiSection") { activeSection ->
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        when (activeSection) {
            0 -> {
        SoundCard {
            Text("Preamplificador · ${selectedRoute.label}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            SoundSlider("Ganancia", "${decimal(draft.preamp)} dB", draft.preamp, -24f..12f, 71, true, { draft = draft.copy(preamp = it); engine.preview(draft) }) { save() }
            SoundSwitch("Margen automático para el EQ", draft.headroom) { save(draft.copy(headroom = it)) }
            SoundSwitch("Limitador de picos", draft.limiter) { save(draft.copy(limiter = it)) }
            Text("El margen baja la ganancia según el realce máximo del ecualizador. El limitador suaviza los picos cuando hay efectos.", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
        }
        SoundCard {
            SoundSwitch("Ecualizador", draft.eq) { save(draft.copy(eq = it)) }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Plano", "Bajos", "Voces").forEachIndexed { index, label ->
                    OutlinedButton(onClick = {
                        val gains = when(index) { 1 -> listOf(4f, 3f, 2f, 0f, -1f, -1f, 0f, 0f, 1f, 1f); 2 -> listOf(-2f, -2f, -1f, 0f, 2f, 3f, 3f, 1f, 0f, 0f); else -> List(10) { 0f } }
                        save(draft.copy(eq = true, bands = defaultBands.mapIndexed { i, band -> band.copy(gain = gains[i]) }))
                    }, colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)) { Text(label, fontSize = 12.sp) }
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val bandWidth = (maxWidth / 10).coerceAtLeast(30.dp)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    draft.bands.forEachIndexed { index, band ->
                        Column(Modifier.width(bandWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(String.format(Locale.ROOT, "%+.1f", band.gain), color = if (draft.eq) accent else Color.White.copy(alpha = 0.3f), fontSize = 9.sp, maxLines = 1, softWrap = false)
                            VerticalEqFader(band.gain, draft.eq, "Banda ${hz(band.hz)}", Modifier.fillMaxWidth().height(164.dp), onValue = { gain ->
                                draft = draft.copy(bands = draft.bands.toMutableList().also { it[index] = band.copy(gain = gain) })
                                engine.preview(draft)
                            }, onFinished = { save() })
                            Text(if (band.hz >= 1000) "${decimal(band.hz / 1000).removeSuffix(".0")}k" else band.hz.roundToInt().toString(), color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Graves", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
                Text("Medios", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
                Text("Agudos", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
            }
            Text("Sube o baja cada barra · −12 a +12 dB", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
            TextButton(onClick = { advancedEq = !advancedEq }, modifier = Modifier.align(Alignment.End)) {
                Text("Avanzado", color = accent)
                Icon(if (advancedEq) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = accent)
            }
            if (advancedEq) {
            EqResponse(if (selectedRoute == activeRoute && signal.rate > 0) draft.copy(rate = signal.rate) else draft, accent)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                draft.bands.forEachIndexed { index, band -> FilterChip(selected = index == bandIndex, onClick = { bandIndex = index }, label = { Text(hz(band.hz), fontSize = 11.sp) }, colors = soundChipColors()) }
            }
            val band = draft.bands[bandIndex]
            fun updateBand(value: EqBand) { draft = draft.copy(bands = draft.bands.toMutableList().also { it[bandIndex] = value }) }
            SoundSlider("Ganancia", "${decimal(band.gain)} dB", band.gain, -12f..12f, 95, draft.eq, { updateBand(band.copy(gain = it)) }) { save() }
            SoundSlider("Frecuencia", hz(band.hz), (ln(band.hz / 20f) / ln(1000f)).coerceIn(0f, 1f), 0f..1f, 0, draft.eq,
                { updateBand(band.copy(hz = 20f * 1000f.pow(it))) }) { save() }
            SoundSlider("Ancho de banda · Q", decimal(band.q), band.q, 0.3f..6f, 56, draft.eq, { updateBand(band.copy(q = it)) }) { save() }
            Text("Q bajo: ajuste amplio. Q alto: ajuste más preciso.", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
            }
        }

            }
            1 -> {
        SoundCard {
            Text("${selectedRoute.label} · hasta ${hz(selectedRoute.ceiling.toFloat())}", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            SoundChoice("Salida", draft.backend.label, OutputBackend.entries.map { it.label }) { selected -> save(draft.copy(backend = OutputBackend.entries[selected])) }
            val rates = listOf(44100, 48000, 88200, 96000, 176400, 192000).filter { it <= selectedRoute.ceiling }
            SoundChoice("Frecuencia", hz(draft.rate.toFloat()), rates.map { hz(it.toFloat()) }) { save(draft.copy(rate = rates[it])) }
            val encodings = when (selectedRoute) { AudioRoute.SPEAKER -> listOf(2); AudioRoute.BLUETOOTH -> listOf(2, 21); else -> listOf(2, 21, 22, 4) }
            SoundChoice("Formato PCM", pcmName(draft.encoding), encodings.map(::pcmName)) { save(draft.copy(encoding = encodings[it])) }
            val buffers = listOf(40, 80, 100, 200, 400, 800)
            SoundChoice("Búfer", "${draft.bufferMs} ms", buffers.map { "$it ms" }) { save(draft.copy(bufferMs = buffers[it])) }
            Text("Un búfer mayor resiste mejor las interrupciones y añade más retardo.", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
        }
            }
            else -> {
        SoundCard {
            if (yin.installed) Text(if (yin.ready) "Yin está listo para recibir el audio" else yin.message.ifBlank { "Activa Yin Control para usar su motor root" }, color = accent, fontSize = 12.sp)
            Text(if (playerState.isPlaying) "Salida en reproducción" else "Última salida abierta", color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(playerState.currentSong?.title ?: "Elige una canción", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            if (signal.rate > 0) {
                Text(signal.backend, color = Color.White, fontSize = 16.sp)
                Text("${signal.device} · ${hz(signal.rate.toFloat())} · ${pcmName(signal.encoding)}", color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp)
                Text("Solicitado: ${hz(signal.requestedRate.toFloat())} · ${pcmName(signal.requestedEncoding)}", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
                Text("Búfer: ${signal.bufferFrames} muestras · ${signal.bufferFrames * 1000 / signal.rate} ms" +
                    if (signal.underruns >= 0) " · ${signal.underruns} cortes de búfer" else "", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                Text("PCM decodificado: ${hz(signal.inputRate.toFloat())} · ${pcmName(signal.inputEncoding)}", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                if (signal.decoder.isNotBlank()) Text("Decodificador: ${signal.decoder}", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
                if (signal.bitrate > 0) Text("Bitrate de origen: ${signal.bitrate / 1000} kbps", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
                if (signal.note.isNotBlank()) Text(signal.note, color = accent.copy(alpha = 0.8f), fontSize = 12.sp)
                Text("El formato del flujo abierto puede diferir del formato interno del DAC o del códec Bluetooth.", color = Color.White.copy(alpha = 0.35f), fontSize = 11.sp)
            } else Text("La ruta y su formato aparecerán al reproducir.", color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp)
        }
            }
        }

        }
        }
        Text(if (section == 1) "Los cambios de salida pueden reiniciar el audio. Cada conexión conserva su propio perfil." else "Estás ajustando ${selectedRoute.label}. Los cambios se guardan en este perfil.", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
        TextButton(onClick = { save(EngineProfile(selectedRoute)) }) { Text("Restablecer este perfil", color = accent) }
    }
}

@Composable private fun SoundCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.045f)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}
@Composable private fun SoundChoice(label: String, value: String, values: List<String>, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { open = true }) { Text(value, color = LocalArtworkAccent.current, fontSize = 13.sp) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Color(0xFF202127)) {
                values.forEachIndexed { index, text -> DropdownMenuItem(text = { Text(text, color = if (text == value) LocalArtworkAccent.current else Color.White) }, onClick = { open = false; onSelect(index) }) }
            }
        }
    }
}
@Composable private fun SoundSwitch(label: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = enabled, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = LocalArtworkAccent.current, checkedThumbColor = Color(0xFF121317)))
    }
}
@Composable private fun SoundSlider(label: String, value: String, amount: Float, range: ClosedFloatingPointRange<Float>, steps: Int, enabled: Boolean, onValue: (Float) -> Unit, onFinished: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(value, color = LocalArtworkAccent.current, fontSize = 12.sp)
        }
        Slider(value = amount, onValueChange = onValue, onValueChangeFinished = onFinished, valueRange = range, steps = steps, enabled = enabled,
            colors = SliderDefaults.colors(thumbColor = LocalArtworkAccent.current, activeTrackColor = LocalArtworkAccent.current, inactiveTrackColor = Color.White.copy(alpha = 0.1f),
                activeTickColor = Color(0xFF121317).copy(alpha = 0.6f), inactiveTickColor = LocalArtworkAccent.current.copy(alpha = 0.3f),
                disabledThumbColor = Color.White.copy(alpha = 0.3f), disabledActiveTrackColor = Color.White.copy(alpha = 0.15f),
                disabledInactiveTrackColor = Color.White.copy(alpha = 0.06f), disabledActiveTickColor = Color.Transparent, disabledInactiveTickColor = Color.Transparent))
    }
}
@Composable private fun soundChipColors() = FilterChipDefaults.filterChipColors(selectedContainerColor = LocalArtworkAccent.current.copy(alpha = 0.15f), selectedLabelColor = LocalArtworkAccent.current, labelColor = Color.White.copy(alpha = 0.5f))
@Composable private fun VerticalEqFader(value: Float, enabled: Boolean, label: String, modifier: Modifier, onValue: (Float) -> Unit, onFinished: () -> Unit) {
    val accent = LocalArtworkAccent.current
    val latestChange by rememberUpdatedState(onValue)
    val latestFinished by rememberUpdatedState(onFinished)
    Canvas(modifier.semantics {
        contentDescription = label
        stateDescription = "${decimal(value)} decibelios"
        progressBarRangeInfo = ProgressBarRangeInfo(value, -12f..12f, 95)
        setProgress { requested -> if (enabled) { latestChange((requested * 4).roundToInt().div(4f).coerceIn(-12f, 12f)); latestFinished(); true } else false }
    }.pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val inset = 12.dp.toPx()
            fun change(y: Float) { val gain = (12 - ((y - inset) / (size.height - inset * 2)).coerceIn(0f, 1f) * 24); latestChange((gain * 4).roundToInt() / 4f) }
            down.consume(); change(down.position.y)
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!pointer.pressed) { pointer.consume(); break }
                    change(pointer.position.y); pointer.consume()
                }
            } finally { latestFinished() }
        }
    }) {
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

@Composable private fun EqResponse(profile: EngineProfile, accent: Color) {
    val curve = remember(profile.bands, profile.eq, profile.rate) {
        (0..160).map { point ->
            val frequency = 20.0 * 1000.0.pow(point / 160.0)
            var db = 0.0
            if (profile.eq && frequency < profile.rate / 2) profile.bands.filter { it.hz < profile.rate * 0.48 }.forEach { band ->
                val a = 10.0.pow(band.gain / 40.0); val w = 2 * PI * band.hz / profile.rate
                val alpha = sin(w) / (2 * band.q); val a0 = 1 + alpha / a
                val b0 = (1 + alpha * a) / a0; val b1 = -2 * cos(w) / a0; val b2 = (1 - alpha * a) / a0
                val a1 = -2 * cos(w) / a0; val a2 = (1 - alpha / a) / a0
                val f = 2 * PI * frequency / profile.rate
                val nr = b0 + b1 * cos(f) + b2 * cos(2 * f); val ni = -b1 * sin(f) - b2 * sin(2 * f)
                val dr = 1 + a1 * cos(f) + a2 * cos(2 * f); val di = -a1 * sin(f) - a2 * sin(2 * f)
                db += 10 * log10(((nr * nr + ni * ni) / (dr * dr + di * di)).coerceAtLeast(1e-12))
            }
            db.coerceIn(-24.0, 24.0).toFloat()
        }
    }
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        for (line in -2..2) { val y = size.height / 2 - line * size.height / 4; drawLine(Color.White.copy(alpha = if (line == 0) 0.16f else 0.06f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx()) }
        val path = Path()
        curve.forEachIndexed { i, value -> val x = size.width * i / 160; val y = size.height / 2 - value * size.height / 48; if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
        drawPath(path, accent.copy(alpha = 0.12f), style = Stroke(10.dp.toPx()))
        drawPath(path, accent, style = Stroke(2.dp.toPx()))
    }
}
