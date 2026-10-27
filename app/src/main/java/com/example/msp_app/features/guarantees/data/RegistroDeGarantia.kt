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
import com.example.msp_app.data.captura.CapturasEnProceso
import kotlinx.coroutines.CancellationException

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
 *
 * E-APP-119 (D15): el marcador lleva en su `RESUMEN` lo capturado
 * ([ResumenDeLaGarantia]), así que una garantía CAPTURADA con 0 escrituras se
 * reanuda con [reanudarPendientes] al volver a la app o al tocar el aviso.
 */
class RegistroDeGarantia(
    private val db: AppDatabase,
    private val clock: AppClock = AppClock.System,
    private val garantias: GuaranteeDao = db.guaranteeDao()
) {
    private val capturas get() = db.capturaEnCursoDao()

    suspend fun capturar(externalId: String, usuario: String, resumen: String = "{}") {
        val ahora = AppTime.toWireFormat(clock.now())
        sinCancelar {
            val nuevo = capturas.capturar(
                CapturaEnCursoEntity(
                    ID = externalId,
                    TIPO = CapturaEnCursoEntity.TIPO_GARANTIA,
                    LLAVE = externalId,
                    USUARIO = usuario,
                    ESTADO = CapturaEnCursoEntity.CAPTURADA,
                    RESUMEN = resumen,
                    CREADA_EN = ahora,
                    ACTUALIZADA_EN = ahora
                )
            )
            // Reintento desde la pantalla: refresca lo capturado mientras siga
            // CAPTURADA (una ESCRITA o CERRADA no cambia).
            if (nuevo == -1L) capturas.actualizarCapturada(externalId, resumen, ahora)
        }
    }

    /** E-APP-119: las fotos ya comprimidas quedan en el resumen; reanudar no vuelve a comprimir. */
    suspend fun anotarResumen(externalId: String, resumen: String) {
        sinCancelar {
            capturas.actualizarCapturada(externalId, resumen, AppTime.toWireFormat(clock.now()))
        }
    }

    /**
     * E-APP-119 (D15, regla del dueño "nada se pierde"): reanuda las garantías
     * CAPTURADA de [usuario] que no llegaron a escribirse. Si el resumen trae
     * las fotos comprimidas, se usan; si no, se comprimen de [comprimir] con
     * sus fuentes. **Nunca escribe una garantía sin imágenes**: si no hay
     * ninguna, la captura queda CAPTURADA y visible en el aviso. Se salta lo
     * que la pantalla de este proceso está escribiendo ([CapturasEnProceso]).
     *
     * @return cuántas quedaron escritas ahora.
     */
    @Suppress("TooGenericExceptionCaught") // una garantía que falla no detiene a las demás.
    suspend fun reanudarPendientes(
        usuario: String,
        comprimir: suspend (
            fuentes: List<String>,
            externalId: String
        ) -> List<Pair<String, String>>,
        encolar: (externalId: String) -> Unit
    ): Int {
        var reanudadas = 0
        val pendientes = capturas.abiertasDe(CapturaEnCursoEntity.TIPO_GARANTIA, usuario)
            .filter { it.ESTADO == CapturaEnCursoEntity.CAPTURADA }
        for (marcador in pendientes) {
            val id = marcador.ID
            val leido = ResumenDeLaGarantia.leer(marcador.RESUMEN) ?: continue
            if (!CapturasEnProceso.entrarSiLibre(id)) continue
            try {
                val resumen = if (leido.imagenes.isNullOrEmpty()) {
                    leido.conImagenes(comprimir(leido.fuentes.orEmpty(), id))
                        .also { anotarResumen(id, it.json()) }
                } else {
                    leido
                }
                val garantia = resumen.garantia(id) ?: continue
                val imagenes = resumen.imagenesDe(id).takeIf { it.isNotEmpty() } ?: continue
                escribir(garantia, imagenes)
                sinCancelar {
                    encolar(id)
                    capturas.marcarCerrada(id, AppTime.toWireFormat(clock.now()))
                }
                reanudadas++
            } catch (cancelada: CancellationException) {
                throw cancelada
            } catch (_: Throwable) {
                // Queda CAPTURADA y visible; el siguiente regreso a la app reintenta.
            } finally {
                CapturasEnProceso.salir(id)
            }
        }
        return reanudadas
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
