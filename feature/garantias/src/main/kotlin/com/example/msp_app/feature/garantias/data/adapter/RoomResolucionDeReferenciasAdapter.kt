package com.example.msp_app.feature.garantias.data.adapter

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.dao.garantiav2.GarantiaComandoDao
import com.example.msp_app.feature.garantias.domain.port.ResolucionDeReferenciasPort
import java.time.Instant
import javax.inject.Inject

/**
 * Propaga a las filas de la cola los ids que el servidor devolvió.
 *
 * Sólo escribe `FOLIO_ID`, `ARTICULO_ID` y `EVENTO_ID`; nunca toca la clave, el
 * cuerpo ni la hora de creación (D3). Vive aparte de
 * [RoomColaDeComandosAdapter] porque es otro contrato sobre la misma tabla, y
 * separarlos deja cada clase con una responsabilidad legible.
 */
class RoomResolucionDeReferenciasAdapter @Inject constructor(
    private val dao: GarantiaComandoDao
) : ResolucionDeReferenciasPort {

    override suspend fun resolverFolio(folioRef: String, folioId: String, ahora: Instant): Int =
        dao.resolverFolio(folioRef, folioId, AppTime.toWireFormat(ahora))

    override suspend fun resolverArticulo(
        articuloRef: String,
        articuloId: String,
        ahora: Instant
    ): Int = dao.resolverArticulo(articuloRef, articuloId, AppTime.toWireFormat(ahora))

    override suspend fun resolverEvento(eventoRef: String, eventoId: String, ahora: Instant): Int =
        dao.resolverEvento(eventoRef, eventoId, AppTime.toWireFormat(ahora))
}
