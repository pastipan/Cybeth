package co.cuadra.app.data

/** Un movimiento guardado. (Archivo sin nada de Android, para poder probarlo solo.) */
data class Tx(
    val id: Long,
    val amount: Long,
    val direction: Direction,
    val counterparty: String?,
    val bank: String?,
    val last4: String?,
    val reference: String?,
    /** "Notificación", "SMS" o "Correo" */
    val source: String,
    val raw: String,
    val time: Long,
    val status: TxStatus = TxStatus.APROBADA,
    /** true cuando este movimiento ya existía y solo le cambió el estado (no se guarda en la BD). */
    val updated: Boolean = false,
)

/** Filtro de la pantalla Movimientos. null = sin filtrar. */
fun filterTxs(list: List<Tx>, dir: Direction?, bank: String?, status: TxStatus? = null): List<Tx> =
    list.filter {
        (dir == null || it.direction == dir) && (bank == null || it.bank == bank) && (status == null || it.status == status)
    }

object Dedup {
    private const val WINDOW = 15 * 60 * 1000L

    private fun sameName(a: String?, b: String?): Boolean {
        if (a == null || b == null) return true
        val x = TxParser.norm(a).trim()
        val y = TxParser.norm(b).trim()
        return x == y || x.contains(y) || y.contains(x)
    }

    /**
     * ¿Es la misma transferencia que una ya guardada?
     * - Misma referencia del banco: sí.
     * - El mismo texto repetido en menos de 15 min (Android a veces repite la notificación): sí.
     * - Llegó por OTRO canal (notificación vs correo vs SMS) con mismo monto, sentido, banco y
     *   nombre compatible en menos de 15 min: sí (es el mismo pago avisado dos veces).
     * - Dos notificaciones distintas del mismo monto (ej. dos clientes pagan $10.000): NO, son dos pagos.
     */
    /**
     * @param sameTextIsRepeat false cuando ya sabemos que es una notificación NUEVA (otro aviso del banco):
     *   dos pagos de $1.000 de la misma persona traen exactamente el mismo texto y son DOS pagos.
     */
    fun isDuplicate(existing: List<Tx>, p: ParsedTx, source: String, raw: String, time: Long, sameTextIsRepeat: Boolean = true): Boolean =
        existing.any { t ->
            if (t.amount != p.amount || t.direction != p.direction) return@any false
            // Un cambio de estado (ej. de "en proceso" a "aprobada") no es repetido: se maneja con statusUpdateFor
            if (t.status != p.status) return@any false
            if (p.reference != null && t.reference != null) return@any t.reference.equals(p.reference, ignoreCase = true)
            val close = kotlin.math.abs(t.time - time) < WINDOW
            if (!close) return@any false
            if (t.raw == raw) return@any sameTextIsRepeat
            t.source != source &&
                (t.bank == null || p.bank == null || t.bank == p.bank) &&
                sameName(t.counterparty, p.counterparty)
        }

    /**
     * Si este mensaje es un CAMBIO DE ESTADO de una transferencia ya guardada, la devuelve.
     * Ej: primero "transferencia en proceso por $250.000 de X" y horas después
     * "se abonó la transferencia de $250.000 de X" → la misma pasa a Aprobada.
     * O "tu envío a MARIA fue devuelto" → la enviada pasa a Rechazada.
     */
    fun statusUpdateFor(existing: List<Tx>, p: ParsedTx, time: Long): Tx? =
        existing
            .filter { t ->
                t.amount == p.amount && t.direction == p.direction && t.status != p.status &&
                    p.status.rank >= t.status.rank &&
                    time - t.time in -WINDOW..(4 * 24 * 60 * 60 * 1000L) &&
                    (t.bank == null || p.bank == null || t.bank == p.bank) &&
                    when {
                        p.reference != null && t.reference != null -> t.reference.equals(p.reference, ignoreCase = true)
                        p.counterparty != null && t.counterparty != null -> sameName(t.counterparty, p.counterparty)
                        // Sin nombre ni referencia: solo si es muy cercano en tiempo (2 horas)
                        else -> kotlin.math.abs(time - t.time) < 2 * 60 * 60 * 1000L
                    }
            }
            .maxByOrNull { it.time }
}
