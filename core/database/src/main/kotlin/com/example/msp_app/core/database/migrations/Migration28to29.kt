package com.example.msp_app.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Dos arreglos del sync de cobranza que tocan el mismo upgrade, así que van en
 * una sola migración:
 *
 * 1. **`AFTER_ID` en `cobranza_sync_state`.** El servidor pagina por el par
 *    `(UPDATED_AT, PK)`, pero el cliente solo persistía la mitad `UPDATED_AT`:
 *    cada corrida arrancaba con `after_id = 0` y volvía a procesar el grupo de
 *    filas empatadas en ese `UPDATED_AT` desde el principio. Con un grupo
 *    chico eso solo gastaba red; tras el backfill de migración —1,835,734 de
 *    2,173,422 filas comparten un único `UPDATED_AT`— el grupo empatado ES el
 *    historial completo y la paginación nunca sale de él (medido en campo:
 *    2,057 pagos re-descargados cada ~76 s, indefinidamente). Persistir la
 *    otra mitad cierra el bucle. NOT NULL con DEFAULT 0 porque 0 es
 *    literalmente "desde el inicio del grupo": las filas que ya existen
 *    retoman en el mismo punto donde el código viejo las dejaba, sin resync
 *    extra ni pérdida de cursor.
 *
 * 2. **Índice sobre `Payment.PAGO_RECIBIDO_ID`.** La migración 26→27 agregó la
 *    columna sin índice, y encima de ella corren la subconsulta de
 *    `PaymentDao.findCollapsibleUuidTwins` (barrido auto-sanable del
 *    reconciler, en cada tick) y el colapso de gemelos UUID de `mergePagos`.
 *    Sin índice cada pasada es un scan completo de `Payment`.
 *
 * Solo agrega una columna y un índice: ninguna tabla se recrea, así que los
 * cursores y los pagos aún sin subir del cobrador quedan intactos.
 */
val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE cobranza_sync_state ADD COLUMN AFTER_ID INTEGER NOT NULL DEFAULT 0"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_Payment_PAGO_RECIBIDO_ID " +
                "ON Payment (PAGO_RECIBIDO_ID)"
        )
    }
}
