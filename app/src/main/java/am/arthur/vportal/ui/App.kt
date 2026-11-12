package am.arthur.vportal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
    data class Player(val node: Node, val url: String?) : Screen
}

@Composable
fun VPortalApp(storage: Storage) {
    var portalRaw by rememberSaveable { mutableStateOf(storage.lastPortal) }
    var diagnostics by remember { mutableStateOf<String?>(null) }
    val client = remember(portalRaw) { portalRaw?.let { PortalConfig.parse(it) }?.let { PortalClient(it, storage) } }
    val stack = remember(client) { mutableStateListOf<Screen>() }
    var homeSection by remember(client) { mutableIntStateOf(-1) }

    val open: (Node) -> Unit = { n ->
        when {
            n.isFolder -> stack.add(Screen.Browse(n))
            else -> stack.add(Screen.Details(n))
        }
    }

    val diag = diagnostics
    if (diag != null) {
        BackHandler { diagnostics = null }
        DiagnosticsScreen(diag, storage)
        return
    }

    if (client == null) {
        ConnectScreen(
            initial = storage.lastPortal ?: PortalConfig.DEFAULT,
            onConnect = { input ->
                val value = PortalConfig.normalize(input)
                storage.resetProto()
                storage.lastPortal = value
                portalRaw = value
            },
            onDiagnostics = { diagnostics = PortalConfig.normalize(it) },
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
        is Screen.Browse -> key(current) {
            NodeGrid(
                client, client.listRequest(current.node), null, open,
                title = current.node.title, autoFocus = true,
            )
        }
        is Screen.Details -> key(current) {
            DetailsScreen(
                client, storage, current.node,
                onPlay = { n, u -> stack.add(Screen.Player(n, u)) },
                onOpen = open,
            )
        }
        is Screen.Player -> key(current) { PlayerScreen(client, storage, current.node, current.url) }
    }
}
