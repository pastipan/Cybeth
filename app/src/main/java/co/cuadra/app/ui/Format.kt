package co.cuadra.app.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val CO = Locale("es", "CO")

fun money(n: Long): String = "$" + String.format(Locale.US, "%,d", n).replace(',', '.')

fun startOfDay(t: Long = System.currentTimeMillis(), daysAgo: Int = 0): Long =
    Calendar.getInstance().apply {
        timeInMillis = t
        add(Calendar.DAY_OF_YEAR, -daysAgo)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

fun isToday(t: Long) = t >= startOfDay()

fun hour(t: Long): String = SimpleDateFormat("h:mm a", CO).format(Date(t))

fun dayLabel(t: Long): String = when {
    t >= startOfDay() -> "Hoy"
    t >= startOfDay(daysAgo = 1) -> "Ayer"
    else -> SimpleDateFormat("EEEE d 'de' MMMM", CO).format(Date(t)).replaceFirstChar { it.uppercase() }
}

fun whenText(t: Long): String = "${dayLabel(t)} ${hour(t)}"

fun ago(t: Long): String {
    if (t == 0L) return "nunca"
    val m = (System.currentTimeMillis() - t) / 60000
    return when {
        m < 1 -> "hace un momento"
        m < 60 -> "hace $m min"
        m < 60 * 24 -> "hace ${m / 60} h"
        else -> whenText(t)
    }
}
