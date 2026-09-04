package com.example.msp_app.core.database.dao.visit

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.msp_app.core.database.entities.VisitRecommendationEntity

/**
 * Acceso a `visita_recomendaciones` — **lo que el sistema sugirió**, la mitad
 * del par que §10 del expediente pide conservar junto a lo que el cobrador
 * hizo.
 *
 * La tabla la creó la migración aditiva de la Task 26; este DAO es su primer
 * consumidor (Task 19). No hay migración nueva aquí.
 *
 * ## El experimento cabe sin otra migración
 *
 * `GRUPO` nace en `'tratamiento'` por default. [vigenteDe] filtra por ese
 * brazo a propósito: una fila de `'control'` **se calculó y no se muestra**,
 * así que no puede terminar ligada a una visita — es el contrafactual. El día
 * que exista aleatorización basta escribir `'control'` al insertar; ni esta
 * interfaz ni el esquema cambian.
 */
@Dao
interface VisitRecommendationDao {

    /**
     * Guarda (o reemplaza) una recomendación. El `ID` lo acuña quien la
     * calcula, así que reescribir la misma fila es idempotente.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(recomendacion: VisitRecommendationEntity)

    /**
     * La recomendación viva del cliente [clienteId]: del brazo de tratamiento
     * (o sea, mostrada), todavía sin visita que la atienda, la más reciente
     * primero.
     *
     * `LIMIT 1` porque la pantalla muestra una, no una lista: el recomendador
     * ordena la ruta, y aquí interesa la sugerencia que llevó al cobrador a
     * ESTA puerta.
     */
    @Query(
        """
        SELECT * FROM visita_recomendaciones
        WHERE CLIENTE_ID = :clienteId
          AND VISITA_ID IS NULL
          AND GRUPO = 'tratamiento'
        ORDER BY GENERADA_EN DESC
        LIMIT 1
        """
    )
    suspend fun vigenteDe(clienteId: Int): VisitRecommendationEntity?

    /**
     * Ata la recomendación [id] a la visita [visitaId] — el momento en que "qué
     * sugirió el sistema" y "qué hizo el cobrador" quedan en la misma fila.
     *
     * @return cuántas filas cambiaron, para que el llamador distinga "ligada"
     *   de "esa recomendación ya no está". Un `0` no se traga: se reporta.
     */
    @Query("UPDATE visita_recomendaciones SET VISITA_ID = :visitaId WHERE ID = :id")
    suspend fun ligarConVisita(id: String, visitaId: String): Int

    /** Solo para lecturas puntuales y pruebas: la fila completa por su id. */
    @Query("SELECT * FROM visita_recomendaciones WHERE ID = :id")
    suspend fun porId(id: String): VisitRecommendationEntity?
}
