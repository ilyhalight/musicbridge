package app.toil.musicbridge.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore

    val onboardingDone: Flow<Boolean> = dataStore.data.map { it[OnboardingDoneKey] ?: false }

    suspend fun setOnboardingDone() {
        dataStore.edit { it[OnboardingDoneKey] = true }
    }

    private companion object {
        val OnboardingDoneKey = booleanPreferencesKey("onboarding_done")
    }
}
