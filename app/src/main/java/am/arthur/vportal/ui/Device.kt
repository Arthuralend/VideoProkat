package am.arthur.vportal.ui

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration

/** true — приложение запущено на Android TV (управление пультом). */
val LocalIsTv = staticCompositionLocalOf { false }

fun detectTv(context: Context): Boolean {
    val ui = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    if (ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true
    val pm = context.packageManager
    return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        pm.hasSystemFeature("android.software.leanback_only")
}

/** Широкая раскладка (меню слева): ТВ, планшет или телефон в горизонтальном положении. */
@Composable
fun isWideLayout(): Boolean {
    if (LocalIsTv.current) return true
    val c = LocalConfiguration.current
    return c.screenWidthDp > c.screenHeightDp && c.screenWidthDp >= 600
}
