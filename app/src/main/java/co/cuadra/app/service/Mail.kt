package co.cuadra.app.service

import android.content.Context
import androidx.core.text.HtmlCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import co.cuadra.app.data.Prefs
import co.cuadra.app.data.Repo
import co.cuadra.app.data.TxParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Properties
import java.util.concurrent.TimeUnit
import javax.mail.AuthenticationFailedException
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.internet.MimeMessage
import javax.mail.search.ComparisonTerm
import javax.mail.search.ReceivedDateTerm

/**
 * Lee el correo de Gmail por IMAP (solo lectura) y saca las transferencias de los
 * correos de los bancos. Usa una "contraseña de aplicación" de Google, no tu clave normal.
 */
object MailChecker {

    data class Result(val ok: Boolean, val message: String, val newTx: Int = 0)

    private fun hostFor(email: String): String {
        val d = email.substringAfter('@').lowercase()
        return when {
            d.contains("gmail") || d.contains("googlemail") -> "imap.gmail.com"
            d.contains("outlook") || d.contains("hotmail") || d.contains("live") -> "outlook.office365.com"
            d.contains("yahoo") -> "imap.mail.yahoo.com"
            else -> "imap.gmail.com" // Google Workspace con dominio propio
        }
    }

    private fun connect(email: String, password: String): javax.mail.Store {
        val props = Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.ssl.enable", "true")
            put("mail.imaps.connectiontimeout", "20000")
            put("mail.imaps.timeout", "30000")
        }
        val store = Session.getInstance(props).getStore("imaps")
        store.connect(hostFor(email), 993, email, password.replace(" ", ""))
        return store
    }

    /** Solo prueba que el correo y la contraseña de aplicación funcionen. */
    suspend fun test(email: String, password: String): Result = withContext(Dispatchers.IO) {
        try {
            connect(email, password).close()
            Result(true, "Conectado")
        } catch (e: AuthenticationFailedException) {
            Result(false, "Correo o contraseña de aplicación incorrectos")
        } catch (e: Exception) {
            Result(false, "No se pudo conectar: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** Revisa correos nuevos de bancos y guarda las transferencias. */
    suspend fun check(ctx: Context): Result = withContext(Dispatchers.IO) {
        val prefs = Prefs(ctx)
        val email = prefs.email
        val pass = prefs.emailPassword
        if (email.isNullOrBlank() || pass.isNullOrBlank()) return@withContext Result(false, "Correo sin enlazar")
        Repo.init(ctx)

        var newCount = 0
        val result = try {
            val store = connect(email, pass)
            try {
                val inbox = store.getFolder("INBOX")
                inbox.open(Folder.READ_ONLY)
                // La primera vez trae los últimos 3 días; después, desde la última revisión (con margen).
                val since = if (prefs.lastEmailCheck == 0L) System.currentTimeMillis() - 3 * DAY
                else prefs.lastEmailCheck - DAY
                val msgs: Array<Message> = inbox.search(ReceivedDateTerm(ComparisonTerm.GE, Date(since)))
                for (m in msgs) {
                    val from = m.from?.joinToString { it.toString() }.orEmpty()
                    val subject = m.subject.orEmpty()
                    val bank = TxParser.detectBank(from) ?: TxParser.detectBank(subject) ?: continue
                    val mid = (m as? MimeMessage)?.messageID ?: "$from|$subject|${m.receivedDate?.time}"
                    if (Repo.emailSeen(mid)) continue
                    val body = textOf(m).take(6000)
                    Repo.markEmailSeen(mid)
                    val parsed = TxParser.parse("$subject\n$body", bank) ?: continue
                    val time = m.receivedDate?.time ?: System.currentTimeMillis()
                    val saved = Repo.insertIfNew(parsed, "Correo", "[$from] $subject\n$body", time)
                    if (saved != null) {
                        newCount++
                        // Solo anunciar en voz alta si el correo es reciente (no al importar los de días pasados)
                        if (System.currentTimeMillis() - time < 30 * 60 * 1000L) Announcer.announce(ctx, saved)
                    }
                }
                inbox.close(false)
            } finally {
                try { store.close() } catch (_: Exception) {}
            }
            Result(true, if (newCount == 0) "Sin transferencias nuevas" else "$newCount transferencia(s) nueva(s)", newCount)
        } catch (e: AuthenticationFailedException) {
            Result(false, "Google rechazó la contraseña de aplicación")
        } catch (e: Exception) {
            Result(false, "Error: ${e.message ?: e.javaClass.simpleName}")
        }
        if (result.ok) prefs.lastEmailCheck = System.currentTimeMillis()
        prefs.lastEmailResult = result.message
        result
    }

    private fun textOf(p: Part): String = try {
        when {
            p.isMimeType("text/plain") -> p.content?.toString().orEmpty()
            p.isMimeType("text/html") -> HtmlCompat.fromHtml(p.content?.toString().orEmpty(), HtmlCompat.FROM_HTML_MODE_LEGACY)
                .toString().replace('￼', ' ')
            p.isMimeType("multipart/*") -> {
                val mp = p.content as Multipart
                val parts = (0 until mp.count).map { mp.getBodyPart(it) }
                // Preferir texto plano; si no hay, HTML
                parts.firstOrNull { it.isMimeType("text/plain") }?.let { textOf(it) }
                    ?: parts.joinToString("\n") { textOf(it) }
            }
            else -> ""
        }
    } catch (_: Exception) {
        ""
    }

    private const val DAY = 24 * 60 * 60 * 1000L
}

/** Revisa el correo cada 15 minutos (lo mínimo que permite Android) aunque la app esté cerrada. */
class MailWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val r = MailChecker.check(applicationContext)
        return if (r.ok || r.message.contains("contraseña")) Result.success() else Result.retry()
    }

    companion object {
        private const val NAME = "cuadra-mail"

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<MailWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun cancel(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork(NAME)
        }
    }
}
