package com.example.msp_app.core.database.entities

import androidx.room.Entity

/**
 * El **catálogo cerrado** de la ficha del cliente: una fila por señal
 * marcada (horario en que se le encuentra, quién atiende, advertencias).
 *
 * Es lo único de la ficha que **lee código**, porque se puede contar y filtrar;
 * la mitad libre vive en [ClientProfileEntity] y no se mezcla con esta (§5).
 *
 * Hoy quien la lee es **la pantalla** (`SenalDeFicha.peso` decide el color del
 * chip y qué sube a la barra de navegación). El conteo por ventana horaria del
 * plan —el BTTC— **todavía no está escrito**, ni en esta app ni en `msp-api`:
 * esta tabla existe para que el día que se escriba encuentre el dato ya
 * estructurado, no porque exista un consumidor hoy.
 *
 * ## Cardinalidad: 0..N por cliente, sin repetidos
 *
 * Un cliente puede ser a la vez *"trabaja de noche"* y *"atiende la suegra"*,
 * así que una columna no alcanza. La llave primaria compuesta
 * `(CLIENTE_ID, SENAL)` da semántica de **conjunto**: marcar dos veces la
 * misma señal no crea una segunda fila, y su prefijo izquierdo ya sirve de
 * índice para "las señales de este cliente".
 *
 * [SENAL] guarda el **nombre de la constante** del catálogo cerrado de Kotlin
 * (molde `SignalType`/`SignalFamily`/`SignalLabels` de kollect, donde agregar
 * un valor no compila hasta que tiene etiqueta en español). El esquema no
 * enumera los valores a propósito: el catálogo *"nace corto y crece con
 * evidencia"*, y crecerlo no debe costar una migración. Un valor retirado se
 * sigue leyendo como texto, igual que hace `TIPO_VISITA`.
 *
 * Sin llave foránea a `cliente`, por la misma razón que [ClientProfileEntity]:
 * `ClienteDao.replaceAll` borra y reinserta la tabla completa en cada
 * sincronización.
 */
@Entity(tableName = "cliente_ficha_senales", primaryKeys = ["CLIENTE_ID", "SENAL"])
data class ClientProfileSignalEntity(
    val CLIENTE_ID: Int,
    val SENAL: String,
    /**
     * **Desde cuándo se sabe esto**, en formato de cable de `AppTime`.
     *
     * No es "cuándo se guardó la ficha por última vez": es la fecha en que ESTA
     * señal se marcó, y no se reescribe mientras siga marcada. Es lo que
     * permite preguntar *"lo del perro, ¿es de esta semana o de hace dos
     * años?"*. Ver `ClientProfileDao.guardar`.
     */
    val ACTUALIZADA_EN: String
)
