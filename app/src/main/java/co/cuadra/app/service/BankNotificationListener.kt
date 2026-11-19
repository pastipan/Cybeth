package co.cuadra.app.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import co.cuadra.app.data.Prefs
import co.cuadra.app.data.Repo
import co.cuadra.app.data.TxParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Android le pasa a este servicio cada notificación que llega al celular.
 * Solo se miran las de apps de bancos (Nequi, Bancolombia, Daviplata, etc.) y los SMS
 * que vienen de un banco. Las demás (WhatsApp, redes, etc.) se ignoran por completo,
 * así nadie puede "engañar" a la app escribiendo "te envié $50.000" en un chat.
 */
class BankNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        Repo.init(applicationContext)
        Prefs(this).listenerConnectedAt = System.currentTimeMillis()
        // Revisar las notificaciones que ya están en la barra (ej. pagos que llegaron mientras
        // Cybeth estaba apagada o antes de actualizar). Las repetidas no se guardan dos veces.
        try {
            activeNotifications?.sortedBy { it.postTime }?.forEach { sbn ->
                try { handle(sbn) } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
    }

    override fun onListenerDisconnected() {
        Prefs(this).listenerDisconnectedAt = System.currentTimeMillis()
        // Pedirle a Android que lo vuelva a conectar
        try {
            requestRebind(android.content.ComponentName(this, BankNotificationListener::class.java))
        } catch (_: Exception) { }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            handle(sbn)
        } catch (_: Exception) {
            // Nunca dejar que una notificación rara cierre el servicio
        }
    }

    private fun handle(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return
        val n = sbn.notification ?: return

        val label = appLabel(pkg)

        // Correos de bancos que llegan a Gmail / Outlook / Hotmail en ESTE celular:
        // se leen directo de la notificación (al instante, como Fortu), sin necesitar contraseña.
        val isEmailApp = isEmailApp(pkg)
        var bankFromEmail: String? = null
        if (isEmailApp) {
            val ex0 = n.extras
            val peek = listOf(
                ex0.getCharSequence(Notification.EXTRA_TITLE), ex0.getCharSequence(Notification.EXTRA_TEXT),
                ex0.getCharSequence(Notification.EXTRA_BIG_TEXT), ex0.getCharSequence(Notification.EXTRA_SUB_TEXT)
            ).joinToString(" ") { it?.toString().orEmpty() }
            bankFromEmail = TxParser.detectBank(peek) ?: return
            // Y si hay un correo enlazado, también se revisa completo por si la notificación venía cortada
            if (Prefs(this).emailLinked) checkMailSoon()
        }

        val isSms = isSmsApp(pkg)
        val bankFromApp = bankFromEmail ?: TxParser.detectBank(label) ?: TxParser.detectBank(pkg)
        if (bankFromApp == null && !isSms) return

        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val lines = ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n").orEmpty()
        // Algunas apps ponen el texto en "mensajes" (estilo chat) o en el resumen
        val msgs = try {
            @Suppress("DEPRECATION")
            ex.getParcelableArray(Notification.EXTRA_MESSAGES)?.mapNotNull { (it as? android.os.Bundle)?.getCharSequence("text")?.toString() }
                ?.joinToString("\n").orEmpty()
        } catch (_: Exception) { "" }
        val extra = listOfNotNull(
            ex.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            ex.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString(),
            n.tickerText?.toString(),
        ).joinToString("\n")
        val body = listOf(title, if (big.length > text.length) big else text, lines, msgs, extra)
            .filter { it.isNotBlank() }.distinct().joinToString("\n")
        if (body.isBlank()) return

        // En SMS, el banco debe aparecer en el remitente o en el mensaje
        val bank = bankFromApp ?: TxParser.detectBank(title) ?: TxParser.detectBank(body) ?: return

        val prefs = Prefs(this)
        val hora = SimpleDateFormat("d MMM h:mm a", Locale("es", "CO")).format(Date())
        prefs.lastBankNotif = "$label · $hora"

        val hhmm = SimpleDateFormat("h:mm a", Locale("es", "CO")).format(Date())
        val oneLine = body.replace("\n", " · ")
        // Directo de la app del banco: más flexible. SMS y correos: estricto.
        val parsed = if (!isSms && !isEmailApp) TxParser.parseFromBankApp(body, bank) else TxParser.parse(body, bank)
        if (parsed == null) {
            prefs.addNotifLog("$hhmm · $label · ✗ Ignorada: ${TxParser.whyIgnored(body)}\n$oneLine")
            return
        }
        val saved = Repo.insertIfNew(
            parsed,
            source = if (isSms) "SMS" else if (isEmailApp) "Aviso de correo" else "Notificación",
            raw = "[$label] $body",
            time = sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis()
        )
        if (saved == null) {
            prefs.addNotifLog("$hhmm · $label · = Repetida (ya estaba guardada)\n$oneLine")
            return
        }
        prefs.addNotifLog("$hhmm · $label · ✓ ${if (saved.updated) "Estado actualizado" else "Guardada"}: ${co.cuadra.app.ui.Wording.headline(saved)}\n$oneLine")
        Announcer.announce(this, saved)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var lastMailCheck = 0L

    private fun checkMailSoon() {
        val now = System.currentTimeMillis()
        if (now - lastMailCheck < 8_000) return
        lastMailCheck = now
        scope.launch {
            delay(2_000) // darle un momento a Gmail para que el correo ya esté en el servidor
            MailChecker.check(applicationContext)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun appLabel(pkg: String): String = try {
        val ai = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(ai).toString()
    } catch (_: Exception) {
        pkg
    }

    private fun isEmailApp(pkg: String): Boolean {
        val p = pkg.lowercase()
        return p == "com.google.android.gm" || p.contains("outlook") || p.contains("hotmail") ||
            p.contains(".email") || p.contains("yahoo.mobile.client.android.mail") || p.contains("protonmail") ||
            p == "com.microsoft.office.outlook"
    }

    private fun isSmsApp(pkg: String): Boolean {
        val p = pkg.lowercase()
        return p.contains("messaging") || p.contains(".mms") || p.contains(".sms") ||
            p == "com.android.messaging" || p == "com.google.android.apps.messaging" ||
            p == "com.samsung.android.messaging" || p.contains("textra")
    }
}
