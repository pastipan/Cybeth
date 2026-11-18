package co.cuadra.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF0D1117)
val CardBg = Color(0xFF161C26)
val Card2 = Color(0xFF1D2532)
val Line = Color(0xFF273042)
val TextC = Color(0xFFEEF2F8)
val Muted = Color(0xFF8B96AA)
val Accent = Color(0xFF14C3AF)  // verde Cybeth
val Navy = Color(0xFF14213D)
val OutRed = Color(0xFFFF8F8F)
val Warn = Color(0xFFFFB547)
val InfoBlue = Color(0xFF6AA8FF)

private val scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF042A26),
    background = Bg,
    onBackground = TextC,
    surface = CardBg,
    onSurface = TextC,
    surfaceVariant = Card2,
    onSurfaceVariant = Muted,
    outline = Line,
    secondaryContainer = Color(0xFF0E3A36),
    onSecondaryContainer = Accent,
    error = OutRed,
)

@Composable
fun CuadraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Color fijo para cada banco (para el ícono redondo). */
fun bankColor(bank: String?): Pair<Color, Color> = when (bank) {
    "Nequi" -> Color(0xFFE0207A) to Color.White
    "Bancolombia" -> Color(0xFFFFD23F) to Color(0xFF2A2300)
    "Daviplata" -> Color(0xFFE2231A) to Color.White
    "Davivienda" -> Color(0xFFC8102E) to Color.White
    "Banco de Bogotá" -> Color(0xFF2155C4) to Color.White
    "BBVA" -> Color(0xFF1464A5) to Color.White
    "Nu" -> Color(0xFF820AD1) to Color.White
    "Dale!" -> Color(0xFF00B4E6) to Color.White
    else -> Color(0xFF3A4458) to Color.White
}
