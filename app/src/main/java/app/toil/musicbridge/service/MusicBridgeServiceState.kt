package app.toil.musicbridge.service

import app.toil.musicbridge.service.mirror.MirrorEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-wide view of [MusicBridgeService] for the UI and other in-process consumers. */
object MusicBridgeServiceState {
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _activePackage = MutableStateFlow<String?>(null)
    val activePackage: StateFlow<String?> = _activePackage.asStateFlow()

    private val _events = MutableSharedFlow<MirrorEvent>(extraBufferCapacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<MirrorEvent> = _events.asSharedFlow()

    internal fun setRunning(running: Boolean) {
        _isRunning.value = running
        if (!running) _activePackage.value = null
    }

    internal fun publish(event: MirrorEvent) {
        if (event is MirrorEvent.ActivePlayerChanged) _activePackage.value = event.packageName
        _events.tryEmit(event)
    }
}
