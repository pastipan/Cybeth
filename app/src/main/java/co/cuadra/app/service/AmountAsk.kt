package co.cuadra.app.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import co.cuadra.app.R
import co.cuadra.app.data.Repo
import co.cuadra.app.data.Tx
import co.cuadra.app.ui.MainActivity

/**
 * Nequi no dice el monto cuando TÚ envías plata. Cybeth te lo pregunta con una notificación
 * donde escribes el valor sin abrir la app. Solo después de confirmarlo sale el correo a la jefe.
 */
object AmountAsk {
    private const val CHANNEL = "monto"
    const val KEY = "monto"
    const val EXTRA_ID = "txId"

    private fun notifId(txId: Long) = 70_000 + (txId % 10_000).toInt()

    fun ask(ctx: Context, t: Tx) {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "Confirmar monto de envíos", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "Pregunta cuánto enviaste cuando Nequi no lo dice"
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val input = RemoteInput.Builder(KEY).setLabel("Monto, ej. 50000").build()
        val replyIntent = Intent(ctx, AmountReplyReceiver::class.java).putExtra(EXTRA_ID, t.id)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        val replyPi = PendingIntent.getBroadcast(ctx, notifId(t.id), replyIntent, flags)
        val action = NotificationCompat.Action.Builder(R.drawable.ic_stat_cuadra, "Escribir monto", replyPi)
            .addRemoteInput(input).setAllowGeneratedReplies(false).build()
        val openPi = PendingIntent.getActivity(
            ctx, notifId(t.id), Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_cuadra)
            .setContentTitle("Enviaste plata por ${t.bank ?: "Nequi"}. ¿Cuánto fue?")
            .setContentText("Escribe el monto para registrarlo y enviarle el aviso a la jefe.")
            .setColor(0xFFE59A12.toInt())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setContentIntent(openPi)
            .addAction(action)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(notifId(t.id), n) } catch (_: SecurityException) { }
    }

    fun clear(ctx: Context, txId: Long) {
        try { NotificationManagerCompat.from(ctx).cancel(notifId(txId)) } catch (_: Exception) { }
    }

    /** "50.000", "$50000", "50 mil" → 50000 */
    fun parseTyped(s: String): Long? {
        val t = s.lowercase().trim()
        val mil = t.contains("mil")
        val digits = t.replace(Regex("[^0-9]"), "")
        val v = digits.toLongOrNull() ?: return null
        val r = if (mil && v < 100_000) v * 1000 else v
        return if (r > 0) r else null
    }

    /** Guardar el monto y, ya confirmado, mandar el correo a la jefe. */
    fun confirm(ctx: Context, txId: Long, amount: Long): Tx? {
        Repo.init(ctx)
        val before = Repo.txs.value.firstOrNull { it.id == txId } ?: return null
        val t = Repo.updateAmount(txId, amount) ?: return null
        clear(ctx, txId)
        if (before.amount == 0L) ForwardMail.enqueue(ctx, t)
        return t
    }
}

class AmountReplyReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getLongExtra(AmountAsk.EXTRA_ID, -1)
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(AmountAsk.KEY)?.toString() ?: return
        val amount = AmountAsk.parseTyped(text)
        if (id < 0 || amount == null) return
        AmountAsk.confirm(ctx, id, amount)
    }
}
