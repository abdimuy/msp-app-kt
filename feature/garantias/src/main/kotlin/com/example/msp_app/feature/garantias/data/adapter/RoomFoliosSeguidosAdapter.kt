package com.example.msp_app.feature.garantias.data.adapter

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.dao.garantiav2.GarantiaFolioDao
import com.example.msp_app.core.database.entities.garantiav2.GarantiaFolioSeguidoEntity
import com.example.msp_app.feature.garantias.domain.model.EstadoLocalDeFolio
import com.example.msp_app.feature.garantias.domain.model.FolioSeguido
import com.example.msp_app.feature.garantias.domain.model.OrigenDeFolio
import com.example.msp_app.feature.garantias.domain.port.FoliosSeguidosPort
import java.time.Instant
import javax.inject.Inject

/**
 * "Mis folios" sobre Room.
 *
 * [FolioSeguido.id] es local y nunca viaja como `id` del folio; [marcarAbierto]
 * es la única escritura que guarda el `id` del servidor, y lo hace cuando
 * `abrir` ya respondió (D4). Mientras tanto la bandeja muestra el folio como
 * pendiente de envío.
 */
class RoomFoliosSeguidosAdapter @Inject constructor(
    private val dao: GarantiaFolioDao
) : FoliosSeguidosPort {

    override suspend fun guardar(folio: FolioSeguido) {
        dao.guardar(folio.aEntidad())
    }

    override suspend fun todos(): List<FolioSeguido> = dao.todos().map { it.aFolio() }

    override suspend fun porId(id: String): FolioSeguido? = dao.porId(id)?.aFolio()

    override suspend fun porFolioId(folioId: String): FolioSeguido? =
        dao.porFolioId(folioId)?.aFolio()

    override suspend fun marcarAbierto(id: String, folioId: String, ahora: Instant) {
        dao.marcarAbierto(id, folioId, AppTime.toWireFormat(ahora))
    }

    override suspend fun marcarEstado(
        id: String,
        estado: EstadoLocalDeFolio,
        error: String?,
        ahora: Instant
    ) {
        dao.marcarEstado(id, estado.wireValue, error, AppTime.toWireFormat(ahora))
    }

    override suspend fun pendientesDeEnvio(): Int = dao.pendientesDeEnvio()
}

private fun FolioSeguido.aEntidad(): GarantiaFolioSeguidoEntity = GarantiaFolioSeguidoEntity(
    ID = id,
    FOLIO_ID = folioId,
    ORIGEN = origen.wireValue,
    CLIENTE_ID = clienteId,
    VENTA_ID = ventaId,
    ESTADO_LOCAL = estadoLocal.wireValue,
    ABIERTO_EN = AppTime.toWireFormat(abiertoEn),
    ACTUALIZADO_EN = AppTime.toWireFormat(actualizadoEn),
    ULTIMO_ERROR = ultimoError
)

private fun GarantiaFolioSeguidoEntity.aFolio(): FolioSeguido = FolioSeguido(
    id = ID,
    folioId = FOLIO_ID,
    origen = OrigenDeFolio.entries.firstOrNull { it.wireValue == ORIGEN }
        ?: error("origen desconocido en mis folios: $ORIGEN"),
    clienteId = CLIENTE_ID,
    ventaId = VENTA_ID,
    estadoLocal = EstadoLocalDeFolio.entries.firstOrNull { it.wireValue == ESTADO_LOCAL }
        ?: error("estado local desconocido en mis folios: $ESTADO_LOCAL"),
    abiertoEn = AppTime.parseWireFormatOrNull(ABIERTO_EN) ?: Instant.EPOCH,
    actualizadoEn = AppTime.parseWireFormatOrNull(ACTUALIZADO_EN) ?: Instant.EPOCH,
    ultimoError = ULTIMO_ERROR
)
