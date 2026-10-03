package app.toil.musicbridge.util

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri

fun Context.isNotificationListenerEnabled(): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

fun Context.isIgnoringBatteryOptimizations(): Boolean =
    getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) == true

fun Context.openNotificationListenerSettings() {
    try {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        e.printStackTrace()
    }
}

@SuppressLint("BatteryLife")
fun Context.requestIgnoreBatteryOptimizations() {
    try {
        startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:$packageName".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
