package com.example.msp_app.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Seis columnas nuevas en `local_sale` para el plan "Corregir una venta
 * antes de que suba" (`docs/superpowers/plans/2026-09-20-editar-venta-antes-de-subir.md`,
 * sección "El mecanismo de la carrera" + la ronda 2 de revisión que cierra una
 * carrera adicional entre editar y subir): el dueño puede corregir una venta
 * capturada sin señal MIENTRAS el subidor intenta mandarla, y las dos cosas
 * no pueden pisarse.
 *
 * ## Por qué es 30→31 y no 29→30 (integración del 2026-09-21)
 *
 * Esta migración nació como la 29→30 de la rama `feat/editar-venta-local`,
 * porque cuando se escribió la v29 era la última publicada. En paralelo, la
 * rama `feat/pagos-y-visitas` escribió **otra** 29→30 distinta (promesa,
 * citas, comprobantes y ficha del cliente). Ninguna de las dos había salido a
 * la flota, así que no hay un teléfono con "una v30" que quedaría a medias —
 * pero dos migraciones no pueden llevar el mismo número, y **la que se integra
 * en segundo lugar renumera**. La de pagos y visitas se quedó con la v30; ésta
 * pasa a 30→31 y la base sube a v31.
 *
 * El contenido NO cambió: las mismas seis columnas, las mismas sentencias
 * `ALTER TABLE ... ADD COLUMN`, los mismos defaults. Una base v29 llega a v31
 * corriendo las dos migraciones en orden, y `Migration29a32Test` lo prueba con
 * una venta local sembrada y sus hijos.
 *
 * - `CLAIM_ID` (nullable): UUID del candado vivo de la fila. `NULL` = nadie
 *   la tiene. Es UN SOLO candado que puede tomar la edición o la subida,
 *   nunca las dos — mutua exclusión por construcción (una sola columna), no
 *   por dos predicados que alguien pueda desincronizar. Lo escribe
 *   `LocalSaleDao.claimForEdit`/`claimForUpload` con un solo `UPDATE`
 *   guardado por predicado — la atomicidad vive en SQLite, no en Kotlin.
 * - `CLAIM_KIND` (nullable): `'EDIT'` o `'UPLOAD'`. Dice qué arrendamiento
 *   aplica para decidir si `CLAIM_ID` venció (cada tipo tiene el suyo).
 * - `CLAIMED_AT` (nullable): epoch ms en que se acuñó el candado. Con esto
 *   el candado tiene arrendamiento: vencido, el otro lado lo recupera solo.
 *   Sin arrendamiento, la app muriendo con el editor abierto (o un POST que
 *   nunca vuelve) dejaría la venta retenida para siempre — dinero perdido en
 *   vez de una carrera incómoda.
 * - `REVISION` (`NOT NULL DEFAULT 0`): correcciones commiteadas, sólo sube.
 *   0 para toda fila preexistente: nadie ha corregido nada todavía.
 * - `CORRECCION_NO_ENVIADA` (`NOT NULL DEFAULT 0`): la subida de un cuerpo
 *   multipart no tiene tope real de tiempo, así que el arrendamiento de
 *   subida puede vencer con el POST todavía en vuelo; si el editor commitea
 *   en esa ventana y LUEGO vuelve el 2xx (con el cuerpo viejo),
 *   `markSentAndCloseEdit` marca esta columna en vez de pisar la corrección
 *   en silencio. 0 para toda fila preexistente: no hay divergencia que
 *   señalar todavía.
 * - `REVISION_POSTEADA` (nullable, sin default): la `REVISION` del cuerpo que
 *   viajó en el PRIMER `POST` emitido para esa venta. `NULL` para toda fila
 *   preexistente (y para toda venta que aún no ha intentado subir): ninguna
 *   de ellas tiene un cuerpo posteado que anclar. Es contra ESTE valor —no
 *   contra el snapshot de la corrida en curso— que `markSentAndCloseEdit`
 *   decide si hay divergencia, y por eso el camino del "2xx perdido"
 *   (respuesta perdida → corrección → `409` → reconciliación por `GET`) deja
 *   de ser invisible.
 *
 * Solo agrega columnas: ninguna tabla se recrea, así que las ventas
 * pendientes del dueño (y sus productos/combos/imágenes) quedan intactas.
 */
val MIGRATION_30_31 = object : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE local_sale ADD COLUMN CLAIM_ID TEXT")
        db.execSQL("ALTER TABLE local_sale ADD COLUMN CLAIM_KIND TEXT")
        db.execSQL("ALTER TABLE local_sale ADD COLUMN CLAIMED_AT INTEGER")
        db.execSQL("ALTER TABLE local_sale ADD COLUMN REVISION INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "ALTER TABLE local_sale ADD COLUMN CORRECCION_NO_ENVIADA INTEGER NOT NULL DEFAULT 0"
        )
        db.execSQL("ALTER TABLE local_sale ADD COLUMN REVISION_POSTEADA INTEGER")
    }
}
