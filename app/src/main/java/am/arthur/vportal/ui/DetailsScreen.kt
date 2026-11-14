package am.arthur.vportal.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed as listItemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SearchField(
    value: String,
    onChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = if (onClear != null && value.isNotEmpty()) {
            {
                Icon(
                    Icons.Filled.Close, contentDescription = "Очистить",
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onClear() }.padding(8.dp),
                )
            }
        } else null,
        placeholder = { Text("Название") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = { keyboard?.hide(); onSearch() },
            onDone = { keyboard?.hide(); onSearch() },
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent,
            cursorColor = Accent,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
        ),
        modifier = modifier.fillMaxWidth().widthIn(max = 560.dp),
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsScreen(
    client: PortalClient,
    storage: Storage,
    node: Node,
    onPlay: (Node, String?) -> Unit,
    onOpen: (Node) -> Unit,
    onBack: () -> Unit = {},
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

    val tv = LocalIsTv.current
    val wide = isWideLayout()
    LaunchedEffect(state) { if (tv) runCatching { firstFocus.requestFocus() } }

    if (!tv && !wide) {
        PhoneDetails(
            storage, node, shown, state, card?.url, card?.variants.orEmpty(), episodes, canPlay, position,
            favorite, onFavorite = { favorite = storage.toggleFavorite(node) },
            onRetry = { attempt++ }, onPlay = onPlay, onOpen = onOpen, onBack = onBack,
        )
        return
    }

    val actions: @Composable () -> Unit = {
        Spacer(Modifier.height(if (wide) 26.dp else 16.dp))

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
            is Load.Ok -> FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
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
                shown.description, color = Color(0xFFD5D8DE),
                fontSize = if (wide) 18.sp else 15.sp, lineHeight = if (wide) 27.sp else 22.sp,
                maxLines = if (wide) 9 else 30, overflow = TextOverflow.Ellipsis,
            )
        }
        if (card != null && !canPlay && episodes.isEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Портал не вернул видео для этого элемента", color = TextDim, fontSize = 16.sp)
        }
    }

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
            columns = GridCells.Adaptive(if (wide) 250.dp else 160.dp),
            contentPadding = if (wide) PaddingValues(horizontal = 56.dp, vertical = 44.dp)
            else PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(if (wide) 44.dp else 16.dp)) {
                    Box(
                        Modifier.width(if (wide) 250.dp else 120.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(16.dp)).background(CardBg)
                    ) { PosterContent(shown) }

                    Column(Modifier.weight(1f)) {
                        Text(
                            shown.title, fontSize = if (wide) 36.sp else 22.sp, fontWeight = FontWeight.Bold,
                            maxLines = if (wide) 2 else 4, overflow = TextOverflow.Ellipsis,
                        )
                        if (shown.meta != null) {
                            Spacer(Modifier.height(10.dp))
                            Text(shown.meta, color = Accent, fontSize = if (wide) 17.sp else 14.sp, maxLines = 3)
                        }
                        if (wide) actions()
                    }
                }
            }
            if (!wide) {
                item(span = { GridItemSpan(maxLineSpan) }) { Column { actions() } }
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
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = touchIndication()) { onClick() }
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

/** Карточка фильма на телефоне: фон-постер, крупная кнопка «Смотреть» во всю ширину, серии списком. */
@Composable
private fun PhoneDetails(
    storage: Storage,
    node: Node,
    shown: Node,
    state: Load<Card>,
    url: String?,
    variants: List<Pair<String, String>>,
    episodes: List<Node>,
    canPlay: Boolean,
    position: Long,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onRetry: () -> Unit,
    onPlay: (Node, String?) -> Unit,
    onOpen: (Node) -> Unit,
    onBack: () -> Unit,
) {
    var expanded by remember(node) { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Box(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(250.dp)) {
                    if (shown.poster != null) {
                        AsyncImage(
                            model = shown.poster, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().alpha(0.45f),
                        )
                    }
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(listOf(Bg.copy(alpha = 0.35f), Bg.copy(alpha = 0.75f), Bg))
                        )
                    )
                }
                Column(Modifier.fillMaxWidth()) {
                    RoundIcon(Icons.Filled.ArrowBack, "Назад", onBack, Modifier.padding(start = 10.dp, top = 10.dp))
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 40.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Box(
                            Modifier.width(118.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(CardBg)
                        ) { PosterContent(shown) }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                shown.title, fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold,
                                maxLines = 4, overflow = TextOverflow.Ellipsis,
                            )
                            if (shown.meta != null) {
                                Spacer(Modifier.height(6.dp))
                                Text(shown.meta, color = Accent, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 3)
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp)) {
                when (state) {
                    Load.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = Accent, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(14.dp))
                        Text("Загрузка…", color = TextDim, fontSize = 16.sp)
                    }
                    is Load.Err -> {
                        Text(state.message, color = ErrorRed, fontSize = 15.sp, maxLines = 4)
                        Spacer(Modifier.height(12.dp))
                        BigButton("Повторить", Icons.Filled.Refresh, primary = true, onClick = onRetry)
                    }
                    is Load.Ok -> {
                        if (canPlay) {
                            BigButton(
                                if (position > 0) "Продолжить с ${formatTime(position)}" else "Смотреть",
                                Icons.Filled.PlayArrow, primary = true,
                                onClick = { onPlay(shown, url) },
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (canPlay && position > 0) {
                                BigButton("С начала", Icons.Filled.Refresh, Modifier.weight(1f)) {
                                    storage.resetPosition(shown.key); onPlay(shown, url)
                                }
                            }
                            BigButton(
                                if (favorite) "В избранном" else "В избранное",
                                if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                Modifier.weight(1f), onClick = onFavorite,
                            )
                        }
                        if (canPlay && variants.size > 1) {
                            Spacer(Modifier.height(16.dp))
                            Text("Качество", color = TextDim, fontSize = 14.sp)
                            Spacer(Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(variants) { (q, u) ->
                                    Text(
                                        q, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(CardBg)
                                            .clickable { onPlay(shown, u) }
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                if (shown.description != null) {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        shown.description, color = Color(0xFFD5D8DE), fontSize = 15.sp, lineHeight = 22.sp,
                        maxLines = if (expanded) Int.MAX_VALUE else 5, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() }, indication = null,
                        ) { expanded = !expanded },
                    )
                    if (!expanded && shown.description.length > 220) {
                        Text(
                            "Показать полностью", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 6.dp).clickable { expanded = true },
                        )
                    }
                }
                if (state is Load.Ok && !canPlay && episodes.isEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("Портал не вернул видео для этого элемента", color = TextDim, fontSize = 15.sp)
                }
            }
        }

        if (episodes.isNotEmpty()) {
            item {
                Text(
                    if (episodes.any { it.isFolder }) "Сезоны" else "Серии",
                    fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 10.dp),
                )
            }
            listItemsIndexed(episodes, key = { i, e -> "$i|${e.key}" }) { i, ep ->
                val epNode = ep.copy(
                    title = if (ep.isPlayable) "${shown.title}. ${ep.title}" else ep.title,
                    poster = ep.poster ?: shown.poster,
                )
                EpisodeCard(
                    title = ep.title.ifBlank { "Серия ${i + 1}" },
                    folder = ep.isFolder || ep.isSeries,
                    progress = if (ep.isPlayable) storage.position(epNode.key) else 0L,
                    onClick = { if (ep.isPlayable) onPlay(epNode, ep.streamUrl) else onOpen(epNode) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                )
            }
        }
    }
}

/** Кнопка под палец: высота 52 dp, на всю доступную ширину. */
@Composable
fun BigButton(
    text: String,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (primary) Accent else CardBg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = touchIndication()) { onClick() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val fg = if (primary) Color.Black else Color.White
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Круглая кнопка-значок на полупрозрачной подложке (назад, поворот экрана). */
@Composable
fun RoundIcon(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(44.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0x66000000))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

/** Верхняя панель с кнопкой «Назад» для экранов телефона. */
@Composable
fun PhoneTopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Panel).height(56.dp).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(50)).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
        }
        Spacer(Modifier.width(6.dp))
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
