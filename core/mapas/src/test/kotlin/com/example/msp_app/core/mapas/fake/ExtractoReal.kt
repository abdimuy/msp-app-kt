package com.example.msp_app.core.mapas.fake

import java.io.File

/**
 * **El extracto de verdad**, para los tests que tienen que correr contra bytes
 * reales y no contra un archivo de laboratorio.
 *
 * ## Por qué vive FUERA del repo
 *
 * `CLAUDE.md` §5: este repositorio es **público**. Un `.pmtiles` de 25 MB no se
 * commitea acá, así que el archivo no puede ser un recurso de test.
 *
 * ## Por qué los tests que lo usan no son obligatorios
 *
 * Porque en otra máquina —o en CI— el archivo no está, y un test que falle por
 * eso estaría midiendo el disco de quien lo corre, no el código. Los que
 * dependen de él hacen `assumeTrue` y **se saltan**; lo que prueban queda
 * declarado en el reporte de la tarea, no escondido en un verde.
 *
 * Lo que NO se salta es todo lo demás: la cabecera sintética, los tres caminos
 * del `Range`, el rechazo del archivo truncado. Esos corren siempre.
 *
 * La ruta se puede mover con `-Dmapa.extracto.real=…` o `MAPA_EXTRACTO_REAL`.
 */
object ExtractoReal {

    /** Donde lo dejó `go-pmtiles extract` el 2026-09-16. */
    private const val RUTA_POR_OMISION =
        "/Volumes/M2-1TB/Developer/mapas-cobranza/ruta-cobranza-z14.pmtiles"

    val archivo: File
        get() = File(
            System.getProperty("mapa.extracto.real")
                ?: System.getenv("MAPA_EXTRACTO_REAL")
                ?: RUTA_POR_OMISION
        )

    val disponible: Boolean get() = archivo.isFile

    /** Los números que `huella-geografica-medida.md` midió, por su propio camino. */
    const val ZOOM_MAXIMO_MEDIDO: Int = 14
    const val OESTE_MEDIDO: Double = -98.2
    const val SUR_MEDIDO: Double = 17.9
    const val ESTE_MEDIDO: Double = -96.4
    const val NORTE_MEDIDO: Double = 19.6
    const val TAMANO_MEDIDO: Long = 25_507_515L
}
