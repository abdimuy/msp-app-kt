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
    UN_ANIO("1 año", ONE_YEAR_DAYS),
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
    val sinAgrupar: Boolean = false,
    /** Tipo: cobros encendidos al entrar. */
    val verCobros: Boolean = true,
    /** Tipo: **visitas APAGADAS al entrar** (decisión del dueño); sólo con el filtro. */
    val verVisitas: Boolean = false,
    /** Tipo: promesas apagadas al entrar, igual que las visitas. */
    val verPromesas: Boolean = false,
    /**
     * "Ver puntos sueltos": con él encendido los sueltos se pintan enteros y
     * entran al encuadre. Apagado (al entrar) se pintan tenues y no lo mueven.
     */
    val verSueltos: Boolean = false
) {
    /** `true` cuando está como al entrar; "Filtrar" enseña un punto si no. */
    val estaLimpio: Boolean
        get() = this == FiltroDeLugares()

    /**
     * Aplica el recorte sobre las mediciones.
     *
     * [ahora] entra como parámetro y no se lee del reloj del sistema: es lo que
     * deja probar "últimos 3 meses" sin que la prueba caduque sola.
     */
    fun aplicar(mediciones: List<MedicionDelCobro>, ahora: Instant): List<MedicionDelCobro> {
        if (!verCobros) return emptyList()
        val desde = ventana.dias?.let { ahora.minus(it, ChronoUnit.DAYS) }
        return mediciones.filter { m ->
            (desde == null || !m.fecha.isBefore(desde)) &&
                (ventaId == null || m.ventaId == ventaId) &&
                (cobrador == null || m.cobrador == cobrador)
        }
    }
}

/**
 * Las visitas que pasan el filtro. Apagadas al entrar: sólo salen si el
 * cobrador enciende "Visitas" o "Promesas" en Tipo.
 */
fun FiltroDeLugares.aplicarAVisitas(
    visitas: List<VisitaMedida>,
    ahora: Instant
): List<VisitaMedida> {
    val desde = ventana.dias?.let { ahora.minus(it, ChronoUnit.DAYS) }
    return visitas.filter { v ->
        (if (v.esPromesa) verPromesas else verVisitas) &&
            (desde == null || !v.fecha.isBefore(desde)) &&
            (cobrador == null || v.cobrador == cobrador)
    }
}

/** Una visita con coordenada: se dibuja como rombo hueco, sólo con el filtro. */
data class VisitaMedida(
    val id: String,
    val punto: com.example.msp_app.core.geo.Punto,
    val fecha: Instant,
    val cobrador: String,
    val tipo: String,
    /** `true` cuando la visita dejó una promesa de pago con fecha. */
    val esPromesa: Boolean
)
