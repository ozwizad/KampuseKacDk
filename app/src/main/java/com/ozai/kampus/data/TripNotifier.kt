package com.ozai.kampus.data

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.ozai.kampus.MainActivity
import com.ozai.kampus.R

/** Ongoing lock-screen notification with a countdown to the next checkpoint. */
object TripNotifier {
    private const val CHANNEL = "trip"
    private const val ID = 1

    private fun allowed(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun update(ctx: Context, title: String, text: String, targetEpochMs: Long) {
        try {
            if (!allowed(ctx)) return
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Yolculuk", NotificationManager.IMPORTANCE_LOW)
                )
            }
            val open = PendingIntent.getActivity(
                ctx, 0,
                Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setWhen(targetEpochMs)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .build()
            nm.notify(ID, n)
        } catch (e: Exception) {
            // Notifications are a convenience; never break the trip because of them.
        }
    }

    fun cancel(ctx: Context) {
        try {
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(ID)
        } catch (e: Exception) {
        }
    }
}
