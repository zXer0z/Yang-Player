package com.yang.player.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yang.player.model.CategoryFilter
import com.yang.player.ui.theme.AccentBlue
import com.yang.player.ui.theme.HiResCyan
import com.yang.player.ui.theme.SurfaceElevated
import com.yang.player.ui.theme.TextMuted
import com.yang.player.ui.theme.TextWhite

@Composable
fun CategoryChipsRow(
    selectedCategory: CategoryFilter,
    onCategorySelected: (CategoryFilter) -> Unit
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryFilter.entries.forEach { filter ->
            val isSelected = filter == selectedCategory

            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.07f),
                animationSpec = tween(220),
                label = "chip_bg"
            )

            val textColor by animateColorAsState(
                targetValue = if (isSelected) Color(0xFF101216) else Color.White.copy(alpha = 0.65f),
                animationSpec = tween(220),
                label = "chip_text"
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(backgroundColor)
                    .clickable { onCategorySelected(filter) }
                    .padding(horizontal = 18.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                if (filter == CategoryFilter.HI_RES && !isSelected) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "+",
                            color = HiResCyan,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Hi-Res",
                            color = textColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Text(
                        text = filter.label,
                        color = textColor,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

