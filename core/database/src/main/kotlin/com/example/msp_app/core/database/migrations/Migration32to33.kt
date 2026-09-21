package com.example.msp_app.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Colapsa corridas de espacios: cada pasada funde pares contiguos, así que unas cuantas
// pasadas alcanzan cualquier corrida razonable en un nombre de padrón.
private const val ESPACIOS_COLAPSAR_PASADAS = 5

/**
 * Una columna nueva en `cliente`: `NOMBRE_NORMALIZADO`, el `NOMBRE` sin acentos, en
 * minúsculas y con espacios colapsados (`normalizeForSearch`, `:app`
 * `core/utils/FuzzyClientSearch.kt`). Arregla el defecto de campo: "por un acento o un
 * espacio de más, el buscador de la Nueva Venta no encuentra al cliente".
 *
 * SQLite no sabe plegar acentos, así que la comparación insensible a acentos tiene que
 * hacerse contra un valor ya normalizado en Kotlin — de ahí la columna, no una expresión
 * en la consulta. `ClienteDao.searchByNormalizedWord` es quien la lee.
 *
 * ## Por qué es 32→33
 *
 * Esta migración nació como 31→32 en su rama, contra una base de integración que
 * todavía no tenía el tramo del nivel 2 de "Corregir una venta". Al fusionarse con
 * `MIGRATION_31_32` (estado del servidor + cola de correcciones remotas sobre
 * `local_sale`, que se quedó con el número 31→32 por haber llegado primero a la rama de
 * integración) esta migración se corre un lugar y pasa a numerarse 32→33 — mismo patrón
 * que ya le pasó una vez a `MIGRATION_30_31` (ver su KDoc).
 *
 * ## Filas preexistentes: pobladas aquí mismo, no en el próximo sync
 *
 * La versión original de esta migración dejaba `NOMBRE_NORMALIZADO` vacío
 * (`DEFAULT ''`) para toda fila preexistente y confiaba en que el siguiente
 * `ClienteRepository.syncFromServer` (`ClienteDao.replaceAll` = `deleteAll` +
 * `insertAll`) la repoblara — válido cuando esta era la única migración de la cadena,
 * pero significaba que el buscador no encontraba a NADIE hasta que corriera esa
 * sincronización. Al integrarse se adapta: la migración misma calcula
 * `NOMBRE_NORMALIZADO` para cada fila que ya existe, con una aproximación en SQL de
 * `normalizeForSearch` — minúsculas vía `LOWER()` (sólo pliega ASCII; SQLite embebido
 * en Android no trae ICU) más una sustitución letra por letra de las vocales acentuadas
 * y la eñe, en ambos casos, que es el alfabeto real del padrón mexicano (ver el KDoc de
 * `ClienteEntity.NOMBRE_NORMALIZADO`) — y colapsa los espacios de más. No es la
 * descomposición NFD completa que usa `normalizeForSearch` en Kotlin (esa cubre
 * cualquier marca diacrítica Unicode, no sólo el español), así que un nombre con un
 * acento fuera de ese alfabeto quedaría sin plegar hasta el siguiente sync — pero el
 * buscador ya funciona de inmediato para el caso real y común, no sólo para clientes
 * nuevos. El próximo `syncFromServer` recalcula con el normalizador real de Kotlin y
 * sustituye este valor aproximado sin que nadie tenga que intervenir.
 *
 * Solo agrega una columna y la puebla con `UPDATE`: ninguna tabla se recrea.
 */
val MIGRATION_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE cliente ADD COLUMN NOMBRE_NORMALIZADO TEXT NOT NULL DEFAULT ''"
        )
        poblarNombreNormalizadoDeFilasPreexistentes(db)
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_cliente_NOMBRE_NORMALIZADO` " +
                "ON `cliente` (`NOMBRE_NORMALIZADO`)"
        )
    }

    /**
     * Aproximación en SQL de `normalizeForSearch`, aplicada a cada fila que ya existía
     * antes de esta migración. Tres pasadas, cada una un `UPDATE` completo sobre
     * `cliente` (el padrón mide ~43,700 clientes activos — trivial para SQLite correr
     * unas cuantas pasadas más sobre esa cantidad de filas, una sola vez, al migrar):
     *
     * 1. `LOWER(NOMBRE)` — pliega el rango ASCII (`JOSE` → `jose`). Deja intactas las
     *    vocales acentuadas y la eñe: `LOWER()` sólo conoce ASCII sin ICU.
     * 2. Una sustitución letra por letra de cada vocal acentuada y la eñe, en mayúscula
     *    y en minúscula (`Á`/`á` → `a`, ..., `Ñ`/`ñ` → `n`, `Ü`/`ü` → `u`) — el
     *    alfabeto real del padrón, no cualquier marca diacrítica Unicode.
     * 3. Colapsa espacios repetidos a uno solo y recorta los de los extremos, igual que
     *    `normalizeForSearch`.
     */
    private fun poblarNombreNormalizadoDeFilasPreexistentes(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE cliente SET NOMBRE_NORMALIZADO = LOWER(NOMBRE)")

        val vocalesAcentuadas = listOf(
            "Á" to "a", "É" to "e", "Í" to "i", "Ó" to "o", "Ú" to "u", "Ñ" to "n", "Ü" to "u",
            "á" to "a", "é" to "e", "í" to "i", "ó" to "o", "ú" to "u", "ñ" to "n", "ü" to "u"
        )
        vocalesAcentuadas.forEach { (acentuada, plano) ->
            db.execSQL(
                "UPDATE cliente SET NOMBRE_NORMALIZADO = REPLACE(NOMBRE_NORMALIZADO, ?, ?)",
                arrayOf(acentuada, plano)
            )
        }

        repeat(ESPACIOS_COLAPSAR_PASADAS) {
            db.execSQL(
                "UPDATE cliente SET NOMBRE_NORMALIZADO = REPLACE(NOMBRE_NORMALIZADO, '  ', ' ')"
            )
        }
        db.execSQL("UPDATE cliente SET NOMBRE_NORMALIZADO = TRIM(NOMBRE_NORMALIZADO)")
    }
}
