package app.toil.musicbridge.ui.common

import android.Manifest
import android.annotation.SuppressLint
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.toil.musicbridge.util.findActivity
import app.toil.musicbridge.util.openAppNotificationSettings

/** A system dialog needs a human to answer it; a result faster than this means the system skipped it. */
private const val InstantDenialMillis = 400L

/**
 * Returns an action that asks for `POST_NOTIFICATIONS` (API 33+) and falls back to the app's notification
 * settings once the system refuses to show the dialog again.
 */
@SuppressLint("InlinedApi") // callers only invoke the action on API 33+
@Composable
fun rememberNotificationPermissionRequest(): () -> Unit {
    val context = LocalContext.current
    var blocked by remember { mutableStateOf(false) }
    var launchedAt by remember { mutableLongStateOf(0L) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) return@rememberLauncherForActivityResult
        val rationale = context.findActivity()
            ?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true
        val dialogSkipped = SystemClock.elapsedRealtime() - launchedAt < InstantDenialMillis
        blocked = !rationale
        if (dialogSkipped) context.openAppNotificationSettings()
    }

    return {
        if (blocked) {
            context.openAppNotificationSettings()
        } else {
            launchedAt = SystemClock.elapsedRealtime()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
