package co.cuadra.app.ui

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.cuadra.app.R
import co.cuadra.app.data.Prefs
import kotlinx.coroutines.delay

enum class LockMode { CREATE, UNLOCK, CHANGE }

fun biometricAvailable(ctx: Context): Boolean =
    Build.VERSION.SDK_INT >= 28 &&
        BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

private fun tooEasy(pin: String): Boolean {
    if (pin.all { it == pin[0] }) return true
    val d = pin.map { it - '0' }
    val up = (1 until d.size).all { d[it] == d[it - 1] + 1 }
    val down = (1 until d.size).all { d[it] == d[it - 1] - 1 }
    return up || down
}

/**
 * Lo primero que se ve al abrir la app.
 * - Si no hay clave: la crea (y pregunta si quiere usar la huella).
 * - Si ya hay clave: la pide (o la huella).
 */
@Composable
fun LockGate(requestBiometric: (onOk: () -> Unit) -> Unit, onUnlocked: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var askBiometric by remember { mutableStateOf(false) }
    val bioOk = remember { biometricAvailable(ctx) }

    if (askBiometric) {
        BiometricQuestion(
            onYes = { requestBiometric { prefs.useBiometric = true; onUnlocked() } },
            onNo = { prefs.useBiometric = false; onUnlocked() }
        )
        return
    }

    if (!prefs.hasPin) {
        PinPad(LockMode.CREATE, showBiometric = false, onBiometric = {}, onSuccess = {
            if (bioOk) askBiometric = true else onUnlocked()
        })
    } else {
        val useBio = bioOk && prefs.useBiometric
        LaunchedEffect(Unit) { if (useBio) requestBiometric(onUnlocked) }
        PinPad(LockMode.UNLOCK, showBiometric = useBio, onBiometric = { requestBiometric(onUnlocked) }, onSuccess = onUnlocked)
    }
}

@Composable
private fun BiometricQuestion(onYes: () -> Unit, onNo: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Bg).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Fingerprint, null, tint = Accent, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(18.dp))
        Text("¿Quieres entrar con tu huella?", color = TextC, fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Es más rápido. Tu clave sigue sirviendo siempre.", color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Button(onClick = onYes, modifier = Modifier.fillMaxWidth()) { Text("Sí, usar huella", fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onNo, modifier = Modifier.fillMaxWidth()) { Text("Ahora no", color = Muted) }
    }
}

/**
 * Teclado de clave de 4 dígitos.
 * CREATE: nueva + confirmar. UNLOCK: pedir clave. CHANGE: clave actual + nueva + confirmar.
 */
@Composable
fun PinPad(
    mode: LockMode,
    showBiometric: Boolean,
    onBiometric: () -> Unit,
    onSuccess: () -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    // paso 0 = clave actual (solo CHANGE), 1 = nueva, 2 = confirmar, 3 = pedir (UNLOCK)
    var step by remember { mutableStateOf(if (mode == LockMode.UNLOCK) 3 else if (mode == LockMode.CHANGE) 0 else 1) }
    var input by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var forgot by remember { mutableStateOf(false) }
    val lockedFor = ((prefs.lockUntil - now) / 1000).coerceAtLeast(0)

    LaunchedEffect(lockedFor > 0) {
        while (prefs.lockUntil > System.currentTimeMillis()) { delay(1000); now = System.currentTimeMillis() }
        now = System.currentTimeMillis()
    }

    val title = when (step) {
        0 -> "Escribe tu clave actual"
        1 -> if (mode == LockMode.CHANGE) "Escribe tu clave nueva" else "Crea tu clave de 4 dígitos"
        2 -> "Confirma tu clave"
        else -> "Ingresa tu clave"
    }
    val subtitle = when (step) {
        1 -> "La vas a usar cada vez que entres a Cybeth"
        2 -> "Escríbela otra vez"
        else -> null
    }

    fun submit(pin: String) {
        when (step) {
            0, 3 -> {
                if (prefs.checkPin(pin)) {
                    error = null
                    if (step == 3) onSuccess() else step = 1
                } else {
                    error = if (prefs.lockUntil > System.currentTimeMillis()) "Demasiados intentos" else "Clave incorrecta (${prefs.failedAttempts} de 5)"
                    now = System.currentTimeMillis()
                }
            }
            1 -> {
                if (tooEasy(pin)) error = "Esa clave es muy fácil. Prueba otra." else { first = pin; error = null; step = 2 }
            }
            2 -> {
                if (pin == first) { prefs.setPin(pin); error = null; onSuccess() } else { error = "No coinciden. Vuelve a crearla."; first = ""; step = 1 }
            }
        }
        input = ""
    }

    Column(
        Modifier.fillMaxSize().background(Bg).padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth()) {
            if (onCancel != null) TextButton(onClick = onCancel) { Text("Cancelar", color = Muted) }
        }
        Spacer(Modifier.height(24.dp))
        Row(
            Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(painterResource(R.drawable.logo_cybeth), contentDescription = "Cybeth", modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(8.dp))
            Text("cybeth", color = Navy, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
        }
        Spacer(Modifier.height(36.dp))
        Text(title, color = TextC, fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center)
        subtitle?.let { Text(it, color = Muted, fontSize = 13.5.sp, modifier = Modifier.padding(top = 4.dp), textAlign = TextAlign.Center) }
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            repeat(4) { i ->
                Box(
                    Modifier.size(16.dp).clip(CircleShape)
                        .background(if (i < input.length) Accent else Color.Transparent)
                        .border(2.dp, if (i < input.length) Accent else Muted, CircleShape)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            if (lockedFor > 0) "Demasiados intentos. Espera $lockedFor s" else error ?: " ",
            color = OutRed, fontSize = 13.5.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.weight(1f))

        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "bio", "0", "del")
        keys.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { k ->
                    val enabled = lockedFor == 0L
                    Box(
                        Modifier.size(74.dp).clip(CircleShape)
                            .background(if (k.length == 1) Card2 else Color.Transparent)
                            .clickable(enabled = enabled && (k != "bio" || showBiometric)) {
                                when (k) {
                                    "del" -> input = input.dropLast(1)
                                    "bio" -> onBiometric()
                                    else -> if (input.length < 4) {
                                        input += k
                                        if (input.length == 4) submit(input)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        when (k) {
                            "del" -> Icon(Icons.AutoMirrored.Filled.Backspace, "Borrar", tint = TextC)
                            "bio" -> if (showBiometric) Icon(Icons.Filled.Fingerprint, "Huella", tint = Accent, modifier = Modifier.size(34.dp))
                            else -> Text(k, color = if (enabled) TextC else Muted, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        if (mode == LockMode.UNLOCK) {
            TextButton(onClick = { forgot = true }, modifier = Modifier.padding(top = 8.dp)) { Text("¿Olvidaste tu clave?", color = Accent) }
        } else Spacer(Modifier.height(48.dp))
    }

    if (forgot) {
        AlertDialog(
            onDismissRequest = { forgot = false },
            containerColor = CardBg,
            title = { Text("¿Olvidaste tu clave?", color = TextC) },
            text = {
                Text(
                    "Por seguridad, para crear una clave nueva hay que borrar todo lo de Cybeth en este celular: " +
                        "movimientos, correo enlazado y ajustes. Tus bancos y tu Nequi no se tocan.",
                    color = TextC
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).clearApplicationUserData()
                }) { Text("Borrar y empezar de nuevo", color = OutRed) }
            },
            dismissButton = { TextButton(onClick = { forgot = false }) { Text("Cancelar", color = Accent) } },
        )
    }
}

/** Fila de ajustes de seguridad (se usa en Ajustes). */
@Composable
fun SecurityPanel(onLockNow: () -> Unit, requestBiometric: (onOk: () -> Unit) -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var changing by remember { mutableStateOf(false) }
    var bio by remember { mutableStateOf(prefs.useBiometric) }
    var msg by remember { mutableStateOf<String?>(null) }
    val bioOk = remember { biometricAvailable(ctx) }

    SectionTitle("Seguridad")
    Panel {
        Text("La app pide tu clave al abrirla y cuando vuelves después de 30 segundos.", color = Muted, fontSize = 12.5.sp)
        Spacer(Modifier.height(8.dp))
        if (bioOk) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Entrar con huella", color = TextC, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp)
                }
                androidx.compose.material3.Switch(
                    checked = bio,
                    onCheckedChange = { want ->
                        if (want) requestBiometric { prefs.useBiometric = true; bio = true }
                        else { prefs.useBiometric = false; bio = false }
                    },
                    colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Color.White)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            OutlinedButton(onClick = { changing = true; msg = null }) { Text("Cambiar clave") }
            OutlinedButton(onClick = onLockNow) { Text("🔒 Bloquear ahora") }
        }
        msg?.let { Text(it, color = Accent, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
    }

    if (changing) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { changing = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            PinPad(
                LockMode.CHANGE, showBiometric = false, onBiometric = {},
                onSuccess = { changing = false; msg = "✓ Clave cambiada" },
                onCancel = { changing = false }
            )
        }
    }
}
