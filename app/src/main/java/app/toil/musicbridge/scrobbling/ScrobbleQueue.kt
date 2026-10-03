package app.toil.musicbridge.scrobbling

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

class ScrobbleQueue(context: Context) {
    private val workManager = WorkManager.getInstance(context)

    fun enqueue(listen: ScrobbleListen): Boolean = runCatching {
        val work = OneTimeWorkRequestBuilder<ScrobbleWorker>()
            .setInputData(workDataOf(
                ScrobbleWorker.ACCOUNT_ID to listen.accountId,
                ScrobbleWorker.PAYLOAD to ListenBrainzClient.payload(listen),
                ScrobbleWorker.TRACK_TITLE to listen.track.title.take(256),
            ))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG)
            .addTag(accountTag(listen.accountId))
            .build()
        workManager.enqueueUniqueWork("listenbrainz_${listen.id}", ExistingWorkPolicy.KEEP, work)
        true
    }.onFailure { Log.e("MBridge-Scrobbling", "Unable to enqueue listen", it) }.getOrDefault(false)

    fun cancelAccount(accountId: String) {
        workManager.cancelAllWorkByTag(accountTag(accountId))
    }

    companion object {
        const val TAG = "listenbrainz_scrobbles"
        fun accountTag(accountId: String) = "listenbrainz_account_$accountId"
    }
}
