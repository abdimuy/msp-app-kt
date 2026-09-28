package com.example.msp_app.data.pagos

import com.example.msp_app.core.database.dao.sale.EstadoCobranza
import com.example.msp_app.core.database.dao.sale.SaleDao
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.data.pagos.CondonacionFixtures.EFECTIVO
import com.example.msp_app.data.pagos.CondonacionFixtures.condonacion
import com.example.msp_app.data.pagos.CondonacionFixtures.condonacionesEn
import com.example.msp_app.data.pagos.CondonacionFixtures.saldoDe
import com.example.msp_app.data.pagos.CondonacionFixtures.venta
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La invariante de la escritura de la condonación, sobre Room real.**
 *
 * > Una condonación sólo se escribe si su monto es positivo y cabe en el
 * > `SALDO_REST` vigente; si no, no se escribe NADA.
 *
 * Reproduce el caso medido de la compuerta del 2026-09-26 —venta
 * `13662829`, saldo $2,300, condonada dos veces en segundos— y el de campo de
 * E-APP-029/E-APP-030 (cuatro condonaciones de $1,000, `SALDO_REST = -3000.0`).
 * Antes del arreglo la segunda condonación entraba (fila nueva) y dejaba el
 * saldo en −2,300: `SaleDao.updateTotal` resta sin piso y nadie releía el saldo.
 */
class RegistroDeCondonacionTest : RoomTestBase() {

    private val registro by lazy { RegistroDeCondonacion(db) }

    @Test
    fun `condonar el saldo dos veces - la segunda no escribe nada`() = runTest {
        db.saleDao().insertAll(listOf(venta(saldo = 2300.0)))

        val primera = registro.condonar(condonacion("cond-1", 2300.0))
        val segunda = registro.condonar(condonacion("cond-2", 2300.0))

        assertEquals(ResultadoDeLaCondonacion.GUARDADA, primera)
        assertEquals(ResultadoDeLaCondonacion.EXCEDE_EL_SALDO, segunda)
        assertEquals("el saldo queda en cero, no en -2300", 0.0, saldoDe(db), 1e-9)
        assertEquals(
            "una sola fila de condonación",
            listOf("cond-1"),
            condonacionesEn(db).map { it.ID }
        )
    }

    @Test
    fun `condonar un peso mas que el saldo no toca nada`() = runTest {
        db.saleDao().insertAll(listOf(venta(saldo = 2300.0)))

        val resultado = registro.condonar(condonacion("cond-1", 2301.0))

        assertEquals(ResultadoDeLaCondonacion.EXCEDE_EL_SALDO, resultado)
        assertEquals(2300.0, saldoDe(db), 1e-9)
        assertTrue(condonacionesEn(db).isEmpty())
    }

    @Test
    fun `condonar cero o negativo no toca nada`() = runTest {
        db.saleDao().insertAll(listOf(venta(saldo = 2300.0)))

        assertEquals(
            ResultadoDeLaCondonacion.MONTO_INVALIDO,
            registro.condonar(condonacion("cond-0", 0.0))
        )
        assertEquals(
            ResultadoDeLaCondonacion.MONTO_INVALIDO,
            registro.condonar(condonacion("cond-neg", -5.0))
        )
        assertEquals(2300.0, saldoDe(db), 1e-9)
        assertTrue(condonacionesEn(db).isEmpty())
    }

    /** Control positivo: una parcial que sí cabe se escribe y descuenta. */
    @Test
    fun `una condonacion parcial que cabe se escribe y descuenta`() = runTest {
        db.saleDao().insertAll(listOf(venta(saldo = 2300.0)))

        val resultado = registro.condonar(condonacion("cond-1", 230.0))

        assertEquals(ResultadoDeLaCondonacion.GUARDADA, resultado)
        assertEquals(2070.0, saldoDe(db), 1e-9)
        assertEquals(1, condonacionesEn(db).size)
        assertEquals(
            "conserva el ESTADO_COBRANZA que la condonación siempre fijó",
            "PAGADO",
            checkNotNull(db.saleDao().getById(CondonacionFixtures.VENTA)).ESTADO_COBRANZA
        )
    }

    @Test
    fun `una venta que no esta en el telefono no escribe nada`() = runTest {
        val resultado = registro.condonar(condonacion("cond-1", 100.0))

        assertEquals(ResultadoDeLaCondonacion.VENTA_NO_ESTA_EN_EL_TELEFONO, resultado)
        assertTrue(condonacionesEn(db).isEmpty())
    }

    @Test
    fun `lo que no es condonacion no pasa por aqui`() = runTest {
        db.saleDao().insertAll(listOf(venta(saldo = 2300.0)))

        val resultado = registro.condonar(condonacion("abono", 100.0, formaCobro = EFECTIVO))

        assertEquals(ResultadoDeLaCondonacion.NO_ES_CONDONACION, resultado)
        assertEquals(2300.0, saldoDe(db), 1e-9)
    }

    /**
     * **El insert y el descuento, o los dos o ninguno.** Si el `UPDATE`
     * condicionado no toca la fila —el saldo dejó de alcanzar entre la lectura y
     * la escritura—, la transacción deshace el insert. Se simula con un `SaleDao`
     * real al que sólo se le cambia la respuesta del descuento.
     */
    @Test
    fun `si el descuento no toca la fila, el insert se deshace`() = runTest {
        db.saleDao().insertAll(listOf(venta(saldo = 2300.0)))
        val registroConCarrera = RegistroDeCondonacion(
            db = db,
            saleDao = ElSaldoDejoDeAlcanzar(db.saleDao())
        )

        val resultado = registroConCarrera.condonar(condonacion("cond-1", 2300.0))

        assertEquals(ResultadoDeLaCondonacion.EXCEDE_EL_SALDO, resultado)
        assertTrue(
            "la fila de la condonación sobrevivió al descuento fallido",
            condonacionesEn(db).isEmpty()
        )
        assertEquals(2300.0, saldoDe(db), 1e-9)
    }

    /**
     * El medio centavo de `descontarSiAlcanza`: un saldo con ruido binario de
     * `Double` tiene que admitir condonarse completo.
     */
    @Test
    fun `un saldo con ruido de flotante se condona completo`() = runTest {
        val conRuido = 1.0 - 0.9 // 0.09999999999999998: POR DEBAJO de 0.10 en binario
        db.saleDao().insertAll(listOf(venta(saldo = conRuido)))

        val resultado = registro.condonar(condonacion("cond-1", 0.10))

        assertEquals(ResultadoDeLaCondonacion.GUARDADA, resultado)
        assertEquals(0.0, saldoDe(db), 0.005)
    }

    /**
     * **El abono y la visita no cambian**: siguen por `updateTotal`, que resta
     * sin piso. Caracterización: si alguien le pusiera el tope de la
     * condonación, el abono que el tercer cinturón ya validó y la visita (0.0)
     * dejarían de comportarse como hoy.
     */
    @Test
    fun `updateTotal del abono y la visita sigue restando igual que antes`() = runTest {
        db.saleDao().insertAll(listOf(venta(saldo = 100.0)))

        db.saleDao().updateTotal(CondonacionFixtures.VENTA, 0.0, EstadoCobranza.VISITADO)
        assertEquals("la visita no mueve el saldo", 100.0, saldoDe(db), 1e-9)

        db.saleDao().updateTotal(CondonacionFixtures.VENTA, 150.0, EstadoCobranza.PAGADO)
        assertEquals("updateTotal no tiene piso, como siempre", -50.0, saldoDe(db), 1e-9)
    }

    private class ElSaldoDejoDeAlcanzar(real: SaleDao) : SaleDao by real {
        override suspend fun descontarSiAlcanza(
            saleId: Int,
            monto: Double,
            estadoCobranza: EstadoCobranza
        ): Int = 0
    }
}
