package com.example.msp_app.core.mapas.ui

import com.example.msp_app.core.mapas.application.MapasTelemetria
import com.example.msp_app.core.mapas.domain.EstadoDelExtracto
import com.example.msp_app.core.mapas.domain.port.ExtractoDeMapaPort
import com.example.msp_app.core.mapas.fake.mapaDePrueba
import com.example.msp_app.core.testing.MainDispatcherRule
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** El ViewModel que comparten el suelo y la pantalla de descarga. */
class MapaViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val telemetry = RecordingTelemetry()

    private class PuertoFalso(inicial: EstadoDelExtracto) : ExtractoDeMapaPort {
        private val flujo = MutableStateFlow(inicial)
        val pedidos: MutableList<Unit> = mutableListOf()
        val borrados: MutableList<Unit> = mutableListOf()

        override fun estado(): Flow<EstadoDelExtracto> = flujo.asStateFlow()

        override suspend fun pedirLaDescarga() {
            pedidos += Unit
        }

        override suspend fun cancelarYBorrar() {
            borrados += Unit
        }
    }

    @Test
    fun `descargar y borrar llegan al puerto`() = runTest(StandardTestDispatcher()) {
        val puerto = PuertoFalso(EstadoDelExtracto.Ausente)
        val vm = MapaViewModel(puerto, telemetry, null)

        vm.descargar()
        vm.borrar()
        advanceUntilIdle()

        assertEquals(1, puerto.pedidos.size)
        assertEquals(1, puerto.borrados.size)
    }

    /**
     * **La norma de errores, medida.** Si alguien borra la línea de telemetría
     * de `motorNoArranco`, este test se pone rojo: un motor que no arranca y no
     * se reporta es exactamente el error silencioso que `CLAUDE.md` ya pagó.
     */
    @Test
    fun `cuando MapLibre no arranca se emite el code, con la clase de la excepcion`() =
        runTest(StandardTestDispatcher()) {
            val vm = MapaViewModel(PuertoFalso(EstadoDelExtracto.Ausente), telemetry, null)

            vm.motorNoArranco(UnsatisfiedLinkError("libmaplibre.so"))

            val evento = telemetry.recorded.single()
            assertEquals(MapasTelemetria.CODE_MOTOR_NO_ARRANCO, evento.name)
            assertEquals(
                "UnsatisfiedLinkError",
                evento.props[MapasTelemetria.PROP_EXCEPCION]
            )
        }

    /**
     * **Anti-PII.** El `message` de una excepción de IO puede traer la ruta del
     * archivo, que lleva el `filesDir` del dispositivo; y el mapa trabaja con
     * coordenadas, que son la dirección del cliente. Ni una cosa ni la otra
     * pueden salir del teléfono.
     */
    @Test
    fun `el reporte del motor no arrastra rutas ni coordenadas`() =
        runTest(StandardTestDispatcher()) {
            val vm =
                MapaViewModel(PuertoFalso(EstadoDelExtracto.Listo(mapaDePrueba())), telemetry, null)

            vm.motorNoArranco(
                IOException("/data/user/0/com.example.msp_app/files/mapas/ruta.pmtiles")
            )

            val evento = telemetry.recorded.single()
            val texto = evento.name + evento.props.values.joinToString(" ")
            assertFalse("una ruta de archivo viajo", texto.contains("/data/"))
            assertFalse("una coordenada viajo", texto.contains("18.4"))
            assertTrue(
                "clave fuera de la lista blanca",
                (evento.props.keys - "message").all { it in MapasTelemetria.CLAVES_PERMITIDAS }
            )
        }
}
