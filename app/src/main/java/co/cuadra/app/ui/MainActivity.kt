package co.cuadra.app.ui

import android.os.Bundle
import android.Manifest
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.cuadra.app.data.Direction
import co.cuadra.app.data.Repo
import co.cuadra.app.data.Tx
import co.cuadra.app.data.TxStatus

class MainActivity : FragmentActivity() {
    /** Sube cada vez que el usuario vuelve a la app (para refrescar permisos). */
    private var resumeTick by mutableIntStateOf(0)
    /** false = se muestra la pantalla de clave. */
    private var unlocked by mutableStateOf(false)
    private var lastStop = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Que no se vea el contenido en "apps recientes" ni en pantallazos (como Nequi)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        Repo.init(this)
        unlocked = savedInstanceState?.getBoolean("unlocked") ?: false
        setContent {
            CuadraTheme {
                if (unlocked) AppRoot(resumeTick, onLock = { unlocked = false }, requestBiometric = ::showBiometric)
                else LockGate(requestBiometric = ::showBiometric, onUnlocked = { unlocked = true })
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("unlocked", unlocked)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) lastStop = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        // Si estuvo afuera más de 30 segundos, vuelve a pedir la clave
        if (lastStop != 0L && SystemClock.elapsedRealtime() - lastStop > 30_000L) unlocked = false
        lastStop = 0L
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }

    /** Muestra el lector de huella del celular. */
    fun showBiometric(onOk: () -> Unit) {
        if (!biometricAvailable(this)) return
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onOk()
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Entrar a Cybeth")
            .setSubtitle("Pon tu dedo en el lector")
            .setNegativeButtonText("Usar clave")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
        prompt.authenticate(info)
    }
}

@Composable
fun AppRoot(resumeTick: Int, onLock: () -> Unit, requestBiometric: (onOk: () -> Unit) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var open by rememberSaveable { mutableStateOf<Long?>(null) }
    // Filtros de Movimientos (se comparten con Inicio para que al tocar un banco se abran sus movimientos)
    var dirName by rememberSaveable { mutableStateOf<String?>(null) }
    var bank by rememberSaveable { mutableStateOf<String?>(null) }
    var statusName by rememberSaveable { mutableStateOf<String?>(null) }
    val status = statusName?.let { TxStatus.valueOf(it) }
    val dir = dirName?.let { Direction.valueOf(it) }
    val txs by Repo.txs.collectAsStateWithLifecycle()

    // Pedir permiso para mostrar avisos (Android 13 o más nuevo) apenas se entra
    val ctx = LocalContext.current
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (!canPostNotifications(ctx) && Build.VERSION.SDK_INT >= 33) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(containerColor = CardBg) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Accent, selectedTextColor = Accent,
                    unselectedIconColor = Muted, unselectedTextColor = Muted,
                    indicatorColor = Card2
                )
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Filled.Home, null) }, label = { Text("Inicio") }, colors = colors)
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Filled.SwapVert, null) }, label = { Text("Movimientos") }, colors = colors)
                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Filled.Settings, null) }, label = { Text("Ajustes") }, colors = colors)
            }
        }
    ) { pad ->
        val m = Modifier.padding(pad)
        val onOpen: (Tx) -> Unit = { open = it.id }
        when (tab) {
            0 -> HomeScreen(
                txs, resumeTick, onOpen,
                onSeeAll = { dirName = null; bank = null; statusName = null; tab = 1 },
                onSettings = { tab = 2 },
                onBankClick = { b -> bank = b; dirName = null; statusName = null; tab = 1 },
                onDirClick = { d -> dirName = d.name; bank = null; statusName = TxStatus.APROBADA.name; tab = 1 },
                onStatusClick = { st -> statusName = st.name; bank = null; dirName = if (st == TxStatus.APROBADA) Direction.IN.name else null; tab = 1 },
                modifier = m
            )
            1 -> TxScreen(txs, onOpen, dir, { dirName = it?.name }, bank, { bank = it }, status, { statusName = it?.name }, modifier = m)
            else -> SettingsScreen(resumeTick, onLock, requestBiometric, modifier = m)
        }
    }

    txs.firstOrNull { it.id == open }?.let { t ->
        TxDetailDialog(t, onClose = { open = null }, onDelete = { Repo.delete(t.id); open = null })
    }
}
