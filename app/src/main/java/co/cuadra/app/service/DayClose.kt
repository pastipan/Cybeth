package co.cuadra.app.service

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import co.cuadra.app.data.Prefs
import co.cuadra.app.data.Repo
import co.cuadra.app.ui.DayReport
import co.cuadra.app.ui.startOfDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * "Finalizar día": arma el resumen del día (totales, por cuenta y cada movimiento) y lo manda
 * por correo con el archivo para Excel adjunto. Se puede hacer con el botón o solo a una hora fija.
 */
object DayClose {

    data class Result(val ok: Boolean, val message: String)

    /** Envía el cierre del día que empieza en [dayStart] (por defecto, hoy). */
    suspend fun send(ctx: Context, dayStart: Long = startOfDay()): Result = withContext(Dispatchers.IO) {
        Repo.init(ctx)
        val prefs = Prefs(ctx)
        val from = prefs.email
        val pass = prefs.emailPassword
        if (from.isNullOrBlank() || pass.isNullOrBlank()) return@withContext Result(false, "Primero enlaza el Gmail que envía los avisos (Ajustes → Correo electrónico)")
        // Va a los correos de "Avisar por correo" (la jefe). Si no hay ninguno, al mismo Gmail de la empresa.
        val to = prefs.forwardList.ifEmpty { listOf(from) }
        val r = DayReport(Repo.txs.value, dayStart)
        val res = try {
            ForwardMail.send(
                from, pass, to, r.subject(), r.html(), r.plain(),
                ForwardMail.Attachment(r.fileName, r.csv(), "text/csv; charset=UTF-8")
            )
            Result(true, "Cierre enviado a ${to.joinToString(", ")}")
        } catch (e: Throwable) {
            Result(false, ForwardMail.explain(e))
        }
        if (res.ok) prefs.lastCloseAt = System.currentTimeMillis()
        prefs.lastCloseResult = res.message
        Announcer.notifyEmail(ctx, res.ok, if (res.ok) "Cierre del día enviado" else "No se pudo enviar el cierre", if (res.ok) "${r.subject()}\n${res.message}" else res.message)
        res
    }

    private const val NAME = "cuadra-cierre"

    /** Programa el próximo cierre automático (hoy a la hora elegida, o mañana si ya pasó). */
    fun schedule(ctx: Context, replace: Boolean = true) {
        val prefs = Prefs(ctx)
        if (!prefs.autoClose) { cancel(ctx); return }
        val now = System.currentTimeMillis()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, prefs.closeHour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now + 60_000L) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        val req = OneTimeWorkRequestBuilder<DayCloseWorker>()
            .setInitialDelay(next - now, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(NAME, if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, req)
    }

    fun cancel(ctx: Context) = WorkManager.getInstance(ctx).cancelUniqueWork(NAME)

    /** Hora en texto: 21 → "9:00 p. m." */
    fun hourLabel(h: Int): String {
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "$h12:00 " + if (h < 12) "a. m." else "p. m."
    }
}

class DayCloseWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        // Si se atrasó y ya pasó la medianoche, se cierra el día anterior
        val hourNow = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val day = if (hourNow < 4) startOfDay(daysAgo = 1) else startOfDay()
        val r = DayClose.send(ctx, day)
        return if (r.ok || runAttemptCount >= 5) {
            DayClose.schedule(ctx) // dejar listo el de mañana
            Result.success()
        } else Result.retry()
    }
}
