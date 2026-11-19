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
import co.cuadra.app.ui.Wording
import co.cuadra.app.ui.money
import java.util.Locale

/** Avisa cuando entra (o sale) plata: en voz alta y con una notificación propia. */
object Announcer {
    private const val CHANNEL = "transferencias"
    private const val CHANNEL_MAIL = "correos"
    private var mailNotifId = 50_000
    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = mutableListOf<String>()

    fun init(ctx: Context) {
        val app = ctx.applicationContext
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "Transferencias", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "Aviso cuando te llega o envías plata"
            app.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
            val ch2 = NotificationChannel(CHANNEL_MAIL, "Correos enviados", NotificationManager.IMPORTANCE_DEFAULT)
            ch2.description = "Confirma cuando se envía (o falla) un correo a la jefe"
            app.getSystemService(NotificationManager::class.java).createNotificationChannel(ch2)
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
        // Ej: "Cristian Rosero te envió 35000 pesos. Entró a Nequi."
        val spoken = Wording.headline(t, spoken = true) + ". " + Wording.accountLine(t).substringBefore(" ****") + "."
        val wantVoice = if (t.direction == Direction.IN) prefs.voiceIn else prefs.voiceOut
        if (wantVoice) speak(ctx, spoken)
        if (prefs.notify) notify(ctx, t)
        // Aviso por correo (por ejemplo a la jefe, que tiene iPhone)
        ForwardMail.enqueue(ctx, t)
    }

    /**
     * Aviso en el celular de que el correo SÍ salió (o de por qué no salió),
     * para no tener que adivinar si a la jefe le llegó.
     */
    fun notifyEmail(ctx: Context, ok: Boolean, title: String, body: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val pi = PendingIntent.getActivity(
            ctx, 1, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL_MAIL)
            .setSmallIcon(R.drawable.ic_stat_cuadra)
            .setContentTitle((if (ok) "✉️ " else "⚠️ ") + title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setColor(if (ok) 0xFF0FB5A3.toInt() else 0xFFE59A12.toInt())
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(mailNotifId++, n)
        } catch (_: SecurityException) { }
    }

    private fun notify(ctx: Context, t: Tx) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val isIn = t.direction == Direction.IN
        val title = Wording.headline(t)
        val body = Wording.accountLine(t) + " · " + Wording.statusLabel(t)
        val pi = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_cuadra)
            .setContentTitle(title)
            .setContentText(body)
            .setColor(if (t.status == co.cuadra.app.data.TxStatus.RECHAZADA) 0xFFE25555.toInt() else if (isIn) 0xFF0FB5A3.toInt() else 0xFFFF8F8F.toInt())
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
