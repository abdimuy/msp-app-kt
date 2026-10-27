package com.example.msp_app.data.captura

import com.example.msp_app.data.auth.emailDeLaSesion
import com.example.msp_app.feature.pagos.domain.port.ReanudarCapturasPort
import com.example.msp_app.feature.visitas.domain.port.RegistroDeVisitaPort
import com.example.msp_app.features.guarantees.data.RegistroDeGarantia
import kotlinx.coroutines.CancellationException

/**
 * E-APP-119 (D15, regla del dueño "nada se pierde"): reanuda las visitas y
 * garantías CAPTURADA con 0 escrituras del usuario en sesión.
 *
 * Lo llaman dos caminos, los dos en primer plano: el regreso a la app
 * ([ReanudarCapturasAlVolver]) y el toque del aviso "N capturas sin terminar".
 * Nunca un worker: la visita escribe `sales` (`ESTADO_COBRANZA`), y el diseño
 * (D10) no deja escribir `sales` en segundo plano.
 *
 * Total: un fallo de una no detiene a la otra ni sale de aquí. Lo que no se
 * reanuda queda CAPTURADA y visible en el aviso.
 */
class ReanudadorDeCapturas(
    private val visitas: RegistroDeVisitaPort,
    private val garantias: RegistroDeGarantia,
    private val comprimirGarantia: suspend (
        fuentes: List<String>,
        externalId: String
    ) -> List<Pair<String, String>>,
    private val encolarGarantia: (externalId: String) -> Unit,
    private val emailEnSesion: () -> String? = ::emailDeLaSesion
) : ReanudarCapturasPort {

    override suspend fun reanudar() {
        sinTumbar { visitas.reanudarPendientes() }
        val usuario = emailEnSesion()?.takeIf { it.isNotBlank() } ?: return
        sinTumbar { garantias.reanudarPendientes(usuario, comprimirGarantia, encolarGarantia) }
    }

    @Suppress("TooGenericExceptionCaught") // reanudar nunca tumba la pantalla que lo pidió.
    private suspend fun sinTumbar(bloque: suspend () -> Unit) {
        try {
            bloque()
        } catch (cancelada: CancellationException) {
            throw cancelada
        } catch (_: Throwable) {
            // Sigue CAPTURADA y visible; el siguiente regreso o toque reintenta.
        }
    }
}
