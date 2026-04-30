package com.affiliatemonitor.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.affiliatemonitor.app.data.Prefs
import com.affiliatemonitor.app.data.scanner.Scanner
import java.util.concurrent.TimeUnit

class ScanWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        Scanner(applicationContext).scanAll()
        Result.success()
    } catch (t: Throwable) {
        Result.retry()
    }

    companion object {
        const val UNIQUE_NAME = "apm_periodic_scan"

        suspend fun schedule(context: Context) {
            val intervalMin = Prefs.scanIntervalMinValue(context).coerceAtLeast(15).toLong()
            val req = PeriodicWorkRequestBuilder<ScanWorker>(intervalMin, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                req,
            )
        }
    }
}
