package app.toil.musicbridge.ui.panel

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.toil.musicbridge.MusicBridgeApplication
import app.toil.musicbridge.R
import app.toil.musicbridge.scrobbling.ListenBrainzClient
import app.toil.musicbridge.scrobbling.ListenBrainzHttpTransport
import app.toil.musicbridge.scrobbling.DEFAULT_SCROBBLING_ENDPOINT
import app.toil.musicbridge.scrobbling.normalizeScrobblingEndpoint
import app.toil.musicbridge.scrobbling.ScrobbleQueue
import app.toil.musicbridge.scrobbling.ScrobblingSettings
import app.toil.musicbridge.scrobbling.ThresholdMode
import app.toil.musicbridge.service.MusicBridgeServiceState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ScrobblingTab(contentPadding: PaddingValues) {
    val context = LocalContext.current
    val app = context.applicationContext as MusicBridgeApplication
    val settings by app.settings.scrobbling.collectAsStateWithLifecycle(initialValue = ScrobblingSettings())
    val running by MusicBridgeServiceState.isRunning.collectAsStateWithLifecycle()
    val workManager = remember(context) { WorkManager.getInstance(context) }
    val workFlow = remember(workManager) { workManager.getWorkInfosByTagFlow(ScrobbleQueue.TAG) }
    val work by workFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val accountWork = settings.accountId?.let { account -> work.filter { ScrobbleQueue.accountTag(account) in it.tags } }.orEmpty()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var token by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf(DEFAULT_SCROBBLING_ENDPOINT) }
    var allowHttp by remember { mutableStateOf(false) }
    var endpointError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var disconnectDialog by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(ThresholdMode.FixedSeconds) }
    var seconds by remember { mutableStateOf("30") }
    var secondsError by remember { mutableStateOf(false) }
    var thresholdSaved by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(settings.thresholdMode, settings.thresholdSeconds) {
        mode = settings.thresholdMode
        seconds = settings.thresholdSeconds.toString()
    }
    LaunchedEffect(settings.endpoint, settings.allowHttp) {
        endpoint = settings.endpoint
        allowHttp = settings.allowHttp
    }

    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "account") {
            ScrobblingCard {
                Text("ListenBrainz / Maloja", style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.scrobbling_description), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it; endpointError = false; allowHttp = false; message = null },
                    label = { Text(stringResource(R.string.scrobbling_endpoint)) },
                    supportingText = {
                        Text(stringResource(if (endpointError) R.string.scrobbling_endpoint_error else R.string.scrobbling_endpoint_hint))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                    singleLine = true,
                    enabled = !busy,
                    isError = endpointError,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (endpoint.trim().startsWith("http://", ignoreCase = true)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.scrobbling_allow_http), modifier = Modifier.weight(1f))
                        Switch(
                            checked = allowHttp,
                            enabled = !busy,
                            onCheckedChange = { allowHttp = it; endpointError = false },
                            modifier = Modifier.semantics { contentDescription = context.getString(R.string.scrobbling_allow_http) },
                        )
                    }
                    Text(stringResource(R.string.scrobbling_http_warning), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(enabled = !busy, onClick = {
                    endpoint = DEFAULT_SCROBBLING_ENDPOINT
                    allowHttp = false
                    endpointError = false
                    message = null
                    token = ""
                }) { Text(stringResource(R.string.scrobbling_use_listenbrainz)) }
                if (runCatching { normalizeScrobblingEndpoint(endpoint, allowHttp) }.getOrNull() == DEFAULT_SCROBBLING_ENDPOINT) {
                    TextButton(onClick = { uriHandler.openUri("https://listenbrainz.org/settings/") }) {
                        Text(stringResource(R.string.scrobbling_get_token))
                    }
                }
                settings.userName?.let { Text(stringResource(R.string.scrobbling_connected, it)) }
                if (settings.accountId != null) Text(stringResource(R.string.scrobbling_connected_server, settings.endpoint))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it; message = null },
                    label = { Text(stringResource(R.string.scrobbling_token)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    singleLine = true,
                    enabled = !busy,
                    isError = message == R.string.scrobbling_invalid_token,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    enabled = !busy,
                    onClick = {
                        val submittedToken = token.trim()
                        val submittedEndpoint = runCatching { normalizeScrobblingEndpoint(endpoint, allowHttp) }.getOrNull()
                        val submittedAllowHttp = allowHttp
                        if (submittedEndpoint == null) {
                            endpointError = true
                        } else if (submittedToken.isEmpty() || submittedToken.length > 256 || submittedToken.any { it.code !in 33..126 }) {
                            message = R.string.scrobbling_invalid_token
                        } else {
                            endpointError = false
                            busy = true
                            message = null
                            scope.launch {
                                try {
                                    val name = withContext(Dispatchers.IO) {
                                        ListenBrainzClient(ListenBrainzHttpTransport(submittedEndpoint, submittedAllowHttp)).validateToken(submittedToken)
                                    }
                                    if (name == null) message = R.string.scrobbling_invalid_token
                                    else {
                                        app.settings.connect(submittedToken, name, submittedEndpoint, submittedAllowHttp)
                                        token = ""
                                        message = R.string.scrobbling_token_saved
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    message = R.string.scrobbling_connect_error
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    },
                ) { Text(stringResource(if (busy) R.string.scrobbling_checking else R.string.scrobbling_connect)) }
                message?.let { StatusText(it) }
                if (settings.authFailed) StatusText(R.string.scrobbling_auth_error)
                if (settings.accountId != null) {
                    TextButton(enabled = !busy, onClick = { disconnectDialog = true }) {
                        Text(stringResource(R.string.scrobbling_disconnect))
                    }
                }
                Text(stringResource(R.string.scrobbling_account_hint), style = MaterialTheme.typography.bodySmall)
            }
        }
        item(key = "enabled") {
            ScrobblingCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.scrobbling_enabled), modifier = Modifier.weight(1f))
                    Switch(
                        checked = settings.enabled,
                        enabled = settings.accountId != null && !busy,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                app.settings.setScrobblingEnabled(enabled)
                                if (!enabled) settings.accountId?.let(app.scrobbleQueue::cancelAccount)
                            }
                        },
                        modifier = Modifier.semantics { contentDescription = context.getString(R.string.scrobbling_enabled) },
                    )
                }
                Text(stringResource(R.string.scrobbling_disable_hint), style = MaterialTheme.typography.bodySmall)
                if (!running) Text(stringResource(R.string.scrobbling_service_stopped), style = MaterialTheme.typography.bodyMedium)
            }
        }
        item(key = "threshold") {
            ScrobblingCard {
                Text(stringResource(R.string.scrobbling_threshold), style = MaterialTheme.typography.titleMedium)
                Column(Modifier.selectableGroup()) {
                    ThresholdOption(mode == ThresholdMode.FixedSeconds, R.string.scrobbling_fixed) {
                        mode = ThresholdMode.FixedSeconds; thresholdSaved = false
                    }
                    ThresholdOption(mode == ThresholdMode.HalfTrack, R.string.scrobbling_half) {
                        mode = ThresholdMode.HalfTrack; thresholdSaved = false
                    }
                }
                if (mode == ThresholdMode.FixedSeconds) {
                    OutlinedTextField(
                        value = seconds,
                        onValueChange = { seconds = it; secondsError = false; thresholdSaved = false },
                        label = { Text(stringResource(R.string.scrobbling_seconds)) },
                        supportingText = { Text(stringResource(R.string.scrobbling_seconds_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = secondsError,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Button(onClick = {
                    val value = if (mode == ThresholdMode.FixedSeconds) seconds.toIntOrNull() else settings.thresholdSeconds
                    if (value == null || value !in 1..3600) secondsError = true
                    else scope.launch {
                        app.settings.setThreshold(mode, value)
                        thresholdSaved = true
                    }
                }) { Text(stringResource(R.string.scrobbling_save_threshold)) }
                if (thresholdSaved) StatusText(R.string.scrobbling_threshold_saved)
                Text(stringResource(R.string.scrobbling_threshold_hint), style = MaterialTheme.typography.bodySmall)
            }
        }
        item(key = "queue") {
            ScrobblingCard {
                Text(stringResource(R.string.scrobbling_delivery), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.scrobbling_pending, accountWork.count { !it.state.isFinished }))
                val failed = accountWork.count { it.state == WorkInfo.State.FAILED }
                if (failed > 0) Text(stringResource(R.string.scrobbling_failed, failed))
                settings.lastSubmittedTitle?.let { Text(stringResource(R.string.scrobbling_last_sent, it)) }
                Text(stringResource(R.string.scrobbling_delivery_hint), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (disconnectDialog) {
        AlertDialog(
            onDismissRequest = { disconnectDialog = false },
            title = { Text(stringResource(R.string.scrobbling_disconnect)) },
            text = { Text(stringResource(R.string.scrobbling_disconnect_hint)) },
            confirmButton = {
                TextButton(onClick = {
                    disconnectDialog = false
                    scope.launch {
                        val account = settings.accountId
                        app.settings.disconnect()
                        account?.let(app.scrobbleQueue::cancelAccount)
                    }
                }) { Text(stringResource(R.string.scrobbling_disconnect)) }
            },
            dismissButton = { TextButton(onClick = { disconnectDialog = false }) { Text(stringResource(R.string.scrobbling_cancel)) } },
        )
    }
}

@Composable
private fun ScrobblingCard(content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun ThresholdOption(selected: Boolean, @StringRes label: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .selectable(selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(stringResource(label))
    }
}

@Composable
private fun StatusText(@StringRes text: Int) {
    Text(stringResource(text), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}
