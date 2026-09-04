package com.example.msp_app.core.database.dao.clientprofile

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.msp_app.core.database.entities.ClientProfileEntity
import com.example.msp_app.core.database.entities.ClientProfileSignalEntity

/**
 * La ficha del cliente: la **nota libre** (`cliente_ficha`, 0..1) y el
 * **catálogo cerrado** de señales (`cliente_ficha_senales`, 0..N). Dos tablas
 * de la Task 26, consumidas por la Task 24; ninguna migración vive aquí.
 *
 * ## La unicidad de la señal la pone la llave, no Kotlin
 *
 * `PRIMARY KEY(CLIENTE_ID, SENAL)` ya da semántica de **conjunto**: marcar dos
 * veces la misma señal reescribe su fila en vez de duplicarla. Por eso
 * [marcar] es `REPLACE` y **no** hay ninguna guarda en código que filtre
 * repetidos antes de insertar — una guarda así probaría a Kotlin, no a la base,
 * y sería la que se rompe el día que alguien inserte por otro camino.
 *
 * ## Por qué se borra de una en una y no con un `IN (...)`
 *
 * `CLAUDE.md:52-56` documenta el caso que costó horas: un `IN (...)` sin
 * trocear lanza *"too many SQL variables"* y un `try/catch` de arriba lo
 * convierte en error silencioso. Aquí ni siquiera hace falta trocear: lo que se
 * desmarca está acotado por el **tamaño del catálogo cerrado** (cuatro valores
 * hoy), así que borrar una por una no tiene borde que cruzar.
 *
 * ## Las dos columnas que NO son intercambiables
 *
 * `CLIENTE_ID` es el cliente y `SENAL` es el nombre de la constante del
 * catálogo. Ninguna consulta de este DAO mezcla un `DOCTO_CC_ACR_ID` (venta)
 * con un `CLIENTE_ID`: la ficha no conoce ventas, y este plan ya lleva siete
 * defectos de la familia "un id donde iba el otro".
 */
@Dao
interface ClientProfileDao {

    /** La nota libre del cliente, o `null` si nunca se escribió una. */
    @Query("SELECT * FROM cliente_ficha WHERE CLIENTE_ID = :clienteId")
    suspend fun fichaDe(clienteId: Int): ClientProfileEntity?

    /**
     * Las señales marcadas del cliente, en orden alfabético del literal.
     *
     * El orden es del **literal guardado**, no del catálogo de Kotlin: así una
     * señal retirada del catálogo —que se sigue leyendo como texto, igual que
     * `TIPO_VISITA`— tiene un lugar determinista en la lista en vez de depender
     * de si el `enum` todavía la conoce.
     */
    @Query("SELECT * FROM cliente_ficha_senales WHERE CLIENTE_ID = :clienteId ORDER BY SENAL ASC")
    suspend fun senalesDe(clienteId: Int): List<ClientProfileSignalEntity>

    /**
     * Escribe la nota. `REPLACE` sobre la PK `CLIENTE_ID`: la nota es 0..1 y
     * volver a guardar es reescribir la misma fila, nunca crear una segunda.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarNota(ficha: ClientProfileEntity)

    /**
     * Marca señales. `REPLACE` contra la PK compuesta — ver el KDoc de la
     * interfaz: la unicidad la pone la llave.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun marcar(senales: List<ClientProfileSignalEntity>)

    /** Desmarca UNA señal de UN cliente. Sin `IN (...)`, a propósito. */
    @Query("DELETE FROM cliente_ficha_senales WHERE CLIENTE_ID = :clienteId AND SENAL = :senal")
    suspend fun desmarcar(clienteId: Int, senal: String)

    /**
     * Guarda la ficha completa en UNA transacción: la nota y el conjunto de
     * señales quedan o no quedan juntos.
     *
     * **[aDesmarcar] es explícita y no "todo lo que no está en [aMarcar]".** Un
     * `DELETE WHERE CLIENTE_ID = :clienteId` seguido de un insert es la forma
     * obvia y **borra en silencio las señales que este build ya no reconoce** —
     * las de un valor retirado del catálogo, que el esquema conserva a propósito
     * como texto. Quien llama pasa exactamente los literales que quiere quitar,
     * y una fila desconocida sobrevive porque nadie la nombró.
     *
     * `@Transaction` es anotación de método: no altera el schema (v30) ni el
     * `identityHash`.
     */
    @Transaction
    suspend fun guardar(
        ficha: ClientProfileEntity,
        aMarcar: List<ClientProfileSignalEntity>,
        aDesmarcar: List<String>
    ) {
        guardarNota(ficha)
        aDesmarcar.forEach { desmarcar(ficha.CLIENTE_ID, it) }
        if (aMarcar.isNotEmpty()) {
            marcar(aMarcar)
        }
    }
}
