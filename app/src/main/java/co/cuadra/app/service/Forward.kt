package co.cuadra.app.service

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import co.cuadra.app.data.Direction
import co.cuadra.app.data.Prefs
import co.cuadra.app.data.Repo
import co.cuadra.app.data.Tx
import co.cuadra.app.ui.money
import co.cuadra.app.ui.whenText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Properties
import java.util.concurrent.TimeUnit
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.activation.CommandMap
import javax.activation.MailcapCommandMap
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

/**
 * Manda un correo (por ejemplo a la jefe, que tiene iPhone) cada vez que se detecta
 * una transferencia. Sale desde el Gmail enlazado en la app, usando la misma
 * contraseña de aplicación.
 */
object ForwardMail {

    data class Result(val ok: Boolean, val message: String)

    private fun smtpHost(email: String): String {
        val d = email.substringAfter('@').lowercase()
        return when {
            d.contains("outlook") || d.contains("hotmail") || d.contains("live") -> "smtp.office365.com"
            d.contains("yahoo") -> "smtp.mail.yahoo.com"
            else -> "smtp.gmail.com"
        }
    }

    /**
     * En Android, la librería de correo a veces no encuentra cómo "armar" un mensaje HTML
     * (error "no object DCH for MIME type text/html"). Esto le registra los tipos a mano.
     */
    fun fixMailOnAndroid() {
        Thread.currentThread().contextClassLoader = ForwardMail::class.java.classLoader
        val mc = CommandMap.getDefaultCommandMap() as? MailcapCommandMap ?: MailcapCommandMap()
        mc.addMailcap("text/html;; x-java-content-handler=com.sun.mail.handlers.text_html")
        mc.addMailcap("text/xml;; x-java-content-handler=com.sun.mail.handlers.text_xml")
        mc.addMailcap("text/plain;; x-java-content-handler=com.sun.mail.handlers.text_plain")
        mc.addMailcap("multipart/*;; x-java-content-handler=com.sun.mail.handlers.multipart_mixed; x-java-fallback-entry=true")
        mc.addMailcap("message/rfc822;; x-java-content-handler=com.sun.mail.handlers.message_rfc822")
        CommandMap.setDefaultCommandMap(mc)
    }

    private fun send(from: String, pass: String, to: List<String>, subject: String, html: String, plain: String) {
        fixMailOnAndroid()
        val host = smtpHost(from)
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.host", host)
            put("mail.smtp.connectiontimeout", "20000")
            put("mail.smtp.timeout", "30000")
            if (host == "smtp.gmail.com") {
                put("mail.smtp.port", "465")
                put("mail.smtp.ssl.enable", "true")
            } else {
                put("mail.smtp.port", "587")
                put("mail.smtp.starttls.enable", "true")
            }
        }
        val session = Session.getInstance(props, object : javax.mail.Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication(from, pass.replace(" ", ""))
        })
        val msg = MimeMessage(session).apply {
            setFrom(InternetAddress(from, "Cybeth SG", "UTF-8"))
            setRecipients(Message.RecipientType.TO, to.map { InternetAddress(it) }.toTypedArray())
            setSubject(subject, "UTF-8")
            // Texto simple + versión bonita (HTML): si el correo del jefe no muestra HTML, ve el texto
            val alt = MimeMultipart("alternative")
            alt.addBodyPart(MimeBodyPart().apply { setText(plain, "UTF-8", "plain") })
            alt.addBodyPart(MimeBodyPart().apply { setText(html, "UTF-8", "html") })
            setContent(alt)
            sentDate = Date()
        }
        Transport.send(msg)
    }

    fun subjectFor(t: Tx): String {
        val inc = t.direction == Direction.IN
        val who = t.counterparty?.let { if (inc) " de $it" else " a $it" } ?: ""
        val bank = t.bank?.let { " · $it" } ?: ""
        return (if (inc) "✅ Llegó ${money(t.amount)}" else "↗ Se envió ${money(t.amount)}") + who + bank
    }

    private fun row(k: String, v: String) =
        "<tr><td style=\"padding:6px 0;color:#6b7690\">$k</td><td style=\"padding:6px 0;text-align:right;font-weight:600;color:#14213D\">${esc(v)}</td></tr>"

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    fun htmlFor(t: Tx): String {
        val inc = t.direction == Direction.IN
        val color = if (inc) "#0FB5A3" else "#E25555"
        val title = if (inc) "Te llegó una transferencia" else "Se envió una transferencia"
        val rows = buildString {
            append(row(if (inc) "De" else "Para", t.counterparty ?: "No identificado"))
            append(row(if (inc) "Llegó a" else "Salió de", (t.bank ?: "Banco no identificado") + (t.last4?.let { " ****$it" } ?: "")))
            t.reference?.let { append(row("Referencia", it)) }
            append(row("Fecha", whenText(t.time)))
            append(row("Detectado por", t.source))
        }
        return """
<div style="font-family:Arial,Helvetica,sans-serif;background:#f2f4f7;padding:20px">
  <div style="max-width:460px;margin:0 auto;background:#ffffff;border-radius:16px;overflow:hidden;border:1px solid #e3e7ee">
    <div style="background:#0FB5A3;padding:14px 18px;color:#ffffff;font-weight:800;font-size:20px">cybeth</div>
    <div style="padding:20px 18px">
      <div style="color:#6b7690;font-size:14px">$title</div>
      <div style="font-size:34px;font-weight:800;color:$color;margin:4px 0 14px">${if (inc) "+" else "-"}${money(t.amount)}</div>
      <table style="width:100%;border-collapse:collapse;font-size:14px">$rows</table>
      <div style="margin-top:16px;padding:10px 12px;background:#f6f8fa;border-radius:10px;color:#6b7690;font-size:12px;white-space:pre-wrap">${esc(t.raw.take(500))}</div>
    </div>
    <div style="padding:10px 18px;color:#9aa3b5;font-size:11px;border-top:1px solid #eef1f5">Aviso automático de Cybeth SG</div>
  </div>
</div>""".trimIndent()
    }

    fun plainFor(t: Tx): String {
        val inc = t.direction == Direction.IN
        return buildString {
            appendLine(if (inc) "Te llegó una transferencia" else "Se envió una transferencia")
            appendLine((if (inc) "+" else "-") + money(t.amount))
            appendLine((if (inc) "De: " else "Para: ") + (t.counterparty ?: "No identificado"))
            appendLine((if (inc) "Llegó a: " else "Salió de: ") + (t.bank ?: "Banco no identificado") + (t.last4?.let { " ****$it" } ?: ""))
            t.reference?.let { appendLine("Referencia: $it") }
            appendLine("Fecha: " + whenText(t.time))
            appendLine()
            appendLine("Aviso automático de Cybeth SG")
        }
    }

    /** Explica el error en palabras simples, con el detalle técnico al final. */
    fun explain(e: Throwable): String {
        val all = generateSequence(e) { it.cause }.joinToString(" | ") { "${it.javaClass.simpleName}: ${it.message}" }
        val low = all.lowercase()
        val simple = when {
            e is javax.mail.AuthenticationFailedException || low.contains("535") || low.contains("username and password not accepted") ->
                "Google rechazó el correo o la contraseña de aplicación. Revisa que sean las 16 letras del Gmail de la empresa."
            low.contains("unknownhost") || low.contains("unable to resolve") || low.contains("network") || low.contains("timeout") || low.contains("timed out") ->
                "No hay internet o la red bloquea el envío. Prueba con datos móviles."
            low.contains("dch") || low.contains("unsupporteddatatype") -> "Error interno al armar el correo."
            low.contains("invalid address") || low.contains("addressexception") || low.contains("553") || low.contains("550") ->
                "El correo del jefe parece estar mal escrito."
            else -> "No se pudo enviar."
        }
        return "$simple\n(Detalle: ${all.take(300)})"
    }

    /** Envía el aviso de una transferencia a los correos configurados. */
    suspend fun sendFor(ctx: Context, t: Tx): Result = withContext(Dispatchers.IO) {
        val prefs = Prefs(ctx)
        val from = prefs.email
        val pass = prefs.emailPassword
        val to = prefs.forwardList
        if (from.isNullOrBlank() || pass.isNullOrBlank()) return@withContext Result(false, "Primero enlaza tu Gmail en Ajustes")
        if (to.isEmpty()) return@withContext Result(false, "No hay correos de destino")
        try {
            send(from, pass, to, subjectFor(t), htmlFor(t), plainFor(t))
            Result(true, "Enviado a ${to.joinToString(", ")}")
        } catch (e: Throwable) {
            Result(false, explain(e))
        }
    }

    /** Correo de prueba para comprobar que a la jefe sí le llega. */
    suspend fun sendTest(ctx: Context): Result {
        val fake = Tx(
            id = -1, amount = 1000, direction = Direction.IN, counterparty = "Prueba de Cybeth", bank = "Nequi",
            last4 = null, reference = "PRUEBA", source = "Prueba", raw = "Este es un correo de prueba de Cybeth SG.",
            time = System.currentTimeMillis()
        )
        return sendFor(ctx, fake)
    }

    /** Pone en cola el envío (se reintenta solo si no hay internet en ese momento). */
    fun enqueue(ctx: Context, t: Tx) {
        val prefs = Prefs(ctx)
        val want = if (t.direction == Direction.IN) prefs.forwardIn else prefs.forwardOut
        if (!want || prefs.forwardList.isEmpty() || !prefs.emailLinked) return
        val req = OneTimeWorkRequestBuilder<ForwardWorker>()
            .setInputData(workDataOf("txId" to t.id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(ctx).enqueue(req)
    }
}

class ForwardWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Repo.init(applicationContext)
        val id = inputData.getLong("txId", -1)
        val t = Repo.txs.value.firstOrNull { it.id == id } ?: return Result.success()
        val r = ForwardMail.sendFor(applicationContext, t)
        Prefs(applicationContext).lastForwardResult = r.message
        return when {
            r.ok -> Result.success()
            runAttemptCount < 5 && !r.message.contains("contraseña") && !r.message.contains("enlaza") && !r.message.contains("mal escrito") -> Result.retry()
            else -> Result.failure()
        }
    }
}
