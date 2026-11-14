package am.arthur.vportal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import am.arthur.vportal.BuildConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import am.arthur.vportal.data.Load
import am.arthur.vportal.data.Node
import am.arthur.vportal.data.Page
import am.arthur.vportal.data.PortalClient
import am.arthur.vportal.data.RootInfo
import am.arthur.vportal.data.Storage
import am.arthur.vportal.data.load
import kotlinx.coroutines.delay

private enum class Kind { SEARCH, ROOT, FOLDER, FILTER, CONTINUE, FAVORITES, PORTAL, DIAG }

private data class Section(
    val title: String,
    val icon: ImageVector,
    val kind: Kind,
    val node: Node? = null,
    val items: List<Node> = emptyList(),
)

private fun iconFor(title: String): ImageVector {
    val t = title.lowercase()
    return when {
        "мульт" in t || "дет" in t -> Icons.Filled.Face
        "сериал" in t -> Icons.Filled.List
        "фильм" in t || "кино" in t -> Icons.Filled.PlayArrow
        "шоу" in t || "тв" in t || "передач" in t -> Icons.Filled.DateRange
        "позн" in t || "док" in t || "науч" in t -> Icons.Filled.Info
        else -> Icons.Filled.Star
    }
}

@Composable
fun HomeScreen(
    client: PortalClient,
    storage: Storage,
    selected: Int,
    onSelect: (Int) -> Unit,
    onOpen: (Node) -> Unit,
    onChangePortal: () -> Unit,
    onDiagnostics: () -> Unit,
) {
    var attempt by remember { mutableIntStateOf(0) }
    var root by remember(client) {
        mutableStateOf<Load<RootInfo>>(client.rootCache?.let { Load.Ok(it) } ?: Load.Loading)
    }
    LaunchedEffect(client, attempt) {
        if (root !is Load.Ok) root = load { client.root() }
    }
    val info = (root as? Load.Ok)?.value

    val sections = remember(info) {
        buildList {
            if (info == null || info.search) add(Section("Поиск", Icons.Filled.Search, Kind.SEARCH))
            if (info != null) {
                if (info.sections.isNotEmpty()) {
                    info.sections.forEach { add(Section(it.title, iconFor(it.title), Kind.FOLDER, it)) }
                } else {
                    add(Section("Главная", Icons.Filled.Home, Kind.ROOT))
                }
                info.filters.forEach { (title, nodes) -> add(Section(title, Icons.Filled.List, Kind.FILTER, items = nodes)) }
            }
            add(Section("Продолжить", Icons.Filled.PlayArrow, Kind.CONTINUE))
            add(Section("Избранное", Icons.Filled.Favorite, Kind.FAVORITES))
            add(Section("Сменить код", Icons.Filled.Refresh, Kind.PORTAL))
            add(Section("Диагностика", Icons.Filled.Settings, Kind.DIAG))
        }
    }
    val defaultIndex = sections.indexOfFirst { it.kind == Kind.ROOT || it.kind == Kind.FOLDER }.takeIf { it >= 0 } ?: 0
    val sel = if (selected in sections.indices && sections[selected].kind !in setOf(Kind.PORTAL, Kind.DIAG)) selected else defaultIndex

    val onPick: (Int) -> Unit = { i ->
        when (sections[i].kind) {
            Kind.PORTAL -> onChangePortal()
            Kind.DIAG -> onDiagnostics()
            else -> onSelect(i)
        }
    }
    val content: @Composable () -> Unit = {
            when (val r = root) {
                Load.Loading -> Loader()
                is Load.Err -> ErrorPane(
                    r.message,
                    onRetry = { client.clearCache(); root = Load.Loading; attempt++ },
                    onDiagnostics = onDiagnostics,
                    onChangePortal = onChangePortal,
                )
                is Load.Ok -> {
                    val s = sections[sel]
                    key(sel, s.title) {
                        when (s.kind) {
                            Kind.SEARCH -> SearchPane(client, onOpen)
                            Kind.ROOT -> PagedGrid(client, r.value.page, onOpen, title = "Главная", memoryKey = "root")
                            Kind.FOLDER -> NodeGrid(client, client.listRequest(s.node!!), null, onOpen, title = s.title)
                            Kind.FILTER -> PagedGrid(client, Page(null, s.items), onOpen, title = s.title, memoryKey = "filter:${s.title}")
                            Kind.CONTINUE -> PagedGrid(
                                client, Page(null, remember { storage.continueNodes() }), onOpen,
                                title = "Продолжить просмотр",
                                emptyText = "Здесь появится то, что вы начали смотреть",
                            )
                            Kind.FAVORITES -> PagedGrid(
                                client, Page(null, remember { storage.favorites() }), onOpen,
                                title = "Избранное",
                                emptyText = "Добавляйте фильмы в избранное кнопкой на странице фильма",
                            )
                            else -> Unit
                        }
                    }
                }
            }
    }

    if (isWideLayout()) {
        Row(Modifier.fillMaxSize()) {
            Rail(sections, sel, onPick)
            Box(Modifier.weight(1f).fillMaxHeight()) { content() }
        }
    } else {
        PhoneHome(sections, sel, onSelect, defaultIndex, onChangePortal, onDiagnostics, content)
    }
}

private val catalogKinds = setOf(Kind.ROOT, Kind.FOLDER, Kind.FILTER)

/**
 * Телефон вертикально: сверху логотип и разделы каталога (прокручиваемые вкладки),
 * снизу — панель под большой палец: Каталог · Поиск · Продолжить · Избранное · Ещё.
 */
@Composable
private fun PhoneHome(
    sections: List<Section>,
    sel: Int,
    onSelect: (Int) -> Unit,
    defaultIndex: Int,
    onChangePortal: () -> Unit,
    onDiagnostics: () -> Unit,
    content: @Composable () -> Unit,
) {
    var more by remember { mutableStateOf(false) }
    var lastCatalog by remember { mutableIntStateOf(defaultIndex) }
    val selKind = sections[sel].kind
    LaunchedEffect(sel) { if (sections[sel].kind in catalogKinds) lastCatalog = sel }
    val catalog = sections.withIndex().filter { it.value.kind in catalogKinds }

    // «Назад» на телефоне сначала возвращает в каталог и только потом закрывает приложение.
    BackHandler(enabled = more || sel != defaultIndex) {
        if (more) more = false else onSelect(defaultIndex)
    }

    Column(Modifier.fillMaxSize()) {
        if (!more && selKind in catalogKinds) {
            Column(Modifier.fillMaxWidth().background(Panel).padding(top = 10.dp, bottom = 10.dp)) {
                AppTitle(iconSize = 30, fontSize = 20, modifier = Modifier.padding(start = 16.dp, bottom = 10.dp))
                if (catalog.size > 1) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(catalog, key = { _, e -> "${e.index}|${e.value.title}" }) { _, item ->
                            TabChip(item.value.title, item.value.icon, item.index == sel) { onSelect(item.index) }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (more) MorePane(onChangePortal, onDiagnostics) else content()
        }
        val searchIdx = sections.indexOfFirst { it.kind == Kind.SEARCH }
        val contIdx = sections.indexOfFirst { it.kind == Kind.CONTINUE }
        val favIdx = sections.indexOfFirst { it.kind == Kind.FAVORITES }
        Row(
            Modifier.fillMaxWidth().background(Panel).height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomTab("Каталог", Icons.Filled.Home, !more && selKind in catalogKinds) {
                more = false; onSelect(lastCatalog)
            }
            if (searchIdx >= 0) BottomTab("Поиск", Icons.Filled.Search, !more && sel == searchIdx) {
                more = false; onSelect(searchIdx)
            }
            BottomTab("Смотрю", Icons.Filled.PlayArrow, !more && sel == contIdx) {
                more = false; onSelect(contIdx)
            }
            BottomTab("Избранное", Icons.Filled.Favorite, !more && sel == favIdx) {
                more = false; onSelect(favIdx)
            }
            BottomTab("Ещё", Icons.Filled.MoreVert, more) { more = true }
        }
    }
}

@Composable
private fun RowScope.BottomTab(title: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val fg = if (selected) Accent else TextDim
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = touchIndication()) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(if (selected) Accent.copy(alpha = 0.18f) else Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Icon(icon, contentDescription = title, tint = fg, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(3.dp))
        Text(
            title, color = fg, fontSize = 12.sp, maxLines = 1,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun MorePane(onChangePortal: () -> Unit, onDiagnostics: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        AppTitle(iconSize = 44, fontSize = 24, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
        MoreItem("Сменить код подключения", Icons.Filled.Refresh, onChangePortal)
        Spacer(Modifier.height(10.dp))
        MoreItem("Диагностика связи", Icons.Filled.Settings, onDiagnostics)
        Spacer(Modifier.weight(1f))
        Text("Версия ${BuildConfig.VERSION_NAME}", color = TextDim, fontSize = 13.sp)
    }
}

@Composable
private fun MoreItem(title: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = touchIndication()) { onClick() }
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TabChip(title: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Accent else CardBg)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (selected) Color.Black else Color.White
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(title, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun Rail(sections: List<Section>, selected: Int, onClick: (Int) -> Unit) {
    val fr = remember { FocusRequester() }
    val tv = LocalIsTv.current
    LaunchedEffect(Unit) { if (tv) runCatching { fr.requestFocus() } }
    Column(
        Modifier.width(230.dp).fillMaxHeight().background(Panel).padding(top = 24.dp)
    ) {
        AppTitle(iconSize = 40, fontSize = 21, modifier = Modifier.padding(start = 20.dp, bottom = 20.dp))
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(sections, key = { i, s -> "$i|${s.title}" }) { i, s ->
                val auto = tv && s.kind != Kind.PORTAL && s.kind != Kind.DIAG
                RailItem(
                    s.title, s.icon, i == selected,
                    onClick = { onClick(i) },
                    onFocusSelect = if (auto) ({ if (i != selected) onClick(i) }) else null,
                    modifier = if (i == selected) Modifier.focusRequester(fr) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun RailItem(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    onFocusSelect: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, label = "rail")
    // Наведение пультом на пункт меню открывает раздел (с небольшой задержкой).
    LaunchedEffect(focused) {
        if (focused && onFocusSelect != null) {
            delay(450)
            onFocusSelect()
        }
    }
    val bg = when {
        focused -> Accent
        selected -> Accent.copy(alpha = 0.18f)
        else -> Color.Transparent
    }
    val fg = when {
        focused -> Color.Black
        selected -> Accent
        else -> Color.White
    }
    Row(
        modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = touchIndication()) { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            title, color = fg, fontSize = 17.sp,
            fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Последний поиск — сохраняется, пока открыта карточка найденного фильма. */
private object SearchMemory {
    var query = ""
    var result: Load<Page>? = null
}

@Composable
fun SearchPane(client: PortalClient, onOpen: (Node) -> Unit) {
    var query by remember { mutableStateOf(SearchMemory.query) }
    var result by remember { mutableStateOf(SearchMemory.result) }
    var request by remember { mutableIntStateOf(0) }
    val tv = LocalIsTv.current
    val fieldFocus = remember { FocusRequester() }

    LaunchedEffect(request) {
        if (request > 0 && query.isNotBlank()) {
            result = Load.Loading
            val r = load { client.search(query.trim()) }
            result = r
            SearchMemory.query = query
            SearchMemory.result = r
        }
    }
    // На телефоне ищем сразу по мере ввода — без кнопки «Найти» на клавиатуре.
    LaunchedEffect(query) {
        if (!tv && query.trim().length >= 3 && query != SearchMemory.query) {
            delay(700)
            request++
        }
    }
    LaunchedEffect(Unit) { if (!tv && result == null) runCatching { fieldFocus.requestFocus() } }

    val pad = if (isWideLayout()) 36.dp else 16.dp
    Column(Modifier.fillMaxSize()) {
        Text(
            "Поиск", fontSize = if (tv) 24.sp else 22.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = pad, top = if (tv) 20.dp else 14.dp),
        )
        Spacer(Modifier.height(12.dp))
        SearchField(
            query, { query = it }, onSearch = { request++ },
            onClear = { query = ""; result = null; SearchMemory.query = ""; SearchMemory.result = null },
            modifier = Modifier.padding(horizontal = pad).focusRequester(fieldFocus),
        )
        Spacer(Modifier.height(8.dp))
        when (val r = result) {
            null -> MessagePane("Введите название фильма, сериала или мультфильма")
            Load.Loading -> Loader()
            is Load.Err -> MessagePane(r.message)
            is Load.Ok -> key(r) {
                PagedGrid(client, r.value, onOpen, emptyText = "Ничего не найдено", memoryKey = "search:${SearchMemory.query}")
            }
        }
    }
}
