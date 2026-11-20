package com.yang.player.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yang.player.data.AppearancePreferences
import com.yang.player.data.ArtworkStyle
import com.yang.player.data.ThemeMode
import com.yang.player.ui.theme.LocalArtworkAccent
import com.yang.player.ui.theme.TextDim
import com.yang.player.ui.theme.TextMuted
import com.yang.player.ui.theme.TextWhite

@Composable
fun AppearanceScreen(
    bottomContentPadding: Dp,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appearancePrefs = remember { AppearancePreferences.get(context) }
    val config by appearancePrefs.config.collectAsState()
    val accent = LocalArtworkAccent.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Volver",
                    tint = accent
                )
            }
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Personalización",
                    color = TextWhite,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Tema de la app, reproductor y colores de acento",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 8.dp,
                bottom = bottomContentPadding + 100.dp
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Section 1: Main Theme Mode
            item {
                SectionHeader("Tema de la navegación")

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White.copy(alpha = 0.045f)),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ThemeOptionItem(
                        title = "Dinámico de carátula",
                        subtitle = "Extrae el acento y resplandor atmosférico de la canción en reproducción",
                        icon = Icons.Rounded.AutoAwesome,
                        isSelected = config.themeMode == ThemeMode.DYNAMIC,
                        accent = accent,
                        onClick = { appearancePrefs.setThemeMode(ThemeMode.DYNAMIC) }
                    )

                    ThemeOptionItem(
                        title = "Negro Puro OLED",
                        subtitle = "Fondo 100% negro absoluto sin resplandores. Acento en blanco/plata y cristal",
                        icon = Icons.Rounded.DarkMode,
                        isSelected = config.themeMode == ThemeMode.OLED_BLACK,
                        accent = accent,
                        onClick = { appearancePrefs.setThemeMode(ThemeMode.OLED_BLACK) }
                    )

                    ThemeOptionItem(
                        title = "Color estático fijo",
                        subtitle = "Fondo oscuro constante con un color de acento permanente que tú elijas",
                        icon = Icons.Rounded.Palette,
                        isSelected = config.themeMode == ThemeMode.CUSTOM_ACCENT,
                        accent = accent,
                        onClick = { appearancePrefs.setThemeMode(ThemeMode.CUSTOM_ACCENT) }
                    )
                }
            }

            // Section 2: Accent Color Selector (visible if CUSTOM_ACCENT)
            item {
                AnimatedVisibility(
                    visible = config.themeMode == ThemeMode.CUSTOM_ACCENT,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeader("Color de acento estático")

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color.White.copy(alpha = 0.045f))
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Elige el color para botones, iconos activos y elementos destacados:",
                                color = TextMuted,
                                fontSize = 13.sp
                            )

                            // Palette grid (3 items per row)
                            val presets = AppearancePreferences.ACCENT_PRESETS
                            val chunked = presets.chunked(3)
                            chunked.forEach { rowPresets ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowPresets.forEach { preset ->
                                        val isSelected = config.staticAccentHex.equals(preset.hex, ignoreCase = true)
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(if (isSelected) preset.color.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.04f))
                                                .clickable { appearancePrefs.setStaticAccent(preset.hex) }
                                                .padding(vertical = 12.dp, horizontal = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clip(CircleShape)
                                                        .background(preset.color),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (isSelected) {
                                                        Icon(
                                                            Icons.Rounded.Check,
                                                            contentDescription = null,
                                                            tint = Color.White,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = preset.name,
                                                    color = if (isSelected) TextWhite else TextMuted,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 3: Artwork Style for the Player
            item {
                SectionHeader("Diseño de carátula en el reproductor")

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White.copy(alpha = 0.045f)),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ArtworkStyle.entries.forEach { style ->
                        val isSelected = config.artworkStyle == style
                        val icon = when (style) {
                            ArtworkStyle.MODERN_ROUNDED -> Icons.Rounded.CropPortrait
                            ArtworkStyle.VINYL_RECORD -> Icons.Rounded.Album
                            ArtworkStyle.CD_CLASSIC -> Icons.Rounded.Adjust
                            ArtworkStyle.STUDIO_FLAT -> Icons.Rounded.CropSquare
                        }

                        ThemeOptionItem(
                            title = style.title,
                            subtitle = style.subtitle,
                            icon = icon,
                            isSelected = isSelected,
                            accent = accent,
                            onClick = { appearancePrefs.setArtworkStyle(style) }
                        )
                    }
                }
            }

            // Section 4: Visual Effects on Playback
            item {
                SectionHeader("Efectos en reproducción")

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White.copy(alpha = 0.045f))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Brillo al ritmo de la música",
                                color = TextWhite,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "El fondo y color de acento de la pantalla grande pulsan dinámicamente con los graves del audio",
                                color = TextMuted,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        Switch(
                            checked = config.beatReactiveAtmosphere,
                            onCheckedChange = { appearancePrefs.setBeatReactiveAtmosphere(it) },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = accent,
                                checkedThumbColor = Color.White
                            )
                        )
                    }

                    if (config.themeMode != ThemeMode.DYNAMIC) {
                        Text(
                            text = "💡 Nota: Esta función pulsa el resplandor de la carátula y se activa cuando el Tema de navegación es Dinámico.",
                            color = TextDim,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                    }
                }
            }

            // Section 5: Live Preview Card
            item {
                SectionHeader("Vista previa en vivo")

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White.copy(alpha = 0.045f))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Así luce tu interfaz de navegación:",
                        color = TextMuted,
                        fontSize = 12.sp
                    )

                    // Mock Song item
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(accent.copy(alpha = 0.12f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(accent.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.MusicNote, null, tint = accent, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Canción en reproducción", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Artista de ejemplo · Álbum", color = TextMuted, fontSize = 12.sp)
                        }
                        Icon(Icons.Rounded.Equalizer, null, tint = accent, modifier = Modifier.size(20.dp))
                    }

                    // Mock Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {},
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Botón principal", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        FilterChip(
                            selected = true,
                            onClick = {},
                            label = { Text("Filtro activo", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent.copy(alpha = 0.18f),
                                selectedLabelColor = accent
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = TextWhite,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun ThemeOptionItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (isSelected) accent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) accent else TextMuted,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (isSelected) TextWhite else TextWhite.copy(alpha = 0.85f),
                fontSize = 15.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = TextMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }

        Spacer(Modifier.width(8.dp))

        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = accent,
                unselectedColor = Color.White.copy(alpha = 0.3f)
            )
        )
    }
}
