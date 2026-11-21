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

    private var ttsCreatedAt = 0L

    /** Crea la voz. Si Android la apagó o falló al iniciar, se vuelve a crear sola. */
    private fun ensureTts(ctx: Context) {
        val stuck = tts != null && !ready && System.currentTimeMillis() - ttsCreatedAt > 10_000L
        if (tts != null && !stuck) return
        try { tts?.shutdown() } catch (_: Exception) { }
        ready = false
        ttsCreatedAt = System.currentTimeMillis()
        // Si en 8 segundos la voz no arrancó, sonar el pitido para no perder el aviso
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!ready && synchronized(pending) { pending.isNotEmpty() }) {
                log(ctx, "🔇 La voz no arrancó a tiempo: sonó el pitido")
                beep()
                synchronized(pending) { pending.clear() }
            }
        }, 8_000L)
        tts = TextToSpeech(ctx.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(Locale("es", "CO"))
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) tts?.setLanguage(Locale("es"))
                // Sonar por el volumen de MULTIMEDIA y como aviso de voz
                tts?.setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) { log(ctx, "🔊 Habló bien") }
                    @Deprecated("") override fun onError(id: String?) { log(ctx, "🔇 La voz falló: sonó el pitido"); beep() }
                    override fun onError(id: String?, code: Int) { log(ctx, "🔇 La voz falló (código $code): sonó el pitido"); beep() }
                })
                ready = true
                synchronized(pending) {
                    pending.forEach { speakNow(ctx, it) }
                    pending.clear()
                }
            } else {
                // Falló: pitido para que igual se entere, y se intentará de nuevo en el próximo aviso
                log(ctx, "🔇 El motor de voz no arrancó (estado $status): sonó el pitido")
                beep()
                tts = null
                ready = false
            }
        }
    }

    private fun speakNow(ctx: Context, text: String) {
        val r = try {
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "cuadra-${System.nanoTime()}") ?: TextToSpeech.ERROR
        } catch (_: Exception) { TextToSpeech.ERROR }
        if (r == TextToSpeech.ERROR) {
            // El motor de voz se murió (pasa cuando Android cierra procesos): reiniciarlo y repetir
            try { tts?.shutdown() } catch (_: Exception) { }
            tts = null; ready = false
            synchronized(pending) { pending += text }
            ensureTts(ctx)
        }
    }

    private fun log(ctx: Context, msg: String) {
        try {
            val h = java.text.SimpleDateFormat("h:mm a", Locale("es", "CO")).format(java.util.Date())
            Prefs(ctx).addNotifLog("$h · Voz · $msg")
        } catch (_: Exception) { }
    }

    /** Pitido de respaldo (dos tonos) por si la voz de Android no funciona. */
    fun beep() {
        try {
            val tg = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 100)
            tg.startTone(android.media.ToneGenerator.TONE_PROP_ACK, 600)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ tg.release() }, 1000)
        } catch (_: Exception) { }
    }

    fun speak(ctx: Context, text: String) {
        // Si el volumen multimedia está en 0, la voz "habla" pero no se oye: avisarlo en el diagnóstico
        try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            if (am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) == 0) log(ctx, "⚠️ El volumen MULTIMEDIA está en 0: súbelo para oír la voz")
        } catch (_: Exception) { }
        ensureTts(ctx)
        if (ready) speakNow(ctx, text) else synchronized(pending) { pending += text }
    }

    fun announce(ctx: Context, t: Tx) {
        val prefs = Prefs(ctx)
        // Ej: "Cristian Rosero te envió 35000 pesos. Entró a Nequi."
        val spoken = Wording.headline(t, spoken = true) + ". " + Wording.accountLine(t).substringBefore(" ****") + "."
        val wantVoice = if (t.direction == Direction.IN) prefs.voiceIn else prefs.voiceOut
        if (wantVoice) speak(ctx, spoken) else log(ctx, "🔇 No habló porque la voz está apagada en Ajustes → Avisos")
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
