package app.toil.musicbridge.ui.onboarding

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.toil.musicbridge.R
import app.toil.musicbridge.ui.common.rememberNotificationPermissionRequest
import app.toil.musicbridge.util.openNotificationListenerSettings
import app.toil.musicbridge.util.requestIgnoreBatteryOptimizations

@Composable
fun PermissionsCard(
    permissions: PermissionState,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val requestNotifications = rememberNotificationPermissionRequest()

    Card(modifier, colors = CardDefaults.cardColors(containerColor = colors.primary)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.onboarding_act_full), style = MaterialTheme.typography.headlineSmall)

            Card(shape = MaterialTheme.shapes.large) {
                PermissionRow(
                    title = R.string.onboarding_act_full_a1,
                    description = R.string.onboarding_act_full_a1_d,
                    icon = R.drawable.notification_settings_24px,
                    granted = permissions.notificationListener,
                    onClick = context::openNotificationListenerSettings,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    HorizontalDivider()
                    PermissionRow(
                        title = R.string.onboarding_act_full_a2,
                        description = R.string.onboarding_act_full_a2_d,
                        icon = R.drawable.notification_settings_24px,
                        granted = permissions.notifications,
                        overline = R.string.onboarding_act_full_a3_e,
                        onClick = requestNotifications,
                    )
                }
                HorizontalDivider()
                PermissionRow(
                    title = R.string.onboarding_act_full_a3,
                    description = R.string.onboarding_act_full_a3_d,
                    icon = R.drawable.battery_android_frame_bolt_24px,
                    granted = permissions.unrestrictedBackground,
                    overline = R.string.onboarding_act_full_a3_e,
                    onClick = context::requestIgnoreBatteryOptimizations,
                )
            }

            Button(
                onClick = onContinue,
                enabled = permissions.notificationListener,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primaryContainer,
                    contentColor = colors.onPrimaryContainer,
                ),
                contentPadding = PaddingValues(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.onboarding_act_full_act))
                    Icon(painterResource(R.drawable.chevron_right_24px), contentDescription = null)
                }
            }

            Text(
                text = stringResource(R.string.onboarding_act_full_hint),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PermissionRow(
    @StringRes title: Int,
    @StringRes description: Int,
    @DrawableRes icon: Int,
    granted: Boolean,
    onClick: () -> Unit,
    @StringRes overline: Int? = null,
) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        modifier = Modifier.fillMaxWidth().clickable(enabled = !granted, onClick = onClick),
        overlineContent = overline?.let { { Text(stringResource(it)) } },
        supportingContent = { Text(stringResource(description)) },
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        trailingContent = {
            val trailing = if (granted) R.drawable.check_24px else R.drawable.chevron_right_24px
            Icon(painterResource(trailing), contentDescription = null)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
