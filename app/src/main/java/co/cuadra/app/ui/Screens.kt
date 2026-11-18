package co.cuadra.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import co.cuadra.app.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import co.cuadra.app.data.Direction
import co.cuadra.app.data.Prefs
import co.cuadra.app.data.TxParser
import co.cuadra.app.data.TxStatus
import co.cuadra.app.data.filterTxs
import co.cuadra.app.data.Tx
import co.cuadra.app.service.Announcer
import co.cuadra.app.service.ForwardMail
import co.cuadra.app.service.MailChecker
import co.cuadra.app.service.MailWorker
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- helpers

fun listenerEnabled(ctx: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

fun openListenerSettings(ctx: Context) {
    ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun canPostNotifications(ctx: Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

@Composable
fun SectionTitle(text: String) {
    Text(text, color = TextC, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(start = 2.dp, top = 18.dp, bottom = 8.dp))
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .border(1.dp, Line, RoundedCornerShape(18.dp))
            .padding(16.dp),
        content = content
    )
}

@Composable
fun BankAvatar(bank: String?) {
    val (bg, fg) = bankColor(bank)
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) {
        Text((bank ?: "?").take(2).uppercase(), color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
    }
}

@Composable
fun TxRow(t: Tx, onClick: () -> Unit) {
    val out = t.direction == Direction.OUT
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .border(1.dp, Line, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BankAvatar(t.bank)
        Spacer(Modifier.width(12.dp))
        val rejected = t.status == TxStatus.RECHAZADA
        Column(Modifier.weight(1f)) {
            Text(
                (if (out) "↗ " else "↙ ") + (t.counterparty ?: if (out) "Transferencia enviada" else "Transferencia recibida"),
                color = TextC, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                Wording.accountLine(t) + " · " + hour(t.time),
                color = Muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(6.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                (if (out) "-" else "+") + money(t.amount),
                color = if (rejected) Muted else if (out) OutRed else Accent,
                fontWeight = FontWeight.Bold, fontSize = 15.sp,
                textDecoration = if (rejected) TextDecoration.LineThrough else null
            )
            Spacer(Modifier.height(3.dp))
            StatusPill(t)
        }
    }
}

fun statusColor(s: TxStatus): Color = when (s) {
    TxStatus.APROBADA -> Accent
    TxStatus.EN_PROCESO -> Warn
    TxStatus.PENDIENTE_COBRO -> InfoBlue
    TxStatus.RECHAZADA -> OutRed
}

/** Etiqueta de color con el estado: "Entró ✓", "En proceso", "Pendiente por cobrar", "Rechazada ✗". */
@Composable
fun StatusPill(t: Tx, big: Boolean = false) {
    val c = statusColor(t.status)
    Text(
        Wording.statusLabel(t),
        color = c, fontWeight = FontWeight.Bold, fontSize = if (big) 14.sp else 11.sp,
        modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(c.copy(alpha = 0.15f))
            .padding(horizontal = if (big) 12.dp else 8.dp, vertical = if (big) 5.dp else 2.dp)
    )
}

/** Lo último que pasó, en grande (como Fortu): quién, cuánto, a qué cuenta y si entró o no. */
@Composable
fun LatestCard(t: Tx, onClick: () -> Unit) {
    val rejected = t.status == TxStatus.RECHAZADA
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(18.dp))
            .background(CardBg).border(2.dp, statusColor(t.status).copy(alpha = 0.6f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Último movimiento", color = Muted, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
            Text(ago(t.time), color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BankAvatar(t.bank)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(Wording.headline(t), color = TextC, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(Wording.accountLine(t), color = Muted, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (t.direction == Direction.OUT) "-" else "+") + money(t.amount),
                color = if (rejected) Muted else if (t.direction == Direction.OUT) OutRed else Accent,
                fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, modifier = Modifier.weight(1f),
                textDecoration = if (rejected) TextDecoration.LineThrough else null
            )
            StatusPill(t, big = true)
        }
    }
}

// ---------------------------------------------------------------- INICIO

@Composable
fun HomeScreen(
    txs: List<Tx>,
    resumeTick: Int,
    onOpen: (Tx) -> Unit,
    onSeeAll: () -> Unit,
    onSettings: () -> Unit,
    onBankClick: (String?) -> Unit,
    onDirClick: (Direction) -> Unit,
    onStatusClick: (TxStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val listenerOn = remember(resumeTick) { listenerEnabled(ctx) }
    val emailOn = remember(resumeTick) { Prefs(ctx).emailLinked }
    val today = txs.filter { isToday(it.time) }
    // Solo cuenta la plata que de verdad entró o salió (aprobada)
    val okToday = today.filter { it.status == TxStatus.APROBADA }
    val inSum = okToday.filter { it.direction == Direction.IN }.sumOf { it.amount }
    val outSum = okToday.filter { it.direction == Direction.OUT }.sumOf { it.amount }
    val net = inSum - outSum

    LazyColumn(modifier.fillMaxSize().background(Bg).padding(horizontal = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                // Logo de Cybeth tal como el original: ícono + "cybeth" en azul oscuro sobre blanco
                Row(
                    Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White).padding(start = 8.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(painterResource(R.drawable.logo_cybeth), contentDescription = "Cybeth", modifier = Modifier.size(34.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("cybeth", color = Navy, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, letterSpacing = (-0.5).sp)
                }
                Spacer(Modifier.weight(1f))
                StatusDot(listenerOn, if (listenerOn) "Escuchando" else "Apagado")
            }
        }
        if (!listenerOn) item {
            Warning(
                "Falta un permiso",
                "Para saber cuándo te llega plata, Cybeth necesita leer las notificaciones de Nequi y de tus bancos.",
                "Activar ahora"
            ) { openListenerSettings(ctx) }
        }
        if (!emailOn) item {
            Warning("Enlaza tu correo (opcional)", "Así también se leen los correos que mandan los bancos.", "Enlazar correo", onSettings)
        }
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF0E4A44), Color(0xFF14213D))))
                    .padding(18.dp)
            ) {
                Column(Modifier.clickable { onDirClick(Direction.IN) }) {
                    Text("Entró hoy  ›", color = Color(0xFFA8C9BB), fontSize = 13.sp)
                    Text("+" + money(inSum), color = TextC, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp)
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    Column(Modifier.weight(1f).clickable { onDirClick(Direction.OUT) }) {
                        Text("Enviaste hoy ›", color = Color(0xFFA8C9BB), fontSize = 12.sp)
                        Text("-" + money(outSum), color = Color(0xFFFF9B9B), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Neto", color = Color(0xFFA8C9BB), fontSize = 12.sp)
                        Text((if (net < 0) "-" else "+") + money(kotlin.math.abs(net)), color = if (net < 0) Color(0xFFFF9B9B) else Color(0xFF7EF0BD), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Movimientos", color = Color(0xFFA8C9BB), fontSize = 12.sp)
                        Text("${today.size}", color = TextC, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            }
        }
        txs.firstOrNull()?.let { last -> item { LatestCard(last) { onOpen(last) } } }
        item {
            SectionTitle("Estado de hoy")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    TxStatus.APROBADA to "Entraron",
                    TxStatus.EN_PROCESO to "En proceso",
                    TxStatus.PENDIENTE_COBRO to "Por cobrar",
                    TxStatus.RECHAZADA to "Rechazadas",
                ).forEach { (st, label) ->
                    val n = if (st == TxStatus.APROBADA) today.count { it.status == st && it.direction == Direction.IN } else today.count { it.status == st }
                    val c = statusColor(st)
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(c.copy(alpha = 0.12f))
                            .clickable { onStatusClick(st) }.padding(vertical = 10.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("$n", color = c, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        Text(label, color = TextC, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }
        // Todos los bancos donde ha habido movimientos; los montos son los de hoy
        val byBank = txs.groupBy { it.bank ?: "Otro" }
        if (byBank.isNotEmpty()) {
            item { SectionTitle("Por banco · hoy") }
            item {
                Panel {
                    byBank.entries.sortedByDescending { e -> e.value.filter { isToday(it.time) }.sumOf { it.amount } }.forEachIndexed { i, (bank, list) ->
                        if (i > 0) HorizontalDivider(color = Line, modifier = Modifier.padding(vertical = 8.dp))
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onBankClick(if (bank == "Otro") null else bank) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BankAvatar(bank)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(bank, color = TextC, fontWeight = FontWeight.SemiBold)
                                Text("${list.size} movimiento${if (list.size == 1) "" else "s"} · ver ›", color = Muted, fontSize = 11.5.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("+" + money(list.filter { it.direction == Direction.IN && isToday(it.time) && it.status == TxStatus.APROBADA }.sumOf { it.amount }), color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("-" + money(list.filter { it.direction == Direction.OUT && isToday(it.time) && it.status == TxStatus.APROBADA }.sumOf { it.amount }), color = OutRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SectionTitle("Últimos movimientos") }
                if (txs.isNotEmpty()) TextButton(onClick = onSeeAll) { Text("Ver todos", color = Accent) }
            }
        }
        if (txs.isEmpty()) item {
            Panel {
                Text("Aún no hay transferencias", color = TextC, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Cuando te llegue o envíes plata por Nequi o tu banco, aparecerá aquí sola. " +
                        "Para probar, pídele a alguien que te mande \$1.000 por Nequi.",
                    color = Muted, fontSize = 13.sp
                )
            }
        }
        items(txs.take(6), key = { it.id }) { TxRow(it) { onOpen(it) } }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun StatusDot(on: Boolean, label: String) {
    Row(
        Modifier.clip(RoundedCornerShape(99.dp)).background(Card2).border(1.dp, Line, RoundedCornerShape(99.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (on) Accent else OutRed))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Muted, fontSize = 12.sp)
    }
}

@Composable
fun Warning(title: String, body: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(16.dp))
            .background(Color(0x14FFB547)).border(1.dp, Color(0x55FFB547), RoundedCornerShape(16.dp)).padding(14.dp)
    ) {
        Text("⚠️  $title", color = Warn, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(body, color = TextC, fontSize = 13.5.sp)
        Spacer(Modifier.height(10.dp))
        Button(onClick = onAction, colors = ButtonDefaults.buttonColors(containerColor = Warn, contentColor = Color(0xFF2A1A00))) { Text(action, fontWeight = FontWeight.Bold) }
    }
}

// ---------------------------------------------------------------- MOVIMIENTOS

@Composable
fun TxScreen(
    txs: List<Tx>,
    onOpen: (Tx) -> Unit,
    dir: Direction?,
    onDir: (Direction?) -> Unit,
    bank: String?,
    onBank: (String?) -> Unit,
    status: TxStatus?,
    onStatus: (TxStatus?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val banks = txs.mapNotNull { it.bank }.distinct().sorted()
    val list = filterTxs(txs, dir, bank, status)
    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = Card2, labelColor = Muted,
        selectedContainerColor = TextC, selectedLabelColor = Bg
    )

    LazyColumn(modifier.fillMaxSize().background(Bg).padding(horizontal = 16.dp)) {
        item { SectionTitle(if (bank != null) "Movimientos · $bank" else "Movimientos") }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf<Pair<Direction?, String>>(null to "Todo", Direction.IN to "↙ Entradas", Direction.OUT to "↗ Enviadas").forEach { (d, l) ->
                    FilterChip(selected = dir == d, onClick = { onDir(d) }, label = { Text(l) }, colors = chipColors)
                }
            }
        }
        item {
            Row(Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = status == null, onClick = { onStatus(null) }, label = { Text("Todos los estados") }, colors = chipColors)
                TxStatus.values().forEach { st ->
                    FilterChip(selected = status == st, onClick = { onStatus(st) }, label = { Text(Wording.statusName(st)) }, colors = chipColors)
                }
            }
        }
        if (banks.isNotEmpty() || bank != null) item {
            Row(Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = bank == null, onClick = { onBank(null) }, label = { Text("Todos los bancos") }, colors = chipColors)
                (if (bank != null && bank !in banks) banks + bank else banks).forEach { b ->
                    FilterChip(selected = bank == b, onClick = { onBank(b) }, label = { Text(b) }, colors = chipColors)
                }
            }
        }
        if (list.isNotEmpty()) item {
            val i = list.filter { it.direction == Direction.IN && it.status == TxStatus.APROBADA }.sumOf { it.amount }
            val o = list.filter { it.direction == Direction.OUT && it.status == TxStatus.APROBADA }.sumOf { it.amount }
            Text("Entradas +${money(i)}   ·   Enviadas -${money(o)}", color = Muted, fontSize = 12.5.sp, modifier = Modifier.padding(vertical = 8.dp))
        }
        if (list.isEmpty()) item {
            Text("No hay movimientos con ese filtro.", color = Muted, modifier = Modifier.padding(vertical = 30.dp).fillMaxWidth())
        }
        var lastDay = ""
        list.forEach { t ->
            val d = dayLabel(t.time)
            if (d != lastDay) {
                lastDay = d
                item(key = "h-${t.id}") { Text(d, color = Muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)) }
            }
            item(key = t.id) { TxRow(t) { onOpen(t) } }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun TxDetailDialog(t: Tx, onClose: () -> Unit, onDelete: () -> Unit) {
    val out = t.direction == Direction.OUT
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = CardBg,
        title = {
            Text((if (out) "-" else "+") + money(t.amount), color = if (out) OutRed else Accent, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(Wording.headline(t), color = TextC, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                StatusPill(t, big = true)
                Spacer(Modifier.height(10.dp))
                Detail("Estado", Wording.statusName(t.status))
                Detail("Cuenta", Wording.accountLine(t))
                Detail(if (out) "Para" else "De", t.counterparty ?: "No identificado")
                Detail(if (out) "Salió de" else "Llegó a", t.bank ?: "No identificado")
                t.last4?.let { Detail("Cuenta", "****$it") }
                t.reference?.let { Detail("Referencia", it) }
                Detail("Fecha", whenText(t.time))
                Detail("Detectado por", t.source)
                Spacer(Modifier.height(12.dp))
                Text("Mensaje original", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                SelectionContainer {
                    Text(
                        t.raw, color = Color(0xFF222222), fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White).padding(10.dp)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Cerrar", color = Accent) } },
        dismissButton = { TextButton(onClick = onDelete) { Text("Borrar", color = OutRed) } },
    )
}

@Composable
private fun Detail(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(k, color = Muted, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
        Text(v, color = TextC, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
    }
}

// ---------------------------------------------------------------- AJUSTES

@Composable
fun SettingsScreen(
    resumeTick: Int,
    onLock: () -> Unit,
    requestBiometric: (onOk: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var refresh by remember { mutableIntStateOf(0) }
    val listenerOn = remember(resumeTick, refresh) { listenerEnabled(ctx) }
    var notifOk by remember(resumeTick) { mutableStateOf(canPostNotifications(ctx)) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifOk = it }

    Column(modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        SecurityPanel(onLockNow = onLock, requestBiometric = requestBiometric)

        SectionTitle("Detectar transferencias")
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Leer notificaciones de bancos", color = TextC, fontWeight = FontWeight.SemiBold)
                    Text(if (listenerOn) "Activo ✓" else "Desactivado — toca Activar", color = if (listenerOn) Accent else OutRed, fontSize = 12.5.sp)
                }
                OutlinedButton(onClick = { openListenerSettings(ctx) }) { Text(if (listenerOn) "Revisar" else "Activar") }
            }
            Text(
                "Se leen las apps de bancos (Nequi, Bancolombia, Daviplata…), los SMS de bancos y los correos de bancos que llegan a Gmail u Outlook en este celular. WhatsApp y demás apps se ignoran. " +
                    "Compras, pagos PSE, pagos en línea y retiros no se registran.",
                color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)
            )
            HorizontalDivider(color = Line, modifier = Modifier.padding(vertical = 12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Que Android no la cierre", color = TextC, fontWeight = FontWeight.SemiBold)
                    Text("Quita la optimización de batería para Cybeth", color = Muted, fontSize = 12.5.sp)
                }
                OutlinedButton(onClick = {
                    try {
                        ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    } catch (_: Exception) {
                        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
                    }
                }) { Text("Abrir") }
            }
            prefs.lastBankNotif?.let {
                Text("Última notificación de banco vista: $it", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            }
        }

        SectionTitle("Avisos")
        Panel {
            if (!notifOk && Build.VERSION.SDK_INT >= 33) {
                Button(onClick = { permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Permitir notificaciones de Cybeth")
                }
                Spacer(Modifier.height(8.dp))
            }
            ToggleRow("Decir en voz alta cuando llega plata", "\"Te llegaron 50.000 pesos de Laura por Nequi\"", prefs.voiceIn) { prefs.voiceIn = it; refresh++ }
            ToggleRow("Decir también cuando envío plata", null, prefs.voiceOut) { prefs.voiceOut = it; refresh++ }
            ToggleRow("Mostrar notificación de Cybeth", null, prefs.notify) { prefs.notify = it; refresh++ }
            OutlinedButton(onClick = { Announcer.speak(ctx, "Te llegaron 50000 pesos de Laura Méndez por Nequi") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text("🔊 Probar la voz")
            }
        }

        EmailSection(prefs, onChanged = { refresh++ })

        ForwardSection(prefs, refresh)

        TestParserSection()

        Text(
            "Todo se guarda solo en este celular.",
            color = Muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
        )
    }
}

@Composable
private fun ToggleRow(title: String, sub: String?, value: Boolean, onChange: (Boolean) -> Unit) {
    var v by remember { mutableStateOf(value) }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextC, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp)
            sub?.let { Text(it, color = Muted, fontSize = 12.sp) }
        }
        Switch(
            checked = v, onCheckedChange = { v = it; onChange(it) },
            colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Color.White)
        )
    }
}

@Composable
private fun EmailSection(prefs: Prefs, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var linked by remember { mutableStateOf(prefs.emailLinked) }
    var email by remember { mutableStateOf(prefs.email ?: "") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var showHelp by remember { mutableStateOf(false) }

    SectionTitle("Correo electrónico")
    Panel {
        if (linked) {
            Text(prefs.email ?: "", color = TextC, fontWeight = FontWeight.SemiBold)
            Text("Se revisa cada 15 minutos · última vez ${ago(prefs.lastEmailCheck)}", color = Muted, fontSize = 12.5.sp)
            prefs.lastEmailResult?.let { Text(it, color = Muted, fontSize = 12.5.sp) }
            msg?.let { Text(it, color = Accent, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy, onClick = {
                    busy = true; msg = "Revisando…"
                    scope.launch {
                        val r = MailChecker.check(ctx)
                        msg = r.message; busy = false; onChanged()
                    }
                }) { Text("Revisar ahora") }
                OutlinedButton(onClick = {
                    prefs.unlinkEmail(); MailWorker.cancel(ctx); linked = false; email = ""; msg = null; onChanged()
                }) { Text("Desenlazar") }
            }
        } else {
            Text(
                "Cybeth lee los correos que te mandan los bancos (solo lectura). Necesitas una \"contraseña de aplicación\" de Google — no es tu clave normal.",
                color = Muted, fontSize = 13.sp
            )
            TextButton(onClick = { showHelp = !showHelp }) { Text(if (showHelp) "Ocultar pasos" else "¿Cómo saco la contraseña de aplicación?", color = Accent) }
            if (showHelp) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card2).padding(12.dp)) {
                    listOf(
                        "1. Activa la verificación en 2 pasos en tu cuenta de Google (si no la tienes).",
                        "2. Abre myaccount.google.com/apppasswords",
                        "3. En nombre escribe \"Cybeth\" y toca Crear.",
                        "4. Google te muestra 16 letras. Cópialas y pégalas abajo.",
                    ).forEach { Text(it, color = TextC, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp)) }
                    TextButton(onClick = {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://myaccount.google.com/apppasswords")))
                    }) { Text("Abrir página de Google", color = Accent) }
                }
            }
            OutlinedTextField(
                value = email, onValueChange = { email = it.trim() }, label = { Text("Correo (Gmail)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
            OutlinedTextField(
                value = pass, onValueChange = { pass = it }, label = { Text("Contraseña de aplicación (16 letras)") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
            msg?.let { Text(it, color = if (it.startsWith("✓")) Accent else OutRed, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
            Button(
                enabled = !busy && email.contains("@") && pass.replace(" ", "").length >= 8,
                onClick = {
                    busy = true; msg = null
                    scope.launch {
                        val t = MailChecker.test(email, pass)
                        if (t.ok) {
                            prefs.email = email
                            prefs.emailPassword = pass.replace(" ", "")
                            MailWorker.schedule(ctx)
                            linked = true
                            msg = "Revisando los últimos 3 días…"
                            val r = MailChecker.check(ctx)
                            msg = "✓ Enlazado. ${r.message}"
                            onChanged()
                        } else msg = t.message
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            ) { Text(if (busy) "Conectando…" else "Enlazar correo") }
        }
    }
}

/** Mandar un correo (por ejemplo a la jefe) por cada transferencia. */
@Composable
private fun ForwardSection(prefs: Prefs, refresh: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var to by remember { mutableStateOf(prefs.forwardTo) }
    var saved by remember { mutableStateOf(prefs.forwardTo) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val linked = remember(refresh) { prefs.emailLinked }

    SectionTitle("Avisar por correo")
    Panel {
        Text(
            "Cada vez que llegue una transferencia, Cybeth manda un correo con el monto, quién la envió y a qué banco llegó. " +
                "Sirve para alguien con iPhone (por ejemplo, la jefe): le llega como un correo normal.",
            color = Muted, fontSize = 13.sp
        )
        if (!linked) {
            Text(
                "Primero enlaza tu Gmail en la sección de arriba: los avisos salen desde ese correo.",
                color = Warn, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)
            )
        }
        OutlinedTextField(
            value = to, onValueChange = { to = it },
            label = { Text("Enviar a (puedes poner varios, separados por coma)") },
            placeholder = { Text("jefe@gmail.com") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        if (to != saved) {
            Button(onClick = {
                prefs.forwardTo = to.trim(); saved = prefs.forwardTo
                msg = if (prefs.forwardList.isEmpty()) "Escribe al menos un correo válido" else "✓ Guardado: ${prefs.forwardList.joinToString(", ")}"
            }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Guardar") }
        }
        ToggleRow("Avisar cuando llega plata", null, prefs.forwardIn) { prefs.forwardIn = it }
        ToggleRow("Avisar también cuando se envía plata", null, prefs.forwardOut) { prefs.forwardOut = it }
        OutlinedButton(
            enabled = linked && !busy && prefs.forwardList.isNotEmpty() && to == saved,
            onClick = {
                busy = true; msg = "Enviando prueba…"
                scope.launch {
                    val r = ForwardMail.sendTest(ctx)
                    msg = if (r.ok) "✓ ${r.message}. Revisa la bandeja (y la de spam)." else r.message
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        ) { Text("✉️ Enviar correo de prueba") }
        msg?.let { Text(it, color = if (it.startsWith("✓")) Accent else Warn, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
        prefs.lastForwardResult?.let { Text("Último aviso: $it", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
    }
}

/** Para comprobar con un texto real que la app lo entiende bien. */
@Composable
private fun TestParserSection() {
    var text by remember { mutableStateOf("") }
    var out by remember { mutableStateOf<String?>(null) }
    SectionTitle("Probar un mensaje")
    Panel {
        Text("Pega aquí el texto de una notificación o correo de tu banco para ver cómo lo entiende Cybeth (no se guarda).", color = Muted, fontSize = 13.sp)
        OutlinedTextField(
            value = text, onValueChange = { text = it }, minLines = 3,
            placeholder = { Text("Ej: Laura Méndez te envió \$42.500") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        OutlinedButton(onClick = {
            val p = TxParser.parse(text)
            out = if (p == null) "Ignorado: no es una transferencia (o es compra/pago/PSE/retiro)."
            else (if (p.direction == Direction.IN) "↙ ENTRADA " else "↗ ENVIADA ") + money(p.amount) + " · " + Wording.statusName(p.status) +
                "\nPersona: ${p.counterparty ?: "—"}\nBanco: ${p.bank ?: "—"}\nCuenta: ${p.last4?.let { "****$it" } ?: "—"}\nReferencia: ${p.reference ?: "—"}"
        }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Probar") }
        out?.let {
            Text(it, color = TextC, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).background(Card2).padding(10.dp))
        }
    }
}
