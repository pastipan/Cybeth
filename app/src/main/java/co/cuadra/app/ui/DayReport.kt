package co.cuadra.app.ui

import co.cuadra.app.data.Direction
import co.cuadra.app.data.Tx
import co.cuadra.app.data.TxStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Cierre del día: totales, totales por banco/cuenta y el detalle de cada movimiento.
 * Sale como texto, como correo bonito (HTML) y como archivo para Excel (CSV).
 * (Sin nada de Android, para poder probarlo.)
 */
class DayReport(all: List<Tx>, val dayStart: Long) {
    private val dayEnd = dayStart + 24 * 60 * 60 * 1000L
    val txs: List<Tx> = all.filter { it.time in dayStart until dayEnd }.sortedBy { it.time }

    private val okIn = txs.filter { it.direction == Direction.IN && it.status == TxStatus.APROBADA }
    private val okOut = txs.filter { it.direction == Direction.OUT && it.status == TxStatus.APROBADA }

    val totalIn: Long = okIn.sumOf { it.amount }
    val countIn: Int = okIn.size
    val totalOut: Long = okOut.sumOf { it.amount }
    val countOut: Int = okOut.size
    val net: Long = totalIn - totalOut

    fun count(s: TxStatus) = txs.count { it.status == s }
    fun sum(s: TxStatus) = txs.filter { it.status == s }.sumOf { it.amount }

    /** Una fila por banco y cuenta: "Bancolombia ****3541". */
    data class AccountLine(val name: String, val totalIn: Long, val countIn: Int, val totalOut: Long, val countOut: Int)

    val byAccount: List<AccountLine> = txs
        .groupBy { (it.bank ?: "Sin banco") + (it.last4?.let { l -> " ****$l" } ?: "") }
        .map { (name, l) ->
            val i = l.filter { it.direction == Direction.IN && it.status == TxStatus.APROBADA }
            val o = l.filter { it.direction == Direction.OUT && it.status == TxStatus.APROBADA }
            AccountLine(name, i.sumOf { it.amount }, i.size, o.sumOf { it.amount }, o.size)
        }
        .sortedByDescending { it.totalIn }

    val dateLabel: String = SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy", Locale("es", "CO"))
        .format(Date(dayStart)).replaceFirstChar { it.uppercase() }
    val fileDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(dayStart))
    val fileName: String = "Cybeth_cierre_$fileDate.csv"

    fun subject(): String = "📊 Cierre del día $fileDate · Entró ${money(totalIn)} ($countIn pagos)"

    private fun signed(n: Long) = (if (n < 0) "-" else "+") + money(kotlin.math.abs(n))

    // ---------------------------------------------------------------- texto

    fun plain(): String = buildString {
        appendLine("CIERRE DEL DÍA · $dateLabel")
        appendLine()
        appendLine("Entró (aprobado): ${money(totalIn)} en $countIn pagos")
        appendLine("Enviado (aprobado): ${money(totalOut)} en $countOut envíos")
        appendLine("Neto: ${signed(net)}")
        appendLine("En proceso: ${count(TxStatus.EN_PROCESO)} (${money(sum(TxStatus.EN_PROCESO))})")
        appendLine("Pendientes por cobrar: ${count(TxStatus.PENDIENTE_COBRO)} (${money(sum(TxStatus.PENDIENTE_COBRO))})")
        appendLine("Rechazadas: ${count(TxStatus.RECHAZADA)} (${money(sum(TxStatus.RECHAZADA))})")
        appendLine()
        appendLine("POR CUENTA")
        if (byAccount.isEmpty()) appendLine("Sin movimientos.")
        byAccount.forEach { appendLine("${it.name}: entró ${money(it.totalIn)} (${it.countIn}) · enviado ${money(it.totalOut)} (${it.countOut})") }
        appendLine()
        appendLine("MOVIMIENTOS")
        if (txs.isEmpty()) appendLine("Sin movimientos.")
        txs.forEach {
            appendLine("${hour(it.time)} · ${Wording.headline(it)} · ${Wording.accountLine(it)} · ${Wording.statusName(it.status)}")
        }
        appendLine()
        appendLine("Va adjunto el archivo $fileName para abrir en Excel.")
        appendLine("Cierre automático de Cybeth SG")
    }

    // ---------------------------------------------------------------- correo bonito

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun stat(label: String, value: String, color: String) =
        "<td style=\"padding:10px;background:#f6f8fa;border-radius:10px;text-align:center\">" +
            "<div style=\"font-size:18px;font-weight:800;color:$color\">${esc(value)}</div>" +
            "<div style=\"font-size:11px;color:#6b7690\">${esc(label)}</div></td>"

    fun html(): String {
        val accRows = if (byAccount.isEmpty()) "<tr><td style=\"padding:6px 0;color:#6b7690\">Sin movimientos</td></tr>"
        else byAccount.joinToString("") {
            "<tr><td style=\"padding:6px 0;font-weight:600;color:#14213D\">${esc(it.name)}</td>" +
                "<td style=\"padding:6px 0;text-align:right;color:#0FB5A3;font-weight:700\">+${money(it.totalIn)} <span style=\"color:#9aa3b5;font-weight:400\">(${it.countIn})</span></td>" +
                "<td style=\"padding:6px 0;text-align:right;color:#E25555;font-weight:700\">-${money(it.totalOut)} <span style=\"color:#9aa3b5;font-weight:400\">(${it.countOut})</span></td></tr>"
        }
        val txRows = if (txs.isEmpty()) "<tr><td style=\"padding:6px 0;color:#6b7690\">Sin movimientos</td></tr>"
        else txs.joinToString("") {
            val color = when (it.status) {
                TxStatus.APROBADA -> if (it.direction == Direction.IN) "#0FB5A3" else "#E25555"
                TxStatus.EN_PROCESO -> "#E59A12"
                TxStatus.PENDIENTE_COBRO -> "#3B7BE0"
                TxStatus.RECHAZADA -> "#9aa3b5"
            }
            val strike = if (it.status == TxStatus.RECHAZADA) "text-decoration:line-through;" else ""
            "<tr style=\"border-top:1px solid #eef1f5\">" +
                "<td style=\"padding:6px 4px 6px 0;color:#6b7690;white-space:nowrap;vertical-align:top\">${hour(it.time)}</td>" +
                "<td style=\"padding:6px 4px;color:#14213D\">${esc(it.counterparty ?: if (it.direction == Direction.IN) "Recibida" else "Enviada")}" +
                "<div style=\"font-size:11px;color:#9aa3b5\">${esc(Wording.accountLine(it))} · ${esc(Wording.statusName(it.status))}</div></td>" +
                "<td style=\"padding:6px 0;text-align:right;font-weight:700;white-space:nowrap;vertical-align:top;color:$color;$strike\">" +
                "${if (it.direction == Direction.IN) "+" else "-"}${money(it.amount)}</td></tr>"
        }
        return """
<div style="font-family:Arial,Helvetica,sans-serif;background:#f2f4f7;padding:20px">
  <div style="max-width:560px;margin:0 auto;background:#ffffff;border-radius:16px;overflow:hidden;border:1px solid #e3e7ee">
    <div style="background:#0FB5A3;padding:14px 18px;color:#ffffff;font-weight:800;font-size:20px">cybeth <span style="font-weight:400;font-size:14px">· Cierre del día</span></div>
    <div style="padding:20px 18px">
      <div style="color:#6b7690;font-size:14px">${esc(dateLabel)}</div>
      <div style="color:#6b7690;font-size:13px;margin-top:10px">Entró hoy (aprobado)</div>
      <div style="font-size:34px;font-weight:800;color:#0FB5A3">+${money(totalIn)}</div>
      <div style="color:#6b7690;font-size:13px;margin-bottom:14px">$countIn pagos · Enviado -${money(totalOut)} · Neto ${signed(net)}</div>
      <table style="width:100%;border-collapse:separate;border-spacing:6px 0;margin:0 -6px 16px"><tr>
        ${stat("Entraron", "$countIn", "#0FB5A3")}
        ${stat("En proceso", "${count(TxStatus.EN_PROCESO)}", "#E59A12")}
        ${stat("Por cobrar", "${count(TxStatus.PENDIENTE_COBRO)}", "#3B7BE0")}
        ${stat("Rechazadas", "${count(TxStatus.RECHAZADA)}", "#E25555")}
      </tr></table>
      <div style="font-weight:700;color:#14213D;margin:6px 0">Por cuenta</div>
      <table style="width:100%;border-collapse:collapse;font-size:13px">$accRows</table>
      <div style="font-weight:700;color:#14213D;margin:16px 0 6px">Movimientos (${txs.size})</div>
      <table style="width:100%;border-collapse:collapse;font-size:13px">$txRows</table>
      <div style="margin-top:16px;padding:10px 12px;background:#f6f8fa;border-radius:10px;color:#6b7690;font-size:12px">📎 Va adjunto <b>${esc(fileName)}</b> para abrir en Excel.</div>
    </div>
    <div style="padding:10px 18px;color:#9aa3b5;font-size:11px;border-top:1px solid #eef1f5">Cierre automático de Cybeth SG</div>
  </div>
</div>""".trimIndent()
    }

    // ---------------------------------------------------------------- archivo para Excel

    private fun cell(s: String): String {
        val v = s.replace("\r", " ").replace("\n", " ")
        return if (v.contains(';') || v.contains('"')) "\"" + v.replace("\"", "\"\"") + "\"" else v
    }

    /**
     * CSV separado por punto y coma (así lo abre bien Excel en español) y con la marca UTF-8
     * al inicio para que las tildes se vean bien. Los montos van como números enteros.
     */
    fun csv(): ByteArray {
        val sb = StringBuilder()
        sb.append("﻿")
        sb.append("Cierre del día;${cell(dateLabel)}\r\n")
        sb.append("Entró (aprobado);$totalIn;Pagos;$countIn\r\n")
        sb.append("Enviado (aprobado);$totalOut;Envíos;$countOut\r\n")
        sb.append("Neto;$net\r\n")
        sb.append("En proceso;${sum(TxStatus.EN_PROCESO)};Cantidad;${count(TxStatus.EN_PROCESO)}\r\n")
        sb.append("Pendientes por cobrar;${sum(TxStatus.PENDIENTE_COBRO)};Cantidad;${count(TxStatus.PENDIENTE_COBRO)}\r\n")
        sb.append("Rechazadas;${sum(TxStatus.RECHAZADA)};Cantidad;${count(TxStatus.RECHAZADA)}\r\n")
        sb.append("\r\n")
        sb.append("Cuenta;Entró;Pagos;Enviado;Envíos\r\n")
        byAccount.forEach { sb.append("${cell(it.name)};${it.totalIn};${it.countIn};${it.totalOut};${it.countOut}\r\n") }
        sb.append("\r\n")
        sb.append("Hora;Tipo;Estado;Persona;Banco;Cuenta;Monto;Referencia;Detectado por\r\n")
        val hf = SimpleDateFormat("HH:mm", Locale.US)
        txs.forEach {
            sb.append(
                listOf(
                    hf.format(Date(it.time)),
                    if (it.direction == Direction.IN) "Entrada" else "Enviada",
                    Wording.statusName(it.status),
                    it.counterparty ?: "",
                    it.bank ?: "",
                    it.last4?.let { l -> "****$l" } ?: "",
                    (if (it.direction == Direction.IN) it.amount else -it.amount).toString(),
                    it.reference ?: "",
                    it.source,
                ).joinToString(";") { c -> cell(c) } + "\r\n"
            )
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }
}
