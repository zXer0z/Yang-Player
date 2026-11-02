package com.yang.player.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yang.player.data.MusicWiki
import com.yang.player.ui.theme.LocalArtworkAccent

@Composable
fun WikipediaCard(title: String, artist: String, isArtist: Boolean) {
    val context = LocalContext.current
    val accent = LocalArtworkAccent.current
    var entry by remember(title, artist, isArtist) { mutableStateOf<MusicWiki.Entry?>(null) }
    var loading by remember(title, artist, isArtist) { mutableStateOf(true) }
    var expanded by remember(title, artist, isArtist) { mutableStateOf(false) }
    DisposableEffect(title, artist, isArtist) {
        var active = true
        val task = MusicWiki.load(context, if (isArtist) null else title, if (isArtist) title else artist) {
            if (active) { entry = it; loading = false }
        }
        if (task == null) loading = false
        onDispose { active = false; task?.cancel(true) }
    }
    val article = entry
    if (!loading && article == null) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp)
        .clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = .045f)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (isArtist) "Sobre el artista" else "Sobre el álbum", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        if (article == null) Text("Buscando información en Wikipedia…", color = Color.White.copy(alpha = .45f), fontSize = 12.sp)
        else {
            Text(if (expanded) article.full else article.text, color = Color.White.copy(alpha = .65f), fontSize = 14.sp, lineHeight = 22.sp)
            if (article.full != article.text) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Ver menos" else "Ver más", color = accent) }
            TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(article.url))) } }) {
                Text("Wikipedia · ${article.language.uppercase()} · Leer artículo ↗", color = accent, fontSize = 11.sp)
            }
            TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://creativecommons.org/licenses/by-sa/4.0/"))) } }) {
                Text("Extracto abreviado · CC BY-SA 4.0 · autores en el artículo", color = Color.White.copy(alpha = .35f), fontSize = 10.sp)
            }
        }
    }
}
