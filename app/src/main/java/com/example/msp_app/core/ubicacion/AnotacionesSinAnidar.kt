package com.example.msp_app.core.ubicacion

import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.entities.PlazoDeEnvioEntity
import com.example.msp_app.core.database.entities.UbicacionDeCapturaEntity

/**
 * Las dos anotaciones que se hacen DENTRO de la transaccion del dinero (E-APP-145, K1): el plazo de
 * envio y la fila de ubicacion de ANTES. Solo escriben en `plazo_de_envio` y `ubicacion_de_captura`.
 *
 * ## Por que SQL directo y no el DAO
 *
 * Un `@Insert` o `@Query` de escritura de Room abre su propia transaccion ANIDADA dentro de la del
 * dinero. Si esa sentencia lanza, aunque se atrape, el `endTransaction` interno sin exito marca la
 * transaccion EXTERNA como fallida y se revierte TODO, el pago incluido (MEDIDO el 2026-10-30 en
 * `UbicacionDeCapturaTest`: con el DAO, un fallo al escribir el plazo se llevaba el pago). Un
 * `execSQL` no abre transaccion anidada: si lanza, la transaccion del dinero sigue sana y se
 * confirma. Es lo que exige K1: escribir el plazo NUNCA puede revertir ni impedir el guardado.
 *
 * Los dos `INSERT` son `OR IGNORE`: repetir un id no lanza ni reescribe. Quien llama atrapa lo que
 * aun asi lance (tabla ausente, disco).
 */
class AnotacionesSinAnidar(private val db: AppDatabase) {

    fun plazo(fila: PlazoDeEnvioEntity) {
        db.openHelper.writableDatabase.execSQL(
            SQL_PLAZO,
            arrayOf(fila.id, fila.tipo, fila.guardadoEnEpochMs, fila.plazoMs)
        )
    }

    fun ubicacion(fila: UbicacionDeCapturaEntity) {
        db.openHelper.writableDatabase.execSQL(
            SQL_UBICACION,
            arrayOf(
                fila.id,
                fila.tipo,
                fila.lat,
                fila.lng,
                fila.precisionM,
                fila.fixAtMs,
                fila.origen,
                fila.estadoEnvio,
                fila.intentos,
                fila.creadaEn
            )
        )
    }

    private companion object {
        const val SQL_PLAZO =
            "INSERT OR IGNORE INTO plazo_de_envio (id, tipo, guardado_en_epoch_ms, plazo_ms) " +
                "VALUES (?, ?, ?, ?)"

        const val SQL_UBICACION =
            "INSERT OR IGNORE INTO ubicacion_de_captura (id, tipo, lat, lng, precision_m, " +
                "fix_at_ms, origen, estado_envio, intentos, creada_en) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
    }
}
