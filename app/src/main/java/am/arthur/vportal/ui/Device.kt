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

/** Телефон в вертикальном положении (нижняя панель вкладок, крупные кнопки под палец). */
@Composable
fun isPhonePortrait(): Boolean = !LocalIsTv.current && !isWideLayout()

/** Подсветка нажатия: на телефоне — «волна» под пальцем, на ТВ её заменяет рамка фокуса. */
@Composable
fun touchIndication(): androidx.compose.foundation.Indication? =
    if (LocalIsTv.current) null else androidx.compose.foundation.LocalIndication.current

/** Ищет Activity за контекстом Compose (нужно плееру: ориентация, яркость, системные панели). */
fun Context.findActivity(): android.app.Activity? {
    var c: Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}
