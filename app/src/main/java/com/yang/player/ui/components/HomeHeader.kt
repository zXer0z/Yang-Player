package com.yang.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yang.player.ui.theme.AccentBlue
import com.yang.player.ui.theme.SurfaceCard
import com.yang.player.ui.theme.TextMuted

@Composable
fun HomeHeader(
    userName: String = "Usuario",
    greeting: String = "Buenas tardes,",
    profileImageUrl: String? = null,
    onProfileClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = greeting,
                color = TextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = userName,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
        }

        // Circular profile photo (Loaded via Coil - prepared for YouTube avatar)
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(SurfaceCard)
                .border(1.5.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                .clickable { onProfileClick() },
            contentAlignment = Alignment.Center
        ) {
            if (!profileImageUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = profileImageUrl,
                    contentDescription = "Avatar de YouTube",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.Person, "Usuario", tint = Color.White.copy(alpha = 0.65f), modifier = Modifier.size(24.dp))
            }
        }
    }
}

