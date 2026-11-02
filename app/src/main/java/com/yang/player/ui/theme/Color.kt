package com.yang.player.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.staticCompositionLocalOf

val LocalArtworkAccent = staticCompositionLocalOf { Color(0xFFB4BECF) }

// Backgrounds & Surfaces
val BackgroundDark = Color(0xFF090A0C)
val SurfaceDark = Color(0xFF13151A)
val SurfaceCard = Color(0xFF181B22)
val SurfaceElevated = Color(0xFF1F222B)

// Accent Colors (Matches user's screenshot)
val AccentBlue = Color(0xFF007AFF)          // Vibrant electric blue for "Jose", "Todo", "Ver todo"
val AccentBlueDark = Color(0xFF0A4480)
val ActiveSongContainer = Color(0xFF0E2238) // Deep translucent blue container for currently playing song
val ActiveSongBorder = Color(0xFF1B4B7A)
val HiResGold = Color(0xFFE5A93C)
val HiResCyan = Color(0xFF38B2AC)

// Text Colors
val TextWhite = Color(0xFFFFFFFF)
val TextMuted = Color(0xFF868C98)
val TextDim = Color(0xFF575B66)

// Glassmorphic MiniPlayer (Lower transparency, deep obsidian black dock)
val MiniPlayerGlass = Color(0xF4111319)
val MiniPlayerBorder = Color(0x2BFFFFFF)

