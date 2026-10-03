package app.toil.musicbridge

import android.app.Application
import app.toil.musicbridge.data.SettingsRepository
import app.toil.musicbridge.scrobbling.ScrobbleQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

class MusicBridgeApplication : Application() {
    val settings: SettingsRepository by lazy { SettingsRepository(this) }
    val scrobbleQueue: ScrobbleQueue by lazy { ScrobbleQueue(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this
        scope.launch {
            var previousAccount: String? = null
            settings.scrobbling.distinctUntilChangedBy { it.enabled to it.accountId }.collect { value ->
                previousAccount?.takeIf { it != value.accountId }?.let(scrobbleQueue::cancelAccount)
                if (!value.enabled) value.accountId?.let(scrobbleQueue::cancelAccount)
                previousAccount = value.accountId
            }
        }
    }

    companion object {
        lateinit var instance: MusicBridgeApplication
            private set
    }
}
