package co.cuadra.app.service

import android.content.Context
import co.cuadra.app.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Manda el cierre del día por WhatsApp usando CallMeBot (gratis, uso personal).
 * El dueño del número lo activa una vez escribiéndole al bot y recibe una "apikey".
 * Solo texto: WhatsApp recibe el mismo resumen que el correo (sin el archivo de Excel).
 */
object WhatsApp {
    data class Result(val ok: Boolean, val message: String)

    suspend fun send(ctx: Context, text: String): Result = withContext(Dispatchers.IO) {
        val p = Prefs(ctx)
        val phone = p.waPhone.filter { it.isDigit() }
        val key = p.waApiKey.trim()
        if (phone.isBlank() || key.isBlank()) return@withContext Result(false, "Falta el número o la apikey de WhatsApp")
        val full = if (phone.length == 10) "57$phone" else phone // número de Colombia sin indicativo
        try {
            val url = URL(
                "https://api.callmebot.com/whatsapp.php?phone=%2B$full&apikey=" + URLEncoder.encode(key, "UTF-8") +
                    "&text=" + URLEncoder.encode(text.take(3500), "UTF-8")
            )
            val c = url.openConnection() as HttpURLConnection
            c.connectTimeout = 20_000; c.readTimeout = 30_000
            val code = c.responseCode
            val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
            c.disconnect()
            val low = body.lowercase()
            when {
                code in 200..299 && !low.contains("error") && !low.contains("apikey is invalid") ->
                    Result(true, "WhatsApp enviado a +$full")
                low.contains("apikey") -> Result(false, "La apikey de WhatsApp no es válida para ese número")
                else -> Result(false, "WhatsApp no se pudo enviar (código $code)")
            }
        } catch (e: Exception) {
            Result(false, "WhatsApp no se pudo enviar: ${e.message ?: e.javaClass.simpleName}")
        }
    }
}
