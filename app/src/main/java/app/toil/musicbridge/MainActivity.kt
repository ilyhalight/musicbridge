package app.toil.musicbridge

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.toil.musicbridge.service.MusicBridgeService
import app.toil.musicbridge.ui.MusicBridgeTheme
import app.toil.musicbridge.ui.navigation.MusicBridgeApp
import app.toil.musicbridge.util.isNotificationListenerEnabled

/**
 * Also the target of `app.toil.musicbridge.action.external.DISPLAY_SETTINGS`,
 * which simply opens the app and lets the usual routing pick the screen.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (applicationContext.isNotificationListenerEnabled()) {
            MusicBridgeService.start(this)
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(Color.argb(128, 27, 27, 27)),
        )

        setContent {
            MusicBridgeTheme {
                MusicBridgeApp()
            }
        }
    }
}
