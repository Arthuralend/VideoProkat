package am.arthur.vportal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import am.arthur.vportal.data.Storage
import am.arthur.vportal.ui.VPortalApp
import am.arthur.vportal.ui.VPortalTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val storage = Storage(applicationContext)
        setContent {
            VPortalTheme {
                VPortalApp(storage)
            }
        }
    }
}
