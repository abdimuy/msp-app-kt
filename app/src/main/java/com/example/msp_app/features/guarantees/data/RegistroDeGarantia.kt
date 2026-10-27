package com.example.msp_app.features.guarantees.data

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.guarantee.GuaranteeDao
import com.example.msp_app.core.database.entities.CapturaEnCursoEntity
import com.example.msp_app.core.database.entities.GuaranteeEntity
import com.example.msp_app.core.database.entities.GuaranteeImageEntity
import com.example.msp_app.core.database.escribirSinCancelar
import com.example.msp_app.core.database.sinCancelar

/**
 * La escritura de una garantía nueva (E-APP-091; no es dinero, entra al arreglo
 * por decisión del dueño 2026-10-20).
 *
 * Antes: `insertGuarantee` e `insertGuaranteeImage` sueltos, sin transacción y
 * bajo un `viewModelScope` cancelable — salir de la pantalla entre los dos
 * dejaba una garantía SIN imágenes. Ahora:
 *
 * 1. [capturar]: marcador GARANTIA CAPTURADA, en `NonCancellable`, antes de
 *    comprimir nada.
 * 2. [escribir]: garantía + imágenes + marcador ESCRITA en UNA
 *    [escribirSinCancelar]. Idempotente por `EXTERNAL_ID`: el reintento con el
 *    mismo id no crea una segunda garantía (la PK es autogenerada y el insert
 *    es REPLACE, así que sin esta guarda se duplicaba).
 * 3. [cerrar]: tras encolar, el marcador pasa a CERRADA.
 */
class RegistroDeGarantia(
    private val db: AppDatabase,
    private val clock: AppClock = AppClock.System,
    private val garantias: GuaranteeDao = db.guaranteeDao()
) {
    private val capturas get() = db.capturaEnCursoDao()

    suspend fun capturar(externalId: String, usuario: String) {
        val ahora = AppTime.toWireFormat(clock.now())
        sinCancelar {
            capturas.capturar(
                CapturaEnCursoEntity(
                    ID = externalId,
                    TIPO = CapturaEnCursoEntity.TIPO_GARANTIA,
                    LLAVE = externalId,
                    USUARIO = usuario,
                    ESTADO = CapturaEnCursoEntity.CAPTURADA,
                    RESUMEN = "{}",
                    CREADA_EN = ahora,
                    ACTUALIZADA_EN = ahora
                )
            )
        }
    }

    /** @return `true` si la escribió ahora; `false` si ya estaba escrita. */
    suspend fun escribir(garantia: GuaranteeEntity, imagenes: List<GuaranteeImageEntity>): Boolean =
        db.escribirSinCancelar {
            val externalId = garantia.EXTERNAL_ID
            if (garantias.getGuaranteeByExternalId(externalId) != null) {
                capturas.marcarEscrita(externalId, AppTime.toWireFormat(clock.now()))
                false
            } else {
                garantias.insertGuarantees(garantia)
                garantias.insertGuaranteesImagen(imagenes)
                capturas.marcarEscrita(externalId, AppTime.toWireFormat(clock.now()))
                true
            }
        }

    suspend fun cerrar(externalId: String) {
        sinCancelar { capturas.marcarCerrada(externalId, AppTime.toWireFormat(clock.now())) }
    }
}
