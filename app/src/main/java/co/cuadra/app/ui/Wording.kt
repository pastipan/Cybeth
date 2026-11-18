package co.cuadra.app.ui

import co.cuadra.app.data.Direction
import co.cuadra.app.data.Tx
import co.cuadra.app.data.TxStatus

/**
 * Textos de cada movimiento, como los muestra Fortu:
 * "Cristian Rosero te envió $35.000" · "Entró a Nequi ****3009" · "Aprobada".
 * Se usan en la app, en la voz, en la notificación y en el correo al jefe.
 * (Sin nada de Android, para poder probarlo.)
 */
object Wording {

    private fun amt(t: Tx, spoken: Boolean) = if (spoken) "${t.amount} pesos" else money(t.amount)

    /** Frase principal. */
    fun headline(t: Tx, spoken: Boolean = false): String {
        val a = amt(t, spoken)
        val who = t.counterparty
        return if (t.direction == Direction.IN) when (t.status) {
            TxStatus.APROBADA -> if (who != null) "$who te envió $a" else "Te llegaron $a"
            TxStatus.EN_PROCESO -> if (who != null) "$who te envió $a: en proceso" else "Transferencia de $a en proceso"
            TxStatus.PENDIENTE_COBRO -> if (who != null) "$who te envió $a: pendiente por cobrar" else "Tienes $a pendiente por cobrar"
            TxStatus.RECHAZADA -> if (who != null) "Rechazada: $who intentó enviarte $a" else "Rechazada: no entraron $a"
        } else when (t.status) {
            TxStatus.APROBADA -> if (who != null) "Le enviaste $a a $who" else "Enviaste $a"
            TxStatus.EN_PROCESO -> if (who != null) "Tu envío de $a a $who está en proceso" else "Tu envío de $a está en proceso"
            TxStatus.PENDIENTE_COBRO -> if (who != null) "$a a $who: pendiente por cobrar" else "Envío de $a pendiente por cobrar"
            TxStatus.RECHAZADA -> if (who != null) "Tu envío de $a a $who fue rechazado" else "Tu envío de $a fue rechazado"
        }
    }

    /** Etiqueta corta del estado. */
    fun statusLabel(t: Tx): String = when (t.status) {
        TxStatus.APROBADA -> if (t.direction == Direction.IN) "Entró ✓" else "Enviada ✓"
        TxStatus.EN_PROCESO -> "En proceso"
        TxStatus.PENDIENTE_COBRO -> "Pendiente por cobrar"
        TxStatus.RECHAZADA -> "Rechazada ✗"
    }

    /** Nombre del estado para filtros y correos. */
    fun statusName(s: TxStatus): String = when (s) {
        TxStatus.APROBADA -> "Aprobada"
        TxStatus.EN_PROCESO -> "En proceso"
        TxStatus.PENDIENTE_COBRO -> "Pendiente por cobrar"
        TxStatus.RECHAZADA -> "Rechazada"
    }

    /** "Entró a Nequi ****3009", "Salió de Bancolombia ****4821", "No entró a …". */
    fun accountLine(t: Tx): String {
        val acc = (t.bank ?: "cuenta no identificada") + (t.last4?.let { " ****$it" } ?: "")
        return if (t.direction == Direction.IN) when (t.status) {
            TxStatus.APROBADA -> "Entró a $acc"
            TxStatus.RECHAZADA -> "No entró a $acc"
            else -> "Va para $acc"
        } else when (t.status) {
            TxStatus.RECHAZADA -> "No salió de $acc (devuelta)"
            else -> "Salió de $acc"
        }
    }

    /** Asunto del correo al jefe. */
    fun emailSubject(t: Tx): String {
        val icon = when (t.status) {
            TxStatus.APROBADA -> if (t.direction == Direction.IN) "✅" else "↗"
            TxStatus.EN_PROCESO -> "⏳"
            TxStatus.PENDIENTE_COBRO -> "🕓"
            TxStatus.RECHAZADA -> "❌"
        }
        val upd = if (t.updated) " (actualización)" else ""
        val bank = t.bank?.let { " · $it" } ?: ""
        return "$icon ${headline(t)}$bank$upd"
    }
}
