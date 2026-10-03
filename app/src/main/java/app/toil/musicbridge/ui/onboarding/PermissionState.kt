package app.toil.musicbridge.ui.onboarding

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.toil.musicbridge.util.isIgnoringBatteryOptimizations
import app.toil.musicbridge.util.isNotificationListenerEnabled

data class PermissionState(
    val notificationListener: Boolean,
    val unrestrictedBackground: Boolean,
)

private fun Context.readPermissionState() = PermissionState(
    notificationListener = isNotificationListenerEnabled(),
    unrestrictedBackground = isIgnoringBatteryOptimizations(),
)

/** Re-reads the permissions every time the user comes back from system settings. */
@Composable
fun rememberPermissionState(): PermissionState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(context.readPermissionState()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state = context.readPermissionState() }
    return state
}
