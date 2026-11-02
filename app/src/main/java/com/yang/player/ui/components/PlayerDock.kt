package com.yang.player.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.yang.player.model.Song
import com.yang.player.ui.theme.LocalArtworkAccent
import com.yang.player.ui.theme.MiniPlayerBorder
import com.yang.player.ui.theme.MiniPlayerGlass

@Composable
fun PlayerDock(
    compact: Boolean,
    selectedTab: NavigationTab,
    currentSong: Song?,
    isPlaying: Boolean,
    backdropLayer: GraphicsLayer?,
    backdropOrigin: Offset,
    onTabSelected: (NavigationTab) -> Unit,
    onHome: () -> Unit,
    onSearch: () -> Unit,
    onOpenPlayer: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bottomSpacing by animateDpAsState(
        targetValue = if (compact) 18.dp else 0.dp,
        animationSpec = tween(550),
        label = "compactDockBottomSpacing"
    )
    // One persistent mini-player morphs in place; only the surrounding controls
    // enter and exit, preserving playback/artwork state throughout the transition.
    Column(modifier.fillMaxWidth().padding(bottom = bottomSpacing)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedVisibility(
                    visible = compact,
                    enter = fadeIn(tween(400, delayMillis = 100)) + expandHorizontally(tween(550)),
                    exit = fadeOut(tween(220)) + shrinkHorizontally(tween(550))
                ) {
                Box(Modifier.padding(end = 8.dp)) {
                DockButton(Icons.Outlined.Home, "Mostrar menú completo", onHome, backdropLayer, backdropOrigin)
                }
                }
                FloatingMiniPlayer(
                    currentSong, isPlaying, onOpenPlayer, onPlayPause, onNext,
                    backdropLayer, backdropOrigin, compact = compact,
                    modifier = Modifier.weight(1f), outerPadding = false
                )
                AnimatedVisibility(
                    visible = compact,
                    enter = fadeIn(tween(400, delayMillis = 100)) + expandHorizontally(tween(550)),
                    exit = fadeOut(tween(220)) + shrinkHorizontally(tween(550))
                ) {
                Box(Modifier.padding(start = 8.dp)) {
                DockButton(Icons.Default.Search, "Buscar canciones", onSearch, backdropLayer, backdropOrigin)
                }
                }
            }
            AnimatedVisibility(
                visible = !compact,
                enter = fadeIn(tween(400, delayMillis = 100)) + expandVertically(tween(550), expandFrom = Alignment.Bottom),
                exit = fadeOut(tween(220)) + shrinkVertically(tween(550), shrinkTowards = Alignment.Bottom)
            ) {
                BottomNavBar(
                    selectedTab = selectedTab,
                    onTabSelected = onTabSelected,
                    backdropLayer = backdropLayer,
                    backdropOrigin = backdropOrigin,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 8.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .border(1.dp, MiniPlayerBorder, RoundedCornerShape(28.dp))
                )
            }
    }
}

@Composable
private fun DockButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    backdropLayer: GraphicsLayer?,
    backdropOrigin: Offset
) {
    Box(Modifier.size(48.dp).clip(CircleShape)
        .background(if (backdropLayer == null) MiniPlayerGlass else Color.Transparent)
        .border(1.dp, MiniPlayerBorder, CircleShape)) {
        if (backdropLayer != null) BackdropGlass(backdropLayer, backdropOrigin, Modifier.matchParentSize())
        IconButton(onClick = onClick, modifier = Modifier.matchParentSize()) {
            Icon(icon, description, tint = LocalArtworkAccent.current, modifier = Modifier.size(24.dp))
        }
    }
}
