package com.yang.player.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yang.player.data.MusicWiki
import com.yang.player.ui.theme.LocalArtworkAccent
import java.util.Locale

@Composable
fun WikipediaCard(title: String, artist: String, isArtist: Boolean) {
    val context = LocalContext.current
    val accent = LocalArtworkAccent.current
    var entry by remember(title, artist, isArtist) { mutableStateOf<MusicWiki.Entry?>(null) }
    var loading by remember(title, artist, isArtist) { mutableStateOf(true) }
    var expanded by remember(title, artist, isArtist) { mutableStateOf(false) }
    var truncated by remember(title, artist, isArtist) { mutableStateOf(false) }
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
    fun openLink(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (article == null) {
            Text("Buscando información en Wikipedia…", color = Color.White.copy(alpha = .45f), fontSize = 12.sp)
        } else {
            Text(
                text = article.full,
                modifier = Modifier.fillMaxWidth(),
                color = Color.White.copy(alpha = .65f),
                textAlign = TextAlign.Center,
                fontSize = 13.sp,
                lineHeight = 21.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!expanded) truncated = it.hasVisualOverflow }
            )
            if (truncated || expanded) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Leer menos" else "Leer más", color = accent, fontSize = 12.sp)
                }
            }
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { openLink(article.url) },
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    modifier = Modifier.semantics { contentDescription = "Leer artículo y autores en Wikipedia" }
                ) {
                    Text("Wikipedia · ${article.language.uppercase(Locale.ROOT)} · extracto ↗", color = accent, fontSize = 10.5.sp)
                }
                TextButton(
                    onClick = { openLink("https://creativecommons.org/licenses/by-sa/4.0/") },
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    modifier = Modifier.semantics { contentDescription = "Licencia del extracto abreviado: Creative Commons Atribución Compartir Igual 4.0" }
                ) {
                    Text("CC BY-SA 4.0", color = Color.White.copy(alpha = .4f), fontSize = 10.sp)
                }
            }
        }
    }
}