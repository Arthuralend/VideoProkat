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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
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

@Composable
fun ErrorPane(
    message: String,
    onRetry: () -> Unit,
    onDiagnostics: (() -> Unit)? = null,
    onChangePortal: (() -> Unit)? = null,
) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    Column(
        Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Не удалось загрузить", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(message, color = TextDim, fontSize = 16.sp, textAlign = TextAlign.Center, maxLines = 6)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            PosterContent(node)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            node.title,
            color = if (focused) Color.White else TextDim,
            fontSize = 15.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
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

/** Сетка постеров с автоподгрузкой следующих страниц. */
@Composable
fun PagedGrid(
    client: PortalClient,
    page: Page,
    onOpen: (Node) -> Unit,
    title: String? = null,
    autoFocus: Boolean = false,
    emptyText: String = "Здесь пока пусто",
) {
    val nodes = remember(page) { mutableStateListOf<Node>().apply { addAll(page.nodes) } }
    var next by remember(page) { mutableStateOf(page.nextRequest) }
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

    Column(Modifier.fillMaxSize()) {
        if (title != null) {
            Text(
                title,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 40.dp, top = 28.dp, end = 40.dp),
            )
        }
        if (nodes.isEmpty()) {
            MessagePane(emptyText)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 24.dp, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalArrangement = Arrangement.spacedBy(26.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(nodes, key = { i, n -> "$i|${n.key}" }) { i, node ->
                    if (i >= nodes.size - 10) LaunchedEffect(nodes.size) { loadMore() }
                    val mod = if (i == 0 && autoFocus) Modifier.focusRequester(firstFocus) else Modifier
                    PosterCard(node, onClick = { onOpen(node) }, modifier = mod.width(150.dp))
                    if (i == 0 && autoFocus) LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
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
        is Load.Ok -> PagedGrid(client, s.value, onOpen, title = title ?: s.value.title, autoFocus = autoFocus)
    }
}
