package co.cuadra.app

import co.cuadra.app.data.Direction
import co.cuadra.app.data.Tx
import co.cuadra.app.data.TxStatus
import co.cuadra.app.ui.DayReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DayReportTest {
    private val day = 1_763_442_000_000L // un día cualquiera a las 00:00
    private fun t(id: Long, amount: Long, dir: Direction, st: TxStatus, bank: String, last4: String?, who: String?, h: Int) =
        Tx(id, amount, dir, who, bank, last4, null, "Notificación", "", day + h * 3_600_000L, st)

    private val list = listOf(
        t(1, 9700, Direction.IN, TxStatus.APROBADA, "Bancolombia", "3541", "Juli Johana Quiroz", 13),
        t(2, 42500, Direction.IN, TxStatus.APROBADA, "Nequi", null, "Laura Méndez", 9),
        t(3, 250000, Direction.IN, TxStatus.EN_PROCESO, "Banco de Bogotá", "0356", "Distribuidora; El Sol", 8),
        t(4, 120000, Direction.IN, TxStatus.RECHAZADA, "Bancolombia", "3541", "Andrea Gómez", 7),
        t(5, 60000, Direction.OUT, TxStatus.APROBADA, "Nequi", null, "Camilo Arias", 11),
        t(6, 99999, Direction.IN, TxStatus.APROBADA, "Nequi", null, "Ayer", -2), // del día anterior: no cuenta
    )

    @Test fun totales() {
        val r = DayReport(list, day)
        assertEquals(5, r.txs.size)
        assertEquals(52200L, r.totalIn)      // solo aprobadas: 9.700 + 42.500
        assertEquals(2, r.countIn)
        assertEquals(60000L, r.totalOut)
        assertEquals(-7800L, r.net)
        assertEquals(1, r.count(TxStatus.EN_PROCESO))
        assertEquals(1, r.count(TxStatus.RECHAZADA))
    }

    @Test fun porCuenta() {
        val r = DayReport(list, day)
        val bc = r.byAccount.first { it.name == "Bancolombia ****3541" }
        assertEquals(9700L, bc.totalIn); assertEquals(1, bc.countIn) // la rechazada no suma
        val nq = r.byAccount.first { it.name == "Nequi" }
        assertEquals(42500L, nq.totalIn); assertEquals(60000L, nq.totalOut)
    }

    @Test fun archivoExcel() {
        val csv = String(DayReport(list, day).csv(), Charsets.UTF_8)
        assertTrue(csv.startsWith("﻿"))
        assertTrue("Debe tener encabezado de detalle", csv.contains("Hora;Tipo;Estado;Persona;Banco;Cuenta;Monto;Referencia;Detectado por"))
        assertTrue("Nombre con ; va entre comillas", csv.contains("\"Distribuidora; El Sol\""))
        assertTrue("Enviada en negativo", csv.contains(";-60000;"))
        assertTrue("No incluye el día anterior", !csv.contains("Ayer"))
    }

    @Test fun diaVacio() {
        val r = DayReport(emptyList(), day)
        assertEquals(0L, r.totalIn)
        assertTrue(r.plain().contains("Sin movimientos"))
        assertTrue(r.html().contains("Sin movimientos"))
    }
}
