package com.example.msp_app.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Lo que el recomendador **sugirió**, junto a lo que el cobrador **hizo**
 * (§10 del expediente: *"guardar la recomendación, no solo el resultado"*).
 *
 * ## Por qué es una tabla y no columnas en `Visit`
 *
 * Dos razones, y la segunda es la que decide:
 *
 * 1. Una recomendación puede no terminar en visita. Guardarla solo en la fila
 *    de la visita registraría **únicamente las recomendaciones atendidas** —
 *    el sesgo de selección exacto que impide evaluar un recomendador.
 * 2. **El grupo de control no produce visita por definición.** Si la única
 *    fila donde vive el brazo del experimento es la visita, el brazo de
 *    control jamás queda registrado y la aleatorización no se puede leer
 *    después. [GRUPO] existe justo para eso: hoy toda fila nace en
 *    `'tratamiento'` (no hay aleatorización y el default lo dice), y el día
 *    que se implemente basta escribir otro valor — **sin otra migración**.
 *
 * ## Deliberadamente SIN llave foránea a `Visit`
 *
 * `VisitDao.deleteUploadedVisits` poda las visitas ya confirmadas por el
 * servidor en cada `syncSales`. Una FK con `CASCADE` se llevaría con ellas la
 * mitad "qué pasó" del par que esta tabla existe para conservar, y una FK sin
 * `CASCADE` haría fallar esa poda. [VISITA_ID] es una referencia suelta a
 * propósito: sobrevive a la poda y sigue diciendo qué visita atendió la
 * recomendación.
 *
 * [MOTIVO] y [ALGORITMO] son códigos de un catálogo cerrado en Kotlin (el
 * molde `SignalType`/`SignalLabels` de kollect), no texto libre: se guardan
 * para poder **contarlos**. [POSICION] es el renglón en que se mostró — sin
 * él no se distingue "el cobrador siguió la recomendación" de "el cobrador
 * fue al primero de la lista".
 */
@Entity(
    tableName = "visita_recomendaciones",
    indices = [
        Index(value = ["CLIENTE_ID"]),
        Index(value = ["VISITA_ID"]),
        Index(value = ["GENERADA_EN"])
    ]
)
data class VisitRecommendationEntity(
    /** UUID generado en el teléfono al construir la lista recomendada. */
    @PrimaryKey val ID: String,
    val CLIENTE_ID: Int,
    /** `IMPTE_DOCTO_CC_ID` cuando la sugerencia es por venta; `NULL` = cliente completo. */
    val VENTA_ID: Int? = null,
    val COBRADOR_ID: Int,
    /** Instante en que se calculó y mostró, formato de cable de `AppTime`. */
    val GENERADA_EN: String,
    /** Renglón en el que se mostró, empezando en 0. */
    val POSICION: Int,
    /** Código del catálogo cerrado que explica la sugerencia (p. ej. cercanía, mejor hora). */
    val MOTIVO: String,
    /** Versión del recomendador que la produjo (p. ej. `cercania_v1`). */
    val ALGORITMO: String,
    /**
     * Brazo del experimento. `'tratamiento'` = se mostró; el día que exista
     * aleatorización, `'control'` = se calculó y NO se mostró. Es el espacio
     * para el grupo de control que §10 pide dejar abierto.
     */
    @ColumnInfo(defaultValue = "'tratamiento'")
    val GRUPO: String = "tratamiento",
    /** `Visit.ID` que atendió la recomendación; `NULL` = todavía sin visita. */
    val VISITA_ID: String? = null
)
