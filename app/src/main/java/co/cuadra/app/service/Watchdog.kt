package co.cuadra.app.service

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import co.cuadra.app.data.Prefs
import java.util.concurrent.TimeUnit

/**
 * Vigilante: Android a veces desconecta el lector de notificaciones (al actualizar la app,
 * al reiniciar el celular o para ahorrar batería) y NO lo vuelve a conectar solo.
 * Esto le pide que lo reconecte: al abrir la app, al prender el celular, después de
 * cada actualización y cada 15 minutos.
 */
object Watchdog {
    fun rebind(ctx: Context) {
        try {
            if (NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)) {
                NotificationListenerService.requestRebind(ComponentName(ctx, BankNotificationListener::class.java))
            }
        } catch (_: Exception) { }
    }

    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("cuadra-vigilante", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** ¿El lector parece caído? (con permiso, pero desconectado o nunca conectado) */
    fun looksDown(ctx: Context): Boolean {
        val p = Prefs(ctx)
        val enabled = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
        return enabled && (p.listenerConnectedAt == 0L || p.listenerDisconnectedAt > p.listenerConnectedAt)
    }
}

class WatchdogWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Watchdog.rebind(applicationContext)
        return Result.success()
    }
}

/** Se ejecuta al prender el celular y justo después de instalar una actualización de Cybeth. */
class StartReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Watchdog.rebind(ctx)
        Watchdog.schedule(ctx)
        DayClose.schedule(ctx, replace = false)
        if (Prefs(ctx).emailLinked) MailWorker.schedule(ctx)
    }
}
