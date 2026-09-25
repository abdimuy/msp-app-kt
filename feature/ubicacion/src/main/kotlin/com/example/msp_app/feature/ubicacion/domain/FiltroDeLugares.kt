package com.example.msp_app.feature.ubicacion.domain

import com.example.msp_app.core.geo.MedicionDelCobro
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * La ventana de tiempo del filtro.
 *
 * `null` en [dias] es "todo", y no se representa con un número gigante a
 * propósito: un `Int.MAX_VALUE` de días es un valor centinela que alguien
 * termina usando en una resta.
 */
enum class VentanaDelFiltro(val etiqueta: String, val dias: Long?) {
    TRES_MESES("3 meses", THREE_MONTHS_DAYS),
    ESTE_ANIO("Este año", ONE_YEAR_DAYS),
    TODO("Todo", null)
}

private const val THREE_MONTHS_DAYS = 90L
private const val ONE_YEAR_DAYS = 365L

/**
 * **Lo que el cobrador eligió ver.**
 *
 * Por omisión no filtra nada, y eso es una decisión, no un descuido: la señal
 * más valiosa de esta pantalla —**si el cliente se mudó, se ve como dos grupos
 * de edades distintas**— desaparece en cuanto se recorta el tiempo. Tiene que
 * verse **sin que el cobrador filtre nada**, porque no sabe que tiene que
 * buscarla.
 *
 * [sinAgrupar] es la salida de emergencia: cada medición como su propio punto.
 * Es lo que se usa cuando el agrupamiento no convence, y existir es más
 * importante que ser bonita.
 */
data class FiltroDeLugares(
    val ventana: VentanaDelFiltro = VentanaDelFiltro.TODO,
    val ventaId: Int? = null,
    val cobrador: String? = null,
    val sinAgrupar: Boolean = false
) {
    /** `true` cuando no recorta nada; la pantalla lo usa para no gritar el chip. */
    val estaLimpio: Boolean
        get() = ventana == VentanaDelFiltro.TODO && ventaId == null && cobrador == null

    /**
     * Aplica el recorte sobre las mediciones.
     *
     * [ahora] entra como parámetro y no se lee del reloj del sistema: es lo que
     * deja probar "últimos 3 meses" sin que la prueba caduque sola.
     */
    fun aplicar(mediciones: List<MedicionDelCobro>, ahora: Instant): List<MedicionDelCobro> {
        val desde = ventana.dias?.let { ahora.minus(it, ChronoUnit.DAYS) }
        return mediciones.filter { m ->
            (desde == null || !m.fecha.isBefore(desde)) &&
                (ventaId == null || m.ventaId == ventaId) &&
                (cobrador == null || m.cobrador == cobrador)
        }
    }
}
