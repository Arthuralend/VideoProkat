package am.arthur.vportal.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import am.arthur.vportal.data.Card
import am.arthur.vportal.data.Load
import am.arthur.vportal.data.Node
import am.arthur.vportal.data.PortalClient
import am.arthur.vportal.data.Storage
import am.arthur.vportal.data.load
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

/**
 * Карточка фильма или сериала.
 * onPlay(элемент, ссылка) — ссылка может быть null, тогда плеер сам запросит её у портала.
 */
@Composable
fun DetailsScreen(
    client: PortalClient,
    storage: Storage,
    node: Node,
    onPlay: (Node, String?) -> Unit,
    onOpen: (Node) -> Unit,
) {
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember(node) { mutableStateOf<Load<Card>>(Load.Loading) }
    LaunchedEffect(node, attempt) {
        state = Load.Loading
        state = load { client.card(node) }
    }
    val card = (state as? Load.Ok)?.value
    val shown = card?.node ?: node
    val position = remember(node, state) { storage.position(shown.key) }
    var favorite by remember(node) { mutableStateOf(storage.isFavorite(node)) }
    val firstFocus = remember { FocusRequester() }

    val canPlay = card?.url != null || (card == null && node.isPlayable)
    val episodes = card?.items.orEmpty()

    LaunchedEffect(state) { runCatching { firstFocus.requestFocus() } }

    Box(Modifier.fillMaxSize()) {
        if (shown.poster != null) {
            AsyncImage(
                model = shown.poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.22f),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Bg, Bg.copy(alpha = 0.88f), Bg.copy(alpha = 0.45f)))
            )
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(250.dp),
            contentPadding = PaddingValues(horizontal = 56.dp, vertical = 44.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(44.dp)) {
                    Box(
                        Modifier.width(250.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(16.dp)).background(CardBg)
                    ) { PosterContent(shown) }

                    Column(Modifier.weight(1f)) {
                        Text(
                            shown.title, fontSize = 36.sp, fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        if (shown.meta != null) {
                            Spacer(Modifier.height(10.dp))
                            Text(shown.meta, color = Accent, fontSize = 17.sp, maxLines = 2)
                        }
                        Spacer(Modifier.height(26.dp))

                        when (val s = state) {
                            Load.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(color = Accent, modifier = Modifier.size(28.dp))
                                Spacer(Modifier.width(14.dp))
                                Text("Загрузка…", color = TextDim, fontSize = 17.sp)
                            }
                            is Load.Err -> Column {
                                Text(s.message, color = ErrorRed, fontSize = 16.sp, maxLines = 4)
                                Spacer(Modifier.height(12.dp))
                                TvButton(
                                    "Повторить", { attempt++ }, icon = Icons.Filled.Refresh, primary = true,
                                    modifier = Modifier.focusRequester(firstFocus),
                                )
                            }
                            is Load.Ok -> Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                if (canPlay) {
                                    TvButton(
                                        if (position > 0) "Продолжить с ${formatTime(position)}" else "Смотреть",
                                        onClick = { onPlay(shown, card?.url) },
                                        icon = Icons.Filled.PlayArrow,
                                        primary = true,
                                        modifier = Modifier.focusRequester(firstFocus),
                                    )
                                    if (position > 0) {
                                        TvButton("С начала", onClick = {
                                            storage.resetPosition(shown.key); onPlay(shown, card?.url)
                                        }, icon = Icons.Filled.Refresh)
                                    }
                                    card?.variants?.take(4)?.forEach { (q, u) ->
                                        TvButton(q, onClick = { onPlay(shown, u) })
                                    }
                                }
                                TvButton(
                                    if (favorite) "В избранном" else "В избранное",
                                    onClick = { favorite = storage.toggleFavorite(node) },
                                    icon = if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    modifier = if (canPlay) Modifier else Modifier.focusRequester(firstFocus),
                                )
                            }
                        }

                        if (shown.description != null) {
                            Spacer(Modifier.height(26.dp))
                            Text(
                                shown.description, color = Color(0xFFD5D8DE), fontSize = 18.sp, lineHeight = 27.sp,
                                maxLines = 9, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (card != null && !canPlay && episodes.isEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            Text("Портал не вернул видео для этого элемента", color = TextDim, fontSize = 16.sp)
                        }
                    }
                }
            }

            if (episodes.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        if (episodes.any { it.isFolder }) "Сезоны" else "Серии",
                        fontSize = 24.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 18.dp),
                    )
                }
                itemsIndexed(episodes, key = { i, e -> "$i|${e.key}" }) { i, ep ->
                    val epNode = ep.copy(
                        title = if (ep.isPlayable) "${shown.title}. ${ep.title}" else ep.title,
                        poster = ep.poster ?: shown.poster,
                    )
                    EpisodeCard(
                        title = ep.title.ifBlank { "Серия ${i + 1}" },
                        folder = ep.isFolder || ep.isSeries,
                        progress = if (ep.isPlayable) storage.position(epNode.key) else 0L,
                        onClick = { if (ep.isPlayable) onPlay(epNode, ep.streamUrl) else onOpen(epNode) },
                        modifier = if (i == 0 && !canPlay) Modifier.focusRequester(firstFocus) else Modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeCard(title: String, folder: Boolean, progress: Long, onClick: () -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, label = "ep")
    Row(
        modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) Accent else CardBg)
            .then(if (focused) Modifier.border(2.dp, Accent, RoundedCornerShape(12.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (focused) Color.Black else Color.White
        Icon(
            if (folder) Icons.Filled.List else Icons.Filled.PlayArrow,
            contentDescription = null, tint = if (focused) Color.Black else Accent,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = fg, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (progress > 0) {
                Text("остановились на ${formatTime(progress)}", color = if (focused) Color.Black else TextDim, fontSize = 13.sp)
            }
        }
    }
}
