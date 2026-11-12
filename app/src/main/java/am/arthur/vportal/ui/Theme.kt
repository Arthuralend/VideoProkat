package am.arthur.vportal.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFFFF7A1A)
val Bg = Color(0xFF0F1014)
val Panel = Color(0xFF16181D)
val CardBg = Color(0xFF1F2128)
val TextDim = Color(0xFF9AA0AA)
val ErrorRed = Color(0xFFFF5A5A)

@Composable
fun VPortalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color.Black,
            background = Bg,
            surface = CardBg,
            onBackground = Color.White,
            onSurface = Color.White,
        )
    ) {
        Surface(color = Bg, contentColor = Color.White, content = content)
    }
}
