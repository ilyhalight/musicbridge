package app.toil.musicbridge

import android.app.Application
import app.toil.musicbridge.data.SettingsRepository

class MusicBridgeApplication : Application() {
    val settings: SettingsRepository by lazy { SettingsRepository(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: MusicBridgeApplication
            private set
    }
}
