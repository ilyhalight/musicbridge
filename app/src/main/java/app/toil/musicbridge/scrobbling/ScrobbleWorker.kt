package app.toil.musicbridge.scrobbling

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.toil.musicbridge.MusicBridgeApplication
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class ScrobbleWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val repository = (applicationContext as MusicBridgeApplication).settings
        val accountId = inputData.getString(ACCOUNT_ID) ?: return@withContext Result.failure()
        val payload = inputData.getString(PAYLOAD) ?: return@withContext Result.failure()
        val settings = repository.scrobbling.first()
        if (!settings.enabled || settings.accountId != accountId) return@withContext Result.success()
        if (settings.authFailed || settings.retryNotBeforeMs > System.currentTimeMillis()) return@withContext Result.retry()
        val credentials = repository.credentials()
        if (credentials == null) {
            repository.markAuthFailed(accountId)
            return@withContext Result.retry()
        }
        if (credentials.accountId != accountId) return@withContext Result.success()
        try {
            val client = ListenBrainzClient(ListenBrainzHttpTransport(credentials.endpoint, credentials.allowHttp))
            val response = client.submit(credentials.token, payload)
            when (submissionDecision(response.code)) {
                SubmissionDecision.Accepted -> {
                    repository.markSubmitted(accountId, inputData.getString(TRACK_TITLE).orEmpty())
                    Result.success()
                }
                SubmissionDecision.Unauthorized -> {
                    repository.markAuthFailed(accountId)
                    Result.retry()
                }
                SubmissionDecision.Retry -> {
                    if (response.code == 429) {
                        val now = System.currentTimeMillis()
                        repository.postponeSubmissions(accountId, now + retryDelayMs(response.retryAfter, now))
                    }
                    Result.retry()
                }
                SubmissionDecision.Rejected -> Result.failure(workDataOf("http_status" to response.code))
            }
        } catch (_: IOException) {
            Result.retry()
        } catch (_: IllegalArgumentException) {
            Result.failure()
        }
    }

    companion object {
        const val ACCOUNT_ID = "account_id"
        const val PAYLOAD = "payload"
        const val TRACK_TITLE = "track_title"
    }
}
