package app.toil.musicbridge.ui.panel

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.toil.musicbridge.R
import app.toil.musicbridge.ui.common.rememberNotificationPermissionRequest

/** Reminder for users who skipped the onboarding row; callers decide when it is relevant (API 33+, not granted). */
internal fun LazyListScope.notificationPermissionSection(visible: Boolean) {
    if (!visible) return
    item(key = "notification-permission") { NotificationPermissionCard() }
}

@Composable
private fun NotificationPermissionCard() {
    val request = rememberNotificationPermissionRequest()
    Card(
        onClick = request,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
    ) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.notification_permission_title)) },
            supportingContent = { Text(stringResource(R.string.notification_permission_desc)) },
            leadingContent = { Icon(painterResource(R.drawable.notification_settings_24px), contentDescription = null) },
            trailingContent = { Icon(painterResource(R.drawable.chevron_right_24px), contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}
