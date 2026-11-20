package com.yang.player.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntSize
import com.yang.player.data.ThemeMode
import com.yang.player.ui.theme.ArtworkPalette
import com.yang.player.ui.theme.BackgroundDark
import kotlin.math.roundToInt

/** Draws the cached, pre-blurred cover behind the controls. */
@Composable
internal fun ArtworkBlurBackdrop(
    palette: ArtworkPalette,
    themeMode: ThemeMode,
    accent: Color,
    modifier: Modifier = Modifier,
    dim: Float = .14f,
) {
    if (themeMode == ThemeMode.OLED_BLACK) {
        Box(modifier.background(Color.Black))
        return
    }

    val scrim = remember(dim) {
        Brush.verticalGradient(
            0f to Color.Black.copy(alpha = dim),
            .46f to Color.Black.copy(alpha = dim + .12f),
            1f to BackgroundDark.copy(alpha = (dim + .58f).coerceAtMost(.96f))
        )
    }
    val dynamic = themeMode == ThemeMode.DYNAMIC
    Crossfade(
        targetState = palette.takeIf { dynamic },
        modifier = modifier,
        animationSpec = tween(450),
        label = "artworkBackdrop"
    ) { colors ->
        val fallback = remember(colors, accent, dynamic) {
            Brush.verticalGradient(
                listOf(
                    lerp(BackgroundDark, colors?.topColor ?: accent, if (dynamic) .86f else .32f),
                    lerp(BackgroundDark, colors?.midColor ?: accent, if (dynamic) .58f else .15f),
                    BackgroundDark
                )
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            val mesh = colors?.ambientMesh
            if (mesh != null) {
                drawImage(
                    image = mesh,
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    filterQuality = FilterQuality.Low
                )
            } else {
                drawRect(fallback)
            }
            drawRect(scrim)
        }
    }
}
