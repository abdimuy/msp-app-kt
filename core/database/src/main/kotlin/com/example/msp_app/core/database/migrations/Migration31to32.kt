package com.example.msp_app.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Siete columnas nuevas en `local_sale` para el plan "Corregir una venta
 * DESPUÉS de que subió, mientras siga en borrador" (nivel 2,
 * `docs/superpowers/plans/2026-09-21-editar-venta-nivel-2.md`, Task A1): el
 * teléfono guarda lo que sabe del SERVIDOR sobre una venta que YA se envió, y
 * arma la cola de correcciones remotas que viajan aparte del `POST` de
 * creación.
 *
 * ## Por qué es 31→32
 *
 * La base de esta rama integrada ya llega a v31 con el candado único del
 * nivel 1 (`CLAIM_ID`/`CLAIM_KIND`/`CLAIMED_AT`/`REVISION`/
 * `CORRECCION_NO_ENVIADA`/`REVISION_POSTEADA`, ver `MIGRATION_30_31`). El
 * plan del nivel 2 se escribió antes de esa integración y habla de "31→32
 * subiendo desde v30"; en el código real la migración de esta tarea es
 * 31→32, y así se numera aquí.
 *
 * - `SERVER_SITUACION` (nullable): `borrador`/`revisada`/`aprobada`/
 *   `cancelada` del último `GET` exitoso. `NULL` para toda fila preexistente:
 *   ninguna venta ya enviada tiene todavía una lectura fresca del servidor.
 * - `SERVER_SINCRONIZACION` (nullable): `pendiente`/`aplicada`. `NULL` para
 *   toda fila preexistente, por la misma razón.
 * - `SERVER_VERSION` (nullable): la `VERSION` leída en esa misma lectura.
 *   Sólo diagnóstico y UI — nunca precondición de una escritura (regla del
 *   arranque limpio del plan). `NULL` para toda fila preexistente.
 * - `SERVER_STATE_AT` (nullable): epoch ms de esa lectura. `NULL` = nunca se
 *   leyó — se trata como estado desconocido, nunca como "corregible".
 * - `CORRECCION_REMOTA_PENDIENTE` (`NOT NULL DEFAULT 0`): hay una corrección
 *   local commiteada que el servidor todavía no confirmó. `0` para toda fila
 *   preexistente: ninguna tiene una corrección remota pendiente todavía.
 * - `CORRECCION_REMOTA_ESTADO` (nullable): `NULL` (sin incidencia),
 *   `'RECHAZADA_ESTADO'`, `'CONFLICTO'` o `'APLICADA_PARCIAL'` — marca
 *   TERMINAL y persistente. `NULL` para toda fila preexistente: no hay
 *   incidencia que arrastrar.
 *   `'APLICADA_PARCIAL'` se agregó cuando la corrección remota pasó de una
 *   petición a tres (header, cliente, líneas): significa que alguna ya había
 *   entrado cuando el servidor cerró la puerta, así que el servidor quedó
 *   DISTINTO de como estaba. Se distingue de `'RECHAZADA_ESTADO'` a propósito
 *   — decirle al cobrador "no entró" cuando entró parte lo mandaría a confiar
 *   en datos viejos. El espejo en dominio es `CorreccionRemotaTerminal`.
 * - `REVISION_REMOTA_ENVIADA` (nullable, sin default): la `REVISION` del
 *   cuerpo que viajó en la corrida remota que cerró con los tres pasos en
 *   2xx. `NULL` para toda fila preexistente — análogo exacto de
 *   `REVISION_POSTEADA` del nivel 1, mismo argumento: sin default, para que
 *   un `0` nunca se confunda con "ya hubo una corrida exitosa".
 *
 * Solo agrega columnas: ninguna tabla se recrea, así que las ventas ya
 * enviadas (y sus productos/combos/imágenes) quedan intactas.
 */
val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE local_sale ADD COLUMN SERVER_SITUACION TEXT")
        db.execSQL("ALTER TABLE local_sale ADD COLUMN SERVER_SINCRONIZACION TEXT")
        db.execSQL("ALTER TABLE local_sale ADD COLUMN SERVER_VERSION INTEGER")
        db.execSQL("ALTER TABLE local_sale ADD COLUMN SERVER_STATE_AT INTEGER")
        db.execSQL(
            "ALTER TABLE local_sale ADD COLUMN CORRECCION_REMOTA_PENDIENTE INTEGER NOT NULL DEFAULT 0"
        )
        db.execSQL("ALTER TABLE local_sale ADD COLUMN CORRECCION_REMOTA_ESTADO TEXT")
        db.execSQL("ALTER TABLE local_sale ADD COLUMN REVISION_REMOTA_ENVIADA INTEGER")
    }
}
