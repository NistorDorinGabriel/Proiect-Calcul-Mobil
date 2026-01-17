package com.example.utilitiestracker.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.utilitiestracker.R
import com.example.utilitiestracker.data.local.AppDatabase
import com.example.utilitiestracker.data.repository.TariffsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TariffSyncService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SYNC -> startSync()
            ACTION_STOP_SYNC -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startSync() {
        createNotificationChannel()

        // IMPORTANT: Foreground service trebuie să afișeze notificare imediat
        startForeground(
            NOTIF_ID,
            buildNotification("Se actualizează tarifele…")
        )

        scope.launch {
            val repo = TariffsRepository(AppDatabase.getInstance(this@TariffSyncService))
            val err = repo.refreshFromInternet()
            val ts = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
            val msg = if (err == null) "Tarife actualizate (Eurostat/BNR) – $ts."
            else "Eroare la update: $err"

            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIF_ID, buildNotification(msg))

            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }
    }

    private fun buildNotification(text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // simplu; îl schimbăm ulterior cu un icon dedicat
            .setContentTitle("Utilities Tracker")
            .setContentText(text)
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tariff Sync",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START_SYNC = "com.example.utilitiestracker.action.START_TARIFF_SYNC"
        const val ACTION_STOP_SYNC = "com.example.utilitiestracker.action.STOP_TARIFF_SYNC"

        private const val CHANNEL_ID = "tariff_sync_channel"
        private const val NOTIF_ID = 1001
    }
}
