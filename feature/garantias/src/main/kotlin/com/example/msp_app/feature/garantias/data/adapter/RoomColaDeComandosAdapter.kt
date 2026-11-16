package com.example.msp_app.feature.garantias.data.adapter

import androidx.room.withTransaction
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.garantiav2.GarantiaComandoDao
import com.example.msp_app.core.database.dao.garantiav2.GarantiaFotoDao
import com.example.msp_app.core.database.entities.garantiav2.GarantiaComandoPendienteEntity
import com.example.msp_app.feature.garantias.di.GarantiasReloj
import com.example.msp_app.feature.garantias.domain.model.ComandoDeGarantia
import com.example.msp_app.feature.garantias.domain.model.ComandoNuevo
import com.example.msp_app.feature.garantias.domain.model.EstadoDeComando
import com.example.msp_app.feature.garantias.domain.model.FotoNueva
import com.example.msp_app.feature.garantias.domain.model.OperacionDeGarantia
import com.example.msp_app.feature.garantias.domain.port.ColaDeComandosPort
import java.time.Instant
import javax.inject.Inject

/**
 * La cola de comandos sobre Room.
 *
 * ## `encolar` es una sola transacción
 *
 * El comando y sus fotos se escriben juntos: si la foto falla, no queda un
 * comando a medias. La fila del comando se escribe **tal como llegó** (clave,
 * cuerpo, hora y ruta congelados) y nunca se vuelve a tocar salvo por estado
 * (D3).
 *
 * ## La resolución de ids vive en otro adaptador
 *
 * [com.example.msp_app.feature.garantias.domain.port.ResolucionDeReferenciasPort]
 * escribe las mismas filas, pero se separó en
 * [RoomResolucionDeReferenciasAdapter] para que ninguna de las dos clases
 * acumule más responsabilidades de las que el linter admite.
 */
class RoomColaDeComandosAdapter @Inject constructor(
    private val db: AppDatabase,
    private val comandoDao: GarantiaComandoDao,
    private val fotoDao: GarantiaFotoDao,
    @GarantiasReloj private val reloj: AppClock
) : ColaDeComandosPort {

    override suspend fun encolar(comando: ComandoNuevo, fotos: List<FotoNueva>): Long =
        db.withTransaction {
            val ahora = ahoraTexto()
            val id = comandoDao.insertar(comando.aEntidad(ahora))
            if (fotos.isNotEmpty()) fotoDao.guardarTodas(fotos.map { it.aEntidad(id, ahora) })
            id
        }

    override suspend fun pendientes(): List<ComandoDeGarantia> =
        comandoDao.pendientes().map { it.aComando() }

    override suspend fun delFolio(folioRef: String): List<ComandoDeGarantia> =
        comandoDao.delFolio(folioRef).map { it.aComando() }

    override suspend fun porClave(clave: String): ComandoDeGarantia? =
        comandoDao.porClave(clave)?.aComando()

    override suspend fun marcarEnviado(id: Long, ahora: Instant) {
        comandoDao.marcarEnviado(id, AppTime.toWireFormat(ahora))
    }

    override suspend fun marcarRechazado(id: Long, error: String, ahora: Instant) {
        comandoDao.marcarRechazado(id, error, AppTime.toWireFormat(ahora))
    }

    override suspend fun anotarIntento(id: Long, error: String, ahora: Instant) {
        comandoDao.anotarIntento(id, error, AppTime.toWireFormat(ahora))
    }

    private fun ahoraTexto(): String = AppTime.toWireFormat(reloj.now())
}

private fun ComandoNuevo.aEntidad(ahora: String): GarantiaComandoPendienteEntity =
    GarantiaComandoPendienteEntity(
        CLAVE = clave,
        OPERACION = operacion.wireValue,
        METODO = metodo,
        RUTA = ruta,
        CUERPO = cuerpo,
        CONTENT_TYPE = contentType,
        DEVICE_CREATED_AT = AppTime.toWireFormat(deviceCreatedAt),
        ESTADO = EstadoDeComando.PENDIENTE.wireValue,
        FOLIO_REF = folioRef,
        ARTICULO_REF = articuloRef,
        EVENTO_REF = eventoRef,
        CREADO_EN = ahora,
        ACTUALIZADO_EN = ahora
    )

/**
 * Una fila de la cola ya guardada.
 *
 * La operación y el estado se traducen con `error` si el literal no existe:
 * una fila así sólo puede venir de una migración a mano, y adivinar el valor
 * mandaría el comando a la ruta equivocada.
 */
internal fun GarantiaComandoPendienteEntity.aComando(): ComandoDeGarantia = ComandoDeGarantia(
    id = ID,
    clave = CLAVE,
    operacion = OperacionDeGarantia.entries.firstOrNull { it.wireValue == OPERACION }
        ?: error("operación desconocida en la cola: $OPERACION"),
    metodo = METODO,
    ruta = RUTA,
    cuerpo = CUERPO,
    contentType = CONTENT_TYPE,
    deviceCreatedAt = AppTime.parseWireFormatOrNull(DEVICE_CREATED_AT) ?: Instant.EPOCH,
    estado = EstadoDeComando.entries.firstOrNull { it.wireValue == ESTADO }
        ?: error("estado desconocido en la cola: $ESTADO"),
    intentos = INTENTOS,
    creadoEn = AppTime.parseWireFormatOrNull(CREADO_EN) ?: Instant.EPOCH,
    actualizadoEn = AppTime.parseWireFormatOrNull(ACTUALIZADO_EN) ?: Instant.EPOCH,
    ultimoError = ULTIMO_ERROR,
    folioRef = FOLIO_REF,
    folioId = FOLIO_ID,
    articuloRef = ARTICULO_REF,
    articuloId = ARTICULO_ID,
    eventoRef = EVENTO_REF,
    eventoId = EVENTO_ID
)
