package com.walzengroup.viennadepart.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Refreshes the bundled transit data (`haltepunkte.csv`, `line_routes.csv`) about once
 * a week in the background, so route/stop data stays current without the user opening
 * Settings. [schedule] is idempotent (KEEP) — safe to call on every app start.
 */
class TransitRefreshWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result =
        TransitData.refresh(applicationContext).fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )

    companion object {
        private const val NAME = "transit-refresh-weekly"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<TransitRefreshWorker>(7, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
