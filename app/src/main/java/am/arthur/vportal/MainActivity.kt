package am.arthur.vportal

import android.os.Bundle
import coil.Coil
import coil.ImageLoader
import coil.memory.MemoryCache
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import am.arthur.vportal.data.Storage
import am.arthur.vportal.ui.LocalIsTv
import am.arthur.vportal.ui.VPortalApp
import am.arthur.vportal.ui.detectTv
import androidx.compose.runtime.CompositionLocalProvider
import am.arthur.vportal.ui.VPortalTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val storage = Storage(applicationContext)
        val isTv = detectTv(this)
        // Кэш постеров в памяти — не больше 15% доступной приложению памяти.
        Coil.setImageLoader(
            ImageLoader.Builder(applicationContext)
                .memoryCache { MemoryCache.Builder(applicationContext).maxSizePercent(0.15).build() }
                .build()
        )
        setContent {
            CompositionLocalProvider(LocalIsTv provides isTv) {
                VPortalTheme {
                    VPortalApp(storage)
                }
            }
        }
    }
}
