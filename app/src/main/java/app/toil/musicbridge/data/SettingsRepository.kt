package app.toil.musicbridge.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.toil.musicbridge.scrobbling.ScrobblingSettings
import app.toil.musicbridge.scrobbling.ThresholdMode
import app.toil.musicbridge.scrobbling.DEFAULT_SCROBBLING_ENDPOINT
import app.toil.musicbridge.scrobbling.normalizeScrobblingEndpoint
import app.toil.musicbridge.scrobbling.sameScrobblingAccount
import app.toil.musicbridge.scrobbling.malojaNativeEndpoint
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore
    private val tokenCipher = TokenCipher()

    val onboardingDone: Flow<Boolean> = dataStore.data.map { it[OnboardingDoneKey] ?: false }
    val scrobbling: Flow<ScrobblingSettings> = dataStore.data.map { values ->
        ScrobblingSettings(
            enabled = values[ScrobblingEnabledKey] ?: false,
            userName = values[UserNameKey],
            accountId = values[AccountIdKey],
            authFailed = values[AuthFailedKey] ?: false,
            thresholdMode = ThresholdMode.entries.firstOrNull { it.name == values[ThresholdModeKey] } ?: ThresholdMode.FixedSeconds,
            thresholdSeconds = (values[ThresholdSecondsKey] ?: 30).coerceIn(1, 3600),
            retryNotBeforeMs = values[RetryNotBeforeKey] ?: 0,
            lastSubmittedTitle = values[LastSubmittedKey],
            endpoint = values[EndpointKey] ?: DEFAULT_SCROBBLING_ENDPOINT,
            allowHttp = values[AllowHttpKey] ?: false,
            splitArtists = values[SplitArtistsKey] ?: false,
        )
    }

    class Credentials(val accountId: String, val token: String, val endpoint: String, val allowHttp: Boolean)

    suspend fun credentials(): Credentials? = withContext(Dispatchers.IO) {
        val values = dataStore.data.first()
        val accountId = values[AccountIdKey] ?: return@withContext null
        val encrypted = values[TokenKey] ?: return@withContext null
        val token = runCatching { tokenCipher.decrypt(encrypted) }.getOrNull() ?: return@withContext null
        Credentials(accountId, token, values[EndpointKey] ?: DEFAULT_SCROBBLING_ENDPOINT, values[AllowHttpKey] ?: false)
    }

    suspend fun connect(token: String, userName: String, endpoint: String, allowHttp: Boolean) = withContext(Dispatchers.IO) {
        val normalizedEndpoint = normalizeScrobblingEndpoint(endpoint, allowHttp)
        val encrypted = tokenCipher.encrypt(token)
        dataStore.edit {
            if (!sameScrobblingAccount(it[EndpointKey] ?: DEFAULT_SCROBBLING_ENDPOINT, it[UserNameKey], normalizedEndpoint, userName) || it[AccountIdKey] == null) {
                it[AccountIdKey] = UUID.randomUUID().toString()
                it.remove(LastSubmittedKey)
                it[SplitArtistsKey] = false
            }
            it[TokenKey] = encrypted
            it[UserNameKey] = userName
            it[EndpointKey] = normalizedEndpoint
            it[AllowHttpKey] = allowHttp && normalizedEndpoint.startsWith("http://")
            it[AuthFailedKey] = false
            it[ScrobblingEnabledKey] = true
            it[RetryNotBeforeKey] = 0
        }
    }

    suspend fun disconnect() {
        dataStore.edit {
            it.remove(TokenKey)
            it.remove(UserNameKey)
            it.remove(AccountIdKey)
            it.remove(LastSubmittedKey)
            it[ScrobblingEnabledKey] = false
            it[AuthFailedKey] = false
        }
    }

    suspend fun setScrobblingEnabled(enabled: Boolean) {
        dataStore.edit { it[ScrobblingEnabledKey] = enabled && it[AccountIdKey] != null }
    }

    suspend fun setSplitArtists(enabled: Boolean) {
        dataStore.edit {
            it[SplitArtistsKey] = enabled && malojaNativeEndpoint(it[EndpointKey] ?: DEFAULT_SCROBBLING_ENDPOINT) != null
        }
    }

    suspend fun setThreshold(mode: ThresholdMode, seconds: Int) {
        require(seconds in 1..3600)
        dataStore.edit {
            it[ThresholdModeKey] = mode.name
            it[ThresholdSecondsKey] = seconds
        }
    }

    suspend fun markAuthFailed(accountId: String) {
        dataStore.edit { if (it[AccountIdKey] == accountId) it[AuthFailedKey] = true }
    }

    suspend fun postponeSubmissions(accountId: String, untilMs: Long) {
        dataStore.edit {
            if (it[AccountIdKey] == accountId) it[RetryNotBeforeKey] = maxOf(it[RetryNotBeforeKey] ?: 0, untilMs)
        }
    }

    suspend fun markSubmitted(accountId: String, title: String) {
        dataStore.edit { if (it[AccountIdKey] == accountId) it[LastSubmittedKey] = title }
    }

    suspend fun setOnboardingDone() {
        dataStore.edit { it[OnboardingDoneKey] = true }
    }

    private companion object {
        val OnboardingDoneKey = booleanPreferencesKey("onboarding_done")
        val ScrobblingEnabledKey = booleanPreferencesKey("scrobbling_enabled")
        val TokenKey = stringPreferencesKey("listenbrainz_encrypted_token")
        val UserNameKey = stringPreferencesKey("listenbrainz_user_name")
        val AccountIdKey = stringPreferencesKey("listenbrainz_account_id")
        val AuthFailedKey = booleanPreferencesKey("listenbrainz_auth_failed")
        val ThresholdModeKey = stringPreferencesKey("scrobbling_threshold_mode")
        val ThresholdSecondsKey = intPreferencesKey("scrobbling_threshold_seconds")
        val RetryNotBeforeKey = longPreferencesKey("listenbrainz_retry_not_before")
        val LastSubmittedKey = stringPreferencesKey("listenbrainz_last_submitted")
        val EndpointKey = stringPreferencesKey("scrobbling_endpoint")
        val AllowHttpKey = booleanPreferencesKey("scrobbling_allow_http")
        val SplitArtistsKey = booleanPreferencesKey("scrobbling_split_artists")
    }
}
