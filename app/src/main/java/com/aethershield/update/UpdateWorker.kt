package com.aethershield.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.aethershield.R
import com.aethershield.ui.MainActivity
import java.util.concurrent.TimeUnit

/**
 * Checks GitHub for a newer release. WorkManager owns the timing: Android decides when a
 * periodic job may actually run, so this is a "check about once a day", not a guarantee.
 */
class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : Worker(appContext, params) {

    override fun doWork(): Result {
        val info = UpdateSource.latest() ?: return Result.success() // no network: try next time
        UpdateStore.save(applicationContext, info)

        val installed = runCatching {
            applicationContext.packageManager
                .getPackageInfo(applicationContext.packageName, 0).versionName
        }.getOrNull() ?: return Result.success()

        if (VersionMath.isNewer(info.version, installed)) {
            UpdateNotifier.notify(applicationContext, installed, info.version)
        }
        return Result.success()
    }
}

object UpdateScheduler {

    private const val PERIODIC_WORK = "aethershield-update-check"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(24, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
            .build()
        runCatching {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }

    /** The explicit "check now" a user can press; the result lands in [UpdateStore]. */
    fun checkNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<UpdateCheckWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        runCatching { WorkManager.getInstance(context).enqueue(request) }
    }
}

object UpdateNotifier {

    private const val CHANNEL = "updates"
    private const val ID = 42

    fun notify(context: Context, installed: String, latest: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return // the in-app update row still shows it
        }

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.updates_channel), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.update_available))
            .setContentText(context.getString(R.string.update_notification_body, installed, latest))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { manager.notify(ID, notification) }
    }
}
