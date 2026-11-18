package co.cuadra.app.data

import java.text.Normalizer

enum class Direction { IN, OUT }

data class ParsedTx(
    val amount: Long,
    val direction: Direction,
    val counterparty: String?,
    val bank: String?,
    val last4: String?,
    val reference: String?,
)

/**
 * Lee el texto de una notificación, SMS o correo de un banco y decide si es una
 * TRANSFERENCIA (recibida o enviada). Las compras con tarjeta, pagos PSE, pagos en
 * línea, facturas, recargas y retiros se descartan (devuelve null).
 */
object TxParser {

    // ---------- Palabras clave (se comparan sin tildes y en minúsculas) ----------

    /** Si aparece cualquiera de estas, NO es una transferencia: se ignora siempre. */
    private val HARD_EXCLUDE = listOf(
        "compra", "compraste", "pse", "retiro", "retiraste", "debito automatico",
        "recarga", "recargaste", "avance", "factura", "boton de pago", "pago en linea",
        "pagos en linea", "cuota", "tarjeta de credito", "con tu tarjeta", "con su tarjeta",
        "tarjeta debito", "tarjeta credito", "datafono", "suscripcion", "codigo de verificacion",
        "clave dinamica", "codigo de seguridad", "otp"
    )

    /** "Pago"/"pagaste" solo se ignora si no hay una señal clara de transferencia recibida. */
    private val SOFT_PAY = listOf("pagaste", "pago a", "pago de", "pago en", "pago por", "pago exitoso", "pago realizado", "realizaste un pago", "hiciste un pago")

    private val IN_WORDS = listOf(
        "recibiste", "te llego", "te llegaron", "te envio", "te enviaron", "te transfirio",
        "te transfirieron", "te consignaron", "te consigno", "has recibido", "ha recibido",
        "recibio una transferencia", "abono", "abonaron", "te pago", "te pagaron",
        "transferencia recibida", "dinero recibido", "plata recibida", " recibida de", " recibido de",
        "recibida por", "recibido por"
    )

    private val OUT_WORDS = listOf(
        "enviaste", "le enviaste", "transferiste", "envio exitoso", "transferencia exitosa",
        "realizaste una transferencia", "hiciste una transferencia", "transferencia enviada",
        "has enviado", "has transferido", "ha transferido", "transfirio desde su cuenta",
        "envio de dinero"
    )

    // ---------- Bancos ----------

    private val BANKS = listOf(
        "daviplata" to "Daviplata",
        "nequi" to "Nequi",
        "davivienda" to "Davivienda",
        "bancolombia" to "Bancolombia",
        "banco de bogota" to "Banco de Bogotá",
        "bancodebogota" to "Banco de Bogotá",
        "bbva" to "BBVA",
        "nu colombia" to "Nu",
        "nubank" to "Nu",
        "lulo" to "Lulo Bank",
        "movii" to "MOVii",
        "av villas" to "AV Villas",
        "avvillas" to "AV Villas",
        "banco de occidente" to "Banco de Occidente",
        "bancodeoccidente" to "Banco de Occidente",
        "banco popular" to "Banco Popular",
        "bancopopular" to "Banco Popular",
        "caja social" to "Banco Caja Social",
        "bancocajasocial" to "Banco Caja Social",
        "itau" to "Itaú",
        "colpatria" to "Scotiabank Colpatria",
        "scotiabank" to "Scotiabank Colpatria",
        "falabella" to "Banco Falabella",
        "banco agrario" to "Banco Agrario",
        "bancoagrario" to "Banco Agrario",
        "dale!" to "Dale!",
        "banco union" to "Banco Unión",
        "pibank" to "Pibank",
        "rappipay" to "RappiPay",
    )

    fun norm(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[¡¿]"), "")
            .replace(Regex("\\s+"), " ")

    /** Banco mencionado en un texto (nombre de la app, remitente del correo o el mensaje). */
    fun detectBank(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val n = norm(text)
        if (Regex("(^|\\W)nu(\\W|$)").containsMatchIn(n) && n.trim().length <= 12) return "Nu" // app "Nu"
        if (n.trim() == "dale" ) return "Dale!"
        return BANKS.firstOrNull { n.contains(it.first) }?.second
    }

    private fun containsAny(n: String, words: List<String>) = words.any { n.contains(it) }

    // ---------- Monto ----------

    private val AMOUNT_RX = Regex("""(?:\$|cop|col\$)\s?([0-9]{1,3}(?:[.,][0-9]{3})+|[0-9]+)(?:[.,]([0-9]{1,2}))?(?![0-9])""", RegexOption.IGNORE_CASE)
    private val AMOUNT_PESOS_RX = Regex("""([0-9]{1,3}(?:[.,][0-9]{3})+|[0-9]{4,})(?:[.,][0-9]{1,2})?\s?(?:pesos|cop)""", RegexOption.IGNORE_CASE)

    /** Toma el monto de la transferencia, saltando el "saldo disponible" o "cupo" que algunos bancos ponen. */
    fun parseAmount(text: String): Long? {
        val all = AMOUNT_RX.findAll(text).toList().ifEmpty { AMOUNT_PESOS_RX.findAll(text).toList() }
        val m = all.firstOrNull { mm ->
            val before = norm(text.substring(maxOf(0, mm.range.first - 28), mm.range.first))
            !(before.contains("saldo") || before.contains("disponible") || before.contains("cupo") || before.contains("costo") || before.contains("comision"))
        } ?: return null
        val digits = m.groupValues[1].replace(".", "").replace(",", "")
        val v = digits.toLongOrNull() ?: return null
        return if (v > 0) v else null
    }

    // ---------- Nombre de quien envía / recibe ----------

    private const val UP = "A-ZÁÉÍÓÚÑÜ"
    private const val WORD = "[$UP][A-Za-zÁÉÍÓÚÑÜáéíóúñü.&'-]*"
    private val STOP_TAIL = setOf("en", "a", "por", "con", "ref", "referencia", "desde", "tu", "su", "el", "la", "al", "y", "cuenta", "hoy", "por", "valor", "fecha", "hora", "mensaje", "de")

    private fun cleanName(raw: String?): String? {
        if (raw == null) return null
        val all = raw.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        // El nombre termina en el primer punto, coma o dos puntos ("JHON PEREZ. Ref ..." -> "JHON PEREZ")
        val cut = mutableListOf<String>()
        for (t in all) {
            cut += t
            if ((t.endsWith(".") && !Regex("^([A-Za-z]\\.)+$").matches(t)) || t.endsWith(",") || t.endsWith(":")) break
        }
        var toks = cut.map { it.trim(',', '.', ':') }.filter { it.isNotBlank() }
        while (toks.isNotEmpty() && norm(toks.last()).trim('.', ',', ':') in STOP_TAIL) toks = toks.dropLast(1)
        while (toks.isNotEmpty() && norm(toks.first()) in STOP_TAIL) toks = toks.drop(1)
        if (toks.isEmpty()) return null
        val name = toks.joinToString(" ").trim(',', '.', ':')
        if (name.length < 3) return null
        // Si el "nombre" es en realidad un banco o una palabra genérica, no sirve
        val n = norm(name)
        if (detectBank(name) != null && n.split(" ").size <= 3) return null
        if (n in setOf("transferencia", "nequi", "envio", "recibiste", "te llego plata")) return null
        return titleCase(name)
    }

    private fun titleCase(s: String): String =
        s.lowercase().split(" ").joinToString(" ") { w ->
            if (w.length <= 3 && w in setOf("de", "del", "la", "las", "los", "y", "sas", "s.a.s", "ltda")) {
                if (w == "sas" || w == "s.a.s" || w == "ltda") w.uppercase() else w
            } else w.replaceFirstChar { it.uppercase() }
        }

    private fun findSender(text: String): String? {
        // "Laura Méndez te envió $42.500"
        Regex("((?:$WORD\\s+){0,4}$WORD)\\s+te\\s+(?:envi|transfiri|consign|pag)").find(text)?.let { m ->
            cleanName(m.groupValues[1])?.let { return it }
        }
        // "...de CARLOS RUIZ en tu cuenta" / "De: PEDRO SALAZAR"
        Regex("\\b(?:de|De|DE|desde|remitente|Remitente|REMITENTE)\\s*:?\\s+((?:$WORD\\s?){1,6})").findAll(text).forEach { m ->
            cleanName(m.groupValues[1])?.let { return it }
        }
        return null
    }

    private fun findReceiver(text: String): String? {
        // "Enviaste $150.000 a ROSA MEDINA, cuenta ..." / "Para: MARIA LOPEZ"
        Regex("\\b(?:a|A|para|Para|PARA|destinatario|Destinatario)\\s*:?\\s+((?:$WORD\\s?){1,6})").findAll(text).forEach { m ->
            cleanName(m.groupValues[1])?.let { return it }
        }
        return null
    }

    private fun findLast4(text: String): String? =
        Regex("""\*+\s?(\d{4})\b""").find(text)?.groupValues?.get(1)
            ?: Regex("""(?:terminada en|termina en|finalizada en)\s*(\d{4})\b""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)

    private fun findReference(text: String): String? =
        Regex("""(?:ref(?:erencia)?|comprobante|aprobaci[oó]n|n[uú]mero de transacci[oó]n)\s*(?:no\.?|n[°º]|#)?\s*[:.]?\s*([A-Z0-9][A-Z0-9-]{3,})""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)

    // ---------- Principal ----------

    /**
     * @param text  título + texto de la notificación, o asunto + cuerpo del correo.
     * @param bankHint banco ya conocido por la app o el remitente del correo.
     */
    fun parse(text: String, bankHint: String? = null): ParsedTx? {
        if (text.isBlank()) return null
        val n = norm(text)

        if (containsAny(n, HARD_EXCLUDE)) return null

        val isIn = containsAny(n, IN_WORDS)
        val isOut = !isIn && containsAny(n, OUT_WORDS)
        if (!isIn && !isOut) return null
        if (!isIn && containsAny(n, SOFT_PAY)) return null

        val amount = parseAmount(text) ?: return null
        val dir = if (isIn) Direction.IN else Direction.OUT
        val bank = bankHint ?: detectBank(text)
        val who = if (dir == Direction.IN) findSender(text) else findReceiver(text)

        return ParsedTx(
            amount = amount,
            direction = dir,
            counterparty = who,
            bank = bank,
            last4 = findLast4(text),
            reference = findReference(text),
        )
    }
}
