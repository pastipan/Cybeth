package co.cuadra.app

import co.cuadra.app.data.Dedup
import co.cuadra.app.data.Direction
import co.cuadra.app.data.Tx
import co.cuadra.app.data.TxParser
import co.cuadra.app.data.filterTxs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas automáticas: se corren solas cada vez que se compila la app. */
class TxLogicTest {

    private fun inOk(text: String, amount: Long, who: String?, bank: String? = "Nequi") {
        val p = TxParser.parse(text, bank)
        assertTrue("Debió detectar ENTRADA: $text", p != null)
        assertEquals("Sentido en: $text", Direction.IN, p!!.direction)
        assertEquals("Monto en: $text", amount, p.amount)
        assertEquals("Nombre en: $text", who, p.counterparty)
    }

    private fun outOk(text: String, amount: Long, who: String?, bank: String? = "Nequi") {
        val p = TxParser.parse(text, bank)
        assertTrue("Debió detectar ENVIADA: $text", p != null)
        assertEquals("Sentido en: $text", Direction.OUT, p!!.direction)
        assertEquals("Monto en: $text", amount, p.amount)
        assertEquals("Nombre en: $text", who, p.counterparty)
    }

    private fun ignored(text: String) = assertNull("Debió IGNORAR: $text", TxParser.parse(text, "Nequi"))

    // ---------------- Nequi: entradas ----------------
    @Test fun nequiTeLlegoPlata() = inOk("¡Te llegó plata!\nLaura Méndez te envió \$42.500", 42500, "Laura Méndez")
    @Test fun nequiRecibiste() = inOk("Nequi\nRecibiste \$50.000 de JUAN CARLOS PEREZ", 50000, "Juan Carlos Perez")
    @Test fun nequiDesdeOtroBanco() = inOk("Te llegaron \$100.000 de Bancolombia", 100000, null)
    @Test fun nequiBreB() = inOk("¡Te llegó plata por Bre-B! MARIA LOPEZ te envió \$35.000 con la llave @maria", 35000, "Maria Lopez")
    @Test fun nequiConSaldoDespues() = inOk("Recibiste \$20.000 de Pedro Gómez. Tu saldo disponible es \$320.500", 20000, "Pedro Gómez")
    @Test fun nequiConSaldoAntes() = inOk("Tu saldo es \$320.500. Recibiste \$20.000 de Pedro Gómez", 20000, "Pedro Gómez")
    @Test fun nequiQrRecibido() = inOk("Te pagaron \$15.000 con QR", 15000, null)
    @Test fun montoConEspacio() = inOk("Te llegaron \$ 1.500.000 de Davivienda", 1500000, null)
    @Test fun montoConDecimales() = inOk("Recibiste \$85.000,00 de CARLOS RUIZ", 85000, "Carlos Ruiz")

    // ---------------- Otros bancos ----------------
    @Test fun bancolombiaRecibida() {
        val p = TxParser.parse("Bancolombia: Recibiste una transferencia por \$1.250.000,00 de DISTRIBUIDORA EL SOL SAS en tu cuenta *4821", "Bancolombia")!!
        assertEquals(1250000L, p.amount); assertEquals("Distribuidora El Sol SAS", p.counterparty); assertEquals("4821", p.last4)
    }
    @Test fun daviplataRecibida() {
        val p = TxParser.parse("DaviPlata: Recibiste \$30.000 de JHON PEREZ. Ref DP55102.", "Daviplata")!!
        assertEquals(30000L, p.amount); assertEquals("Jhon Perez", p.counterparty); assertEquals("DP55102", p.reference)
    }
    @Test fun bancoDetectadoEnSms() = assertEquals("Bancolombia", TxParser.detectBank("Bancolombia le informa Transferencia recibida"))

    // ---------------- Enviadas ----------------
    @Test fun nequiEnvioExitoso() = outOk("Envío exitoso. Le enviaste \$60.000 a CAMILO ARIAS", 60000, "Camilo Arias")
    @Test fun nequiAOtroBanco() = outOk("Nequi: Enviaste \$150.000 a ROSA MEDINA, cuenta Davivienda ****9910", 150000, "Rosa Medina")
    @Test fun bancolombiaTransferiste() = outOk("Bancolombia: Transferiste \$180.000 desde tu cuenta *4821 a la cuenta *5678", 180000, null, "Bancolombia")

    // ---------------- Ignorados (compras, pagos, PSE, retiros, etc.) ----------------
    @Test fun pagasteEnComercio() = ignored("Pagaste \$23.500 en Rappi con tu Nequi")
    @Test fun pagoQr() = ignored("Hiciste un pago con QR por \$12.000 en Tienda D1")
    @Test fun compraTarjeta() = ignored("Compra aprobada por \$67.800 en EXITO con tu tarjeta Nequi")
    @Test fun pse() = ignored("Pago PSE exitoso por \$94.300 a ENEL")
    @Test fun recarga() = ignored("Recargaste \$50.000 a tu Nequi desde Bancolombia")
    @Test fun sacaste() = ignored("Sacaste \$100.000 en cajero")
    @Test fun retiro() = ignored("Retiraste \$50.000 en cajero")
    @Test fun factura() = ignored("Pagaste la factura de Claro por \$45.000")
    @Test fun claveDinamica() = ignored("Tu clave dinámica es 123456")
    @Test fun publicidad() = ignored("¡Hola! Activa tu Nequi y gana \$5.000")
    @Test fun sinMonto() = ignored("Te enviamos tu extracto")

    // ---------------- Repetidos ----------------
    private fun tx(id: Long, amount: Long, who: String?, source: String, raw: String, time: Long, ref: String? = null) =
        Tx(id, amount, Direction.IN, who, "Nequi", null, ref, source, raw, time)

    @Test fun dosClientesMismoMontoSonDosPagos() {
        val a = tx(1, 10000, "Ana Ruiz", "Notificación", "Ana Ruiz te envió \$10.000", 1_000_000)
        val p = TxParser.parse("Luis Mora te envió \$10.000", "Nequi")!!
        assertFalse(Dedup.isDuplicate(listOf(a), p, "Notificación", "Luis Mora te envió \$10.000", 1_030_000))
    }
    @Test fun mismoMontoSinNombreMismaFuenteSonDosPagos() {
        val a = tx(1, 10000, null, "Notificación", "[Nequi] Te pagaron \$10.000 con QR", 1_000_000)
        val p = TxParser.parse("Te pagaron \$10.000 con QR.", "Nequi")!!
        assertFalse(Dedup.isDuplicate(listOf(a), p, "Notificación", "[Nequi] Te pagaron \$10.000 con QR.", 1_060_000))
    }
    @Test fun notificacionRepetidaEsUnSolo() {
        val raw = "[Nequi] Laura te envió \$42.500"
        val a = tx(1, 42500, "Laura", "Notificación", raw, 1_000_000)
        val p = TxParser.parse("Laura te envió \$42.500", "Nequi")!!
        assertTrue(Dedup.isDuplicate(listOf(a), p, "Notificación", raw, 1_005_000))
    }
    @Test fun notificacionYCorreoDelMismoPagoEsUnSolo() {
        val a = tx(1, 42500, "Laura Méndez", "Notificación", "x", 1_000_000)
        val p = TxParser.parse("Recibiste \$42.500 de LAURA MENDEZ RUIZ", "Nequi")!!
        assertTrue(Dedup.isDuplicate(listOf(a), p, "Correo", "y", 1_200_000))
    }
    @Test fun mismaReferenciaEsUnSolo() {
        val a = tx(1, 30000, "Jhon", "SMS", "a", 1_000_000, "DP55101")
        val p = TxParser.parse("Recibiste \$30.000 de JHON. Ref DP55101", "Nequi")!!
        assertTrue(Dedup.isDuplicate(listOf(a), p, "Correo", "b", 9_000_000))
    }
    @Test fun referenciaDistintaSonDos() {
        val a = tx(1, 30000, "Jhon", "SMS", "a", 1_000_000, "DP55101")
        val p = TxParser.parse("Recibiste \$30.000 de JHON. Ref DP55102", "Nequi")!!
        assertFalse(Dedup.isDuplicate(listOf(a), p, "Correo", "b", 1_000_500))
    }

    // ---------------- Filtros de Movimientos ----------------
    @Test fun filtros() {
        val l = listOf(
            Tx(1, 1, Direction.IN, null, "Nequi", null, null, "N", "", 0),
            Tx(2, 2, Direction.OUT, null, "Nequi", null, null, "N", "", 0),
            Tx(3, 3, Direction.IN, null, "Bancolombia", null, null, "N", "", 0),
        )
        assertEquals(3, filterTxs(l, null, null).size)
        assertEquals(listOf(1L, 3L), filterTxs(l, Direction.IN, null).map { it.id })
        assertEquals(listOf(2L), filterTxs(l, Direction.OUT, null).map { it.id })
        assertEquals(listOf(1L, 2L), filterTxs(l, null, "Nequi").map { it.id })
        assertEquals(listOf(1L), filterTxs(l, Direction.IN, "Nequi").map { it.id })
        assertEquals(0, filterTxs(l, Direction.OUT, "Bancolombia").size)
    }
}
