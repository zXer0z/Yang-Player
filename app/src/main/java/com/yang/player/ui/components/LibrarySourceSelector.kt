package com.yang.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yang.player.model.LibrarySource
import com.yang.player.ui.theme.LocalArtworkAccent
import com.yang.player.ui.theme.TextWhite

@Composable
fun LibrarySourceSelector(selected: LibrarySource, onSelect: (LibrarySource) -> Unit) {
    val accent = LocalArtworkAccent.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (source in LibrarySource.entries) {
            val active = source == selected
            val shape = RoundedCornerShape(20.dp)
            Box(
                Modifier.clip(shape)
                    .background(if (active) accent.copy(alpha = .18f) else Color.White.copy(alpha = .055f))
                    .selectable(selected = active, role = Role.Tab, onClick = { onSelect(source) })
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    source.label,
                    color = if (active) accent else TextWhite.copy(alpha = .73f),
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}
