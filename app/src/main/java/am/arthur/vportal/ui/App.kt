package am.arthur.vportal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import am.arthur.vportal.data.Node
import am.arthur.vportal.data.PortalClient
import am.arthur.vportal.data.PortalConfig
import am.arthur.vportal.data.Storage

sealed interface Screen {
    data class Browse(val node: Node) : Screen
    data class Details(val node: Node) : Screen
    data class Player(val node: Node) : Screen
}

@Composable
fun VPortalApp(storage: Storage) {
    var portalRaw by rememberSaveable { mutableStateOf(storage.lastPortal) }
    var diagnostics by remember { mutableStateOf<String?>(null) }
    val client = remember(portalRaw) { portalRaw?.let { PortalConfig.parse(it) }?.let { PortalClient(it) } }
    val stack = remember(client) { mutableStateListOf<Screen>() }
    var homeSection by remember(client) { mutableIntStateOf(-1) }

    val open: (Node) -> Unit = { n ->
        when {
            n.isPlayable -> stack.add(Screen.Details(n))
            n.isFolder -> stack.add(Screen.Browse(n))
        }
    }

    val diag = diagnostics
    if (diag != null) {
        BackHandler { diagnostics = null }
        DiagnosticsScreen(diag)
        return
    }

    if (client == null) {
        ConnectScreen(
            initial = PortalConfig.display(storage.lastPortal),
            onConnect = { input ->
                val resolved = PortalConfig.resolve(input)
                storage.lastPortal = resolved
                portalRaw = resolved
            },
            onDiagnostics = { diagnostics = PortalConfig.resolve(it) },
        )
        return
    }

    val current = stack.lastOrNull()
    BackHandler(enabled = current != null) { stack.removeAt(stack.lastIndex) }

    when (current) {
        null -> HomeScreen(
            client = client,
            storage = storage,
            selected = homeSection,
            onSelect = { homeSection = it },
            onOpen = open,
            onChangePortal = {
                storage.lastPortal = null
                portalRaw = null
            },
            onDiagnostics = { diagnostics = client.portal.raw },
        )
        is Screen.Browse -> NodeGrid(
            client, current.node.folderUrl, current.node.inline, open,
            title = current.node.title, autoFocus = true,
        )
        is Screen.Details -> DetailsScreen(storage, current.node, onPlay = { stack.add(Screen.Player(it)) })
        is Screen.Player -> PlayerScreen(client, storage, current.node)
    }
}
