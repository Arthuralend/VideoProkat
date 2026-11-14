package am.arthur.vportal.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import am.arthur.vportal.data.Load
import am.arthur.vportal.data.Node
import am.arthur.vportal.data.Page
import am.arthur.vportal.data.PortalClient
import am.arthur.vportal.data.load
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/** Кнопка, заметная при фокусе с пульта. */
@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "btn")
    val bg = when {
        focused -> Accent
        primary -> Accent.copy(alpha = 0.25f)
        else -> CardBg
    }
    Row(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = touchIndication()) { onClick() }
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val color = if (focused) Color.Black else Color.White
        if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Text(text, color = color, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Loader(modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Accent)
    }
}

@Composable
fun MessagePane(text: String, modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = TextDim, fontSize = 18.sp, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ErrorPane(
    message: String,
    onRetry: () -> Unit,
    onDiagnostics: (() -> Unit)? = null,
    onChangePortal: (() -> Unit)? = null,
) {
    val fr = remember { FocusRequester() }
    val tv = LocalIsTv.current
    LaunchedEffect(Unit) { if (tv) runCatching { fr.requestFocus() } }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Не удалось загрузить", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(message, color = TextDim, fontSize = 16.sp, textAlign = TextAlign.Center, maxLines = 6)
        Spacer(Modifier.height(24.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TvButton("Повторить", onRetry, Modifier.focusRequester(fr), primary = true)
            if (onDiagnostics != null) TvButton("Диагностика", onDiagnostics)
            if (onChangePortal != null) TvButton("Сменить портал", onChangePortal)
        }
    }
}

/** Карточка-постер. */
@Composable
fun PosterCard(node: Node, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "card")
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(RoundedCornerShape(12.dp))
                .background(CardBg)
                .then(if (focused) Modifier.border(3.dp, Accent, RoundedCornerShape(12.dp)) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = touchIndication()) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            PosterContent(node)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            node.title,
            color = if (focused || !LocalIsTv.current) Color(0xFFE6E8EC) else TextDim,
            fontSize = if (LocalIsTv.current) 14.sp else 13.sp,
            lineHeight = 18.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun PosterContent(node: Node) {
    if (node.poster != null) {
        AsyncImage(
            model = node.poster,
            contentDescription = node.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                when {
                    node.isPlayable -> Icons.Filled.PlayArrow
                    node.isSeries -> Icons.Filled.List
                    else -> Icons.Filled.Star
                },
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(node.title, fontSize = 16.sp, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Подгруженные страницы и место прокрутки списков: после возврата из карточки фильма список не начинается заново. */
private class SavedGrid(val nodes: List<Node>, val next: String?, val index: Int, val offset: Int)

private object GridMemory {
    private val map = object : LinkedHashMap<String, SavedGrid>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SavedGrid>?) = size > 24
    }
    fun get(key: String): SavedGrid? = synchronized(map) { map[key] }
    fun put(key: String, value: SavedGrid) = synchronized(map) { map[key] = value }
}

/** Сетка постеров с автоподгрузкой следующих страниц. */
@Composable
fun PagedGrid(
    client: PortalClient,
    page: Page,
    onOpen: (Node) -> Unit,
    title: String? = null,
    autoFocus: Boolean = false,
    emptyText: String = "Здесь пока пусто",
    memoryKey: String? = null,
) {
    val saved = remember(page, memoryKey) { memoryKey?.let { GridMemory.get(it) } }
    val nodes = remember(page) { mutableStateListOf<Node>().apply { addAll(saved?.nodes ?: page.nodes) } }
    var next by remember(page) { mutableStateOf(if (saved != null) saved.next else page.nextRequest) }
    val gridState = rememberLazyGridState(saved?.index ?: 0, saved?.offset ?: 0)
    val focusIndex = saved?.index ?: 0
    DisposableEffect(page, memoryKey) {
        onDispose {
            if (memoryKey != null && nodes.isNotEmpty()) {
                GridMemory.put(
                    memoryKey,
                    SavedGrid(nodes.toList(), next, gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset),
                )
            }
        }
    }
    var loadingMore by remember(page) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }

    fun loadMore() {
        val req = next ?: return
        if (loadingMore) return
        loadingMore = true
        scope.launch {
            when (val r = load { client.page(req) }) {
                is Load.Ok -> {
                    val known = nodes.map { it.key }.toHashSet()
                    val fresh = r.value.nodes.filter { it.key !in known }
                    nodes.addAll(fresh)
                    next = r.value.nextRequest?.takeIf { it != req && fresh.isNotEmpty() }
                }
                else -> next = null
            }
            loadingMore = false
        }
    }

    val tv = LocalIsTv.current
    val wide = isWideLayout()
    Column(Modifier.fillMaxSize()) {
        if (!title.isNullOrBlank()) {
            Text(
                title,
                fontSize = if (tv) 24.sp else if (wide) 24.sp else 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(
                    start = if (wide) 36.dp else 16.dp,
                    top = if (wide) 20.dp else 12.dp,
                    end = 16.dp,
                ),
            )
        }
        if (nodes.isEmpty()) {
            MessagePane(emptyText)
        } else BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val hPad = if (wide) 36.dp else 12.dp
            val vPad = if (tv) 14.dp else 12.dp
            val hGap = if (tv) 20.dp else if (wide) 12.dp else 10.dp
            val vGap = if (tv) 18.dp else 14.dp
            val columns: GridCells
            val cardWidth: Dp?
            if (tv) {
                // Ровно 4 фильма в ряд и 2 ряда на экране: размер постера считается от высоты области.
                val labelH = 46.dp
                val rowH = (maxHeight - vPad * 2 - vGap) / 2
                val byHeight = (rowH - labelH) / 1.5f
                val byWidth = (maxWidth - hPad * 2 - hGap * 3) / 4
                cardWidth = minOf(byHeight, byWidth)
                columns = GridCells.Fixed(4)
            } else {
                cardWidth = null
                // Телефон вертикально: 3 постера в ряд (4 — на широких телефонах), крупно и ровно под палец.
                columns = if (wide) GridCells.Adaptive(132.dp)
                else GridCells.Fixed(if (maxWidth >= 480.dp) 4 else 3)
            }
            LazyVerticalGrid(
                columns = columns,
                contentPadding = PaddingValues(start = hPad, end = hPad, top = vPad, bottom = vPad + if (tv) 0.dp else 24.dp),
                horizontalArrangement = Arrangement.spacedBy(hGap),
                verticalArrangement = Arrangement.spacedBy(vGap),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(nodes, key = { i, n -> "$i|${n.key}" }) { i, node ->
                    if (i >= nodes.size - 10) LaunchedEffect(nodes.size) { loadMore() }
                    val focusMod = if (i == focusIndex && autoFocus && tv) Modifier.focusRequester(firstFocus) else Modifier
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        PosterCard(
                            node,
                            onClick = { onOpen(node) },
                            modifier = focusMod.then(if (cardWidth != null) Modifier.width(cardWidth) else Modifier.fillMaxWidth()),
                        )
                    }
                    if (i == focusIndex && autoFocus && tv) LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
                }
            }
        }
    }
}

/** Загружает список по запросу портала (или берёт готовый) и показывает сеткой. */
@Composable
fun NodeGrid(
    client: PortalClient,
    request: String?,
    inline: Page?,
    onOpen: (Node) -> Unit,
    title: String? = null,
    autoFocus: Boolean = false,
) {
    var attempt by remember { mutableStateOf(0) }
    var state by remember(request, inline) {
        mutableStateOf<Load<Page>>(
            inline?.let { Load.Ok(it) } ?: request?.let { client.cached(it) }?.let { Load.Ok(it) } ?: Load.Loading
        )
    }
    LaunchedEffect(request, inline, attempt) {
        if (state !is Load.Ok) {
            state = if (request != null) load { client.page(request) } else Load.Err("Пустой раздел")
        }
    }
    when (val s = state) {
        Load.Loading -> Loader()
        is Load.Err -> ErrorPane(s.message, onRetry = { state = Load.Loading; attempt++ })
        is Load.Ok -> PagedGrid(
            client, s.value, onOpen, title = title ?: s.value.title, autoFocus = autoFocus,
            memoryKey = request?.let { "req:$it" },
        )
    }
}
