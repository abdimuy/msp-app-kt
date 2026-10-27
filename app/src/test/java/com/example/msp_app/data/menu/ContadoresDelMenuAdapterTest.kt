package com.example.msp_app.data.menu

import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.navigation.menu.IdsDelMenu
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ContadoresDelMenuAdapterTest {

    private val telemetria = RecordingTelemetry(FakeClock())
    private var consultasDeCartera = 0

    private fun adaptador(porVolver: Int = 8, sinSubir: Int = 1, fallaCartera: Boolean = false) =
        ContadoresDelMenuAdapter(
            clientesPorVisitar = {
                consultasDeCartera += 1
                if (fallaCartera) error("cartera ilegible") else porVolver
            },
            ventasSinSubir = { sinSubir },
            telemetry = telemetria
        )

    @Test
    fun `formatea los dos contadores con fuente`() = runTest {
        assertEquals(
            mapOf(IdsDelMenu.CLIENTES to "8 por visitar", IdsDelMenu.VENTAS to "1 sin subir"),
            adaptador().contadores(
                setOf(IdsDelMenu.CLIENTES, IdsDelMenu.VENTAS, IdsDelMenu.TRASPASOS)
            )
        )
    }

    @Test
    fun `un contador en cero no se pinta`() = runTest {
        assertEquals(
            emptyMap<String, String>(),
            adaptador(
                porVolver = 0,
                sinSubir = 0
            ).contadores(setOf(IdsDelMenu.CLIENTES, IdsDelMenu.VENTAS))
        )
    }

    @Test
    fun `sin la opcion visible no se calcula su contador`() = runTest {
        adaptador().contadores(setOf(IdsDelMenu.VENTAS))
        assertEquals(0, consultasDeCartera)
    }

    @Test
    fun `si una fuente falla las demas siguen y se reporta sin datos personales`() = runTest {
        val resultado = adaptador(
            fallaCartera = true
        ).contadores(setOf(IdsDelMenu.CLIENTES, IdsDelMenu.VENTAS))

        assertEquals(mapOf(IdsDelMenu.VENTAS to "1 sin subir"), resultado)
        val error = telemetria.recorded.single {
            it.type == TelemetryEventType.ERROR && it.name == ContadoresDelMenuAdapter.CODE_CONTADOR_FALLO
        }
        // RecordingTelemetry.error() agrega "message" a props (ver su KDoc) — el brief
        // original no lo incluía; MEDIDO al correr la prueba contra el fake real.
        assertEquals(
            mapOf(
                "contador" to IdsDelMenu.CLIENTES,
                "excepcion" to "IllegalStateException",
                "message" to "el menu lateral no pudo contar una opcion"
            ),
            error.props
        )
    }

    @Test
    fun `traspasos nunca lleva contador`() = runTest {
        assertEquals(
            emptyMap<String, String>(),
            adaptador().contadores(setOf(IdsDelMenu.TRASPASOS, IdsDelMenu.REPORTE))
        )
    }
}
