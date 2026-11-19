package co.cuadra.app.data

import java.text.Normalizer

enum class Direction { IN, OUT }

/** Estado de la transferencia, como en Fortu. */
enum class TxStatus {
    /** La plata ya llegó / se envió bien. */
    APROBADA,
    /** El banco la está procesando (ej. ACH a otro banco, se abona en el siguiente ciclo). */
    EN_PROCESO,
    /** Enviada a alguien que todavía no la ha reclamado (ej. "plata por cobrar" de Nequi). */
    PENDIENTE_COBRO,
    /** Rechazada, devuelta, reversada o fallida: la plata NO llegó. */
    RECHAZADA;

    /** Orden en que puede cambiar: En proceso / Por cobrar → Aprobada → Rechazada (devuelta). */
    val rank: Int get() = when (this) { EN_PROCESO, PENDIENTE_COBRO -> 1; APROBADA -> 2; RECHAZADA -> 3 }
}

data class ParsedTx(
    val amount: Long,
    val direction: Direction,
    val counterparty: String?,
    val bank: String?,
    val last4: String?,
    val reference: String?,
    val status: TxStatus = TxStatus.APROBADA,
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

    /**
     * Señales FUERTES de que te llegó plata (te pagaron por QR, con tarjeta, por Nequi, etc.).
     * Si aparecen, no se descarta aunque el mensaje diga "tarjeta" o "QR": es un cobro tuyo.
     */
    private val STRONG_IN = listOf(
        "recibiste", "te llego", "te llegaron", "te envio", "te enviaron", "te transfirio", "te transfirieron",
        "te pagaron", "te pago ", "has recibido", "recibio una transferencia", "tienes una transferencia",
        "pago recibido", "venta aprobada", "cobro recibido", "te consignaron"
    )

    /** Esto nunca es una transferencia (códigos de seguridad). */
    private val NEVER = listOf("codigo de verificacion", "clave dinamica", "codigo de seguridad", "otp", "tu clave es")

    /** "Pago"/"pagaste" solo se ignora si no hay una señal clara de transferencia recibida. */
    private val SOFT_PAY = listOf("pagaste", "pago a", "pago de", "pago en", "pago por", "pago exitoso", "pago realizado", "realizaste un pago", "hiciste un pago")

    private val IN_WORDS = listOf(
        "recibiste", "te llego", "te llegaron", "te envio", "te enviaron", "te transfirio",
        "te transfirieron", "te consignaron", "te consigno", "has recibido", "ha recibido",
        "recibio una transferencia", "abono", "abonaron", "te pago", "te pagaron",
        "transferencia recibida", "dinero recibido", "plata recibida", " recibida de", " recibido de",
        "recibida por", "recibido por", " a tu cuenta", " a su cuenta", "hacia tu cuenta", "tienes una transferencia",
        "tiene una transferencia", "transferencia entrante", "te estan enviando", "te van a llegar"
    )

    private val OUT_WORDS = listOf(
        "enviaste", "le enviaste", "transferiste", "envio exitoso", "transferencia exitosa",
        "realizaste una transferencia", "hiciste una transferencia", "transferencia enviada",
        "has enviado", "has transferido", "ha transferido", "transfirio desde su cuenta",
        "envio de dinero", "tu envio", "tu transferencia", "su transferencia", "envio a ", "transferencia a ",
        "plata por cobrar", "enviada a"
    )

    // ---------- Estado ----------

    private val REJECTED = listOf(
        "rechaz", "no fue exitos", "no exitos", "no se pudo", "no pudimos", "fallid", "fallo", "declinad",
        "devuelt", "devolvimos", "devolucion", "revers", "reintegr", "anulad", "cancelad", "no se realizo",
        "no se completo", "sin exito", "no aprobad", "denegad"
    )
    private val TO_COLLECT = listOf(
        "por cobrar", "pendiente de cobro", "pendiente por cobrar", "por reclamar", "debe reclamar", "puede reclamar",
        "sin reclamar", "pendiente de aceptacion", "aun no la ha recibido", "aun no la reclama", "tiene 30 dias", "tiene hasta"
    )
    private val IN_PROCESS = listOf(
        "en proceso", "procesando", "en tramite", "pendiente", "se abonara", "sera abonad", "en validacion",
        "programad", "en curso", "siguiente ciclo", "proximo dia habil", "hasta 24 horas", "en camino", "esta siendo procesad"
    )

    fun detectStatus(n: String): TxStatus = when {
        containsAny(n, REJECTED) -> TxStatus.RECHAZADA
        containsAny(n, TO_COLLECT) -> TxStatus.PENDIENTE_COBRO
        containsAny(n, IN_PROCESS) -> TxStatus.EN_PROCESO
        else -> TxStatus.APROBADA
    }

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
        val m = mainAmount(text) ?: return null
        val digits = m.groupValues[1].replace(".", "").replace(",", "")
        val v = digits.toLongOrNull() ?: return null
        return if (v > 0) v else null
    }

    /**
     * Nequi a veces escribe el monto SIN signo $: "CRISTIAN GUANCHA te envió 1000, ¡lo mejor!".
     * Se acepta un número justo después de palabras como "te envió", "recibiste", "te llegaron"...
     */
    private val AMOUNT_AFTER_WORD_RX = Regex(
        """(?:env[ií][oó]|enviaron|recibiste|llegaron|lleg[oó]|enviaste|transfiri[oó]|transfirieron|transferiste|pag[oó]|pagaron|consign[oó]|abon[oó])""" +
            """[ \t]+(?:de[ \t]+|por[ \t]+)?(?:\$[ \t]?)?([0-9]{1,3}(?:[.,][0-9]{3})+|[0-9]{3,})(?:[.,][0-9]{1,2})?(?![0-9])""",
        RegexOption.IGNORE_CASE
    )

    private fun mainAmount(text: String): MatchResult? {
        val all = AMOUNT_RX.findAll(text).toList()
            .ifEmpty { AMOUNT_AFTER_WORD_RX.findAll(text).toList() }
            .ifEmpty { AMOUNT_PESOS_RX.findAll(text).toList() }
        return all.firstOrNull { mm ->
            val before = norm(text.substring(maxOf(0, mm.range.first - 28), mm.range.first))
            !(before.contains("saldo") || before.contains("disponible") || before.contains("cupo") || before.contains("costo") || before.contains("comision"))
        }
    }

    /**
     * Los correos de los bancos traen al final "consejos de seguridad" y publicidad que pueden
     * decir "compras", "pendiente", "PSE"… Se corta ese pie y se mira solo el mensaje principal
     * (lo que está alrededor del monto).
     */
    private val FOOTER_RX = Regex(
        "(consejos de seguridad|para que enviar plata|dudas al|si no reconoces|si no realizaste|nunca te (pediremos|pedira|solicitar)|" +
            "este (correo|mensaje) (es|fue) (generado|enviado)|no respondas este|aviso de privacidad|" +
            "politica de (privacidad|tratamiento)|para mas informacion|descarga la app)",
        RegexOption.IGNORE_CASE
    )

    fun focus(text: String): String {
        val plain = Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        val footerAt = FOOTER_RX.find(plain)?.range?.first
        val cut = if (footerAt != null && footerAt > 0) text.substring(0, minOf(footerAt, text.length)) else text
        // Si al cortar se perdió el monto (el "pie" estaba arriba), se usa todo el texto
        val body = if (mainAmount(cut) != null) cut else text
        val m = mainAmount(body) ?: return body
        val from = maxOf(0, m.range.first - 260)
        val to = minOf(body.length, m.range.last + 260)
        return body.substring(from, to)
    }

    // ---------- Nombre de quien envía / recibe ----------

    private const val UP = "A-ZÁÉÍÓÚÑÜ"
    private const val WORD = "[$UP][A-Za-zÁÉÍÓÚÑÜáéíóúñü.&'-]*"
    /** Palabras que pueden quedar pegadas antes del nombre ("Envío CRISTIAN…"). */
    private val LEADING_NOISE = setOf("envio", "nequi", "listo", "transferencia", "pago", "recibido", "plata", "movimiento")

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
        while (toks.isNotEmpty() && (norm(toks.first()) in STOP_TAIL || norm(toks.first()) in LEADING_NOISE)) toks = toks.drop(1)
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
        Regex("((?:$WORD[ \\t]+){0,4}$WORD)[ \\t]+te[ \\t]+(?:envi|transfiri|consign|pag)").find(text)?.let { m ->
            cleanName(m.groupValues[1])?.let { return it }
        }
        // "...de CARLOS RUIZ en tu cuenta" / "De: PEDRO SALAZAR"
        Regex("\\b(?:de|De|DE|desde|remitente|Remitente|REMITENTE)\\s*:?\\s+((?:$WORD\\s?){1,6})").findAll(text).forEach { m ->
            // "Banco de Bogotá", "Banco de Occidente"… no son personas
            val before = norm(text.substring(maxOf(0, m.range.first - 7), m.range.first))
            if (before.trimEnd().endsWith("banco") || before.trimEnd().endsWith("caja")) return@forEach
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
    fun parse(fullText: String, bankHint: String? = null): ParsedTx? {
        if (fullText.isBlank()) return null
        val bankFromAll = detectBank(fullText)
        val text = focus(fullText)
        val n = norm(text)

        if (containsAny(n, NEVER)) return null
        val strongIn = containsAny(n, STRONG_IN)
        // Compras, PSE, retiros, etc. se ignoran, salvo que sea plata que TE pagaron (QR, tarjeta, Nequi…)
        if (!strongIn && containsAny(n, HARD_EXCLUDE)) return null

        val isIn = strongIn || containsAny(n, IN_WORDS)
        val isOut = !isIn && containsAny(n, OUT_WORDS)
        if (!isIn && !isOut) return null
        if (!isIn && containsAny(n, SOFT_PAY)) return null

        val amount = parseAmount(text) ?: return null
        val dir = if (isIn) Direction.IN else Direction.OUT
        val bank = bankHint ?: detectBank(text) ?: bankFromAll
        val who = if (dir == Direction.IN) findSender(text) else findReceiver(text)

        return ParsedTx(
            amount = amount,
            direction = dir,
            counterparty = who,
            bank = bank,
            last4 = findLast4(text),
            reference = findReference(text),
            status = detectStatus(n),
        )
    }

    // ---------- Más flexible con las apps de bancos (Nequi, Daviplata…) ----------

    private val PROMO = listOf(
        "gana ", "ganate", "premio", "descuento", "oferta", "promo", "credito", "prestamo", "cashback", "regalo",
        "invita", "sorteo", "bono", "participa", "conoce", "nuevo en nequi", "actualiza", "tu saldo", "saldo disponible",
        "meta", "colchon", "bolsillo", "recordatorio", "vence", "cuota"
    )

    /**
     * Para notificaciones que vienen DIRECTO de la app de un banco (no SMS ni correo):
     * si no se entendió pero trae un monto y no es compra/pago/publicidad, se toma como plata que te llegó.
     * Así no se pierde un pago de Nequi si cambian el texto de su notificación.
     */
    fun parseFromBankApp(text: String, bank: String?): ParsedTx? {
        parse(text, bank)?.let { return it }
        val n = norm(focus(text))
        if (containsAny(n, NEVER) || containsAny(n, HARD_EXCLUDE) || containsAny(n, SOFT_PAY) || containsAny(n, PROMO)) return null
        if (containsAny(n, OUT_WORDS)) return null
        val amount = parseAmount(text) ?: return null
        return ParsedTx(
            amount = amount, direction = Direction.IN, counterparty = findSender(text), bank = bank,
            last4 = findLast4(text), reference = findReference(text), status = detectStatus(n),
        )
    }

    /** Explica en palabras por qué un mensaje se ignoró (para la pantalla de diagnóstico). */
    fun whyIgnored(text: String): String {
        val n = norm(focus(text))
        return when {
            containsAny(n, NEVER) -> "es un código de seguridad"
            !containsAny(n, STRONG_IN) && containsAny(n, HARD_EXCLUDE) -> "es compra, PSE, retiro o recarga"
            parseAmount(text) == null -> "no trae un monto en pesos"
            containsAny(n, PROMO) -> "parece publicidad o aviso de la app"
            else -> "no dice que te llegó ni que enviaste plata"
        }
    }
}
