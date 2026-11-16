package com.example.msp_app.feature.garantias.application

import com.example.msp_app.feature.garantias.domain.model.EstadoDeComando
import com.example.msp_app.feature.garantias.domain.port.ColaDeComandosPort
import com.example.msp_app.feature.garantias.domain.port.FoliosSeguidosPort
import com.example.msp_app.feature.garantias.domain.port.FotosDeGarantiaPort
import javax.inject.Inject

/**
 * Arma la bandeja de "mis folios" con lo que este teléfono ya sabe.
 *
 * No toca la red: los folios sin enviar no existen del lado del servidor y los
 * enviados se leen completos al abrirlos. Así la bandeja abre al instante en un
 * sótano sin señal, que es donde el cobrador la necesita (D5).
 *
 * Los contadores salen de la cola y de las fotos, no de una columna duplicada:
 * una sola fuente de verdad para "cuánto falta".
 */
class LeerMisFolios @Inject constructor(
    private val folios: FoliosSeguidosPort,
    private val cola: ColaDeComandosPort,
    private val fotos: FotosDeGarantiaPort
) {

    suspend fun ejecutar(): List<FolioEnBandeja> {
        val fotosPorFolio = fotos.pendientes()
            .groupingBy { it.folioRef }
            .eachCount()

        return folios.todos().map { folio ->
            val comandos = cola.delFolio(folio.id)
            FolioEnBandeja(
                folio = folio,
                comandosPendientes = comandos.count { it.estado == EstadoDeComando.PENDIENTE },
                fotosPendientes = fotosPorFolio[folio.id] ?: 0,
                ultimoError = folio.ultimoError
                    ?: comandos.firstOrNull { it.estado == EstadoDeComando.RECHAZADO }?.ultimoError
            )
        }
    }
}
