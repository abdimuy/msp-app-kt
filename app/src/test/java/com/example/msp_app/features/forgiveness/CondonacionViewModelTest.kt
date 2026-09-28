package com.example.msp_app.features.forgiveness

import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.data.pagos.CondonacionFixtures
import com.example.msp_app.data.pagos.CondonacionFixtures.condonacion
import com.example.msp_app.data.pagos.CondonacionFixtures.condonacionesEn
import com.example.msp_app.data.pagos.CondonacionFixtures.saldoDe
import com.example.msp_app.data.pagos.CondonacionFixtures.venta
import com.example.msp_app.data.pagos.RegistroDeCondonacion
import com.example.msp_app.features.forgiveness.components.validarMontoDeCondonacion
import com.example.msp_app.features.forgiveness.viewmodels.CondonacionUiState
import com.example.msp_app.features.forgiveness.viewmodels.CondonacionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * **El ViewModel del diálogo: espera la escritura, relee el saldo y no deja
 * condonar dos veces.**
 *
 * Los tres defectos del diálogo viejo que esto cierra están en E-APP-043 (#2 y
 * #4) y E-APP-047: no había guarda contra el segundo toque, el tope salía de la
 * foto de la venta cargada al abrir, y la pantalla seguía sin esperar a que la
 * escritura terminara.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CondonacionViewModelTest : RoomTestBase() {

    @Before
    fun main() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun restaurarMain() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = CondonacionViewModel(RegistroDeCondonacion(db))

    private fun CondonacionViewModel.esperar(
        condicion: (CondonacionUiState) -> Boolean
    ): CondonacionUiState = runBlocking {
        withTimeout(TIEMPO_MAXIMO_MS) { estado.first(condicion) }
    }

    @Test
    fun `carga el saldo vigente de Room, no el de la pantalla`() {
        runBlocking { db.saleDao().insertAll(listOf(venta(saldo = 2300.0))) }
        val vm = viewModel()

        vm.cargar(CondonacionFixtures.VENTA)

        assertEquals(2300.0, vm.esperar { it.saldo != null }.saldo!!, 1e-9)
    }

    /**
     * **Reabrir el diálogo relee el saldo.** En la puerta legada el ViewModel
     * vive lo que vive la pantalla: si entre una apertura y otra el sync movió
     * el saldo, el diálogo tiene que ver el nuevo, no la foto de la primera vez.
     */
    @Test
    fun `volver a cargar relee el saldo que cambio por fuera`() {
        runBlocking { db.saleDao().insertAll(listOf(venta(saldo = 2300.0))) }
        val vm = viewModel()
        vm.cargar(CondonacionFixtures.VENTA)
        vm.esperar { it.saldo == 2300.0 }

        runBlocking { db.saleDao().insertAll(listOf(venta(saldo = 500.0))) }
        vm.cargar(CondonacionFixtures.VENTA)

        assertEquals(500.0, vm.esperar { it.saldo == 500.0 }.saldo!!, 1e-9)
    }

    /**
     * **Tras guardar, el tope usa el saldo releído.** La condonación del saldo
     * completo deja el saldo en 0, y el mismo monto ya no pasa la validación del
     * diálogo — que es lo que dejaba repetir la condonación (P1).
     */
    @Test
    fun `tras guardar, el tope usa el saldo releido`() {
        runBlocking { db.saleDao().insertAll(listOf(venta(saldo = 2300.0))) }
        val vm = viewModel()
        vm.cargar(CondonacionFixtures.VENTA)
        vm.esperar { it.saldo != null }

        assertNull(
            "control positivo: antes de guardar, $2,300 sí se puede condonar",
            validarMontoDeCondonacion("2300", vm.estado.value.saldo)
        )

        vm.condonar(condonacion("cond-1", 2300.0))
        val guardada = vm.esperar { it.guardadaId != null }

        assertEquals("cond-1", guardada.guardadaId)
        assertEquals("el saldo del estado es el releído", 0.0, guardada.saldo!!, 1e-9)
        assertEquals(
            "El monto no puede ser mayor al saldo restante",
            validarMontoDeCondonacion("2300", guardada.saldo)
        )
    }

    /**
     * **Doble toque → una sola fila.** El segundo `condonar` llega mientras el
     * primero está en vuelo (o ya guardado y sin atender) y se ignora de forma
     * síncrona, antes de cualquier escritura. Cada toque trae su propio id, como
     * lo arma el diálogo.
     */
    @Test
    fun `doble toque escribe una sola condonacion`() {
        runBlocking { db.saleDao().insertAll(listOf(venta(saldo = 2300.0))) }
        val vm = viewModel()
        vm.cargar(CondonacionFixtures.VENTA)
        vm.esperar { it.saldo != null }

        vm.condonar(condonacion("toque-1", 100.0))
        vm.condonar(condonacion("toque-2", 100.0))
        vm.esperar { it.guardadaId != null }

        runBlocking {
            assertEquals(listOf("toque-1"), condonacionesEn(db).map { it.ID })
            assertEquals(2200.0, saldoDe(db), 1e-9)
        }
    }

    /** Mientras guarda, el estado lo dice: es lo que deshabilita "Confirmar". */
    @Test
    fun `mientras guarda el estado dice guardando`() {
        runBlocking { db.saleDao().insertAll(listOf(venta(saldo = 2300.0))) }
        val vm = viewModel()
        vm.cargar(CondonacionFixtures.VENTA)
        vm.esperar { it.saldo != null }

        vm.condonar(condonacion("cond-1", 100.0))
        // El `guardando = true` se publica ANTES del launch: síncrono.
        val justoDespues = vm.estado.value
        assertTrue(
            "el estado no dijo 'guardando' ni 'guardada' tras el toque",
            justoDespues.guardando || justoDespues.guardadaId != null
        )
        assertEquals(false, vm.esperar { it.guardadaId != null }.guardando)
    }

    /** Si la escritura no pasa, el error queda visible y NO hay id que navegar. */
    @Test
    fun `si excede el saldo, queda el error y no se navega`() {
        runBlocking { db.saleDao().insertAll(listOf(venta(saldo = 2300.0))) }
        val vm = viewModel()
        vm.cargar(CondonacionFixtures.VENTA)
        vm.esperar { it.saldo != null }

        vm.condonar(condonacion("cond-1", 2301.0))
        val fin = vm.esperar { !it.guardando && it.error != null }

        assertEquals("El monto excede el saldo actual", fin.error)
        assertNull(fin.guardadaId)
        runBlocking { assertTrue(condonacionesEn(db).isEmpty()) }
    }

    /**
     * **Una vez guardada, esa instancia queda TERMINADA para siempre.** El
     * dueño lo vio en el aparato: tras guardar, el formulario sigue un instante
     * en pantalla mientras navega al ticket. Si en ese instante el ViewModel
     * vuelve a aceptar, una condonación PARCIAL (2300 → 1000 → 1300) deja el
     * resto condonable con un toque rápido — válido contra el tope, perdón de
     * deuda de más. Se hace aquí todo lo que el diálogo hace tras guardar.
     */
    @Test
    fun `tras guardar, un segundo condonar parcial no escribe`() {
        runBlocking { db.saleDao().insertAll(listOf(venta(saldo = 2300.0))) }
        val vm = viewModel()
        vm.cargar(CondonacionFixtures.VENTA)
        vm.esperar { it.saldo != null }
        vm.condonar(condonacion("cond-1", 1000.0))
        vm.esperar { it.guardadaId != null }

        despuesDeGuardarComoElDialogo(vm)
        vm.condonar(condonacion("cond-2", 300.0))
        runBlocking { delay(ESPERA_REAL_MS) }

        runBlocking {
            assertEquals(listOf("cond-1"), condonacionesEn(db).map { it.ID })
            assertEquals(1300.0, saldoDe(db), 1e-9)
        }
        assertTrue("la instancia guardada tiene que quedar terminada", vm.estado.value.terminada)
    }

    /**
     * Lo que `CondonacionEnPantalla` le hace al ViewModel después de guardar:
     * nada. Antes le pedía volver a aceptar (`navegacionAtendida`), y ése era el
     * defecto; el método ya no existe.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun despuesDeGuardarComoElDialogo(vm: CondonacionViewModel) = Unit

    private companion object {
        const val TIEMPO_MAXIMO_MS = 5_000L
        const val ESPERA_REAL_MS = 500L
    }
}
