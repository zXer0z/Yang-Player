package com.yang.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yang.player.ui.theme.LocalArtworkAccent
import com.yang.player.ui.theme.MiniPlayerBorder
import com.yang.player.ui.theme.MiniPlayerGlass
import com.yang.player.ui.theme.BackgroundDark
import com.yang.player.ui.theme.TextDim

enum class NavigationTab(val label: String, val icon: ImageVector) {
    INICIO("Inicio", Icons.Outlined.Home),
    CANCIONES("Canciones", Icons.Default.MusicNote),
    HI_FI("Hi-Fi", Icons.Default.GraphicEq),
    ONLINE("Online", Icons.Outlined.Language),
    TU("Tú", Icons.Outlined.Person)
}

@Composable
fun BottomNavBar(
    selectedTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit,
    modifier: Modifier = Modifier,
    backdropLayer: GraphicsLayer? = null,
    backdropOrigin: Offset = Offset.Zero
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(if (backdropLayer == null) MiniPlayerGlass else Color.Transparent)
    ) {
        if (backdropLayer != null) {
            BackdropGlass(backdropLayer, backdropOrigin, Modifier.matchParentSize())
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavigationTab.entries.forEach { tab ->
                val isSelected = tab == selectedTab
                val tint = if (isSelected) LocalArtworkAccent.current else Color.White.copy(alpha = 0.50f)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clickable { onTabSelected(tab) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = tint,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = tab.label,
                        color = tint,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.align(Alignment.TopCenter),
            color = MiniPlayerBorder,
            thickness = 1.dp
        )
    }
}
