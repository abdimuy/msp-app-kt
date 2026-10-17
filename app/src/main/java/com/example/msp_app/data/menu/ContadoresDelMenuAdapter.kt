package com.example.msp_app.data.menu

import com.example.msp_app.core.navegacion.domain.port.ContadoresDelMenuPort
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.navigation.menu.IdsDelMenu
import kotlinx.coroutines.CancellationException

/**
 * Los contadores con fuente verificada (spec §4): Clientes "N por visitar" y
 * Ventas "N sin subir". Reporte y Traspasos no tienen fuente en este plan y no
 * aparecen. Cada fuente falla sola: si la cartera no se puede leer, Ventas
 * conserva su número.
 */
class ContadoresDelMenuAdapter(
    private val clientesPorVisitar: suspend () -> Int,
    private val ventasSinSubir: suspend () -> Int,
    private val telemetry: Telemetry
) : ContadoresDelMenuPort {

    override suspend fun contadores(ids: Set<String>): Map<String, String> = buildMap {
        if (IdsDelMenu.CLIENTES in ids) {
            contar(
                IdsDelMenu.CLIENTES,
                clientesPorVisitar
            )?.let { put(IdsDelMenu.CLIENTES, "$it por visitar") }
        }
        if (IdsDelMenu.VENTAS in ids) {
            contar(
                IdsDelMenu.VENTAS,
                ventasSinSubir
            )?.let { put(IdsDelMenu.VENTAS, "$it sin subir") }
        }
    }

    @Suppress(
        "TooGenericExceptionCaught"
    ) // una fuente rota deja SU opción sin contador, nunca las demás
    private suspend fun contar(id: String, fuente: suspend () -> Int): Int? = try {
        fuente().takeIf { it > 0 }
    } catch (cancelada: CancellationException) {
        throw cancelada
    } catch (falla: Exception) {
        telemetry.error(
            code = CODE_CONTADOR_FALLO,
            message = "el menu lateral no pudo contar una opcion",
            props = mapOf("contador" to id, "excepcion" to falla::class.java.simpleName)
        )
        null
    }

    companion object {
        const val CODE_CONTADOR_FALLO = "menu_contador_fallo"
    }
}
