package com.example.msp_app.core.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cliente",
    indices = [Index(value = ["NOMBRE"]), Index(value = ["NOMBRE_NORMALIZADO"])]
)
class ClienteEntity(
    @PrimaryKey val CLIENTE_ID: Int,
    val NOMBRE: String,
    val ESTATUS: String,
    val CAUSA_SUSP: String?,
    /**
     * `NOMBRE` pasado por `normalizeForSearch` (`:app` `core/utils/FuzzyClientSearch.kt`):
     * minúsculas, sin acentos, espacios colapsados. Es la ÚNICA columna contra la que
     * el buscador de la Nueva Venta compara — nunca `NOMBRE` directo, porque SQLite no
     * sabe plegar acentos y el padrón trae nombres capturados en Microsip con y sin
     * ellos (~43,700 clientes activos medidos en la base de referencia).
     *
     * Se calcula UNA vez en Kotlin al sincronizar (`ClienteRepository.syncFromServer`),
     * no en SQL — Room no puede correr `Normalizer.normalize` en una expresión de
     * columna. Por eso lleva un default igual a `NOMBRE` sin normalizar: así los
     * call-sites que construyen un `ClienteEntity` sin buscar nada (fixtures de otras
     * pruebas, ficha del cliente) siguen compilando sin tener que importar el
     * normalizador de `:app` — solo el camino real de sincronización y las pruebas del
     * buscador pasan el valor calculado.
     */
    val NOMBRE_NORMALIZADO: String = NOMBRE
)

/**
 * Proyección ligera de un cliente: solo su nombre, indexada por `CLIENTE_ID`. Alimenta el
 * enriquecimiento del nombre real de un cliente visitado (`RoomVisitsAdapter`,
 * `:feature:collectionReport`) sin traer la entidad `cliente` completa. Solo lectura — no es
 * una `@Entity`, no toca el schema.
 */
data class ClienteRefRow(
    val CLIENTE_ID: Int,
    val NOMBRE: String
)
