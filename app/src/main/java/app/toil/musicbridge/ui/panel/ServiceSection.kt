package app.toil.musicbridge.ui.panel

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.toil.musicbridge.R
import app.toil.musicbridge.service.MusicBridgeService
import app.toil.musicbridge.service.MusicBridgeServiceState

internal fun LazyListScope.serviceSection() {
    item(key = "service") { ServiceCard() }
}

@Composable
private fun ServiceCard() {
    val context = LocalContext.current
    val running by MusicBridgeServiceState.isRunning.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme

    if (running) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = colors.primary),
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.control_service_started)) },
                leadingContent = { Icon(painterResource(R.drawable.check_24px), contentDescription = null) },
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent,
                    headlineColor = colors.onPrimary,
                    leadingIconColor = colors.onPrimary,
                ),
            )
        }
    } else {
        Card(
            onClick = { MusicBridgeService.start(context) },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = colors.error),
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.control_service_stopped)) },
                supportingContent = { Text(stringResource(R.string.control_service_stopped_desc)) },
                leadingContent = { Icon(painterResource(R.drawable.rounded_error_24), contentDescription = null) },
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent,
                    headlineColor = colors.onError,
                    supportingColor = colors.onError,
                    leadingIconColor = colors.onError,
                ),
            )
        }
    }
}
