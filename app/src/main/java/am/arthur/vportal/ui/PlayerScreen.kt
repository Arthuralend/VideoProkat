package am.arthur.vportal.ui

import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.provider.Settings
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.abs
import kotlin.math.roundToInt
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
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import am.arthur.vportal.data.Load
import am.arthur.vportal.data.Node
import am.arthur.vportal.data.load
import am.arthur.vportal.data.PortalClient
import am.arthur.vportal.data.Storage

/**
 * PlayerView для ТВ и телефона.
 * Пульт: ← / → — перемотка на 10 с, OK — пауза.
 * Палец (touchGestures): тап — показать/скрыть панель, двойной тап слева/справа — ∓10 с, в центре — пауза;
 * свайп по горизонтали — перемотка, по вертикали справа — громкость, слева — яркость.
 */
@OptIn(UnstableApi::class)
class TvPlayerView(context: Context) : PlayerView(context) {
    var touchGestures = false
    var onHint: ((String?) -> Unit)? = null

    private enum class Mode { NONE, SEEK, VOLUME, BRIGHTNESS, IGNORE }

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val slop = ViewConfiguration.get(context).scaledTouchSlop * 2
    private var mode = Mode.NONE
    private var downX = 0f
    private var downY = 0f
    private var startPos = 0L
    private var seekTarget = -1L
    private var startVolume = 0
    private var startBrightness = 0.5f
    private val clearHint = Runnable { onHint?.invoke(null) }

    private fun hint(text: String, hideAfterMs: Long = 0) {
        removeCallbacks(clearHint)
        onHint?.invoke(text)
        if (hideAfterMs > 0) postDelayed(clearHint, hideAfterMs)
    }

    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (isControllerFullyVisible) hideController() else showController()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val p = player ?: return true
            val third = width / 3f
            when {
                e.x < third -> { p.seekBack(); hint("« 10 с", 700) }
                e.x > third * 2 -> { p.seekForward(); hint("10 с »", 700) }
                else -> {
                    if (p.isPlaying) { p.pause(); hint("Пауза", 700) } else { p.play(); hint("Воспроизведение", 700) }
                }
            }
            return true
        }
    })

    private fun window() = context.findActivity()?.window

    private fun currentBrightness(): Float {
        val b = window()?.attributes?.screenBrightness ?: -1f
        if (b >= 0f) return b
        val sys = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrDefault(128)
        return (sys / 255f).coerceIn(0.01f, 1f)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val p = player
        if (!touchGestures || p == null) return super.onTouchEvent(ev)
        detector.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y
                mode = Mode.NONE
                seekTarget = -1L
                startPos = p.currentPosition
                startVolume = audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                startBrightness = currentBrightness()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (mode == Mode.NONE && (abs(dx) > slop || abs(dy) > slop)) {
                    // У самого верха и низа экрана жесты оставляем системе (шторка, «Домой»).
                    val edge = downY < height * 0.07f || downY > height * 0.93f
                    mode = when {
                        edge -> Mode.IGNORE
                        abs(dx) > abs(dy) -> Mode.SEEK
                        downX > width / 2f -> Mode.VOLUME
                        else -> Mode.BRIGHTNESS
                    }
                }
                when (mode) {
                    Mode.SEEK -> {
                        val duration = p.duration
                        if (duration > 0) {
                            // Свайп на всю ширину экрана — 3 минуты.
                            val delta = (dx / width * 180_000).toLong()
                            seekTarget = (startPos + delta).coerceIn(0L, duration)
                            val sign = if (delta >= 0) "+" else "−"
                            hint("$sign${formatTime(abs(delta))}  ·  ${formatTime(seekTarget)} / ${formatTime(duration)}")
                        }
                    }
                    Mode.VOLUME -> {
                        val a = audio ?: return true
                        val max = a.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val v = (startVolume + (-dy / (height * 0.7f)) * max).roundToInt().coerceIn(0, max)
                        a.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
                        hint("Громкость ${if (max > 0) v * 100 / max else 0}%")
                    }
                    Mode.BRIGHTNESS -> {
                        val w = window() ?: return true
                        val b = (startBrightness + (-dy / (height * 0.7f))).coerceIn(0.01f, 1f)
                        w.attributes = w.attributes.apply { screenBrightness = b }
                        hint("Яркость ${(b * 100).roundToInt()}%")
                    }
                    else -> Unit
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (mode == Mode.SEEK && seekTarget >= 0 && ev.actionMasked == MotionEvent.ACTION_UP) {
                    p.seekTo(seekTarget)
                }
                if (mode != Mode.NONE) postDelayed(clearHint, 500)
                mode = Mode.NONE
            }
        }
        return true
    }

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
fun PlayerScreen(client: PortalClient, storage: Storage, node: Node, url: String?, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val tv = LocalIsTv.current
    val activity = remember { context.findActivity() }
    var landscape by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }
    var hint by remember { mutableStateOf<String?>(null) }

    // Телефон: кино — во весь экран, горизонтально, без строки состояния и кнопок навигации.
    if (!tv) {
        DisposableEffect(activity) {
            val window = activity?.window
            val bars = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
            bars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars?.hide(WindowInsetsCompat.Type.systemBars())
            onDispose {
                bars?.show(WindowInsetsCompat.Type.systemBars())
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                window?.let { w ->
                    w.attributes = w.attributes.apply {
                        screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    }
                }
            }
        }
        LaunchedEffect(landscape) {
            activity?.requestedOrientation =
                if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    }
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
        // Буфер ограничен ~30 с и 24 МБ: по умолчанию ExoPlayer держит до ~128 МБ видео в памяти,
        // а на ТВ-приставках приложению дают всего 128–256 МБ — отсюда OutOfMemoryError посреди фильма.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 30_000, 2_500, 5_000)
            .setTargetBufferBytes(24 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(false)
            .setBackBuffer(0, false)
            .build()
        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
    }

    DisposableEffect(player) {
        var retries = 0
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                // Сбой сети или памяти — тихо переподключаемся с того же места (до 5 раз подряд).
                if (retries < 5) {
                    retries++
                    System.gc()
                    player.prepare()
                    player.playWhenReady = true
                    return
                }
                error = "Не удалось воспроизвести: ${e.errorCodeName}\n${e.cause?.message ?: e.message ?: ""}"
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) { error = null; retries = 0 }
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
                    controllerShowTimeoutMs = if (tv) 3500 else 3000
                    touchGestures = !tv
                    onHint = { hint = it }
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { v -> controlsVisible = v == View.VISIBLE }
                    )
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
        if (!tv && controlsVisible) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xAA000000), Color.Transparent)))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIcon(Icons.Filled.ArrowBack, "Назад", onBack)
                Spacer(Modifier.width(12.dp))
                Text(
                    node.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                RoundIcon(Icons.Filled.Refresh, "Повернуть экран", { landscape = !landscape })
            }
        }
        hint?.let {
            Text(
                it,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xB3000000))
                    .padding(horizontal = 22.dp, vertical = 14.dp),
            )
        }
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
