package com.example.msp_app.feature.garantias.data.adapter

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.dao.garantiav2.GarantiaFotoDao
import com.example.msp_app.feature.garantias.domain.port.ArchivosDeGarantiaPort
import com.example.msp_app.feature.garantias.domain.port.EstadoDeLasFotosPort
import java.time.Instant
import javax.inject.Inject

/**
 * El estado de una foto después de intentar subirla, sobre Room.
 *
 * [eliminar] es la única escritura que borra el archivo, y el procesador la
 * llama **después** de [marcarSubida]. Si la subida no respondió 2xx, ni la
 * fila ni el archivo se tocan: la evidencia sigue visible y reintentable (D3,
 * mutante A7).
 */
class RoomEstadoDeLasFotosAdapter @Inject constructor(
    private val dao: GarantiaFotoDao,
    private val archivos: ArchivosDeGarantiaPort
) : EstadoDeLasFotosPort {

    override suspend fun marcarSubida(clave: String, ahora: Instant) {
        dao.marcarSubida(clave, AppTime.toWireFormat(ahora))
    }

    override suspend fun marcarRechazada(clave: String, error: String, ahora: Instant) {
        dao.marcarRechazada(clave, error, AppTime.toWireFormat(ahora))
    }

    override suspend fun anotarIntento(clave: String, error: String, ahora: Instant) {
        dao.anotarIntento(clave, error, AppTime.toWireFormat(ahora))
    }

    override suspend fun resolverEvento(
        eventoRef: String,
        eventoId: String,
        folioId: String,
        ahora: Instant
    ): Int = dao.resolverEvento(eventoRef, eventoId, folioId, AppTime.toWireFormat(ahora))

    override suspend fun eliminar(clave: String) {
        val foto = dao.porClave(clave) ?: return
        dao.eliminar(clave)
        archivos.borrar(foto.RUTA_ARCHIVO)
    }
}
