package co.cuadra.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import co.cuadra.app.data.Tx
import co.cuadra.app.data.TxStatus
import co.cuadra.app.service.DayClose
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val DAY = 24 * 60 * 60 * 1000L

/** Un rango para el cierre: desde (00:00) hasta (00:00 del día siguiente). */
private data class Range(val start: Long, val end: Long, val name: String?)

private fun cal(t: Long = System.currentTimeMillis()) = Calendar.getInstance().apply { timeInMillis = t; firstDayOfWeek = Calendar.MONDAY }

private fun presets(): List<Range> {
    val today = startOfDay()
    val tomorrow = today + DAY
    val weekStart = cal(today).apply { set(Calendar.DAY_OF_WEEK, Calendar.MONDAY); if (timeInMillis > today) add(Calendar.WEEK_OF_YEAR, -1) }.timeInMillis
    val monthStart = cal(today).apply { set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
    val prevMonthStart = cal(monthStart).apply { add(Calendar.MONTH, -1) }.timeInMillis
    return listOf(
        Range(weekStart, tomorrow, "Esta semana"),
        Range(weekStart - 7 * DAY, weekStart, "Semana pasada"),
        Range(monthStart, tomorrow, "Este mes"),
        Range(prevMonthStart, monthStart, "Mes pasado"),
        Range(today - 6 * DAY, tomorrow, "Últimos 7 días"),
        Range(today - 29 * DAY, tomorrow, "Últimos 30 días"),
    )
}

/** Ver y enviar cierres de días anteriores o de un periodo (semana, mes, fechas a escoger). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PastClosesDialog(txs: List<Tx>, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(0) } // 0 un día, 1 periodo
    var sel by remember { mutableStateOf<Range?>(null) }
    var pickDates by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val chipColors = FilterChipDefaults.filterChipColors(containerColor = Card2, labelColor = Muted, selectedContainerColor = TextC, selectedLabelColor = Bg)
    val dayFmt = remember { SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "CO")) }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Bg).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Cierres anteriores", color = TextC, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("Cerrar", color = Accent) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                FilterChip(mode == 0, { mode = 0; sel = null; msg = null }, { Text("Un día") }, colors = chipColors)
                FilterChip(mode == 1, { mode = 1; sel = null; msg = null }, { Text("Por periodo") }, colors = chipColors)
            }

            // Lo elegido: resumen y botón de enviar
            sel?.let { rg ->
                val r = remember(rg, txs) { DayReport(txs, rg.start, rg.end, rg.name) }
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardBg)
                        .border(2.dp, Accent.copy(alpha = 0.6f), RoundedCornerShape(16.dp)).padding(14.dp)
                ) {
                    Text(r.kind, color = Muted, fontSize = 12.sp)
                    Text(r.dateLabel.replaceFirstChar { it.uppercase() }, color = TextC, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("+" + money(r.totalIn), color = Accent, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
                    Text("${r.countIn} pagos · enviado -${money(r.totalOut)} · neto ${if (r.net < 0) "-" else "+"}${money(kotlin.math.abs(r.net))}", color = Muted, fontSize = 12.5.sp)
                    Text("En proceso ${r.count(TxStatus.EN_PROCESO)} · Por cobrar ${r.count(TxStatus.PENDIENTE_COBRO)} · Rechazadas ${r.count(TxStatus.RECHAZADA)} · ${r.txs.size} movimientos", color = Muted, fontSize = 12.sp)
                    if (r.byAccount.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        r.byAccount.forEach { Text("• ${it.name}: +${money(it.totalIn)} / -${money(it.totalOut)}", color = TextC, fontSize = 12.5.sp) }
                    }
                    Button(
                        enabled = !busy && r.txs.isNotEmpty(),
                        onClick = {
                            busy = true; msg = "Enviando…"
                            scope.launch {
                                val res = DayClose.send(ctx, rg.start, rg.end, rg.name)
                                msg = if (res.ok) "✓ ${res.message}" else res.message
                                busy = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                    ) { Text(if (r.txs.isEmpty()) "Sin movimientos para enviar" else "Enviar a la jefe (correo + WhatsApp)") }
                    msg?.let { Text(it, color = if (it.startsWith("✓")) Accent else Warn, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
                }
                Spacer(Modifier.height(10.dp))
            }

            if (mode == 0) {
                Text("Toca un día para ver su cierre:", color = Muted, fontSize = 12.5.sp)
                val days = remember(txs) { (0 until 60).map { startOfDay(daysAgo = it) } }
                LazyColumn(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    items(days) { d ->
                        val r = remember(d, txs) { DayReport(txs, d) }
                        val on = sel?.start == d && sel?.end == d + DAY
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (on) Card2 else CardBg).clickable { sel = Range(d, d + DAY, null); msg = null }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(dayLabel(d).let { if (it == "Hoy" || it == "Ayer") "$it · " + dayFmt.format(Date(d)) else it }, color = TextC, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("${r.countIn} pagos · ${r.txs.size} movimientos", color = Muted, fontSize = 12.sp)
                            }
                            Text("+" + money(r.totalIn), color = if (r.totalIn > 0) Accent else Muted, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Text("Elige un periodo:", color = Muted, fontSize = 12.5.sp)
                val ps = remember { presets() }
                Column(Modifier.padding(top = 6.dp)) {
                    ps.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { p ->
                                FilterChip(sel == p, { sel = p; msg = null }, { Text(p.name ?: "") }, colors = chipColors)
                            }
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        FilterChip(sel != null && sel?.name == "Fechas elegidas", { pickDates = true }, { Text("📅 Elegir fechas…") }, colors = chipColors)
                    }
                }
            }
        }
    }

    if (pickDates) {
        val st = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { pickDates = false },
            confirmButton = {
                TextButton(onClick = {
                    val a = st.selectedStartDateMillis; val b = st.selectedEndDateMillis ?: a
                    if (a != null && b != null) {
                        // El selector da fechas en UTC: pasarlas a la medianoche local
                        fun local(utc: Long): Long {
                            val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
                            return Calendar.getInstance().apply {
                                clear(); set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH))
                            }.timeInMillis
                        }
                        sel = Range(local(a), local(b) + DAY, "Fechas elegidas"); msg = null
                    }
                    pickDates = false
                }) { Text("Listo", color = Accent) }
            },
            dismissButton = { TextButton(onClick = { pickDates = false }) { Text("Cancelar") } },
        ) {
            DateRangePicker(state = st, modifier = Modifier.weight(1f), title = { Text("Desde – hasta", modifier = Modifier.padding(16.dp)) })
        }
    }
}
