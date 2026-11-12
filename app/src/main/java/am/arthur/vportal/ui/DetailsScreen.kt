package am.arthur.vportal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import am.arthur.vportal.data.Node
import am.arthur.vportal.data.Storage
import coil.compose.AsyncImage

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, onSearch: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        placeholder = { Text("Название") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }, onDone = { onSearch() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent,
            cursorColor = Accent,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
        ),
        modifier = modifier.width(560.dp),
    )
}

fun formatTime(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

@Composable
fun DetailsScreen(storage: Storage, node: Node, onPlay: (Node) -> Unit) {
    val url = node.streamUrl
    val position = remember(node) { url?.let { storage.position(it) } ?: 0L }
    var favorite by remember(node) { mutableStateOf(storage.isFavorite(node)) }
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }

    Box(Modifier.fillMaxSize()) {
        if (node.poster != null) {
            AsyncImage(
                model = node.poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.22f),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Bg, Bg.copy(alpha = 0.85f), Bg.copy(alpha = 0.4f)))
            )
        )
        Row(
            Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(44.dp),
        ) {
            Box(
                Modifier.width(270.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(16.dp)).background(Card)
            ) { PosterContent(node) }

            Column(Modifier.weight(1f)) {
                Text(
                    node.title, fontSize = 38.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (node.meta != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(node.meta, color = Accent, fontSize = 17.sp, maxLines = 2)
                }
                Spacer(Modifier.height(28.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TvButton(
                        if (position > 0) "Продолжить с ${formatTime(position)}" else "Смотреть",
                        onClick = { onPlay(node) },
                        icon = Icons.Filled.PlayArrow,
                        primary = true,
                        modifier = Modifier.focusRequester(playFocus),
                    )
                    if (position > 0 && url != null) {
                        TvButton("С начала", onClick = { storage.resetPosition(url); onPlay(node) }, icon = Icons.Filled.Refresh)
                    }
                    TvButton(
                        if (favorite) "В избранном" else "В избранное",
                        onClick = { favorite = storage.toggleFavorite(node) },
                        icon = if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    )
                }
                if (node.description != null) {
                    Spacer(Modifier.height(28.dp))
                    Text(
                        node.description, color = Color(0xFFD5D8DE), fontSize = 18.sp, lineHeight = 27.sp,
                        maxLines = 11, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
