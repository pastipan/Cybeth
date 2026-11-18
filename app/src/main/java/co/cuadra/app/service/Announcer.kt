package co.cuadra.app.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import co.cuadra.app.R
import co.cuadra.app.data.Direction
import co.cuadra.app.data.Prefs
import co.cuadra.app.data.Tx
import co.cuadra.app.ui.MainActivity
import co.cuadra.app.ui.money
import java.util.Locale

/** Avisa cuando entra (o sale) plata: en voz alta y con una notificación propia. */
object Announcer {
    private const val CHANNEL = "transferencias"
    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = mutableListOf<String>()

    fun init(ctx: Context) {
        val app = ctx.applicationContext
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "Transferencias", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "Aviso cuando te llega o envías plata"
            app.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun ensureTts(ctx: Context) {
        if (tts != null) return
        tts = TextToSpeech(ctx.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val co = Locale("es", "CO")
                val r = tts?.setLanguage(co)
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale("es"))
                }
                ready = true
                synchronized(pending) {
                    pending.forEach { speakNow(it) }
                    pending.clear()
                }
            }
        }
    }

    private fun speakNow(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "cuadra-${System.nanoTime()}")
    }

    fun speak(ctx: Context, text: String) {
        ensureTts(ctx)
        if (ready) speakNow(text) else synchronized(pending) { pending += text }
    }

    fun announce(ctx: Context, t: Tx) {
        val prefs = Prefs(ctx)
        val who = t.counterparty
        val bank = t.bank
        val spoken = if (t.direction == Direction.IN) {
            buildString {
                append("Te llegaron ${t.amount} pesos")
                if (who != null) append(" de $who")
                if (bank != null) append(" por $bank")
            }
        } else {
            buildString {
                append("Enviaste ${t.amount} pesos")
                if (who != null) append(" a $who")
                if (bank != null) append(" desde $bank")
            }
        }
        val wantVoice = if (t.direction == Direction.IN) prefs.voiceIn else prefs.voiceOut
        if (wantVoice) speak(ctx, spoken)
        if (prefs.notify) notify(ctx, t)
        // Aviso por correo (por ejemplo a la jefe, que tiene iPhone)
        ForwardMail.enqueue(ctx, t)
    }

    private fun notify(ctx: Context, t: Tx) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val isIn = t.direction == Direction.IN
        val title = if (isIn) "↙ Te llegó ${money(t.amount)}" else "↗ Enviaste ${money(t.amount)}"
        val body = listOfNotNull(
            t.counterparty?.let { if (isIn) "De $it" else "A $it" },
            t.bank,
            t.last4?.let { "****$it" }
        ).joinToString(" · ").ifBlank { "Transferencia detectada" }
        val pi = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_cuadra)
            .setContentTitle(title)
            .setContentText(body)
            .setColor(if (isIn) 0xFF0FB5A3.toInt() else 0xFFFF8F8F.toInt())
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // Con el celular bloqueado no se muestra el monto, solo "Nueva transferencia"
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(ctx, CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat_cuadra)
                    .setContentTitle(if (isIn) "Nueva transferencia recibida" else "Nueva transferencia enviada")
                    .setContentText("Abre Cybeth para ver el detalle")
                    .build()
            )
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify((t.id % Int.MAX_VALUE).toInt(), n)
        } catch (_: SecurityException) { }
    }
}
