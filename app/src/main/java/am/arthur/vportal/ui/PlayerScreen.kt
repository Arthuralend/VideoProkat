package am.arthur.vportal.ui

import android.content.Context
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import am.arthur.vportal.data.Load
import am.arthur.vportal.data.Node
import am.arthur.vportal.data.load
import am.arthur.vportal.data.PortalClient
import am.arthur.vportal.data.Storage

/** PlayerView с перемоткой стрелками пульта: ← / → — на 10 секунд назад/вперёд. */
@OptIn(UnstableApi::class)
class TvPlayerView(context: Context) : PlayerView(context) {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val p = player
        if (p != null && event.action == KeyEvent.ACTION_DOWN && !isControllerFullyVisible) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    p.seekBack(); showController(); return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    p.seekForward(); showController(); return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (p.isPlaying) p.pause() else p.play()
                    showController(); return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(client: PortalClient, storage: Storage, node: Node, url: String?) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var resolved by remember(node, url) { mutableStateOf(url) }

    // Ссылки нет — спрашиваем у портала (cmd:"flick").
    LaunchedEffect(node, url) {
        if (resolved == null) {
            when (val r = load { client.streamUrl(node) }) {
                is Load.Ok -> resolved = r.value
                is Load.Err -> error = r.message
                Load.Loading -> Unit
            }
        }
    }

    val player = remember(node) {
        val dataSource = OkHttpDataSource.Factory(client.http).setUserAgent(PortalClient.USER_AGENT)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                error = "Не удалось воспроизвести: ${e.errorCodeName}\n${e.cause?.message ?: e.message ?: ""}"
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) error = null
            }
        }
        player.addListener(listener)
        onDispose {
            storage.savePosition(node, player.currentPosition, player.duration)
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(resolved) {
        val u = resolved ?: return@LaunchedEffect
        val item = MediaItem.Builder()
            .setUri(u)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(node.title).build())
            .build()
        player.setMediaItem(item)
        val start = storage.position(node.key)
        if (start > 0) player.seekTo(start)
        player.prepare()
        player.playWhenReady = true
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                player.pause()
                storage.savePosition(node, player.currentPosition, player.duration)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                TvPlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    controllerShowTimeoutMs = 3500
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    keepScreenOn = true
                    isFocusable = true
                    isFocusableInTouchMode = true
                    post { requestFocus() }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (resolved == null && error == null) Loader()
        error?.let {
            Text(
                it,
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xCC000000))
                    .padding(24.dp),
            )
        }
    }
}
