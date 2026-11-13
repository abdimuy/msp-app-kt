package com.example.msp_app.feature.garantias.data.adapter

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.dao.garantiav2.GarantiaFotoDao
import com.example.msp_app.core.database.entities.garantiav2.GarantiaFotoPendienteEntity
import com.example.msp_app.feature.garantias.di.GarantiasReloj
import com.example.msp_app.feature.garantias.domain.model.EstadoDeFoto
import com.example.msp_app.feature.garantias.domain.model.FotoNueva
import com.example.msp_app.feature.garantias.domain.model.FotoPendienteDeGarantia
import com.example.msp_app.feature.garantias.domain.port.FotosDeGarantiaPort
import java.time.Instant
import javax.inject.Inject

/**
 * Las fotos en espera sobre Room.
 *
 * La clave de la foto es su llave primaria: guardar dos veces la misma no
 * duplica la evidencia, y un reintento no crea una segunda imagen en el
 * servidor (D3, mutante A1). El archivo vive en `filesDir` y se borra desde
 * [RoomEstadoDeLasFotosAdapter], sólo después del 2xx.
 */
class RoomFotosDeGarantiaAdapter @Inject constructor(
    private val dao: GarantiaFotoDao,
    @GarantiasReloj private val reloj: AppClock
) : FotosDeGarantiaPort {

    override suspend fun guardar(foto: FotoNueva, comandoId: Long?) {
        dao.guardar(foto.aEntidad(comandoId, ahoraTexto()))
    }

    override suspend fun guardarTodas(fotos: List<FotoNueva>, comandoId: Long?) {
        if (fotos.isEmpty()) return
        val ahora = ahoraTexto()
        dao.guardarTodas(fotos.map { it.aEntidad(comandoId, ahora) })
    }

    override suspend fun pendientes(): List<FotoPendienteDeGarantia> =
        dao.pendientes().map { it.aFoto() }

    override suspend fun listasParaSubir(): List<FotoPendienteDeGarantia> =
        dao.listasParaSubir().map { it.aFoto() }

    override suspend fun porClave(clave: String): FotoPendienteDeGarantia? =
        dao.porClave(clave)?.aFoto()

    override suspend fun pendientesCount(): Int = dao.pendientesCount()

    override suspend fun rutasVivas(): List<String> = dao.rutasVivas()

    private fun ahoraTexto(): String = AppTime.toWireFormat(reloj.now())
}

internal fun FotoNueva.aEntidad(comandoId: Long?, ahora: String): GarantiaFotoPendienteEntity =
    GarantiaFotoPendienteEntity(
        CLAVE = clave,
        COMANDO_ID = comandoId,
        FOLIO_REF = folioRef,
        EVENTO_REF = eventoRef,
        RUTA_ARCHIVO = rutaArchivo,
        MIME = mime,
        ESTADO = EstadoDeFoto.PENDIENTE.valor,
        CREADO_EN = ahora,
        ACTUALIZADO_EN = ahora
    )

internal fun GarantiaFotoPendienteEntity.aFoto(): FotoPendienteDeGarantia = FotoPendienteDeGarantia(
    clave = CLAVE,
    rutaArchivo = RUTA_ARCHIVO,
    mime = MIME,
    estado = EstadoDeFoto.entries.firstOrNull { it.valor == ESTADO }
        ?: error("estado de foto desconocido: $ESTADO"),
    intentos = INTENTOS,
    creadoEn = AppTime.parseWireFormatOrNull(CREADO_EN) ?: Instant.EPOCH,
    actualizadoEn = AppTime.parseWireFormatOrNull(ACTUALIZADO_EN) ?: Instant.EPOCH,
    comandoId = COMANDO_ID,
    folioRef = FOLIO_REF,
    folioId = FOLIO_ID,
    eventoRef = EVENTO_REF,
    eventoId = EVENTO_ID,
    ultimoError = ULTIMO_ERROR,
    subidaEn = AppTime.parseWireFormatOrNull(SUBIDA_EN)
)
