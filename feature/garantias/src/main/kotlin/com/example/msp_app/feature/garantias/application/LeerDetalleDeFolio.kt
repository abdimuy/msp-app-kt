package com.example.msp_app.feature.garantias.application

import com.example.msp_app.feature.garantias.domain.model.ComandoDeGarantia
import com.example.msp_app.feature.garantias.domain.model.EstadoDeComando
import com.example.msp_app.feature.garantias.domain.model.EstadoLocalDeFolio
import com.example.msp_app.feature.garantias.domain.model.FolioSeguido
import com.example.msp_app.feature.garantias.domain.port.ColaDeComandosPort
import com.example.msp_app.feature.garantias.domain.port.FoliosSeguidosPort
import com.example.msp_app.feature.garantias.domain.port.FotosDeGarantiaPort
import com.example.msp_app.feature.garantias.domain.port.GarantiasRemotasPort
import javax.inject.Inject

/**
 * Lee el folio completo y lo deja listo para pintar, con su línea de tiempo.
 *
 * ## Nunca deja la pantalla en blanco
 *
 * Si el folio todavía no existe en el servidor, si no hay señal o si `abrir`
 * fue rechazado, devuelve igual el folio local con sus contadores de pendientes:
 * el cobrador ve lo que capturó y qué falta, en vez de un error de red. La
 * lectura remota es un adorno del detalle, no su fuente de verdad.
 *
 * ## La referencia local de cada artículo
 *
 * "Recolectar" necesita el `articuloRef` con el que se encoló el artículo,
 * porque el comando `avanzar` se enlaza a esa referencia y no al id del
 * servidor (D4). Se busca en los comandos del folio por el `articuloId` que la
 * cola ya resolvió; un artículo que este teléfono no encoló no se puede
 * recolectar desde acá.
 */
class LeerDetalleDeFolio @Inject constructor(
    private val folios: FoliosSeguidosPort,
    private val cola: ColaDeComandosPort,
    private val fotos: FotosDeGarantiaPort,
    private val remotas: GarantiasRemotasPort
) {

    // Cualquier fallo al leer el detalle se pinta como "sin señal": la lectura es
    // un adorno y la pantalla ya tiene la verdad local. Atrapar sólo `IOException`
    // dejaría caer un 404 o un cuerpo raro y cerraría la pantalla.
    @Suppress("TooGenericExceptionCaught")
    suspend fun ejecutar(folioRef: String): DetalleDelFolio {
        val folio = folios.porId(folioRef) ?: return DetalleDelFolio.noEncontrado()
        val comandos = cola.delFolio(folioRef)
        val pendientes = comandos.count { it.estado == EstadoDeComando.PENDIENTE }
        val fotosPendientes = fotos.pendientes().count { it.folioRef == folioRef }

        if (folio.estadoLocal == EstadoLocalDeFolio.RECHAZADO) {
            return DetalleDelFolio(
                folio = folio,
                estado = EstadoDeLaLectura.RECHAZADO,
                comandosPendientes = pendientes,
                fotosPendientes = fotosPendientes,
                motivo = motivoDelRechazo(folio, comandos)
            )
        }

        val folioId = folio.folioId ?: return DetalleDelFolio(
            folio = folio,
            estado = EstadoDeLaLectura.PENDIENTE_ENVIO,
            comandosPendientes = pendientes,
            fotosPendientes = fotosPendientes
        )

        return try {
            val detalle = remotas.detalle(folioId)
            DetalleDelFolio(
                folio = folio,
                estado = EstadoDeLaLectura.DISPONIBLE,
                comandosPendientes = pendientes,
                fotosPendientes = fotosPendientes,
                detalle = detalle,
                articulos = detalle.articulos.map { articulo ->
                    ArticuloDelFolio(
                        remoto = articulo,
                        articuloRef = comandos.firstOrNull { it.articuloId == articulo.id }
                            ?.articuloRef
                    )
                },
                eventos = detalle.eventos
            )
        } catch (_: Exception) {
            DetalleDelFolio(
                folio = folio,
                estado = EstadoDeLaLectura.SIN_SENAL,
                comandosPendientes = pendientes,
                fotosPendientes = fotosPendientes,
                motivo = folio.ultimoError
            )
        }
    }

    private fun motivoDelRechazo(folio: FolioSeguido, comandos: List<ComandoDeGarantia>): String? =
        folio.ultimoError
            ?: comandos.firstOrNull { it.estado == EstadoDeComando.RECHAZADO }?.ultimoError
}
