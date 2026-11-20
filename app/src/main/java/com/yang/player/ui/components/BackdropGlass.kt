package com.yang.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yang.player.data.AppearancePreferences
import com.yang.player.data.ThemeMode

/** Samples only the screen behind overlays, preventing recursive glass captures. */
@Composable
fun BackdropGlass(
    layer: GraphicsLayer,
    sourceOrigin: Offset,
    modifier: Modifier = Modifier,
    tint: Color? = null
) {
    val context = LocalContext.current
    val appearancePrefs = remember { AppearancePreferences.get(context) }
    val appearanceConfig by appearancePrefs.config.collectAsState()

    val effectiveTint = tint ?: when (appearanceConfig.themeMode) {
        ThemeMode.OLED_BLACK -> Color(0xF4000000)
        else -> Color(0xEE111319)
    }

    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(modifier.clipToBounds().onGloballyPositioned { offset = it.positionInRoot() - sourceOrigin }) {
        Box(Modifier.matchParentSize()
            .graphicsLayer {
                renderEffect = BlurEffect(28.dp.toPx(), 28.dp.toPx(), TileMode.Clamp)
            }
            .drawWithContent {
                translate(-offset.x, -offset.y) { drawLayer(layer) }
            })
        Box(Modifier.matchParentSize().background(effectiveTint))
    }
}
