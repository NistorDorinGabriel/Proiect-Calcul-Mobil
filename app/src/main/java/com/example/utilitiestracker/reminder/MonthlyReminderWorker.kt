package com.example.utilitiestracker.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.utilitiestracker.R
import com.example.utilitiestracker.ReadingsActivity
import com.example.utilitiestracker.data.local.AppDatabase
import java.time.LocalDate
import java.time.YearMonth
import androidx.core.content.edit

class MonthlyReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(KEY_ENABLED, true)
        if (!enabled) return Result.success()

        val now = LocalDate.now()
        val ym = YearMonth.from(now)

        // evităm spam: notificăm o singură dată / lună
        val lastNotified = prefs.getString(KEY_LAST_NOTIFIED, null)
        if (lastNotified == ym.toString()) return Result.success()

        val db = AppDatabase.getInstance(applicationContext)

        val start = ym.atDay(1).toEpochDay()
        val end = ym.atEndOfMonth().toEpochDay()

        val metersTotal = db.meterDao().count()
        val metersWithReading = db.readingDao().countMetersWithReadingInRange(start, end)

        // dacă nu au toate utilitățile măcar o citire luna aceasta -> remind
        if (metersWithReading < metersTotal) {
            showNotification()
            prefs.edit { putString(KEY_LAST_NOTIFIED, ym.toString()) }
        }

        return Result.success()
    }

    private fun showNotification() {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Utilities reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        nm.createNotificationChannel(channel)

        val intent = Intent(applicationContext, ReadingsActivity::class.java)
        val pi = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (PendingIntent.FLAG_IMMUTABLE)
        )

        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Utilities Tracker")
            .setContentText("E timpul să introduci citirile la utilități pentru luna aceasta.")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIF_ID, notif)
    }

    companion object {
        private const val CHANNEL_ID = "utilities_reminders"
        private const val NOTIF_ID = 3001

        private const val PREFS = "reminder_prefs"
        const val KEY_ENABLED = "enabled"
        private const val KEY_LAST_NOTIFIED = "last_notified_ym"
    }
}
