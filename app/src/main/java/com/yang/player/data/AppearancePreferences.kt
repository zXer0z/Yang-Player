package com.yang.player.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    DYNAMIC,        // Extrae color y resplandor de la carátula en reproducción
    OLED_BLACK,     // Fondo 100% negro puro, sin resplandor, acento en plata/blanco
    CUSTOM_ACCENT   // Fondo oscuro uniforme, acento estático fijo elegido por el usuario
}

enum class ArtworkStyle(val id: String, val title: String, val subtitle: String) {
    MODERN_ROUNDED("modern_rounded", "Moderna flotante", "Esquinas redondeadas de 24dp, cristal fino y sombra profunda"),
    VINYL_RECORD("vinyl_record", "Vinilo / Acetato giratorio", "Disco negro con surcos realistas, gira al reproducir e inercia al pausar"),
    CD_CLASSIC("cd_classic", "CD clásico brillante", "Disco compacto con anillo transparente y reflejo prismático"),
    STUDIO_FLAT("studio_flat", "Borde a borde", "Carátula plana pura de estudio de grabación")
}

data class AccentOption(
    val id: String,
    val name: String,
    val hex: String,
    val color: Color
)

data class AppearanceConfig(
    val themeMode: ThemeMode = ThemeMode.DYNAMIC,
    val staticAccentHex: String = "#007AFF",
    val beatReactiveAtmosphere: Boolean = false,
    val artworkStyle: ArtworkStyle = ArtworkStyle.MODERN_ROUNDED,
    val roonRadioEnabled: Boolean = true
)

class AppearancePreferences private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("appearance_settings_v1", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<AppearanceConfig> = _config.asStateFlow()

    private fun loadConfig(): AppearanceConfig {
        val modeName = prefs.getString("theme_mode", ThemeMode.DYNAMIC.name) ?: ThemeMode.DYNAMIC.name
        val mode = runCatching { ThemeMode.valueOf(modeName) }.getOrDefault(ThemeMode.DYNAMIC)
        val hex = prefs.getString("static_accent_hex", "#007AFF") ?: "#007AFF"
        val beatReactive = prefs.getBoolean("beat_reactive_atmosphere", false)
        val styleName = prefs.getString("artwork_style", ArtworkStyle.MODERN_ROUNDED.name) ?: ArtworkStyle.MODERN_ROUNDED.name
        val style = runCatching { ArtworkStyle.valueOf(styleName) }.getOrDefault(ArtworkStyle.MODERN_ROUNDED)
        val roonRadio = prefs.getBoolean("roon_radio_enabled", true)
        return AppearanceConfig(
            themeMode = mode,
            staticAccentHex = hex,
            beatReactiveAtmosphere = beatReactive,
            artworkStyle = style,
            roonRadioEnabled = roonRadio
        )
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _config.value = _config.value.copy(themeMode = mode)
    }

    fun setStaticAccent(hex: String) {
        prefs.edit().putString("static_accent_hex", hex).apply()
        _config.value = _config.value.copy(staticAccentHex = hex)
    }

    fun setBeatReactiveAtmosphere(enabled: Boolean) {
        prefs.edit().putBoolean("beat_reactive_atmosphere", enabled).apply()
        _config.value = _config.value.copy(beatReactiveAtmosphere = enabled)
    }

    fun setArtworkStyle(style: ArtworkStyle) {
        prefs.edit().putString("artwork_style", style.name).apply()
        _config.value = _config.value.copy(artworkStyle = style)
    }

    fun setRoonRadioEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("roon_radio_enabled", enabled).apply()
        _config.value = _config.value.copy(roonRadioEnabled = enabled)
    }

    companion object {
        val ACCENT_PRESETS = listOf(
            AccentOption("blue", "Azul Yang", "#007AFF", Color(0xFF007AFF)),
            AccentOption("purple", "Púrpura Neón", "#A855F7", Color(0xFFA855F7)),
            AccentOption("green", "Verde Esmeralda", "#10B981", Color(0xFF10B981)),
            AccentOption("gold", "Dorado Hi-Fi", "#E5A93C", Color(0xFFE5A93C)),
            AccentOption("red", "Rojo Carmesí", "#EF4444", Color(0xFFEF4444)),
            AccentOption("cyan", "Cian Glaciar", "#06B6D4", Color(0xFF06B6D4)),
            AccentOption("pink", "Rosa Magenta", "#EC4899", Color(0xFFEC4899)),
            AccentOption("orange", "Naranja Atardecer", "#F97316", Color(0xFFF97316)),
            AccentOption("silver", "Plata Minimalista", "#E2E8F0", Color(0xFFE2E8F0))
        )

        @Volatile
        private var instance: AppearancePreferences? = null

        fun get(context: Context): AppearancePreferences = instance ?: synchronized(this) {
            instance ?: AppearancePreferences(context.applicationContext).also { instance = it }
        }
    }
}
