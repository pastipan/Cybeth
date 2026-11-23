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
    val fileName: String = "Cybeth_cierre_$fileDate.xlsx"

    val missingAmount: Int = txs.count { it.amount == 0L }

    fun subject(): String = "📊 Cierre del día $fileDate · Entró ${money(totalIn)} ($countIn pagos)"

    private fun signed(n: Long) = (if (n < 0) "-" else "+") + money(kotlin.math.abs(n))

    // ---------------------------------------------------------------- texto

    fun plain(): String = buildString {
        appendLine("CIERRE DEL DÍA · $dateLabel")
        appendLine()
        appendLine("Entró (aprobado): ${money(totalIn)} en $countIn pagos")
        appendLine("Enviado (aprobado): ${money(totalOut)} en $countOut envíos")
        appendLine("Neto: ${signed(net)}")
        if (missingAmount > 0) appendLine("⚠️ $missingAmount envío(s) sin monto: el neto puede no ser exacto")
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

    private fun statusColor(t: Tx) = when (t.status) {
        TxStatus.APROBADA -> if (t.direction == Direction.IN) "#0B8F80" else "#C0392B"
        TxStatus.EN_PROCESO -> "#B7791F"
        TxStatus.PENDIENTE_COBRO -> "#2F6BD6"
        TxStatus.RECHAZADA -> "#9AA3B5"
    }

    private fun card(label: String, value: String, color: String, sub: String) =
        """<td width="25%" style="padding:4px"><div style="background:#F6F8FA;border-radius:12px;padding:12px 6px;text-align:center">
<div style="font-size:20px;font-weight:800;color:$color">${esc(value)}</div>
<div style="font-size:11px;color:#6B7690;margin-top:2px">${esc(label)}</div>
<div style="font-size:11px;color:#9AA3B5">${esc(sub)}</div></div></td>"""

    /**
     * Correo del cierre. [logoSrc]: "cid:logo" cuando el logo va adjunto adentro del correo.
     */
    fun html(logoSrc: String? = null): String {
        val th = "padding:8px 6px;background:#EAF7F5;color:#14213D;font-size:12px;font-weight:700;border-bottom:1px solid #DDE2EA"
        val td = "padding:8px 6px;border-bottom:1px solid #EEF1F5;font-size:13px;color:#14213D;vertical-align:top"
        val accRows = if (byAccount.isEmpty()) "<tr><td colspan=\"3\" style=\"$td;color:#6B7690\">Sin movimientos</td></tr>"
        else byAccount.joinToString("") {
            "<tr><td style=\"$td;font-weight:600\">${esc(it.name)}</td>" +
                "<td style=\"$td;text-align:right;color:#0B8F80;font-weight:700;white-space:nowrap\">+${money(it.totalIn)}<div style=\"font-size:11px;color:#9AA3B5;font-weight:400\">${it.countIn} pagos</div></td>" +
                "<td style=\"$td;text-align:right;color:#C0392B;font-weight:700;white-space:nowrap\">-${money(it.totalOut)}<div style=\"font-size:11px;color:#9AA3B5;font-weight:400\">${it.countOut} envíos</div></td></tr>"
        }
        val txRows = if (txs.isEmpty()) "<tr><td colspan=\"4\" style=\"$td;color:#6B7690\">Sin movimientos</td></tr>"
        else txs.joinToString("") {
            val strike = if (it.status == TxStatus.RECHAZADA) "text-decoration:line-through;" else ""
            val amount = if (it.amount == 0L) "sin monto" else (if (it.direction == Direction.IN) "+" else "-") + money(it.amount)
            "<tr><td style=\"$td;color:#6B7690;white-space:nowrap\">${hour(it.time)}</td>" +
                "<td style=\"$td\"><b>${esc(it.counterparty ?: if (it.direction == Direction.IN) "Recibida" else "Enviada")}</b>" +
                "<div style=\"font-size:11px;color:#6B7690\">${esc(Wording.accountLine(it))}</div></td>" +
                "<td style=\"$td;text-align:center\"><span style=\"display:inline-block;padding:2px 8px;border-radius:99px;font-size:11px;font-weight:700;color:${statusColor(it)};background:#F6F8FA\">${esc(Wording.statusName(it.status))}</span></td>" +
                "<td style=\"$td;text-align:right;font-weight:800;white-space:nowrap;color:${statusColor(it)};$strike\">$amount</td></tr>"
        }
        val logo = if (logoSrc != null) "<img src=\"$logoSrc\" width=\"44\" height=\"44\" alt=\"cybeth\" style=\"display:block;border-radius:10px\">" else ""
        val warn = if (missingAmount > 0) "<div style=\"margin:0 0 14px;padding:10px 12px;background:#FFF4E0;border-radius:10px;color:#8A5A00;font-size:13px\">⚠️ $missingAmount envío(s) sin monto: el neto puede no ser exacto.</div>" else ""
        return """
<div style="font-family:Arial,Helvetica,sans-serif;background:#F2F4F7;padding:20px 10px">
 <div style="max-width:600px;margin:0 auto;background:#FFFFFF;border-radius:18px;overflow:hidden;border:1px solid #E3E7EE">
  <table width="100%" cellpadding="0" cellspacing="0" style="border-bottom:4px solid #0FB5A3"><tr>
   <td style="padding:16px 18px;width:52px">$logo</td>
   <td style="padding:16px 0"><div style="font-size:26px;font-weight:800;color:#14213D;letter-spacing:-0.5px">cybeth</div>
     <div style="font-size:13px;color:#6B7690">Cierre del día</div></td>
   <td style="padding:16px 18px;text-align:right;font-size:13px;color:#6B7690">${esc(dateLabel)}</td>
  </tr></table>
  <div style="padding:20px 18px">
   <div style="font-size:13px;color:#6B7690">Total que entró (aprobado)</div>
   <div style="font-size:38px;font-weight:800;color:#0FB5A3;line-height:1.1">+${money(totalIn)}</div>
   <div style="font-size:13px;color:#6B7690;margin:4px 0 16px">$countIn pagos recibidos</div>
   <table width="100%" cellpadding="0" cellspacing="0" style="margin:0 -4px 16px"><tr>
    ${card("Entró", money(totalIn), "#0B8F80", "$countIn pagos")}
    ${card("Enviado", money(totalOut), "#C0392B", "$countOut envíos")}
    ${card("Neto", signed(net), if (net < 0) "#C0392B" else "#14213D", "entró − enviado")}
    ${card("Pendientes", "${count(TxStatus.EN_PROCESO) + count(TxStatus.PENDIENTE_COBRO)}", "#B7791F", "${count(TxStatus.RECHAZADA)} rechazadas")}
   </tr></table>
   $warn
   <div style="font-size:15px;font-weight:800;color:#14213D;margin:6px 0 8px">🏦 Por cuenta</div>
   <table width="100%" cellpadding="0" cellspacing="0" style="border-collapse:collapse">
    <tr><th align="left" style="$th">Cuenta</th><th align="right" style="$th">Entró</th><th align="right" style="$th">Enviado</th></tr>
    $accRows
   </table>
   <div style="font-size:15px;font-weight:800;color:#14213D;margin:20px 0 8px">📋 Movimientos (${txs.size})</div>
   <table width="100%" cellpadding="0" cellspacing="0" style="border-collapse:collapse">
    <tr><th align="left" style="$th">Hora</th><th align="left" style="$th">Persona y cuenta</th><th style="$th">Estado</th><th align="right" style="$th">Monto</th></tr>
    $txRows
   </table>
   <div style="margin-top:18px;padding:10px 12px;background:#EAF7F5;border-radius:10px;color:#0B6E63;font-size:12px">📎 Va adjunto <b>${esc(fileName)}</b> con todo el detalle para Excel.</div>
  </div>
  <div style="padding:12px 18px;color:#9AA3B5;font-size:11px;border-top:1px solid #EEF1F5">Cierre automático de <b style="color:#0FB5A3">cybeth</b> SG</div>
 </div>
</div>""".trimIndent()
    }

    // ---------------------------------------------------------------- WhatsApp

    fun whatsapp(): String = buildString {
        appendLine("*cybeth · Cierre del día*")
        appendLine("_${dateLabel}_")
        appendLine()
        appendLine("💰 *Entró:* ${money(totalIn)}  ($countIn pagos)")
        appendLine("↗️ *Enviado:* ${money(totalOut)}  ($countOut envíos)")
        appendLine("📊 *Neto:* ${signed(net)}")
        appendLine("⏳ En proceso: ${count(TxStatus.EN_PROCESO)} · 🕓 Por cobrar: ${count(TxStatus.PENDIENTE_COBRO)} · ❌ Rechazadas: ${count(TxStatus.RECHAZADA)}")
        if (missingAmount > 0) appendLine("⚠️ $missingAmount envío(s) sin monto")
        appendLine()
        appendLine("🏦 *Por cuenta*")
        if (byAccount.isEmpty()) appendLine("Sin movimientos")
        byAccount.forEach { appendLine("• ${it.name}: +${money(it.totalIn)} (${it.countIn}) / -${money(it.totalOut)} (${it.countOut})") }
        appendLine()
        appendLine("📋 *Movimientos*")
        if (txs.isEmpty()) appendLine("Sin movimientos")
        txs.forEach {
            val amount = if (it.amount == 0L) "sin monto" else (if (it.direction == Direction.IN) "+" else "-") + money(it.amount)
            val icon = when (it.status) {
                TxStatus.APROBADA -> if (it.direction == Direction.IN) "✅" else "↗️"
                TxStatus.EN_PROCESO -> "⏳"; TxStatus.PENDIENTE_COBRO -> "🕓"; TxStatus.RECHAZADA -> "❌"
            }
            appendLine("$icon ${hour(it.time)} · ${it.counterparty ?: "—"} · ${it.bank ?: ""} · *$amount*")
        }
        appendLine()
        append("_Enviado por cybeth SG_")
    }

    // ---------------------------------------------------------------- archivo de Excel (.xlsx)

    /** Excel ordenado: logo y nombre arriba, resumen, por cuenta y la tabla de movimientos. */
    fun xlsx(logoPng: ByteArray? = null): ByteArray {
        val x = Xlsx()
        // Encabezado con logo (columna A) y nombre
        x.height(0, 30.0); x.height(1, 20.0); x.height(2, 18.0)
        x.text(0, 1, "cybeth", Xlsx.S_TITLE)
        x.text(1, 1, "Cierre del día · $dateLabel", Xlsx.S_SUB)
        x.text(2, 1, "Generado por cybeth SG", Xlsx.S_SUB)

        var r = 4
        x.text(r, 0, "RESUMEN DEL DÍA", Xlsx.S_SECTION); (1..5).forEach { x.text(r, it, "", Xlsx.S_SECTION) }; x.height(r, 22.0); r++
        x.text(r, 0, "Concepto", Xlsx.S_TH); x.text(r, 1, "Valor", Xlsx.S_TH); x.text(r, 2, "Cantidad", Xlsx.S_TH); r++
        fun line(label: String, v: Long, n: Int) {
            x.text(r, 0, label, Xlsx.S_TD); x.num(r, 1, v, Xlsx.S_TD_MONEY); x.num(r, 2, n, Xlsx.S_TD_NUM); r++
        }
        line("Entró (aprobado)", totalIn, countIn)
        line("Enviado (aprobado)", -totalOut, countOut)
        x.text(r, 0, "NETO", Xlsx.S_TH); x.num(r, 1, net, Xlsx.S_TD_MONEY); x.text(r, 2, "", Xlsx.S_TD); r++
        line("En proceso", sum(TxStatus.EN_PROCESO), count(TxStatus.EN_PROCESO))
        line("Pendientes por cobrar", sum(TxStatus.PENDIENTE_COBRO), count(TxStatus.PENDIENTE_COBRO))
        line("Rechazadas", sum(TxStatus.RECHAZADA), count(TxStatus.RECHAZADA))
        if (missingAmount > 0) { x.text(r, 0, "⚠ $missingAmount envío(s) sin monto: el neto puede no ser exacto", Xlsx.S_TD_WARN); x.merge("A${r + 1}:C${r + 1}"); r++ }

        r++
        x.text(r, 0, "POR CUENTA", Xlsx.S_SECTION); (1..5).forEach { x.text(r, it, "", Xlsx.S_SECTION) }; x.height(r, 22.0); r++
        listOf("Cuenta", "Entró", "Pagos", "Enviado", "Envíos").forEachIndexed { i, h -> x.text(r, i, h, Xlsx.S_TH) }; r++
        if (byAccount.isEmpty()) { x.text(r, 0, "Sin movimientos", Xlsx.S_TD); r++ }
        byAccount.forEach {
            x.text(r, 0, it.name, Xlsx.S_TD); x.num(r, 1, it.totalIn, Xlsx.S_TD_MONEY); x.num(r, 2, it.countIn, Xlsx.S_TD_NUM)
            x.num(r, 3, -it.totalOut, Xlsx.S_TD_MONEY); x.num(r, 4, it.countOut, Xlsx.S_TD_NUM); r++
        }

        r++
        x.text(r, 0, "MOVIMIENTOS (${txs.size})", Xlsx.S_SECTION); (1..5).forEach { x.text(r, it, "", Xlsx.S_SECTION) }; x.height(r, 22.0); r++
        listOf("Hora", "Persona", "Cuenta", "Estado", "Monto", "Detectado por").forEachIndexed { i, h -> x.text(r, i, h, Xlsx.S_TH) }; r++
        if (txs.isEmpty()) { x.text(r, 0, "Sin movimientos", Xlsx.S_TD); r++ }
        txs.forEach {
            x.text(r, 0, hour(it.time), Xlsx.S_TD)
            x.text(r, 1, it.counterparty ?: if (it.direction == Direction.IN) "Recibida" else "Enviada", Xlsx.S_TD)
            x.text(r, 2, (it.bank ?: "") + (it.last4?.let { l -> " ****$l" } ?: ""), Xlsx.S_TD)
            val st = when (it.status) {
                TxStatus.APROBADA -> Xlsx.S_TD_OK
                TxStatus.RECHAZADA -> Xlsx.S_TD_BAD
                else -> Xlsx.S_TD_WARN
            }
            x.text(r, 3, Wording.statusLabel(it), st)
            if (it.amount == 0L) x.text(r, 4, "sin monto", Xlsx.S_TD_WARN)
            else x.num(r, 4, if (it.direction == Direction.IN) it.amount else -it.amount, Xlsx.S_TD_MONEY)
            x.text(r, 5, it.source, Xlsx.S_TD)
            r++
        }
        return x.build("Cierre $fileDate", listOf(26.0, 26.0, 24.0, 18.0, 16.0, 16.0), logoPng)
    }
}
