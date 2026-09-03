package com.example.msp_app.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * La **nota libre** de la ficha del cliente: lo que no anticipamos y lee el
 * humano que llega mañana (*"casa azul, portón negro"*).
 *
 * ## Por qué NO son columnas en `cliente`
 *
 * Porque se borrarían solas. `ClienteDao.replaceAll` hace
 * `deleteAll()` + `insertAll(...)` en una transacción cada vez que se
 * sincroniza el catálogo de clientes: una columna nueva en `cliente` viviría
 * exactamente hasta la siguiente sincronización. La ficha es conocimiento
 * **local y persistente** del cobrador, no una proyección del servidor, así
 * que vive en su propia tabla y por la misma razón **no lleva llave foránea a
 * `cliente`** — una FK con `CASCADE` la borraría en ese mismo `deleteAll()`,
 * y una sin `CASCADE` haría fallar la sincronización.
 *
 * ## Por qué es una tabla aparte de [ClientProfileSignalEntity]
 *
 * Son **dos campos con trabajos distintos** (§5): el catálogo cerrado es lo
 * único que puede alimentar el BTTC porque se puede contar y consultar; la
 * nota libre es para el humano y no se consulta. Juntarlos en un solo campo
 * reproduciría el defecto que este plan vino a arreglar. Además la
 * cardinalidad difiere: la nota es 0..1 por cliente (una columna), las
 * señales son 0..N (filas).
 *
 * Un cliente puede tener nota sin señales, señales sin nota, o ambas: las dos
 * tablas se relacionan solo por `CLIENTE_ID` y ninguna exige a la otra.
 */
@Entity(tableName = "cliente_ficha")
data class ClientProfileEntity(
    @PrimaryKey val CLIENTE_ID: Int,
    /** Texto libre del cobrador. `NULL` = hay señales pero nadie escribió nota. */
    val NOTA: String? = null,
    /** Última edición, formato de cable de `AppTime`. */
    val ACTUALIZADA_EN: String,
    /** Quién editó por última vez; `NULL` cuando el editor no se conoce. */
    val COBRADOR_ID: Int? = null
)
