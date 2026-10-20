package com.example.msp_app.feature.visitas.ui

import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.MainDispatcherRule
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.feature.visitas.application.VisitasTelemetria
import com.example.msp_app.feature.visitas.domain.model.PendientesDelTelefono
import com.example.msp_app.feature.visitas.domain.port.PendientesDelTelefonoPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** E-APP-096: el estado del aviso sigue al puerto y una lectura fallida no se calla. */
class AvisoDePendientesViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val telemetria = RecordingTelemetry()

    @Test
    fun `E-APP-096 el aviso sigue a los conteos del puerto`() = runTest {
        val conteos = MutableSharedFlow<PendientesDelTelefono>()
        val vm = AvisoDePendientesViewModel(puerto { conteos }, telemetria)
        val recolector = backgroundScope.launch(main.testDispatcher) { vm.pendientes.collect {} }

        assertEquals(PendientesDelTelefono(), vm.pendientes.value)
        conteos.emit(PendientesDelTelefono(visitas = 1, fotos = 2))
        assertEquals(3, vm.pendientes.value.total)
        conteos.emit(PendientesDelTelefono())
        assertEquals(0, vm.pendientes.value.total)
        recolector.cancel()
    }

    @Test
    fun `E-APP-096 una lectura que falla deja rastro y conserva lo ultimo`() = runTest {
        val vm = AvisoDePendientesViewModel(
            puerto {
                flow {
                    emit(PendientesDelTelefono(pagos = 2))
                    error("db cerrada")
                }
            },
            telemetria
        )
        val recolector = backgroundScope.launch(main.testDispatcher) { vm.pendientes.collect {} }

        assertEquals(2, vm.pendientes.value.total)
        assertTrue(
            telemetria.recorded.any {
                it.type == TelemetryEventType.ERROR &&
                    it.name == VisitasTelemetria.CODE_AVISO_PENDIENTES_FALLO
            }
        )
        recolector.cancel()
    }

    private fun puerto(fuente: () -> Flow<PendientesDelTelefono>): PendientesDelTelefonoPort =
        object : PendientesDelTelefonoPort {
            override fun observar(): Flow<PendientesDelTelefono> = fuente()
        }
}
