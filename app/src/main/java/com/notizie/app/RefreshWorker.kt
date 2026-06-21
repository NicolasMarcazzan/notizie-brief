package com.notizie.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

class RefreshWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("notizie", Context.MODE_PRIVATE)
        val workerUrl = prefs.getString("worker_url", null) ?: return Result.failure()

        val brief = DataFetcher.fetchBrief(workerUrl)
        if (brief != null) {
            prefs.edit().putString("cached_brief", Json.encodeToString(brief)).apply()
            return Result.success()
        }
        return Result.retry()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "daily_brief_refresh",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
