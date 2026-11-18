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
)

/** Filtro de la pantalla Movimientos. dir: null = todo. bank: null = todos los bancos. */
fun filterTxs(list: List<Tx>, dir: Direction?, bank: String?): List<Tx> =
    list.filter { (dir == null || it.direction == dir) && (bank == null || it.bank == bank) }

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
    fun isDuplicate(existing: List<Tx>, p: ParsedTx, source: String, raw: String, time: Long): Boolean =
        existing.any { t ->
            if (t.amount != p.amount || t.direction != p.direction) return@any false
            if (p.reference != null && t.reference != null) return@any t.reference.equals(p.reference, ignoreCase = true)
            val close = kotlin.math.abs(t.time - time) < WINDOW
            if (!close) return@any false
            if (t.raw == raw) return@any true
            t.source != source &&
                (t.bank == null || p.bank == null || t.bank == p.bank) &&
                sameName(t.counterparty, p.counterparty)
        }
}
