package com.example.msp_app.feature.ubicacion.domain

import com.example.msp_app.core.geo.LugarAgrupado
import com.example.msp_app.core.geo.LugaresDelCliente
import com.example.msp_app.core.geo.MedicionDelCobro
import com.example.msp_app.core.geo.Punto
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/**
 * **Qué clase de lugar es, dicho sólo con lo que se midió.**
 *
 * Regla dura del dueño: la app **no sabe qué es un lugar**. Nunca "casa",
 * "trabajo", "domicilio" ni "tienda": sólo dónde se registraron los cobros y
 * cuándo. Los nombres de la hoja salen de aquí y de ningún otro lado.
 */
enum class ClaseDeLugar(val titulo: String) {
    /** El lugar que se ganó el título en `LugaresDelCliente` (no compartido, no sólo transferencias). */
    DONDE_MAS_PAGA("Donde más paga"),

    /** Otro lugar con 3+ cobros cuyas fechas se cruzan con las del principal: alterna. */
    OTRO_LUGAR("Otro lugar donde paga"),

    /** Un lugar con 3+ cobros que se acabó **antes** de que empezara el principal. */
    PAGABA_ANTES("Pagaba aquí antes"),

    /** Cae donde cobran a 5+ clientes. Se enseña el número, no un nombre. */
    COMPARTIDO("Punto compartido"),

    /** Sólo transferencias: la coordenada es del cobrador, no del cliente. */
    TRANSFERENCIAS("Transferencias"),

    /** 1 o 2 cobros, **sin umbral de distancia**. Ver [MapaDelCliente.sueltos]. */
    SUELTO("Punto suelto")
}

/** Un lugar con su clase y su distancia al principal (`null` si él es el principal o no hay). */
data class LugarClasificado(
    val lugar: LugarAgrupado,
    val clase: ClaseDeLugar,
    val distanciaAlPrincipalM: Double?
) {
    val conteo: Int get() = lugar.conteo
    val centro: Punto get() = lugar.centro

    /** "Cómo llegar" sólo donde el cliente paga hoy: nunca en "antes" ni en un compartido. */
    val ofreceComoLlegar: Boolean
        get() = clase == ClaseDeLugar.DONDE_MAS_PAGA || clase == ClaseDeLugar.OTRO_LUGAR
}

/**
 * **Todo lo que la pantalla dice de un cliente, ya decidido y sin Compose.**
 *
 * Cada regla que aquí se aplica tiene su prueba en `MapaDelClienteTest`: la
 * clasificación, los sueltos, el encuadre de entrada, el "suele pagar", el abono
 * típico y la distancia.
 */
data class MapaDelCliente(
    /** Principal, otros, antes y compartidos, en ese orden: las cards de la hoja. */
    val lugares: List<LugarClasificado>,
    /** Lugares de puras transferencias con 3+ cobros: van plegados en la lista. */
    val transferencias: List<LugarClasificado>,
    /**
     * **Puntos sueltos: lugares con 1 o 2 cobros, sin umbral de distancia.**
     *
     * Hay tres casos que la app no puede distinguir —un error de GPS (hay puntos
     * a más de 1,000 km), un cobro registrado después en otro lado, o que el
     * cliente sí pagó una vez en otro sitio— así que no se marcan como error:
     * no mueven el encuadre, se pintan tenues y van plegados al final.
     */
    val sueltos: List<LugarClasificado>,
    /** El cobro más reciente de todos, o `null` sin cobros. */
    val ultimoCobro: MedicionDelCobro?
) {
    val principal: LugarClasificado? get() = lugares.firstOrNull {
        it.clase == ClaseDeLugar.DONDE_MAS_PAGA
    }

    /** "4 lugares · 34 cobros": cuentan sólo las cards, no las transferencias ni los sueltos. */
    val cuantosLugares: Int get() = lugares.size
    val cobrosEnLugares: Int get() = lugares.sumOf { it.conteo }

    /** El lugar "antes" más reciente, que es el que dispara "Cambió de lugar". */
    val lugarAnterior: LugarClasificado?
        get() = lugares.filter {
            it.clase == ClaseDeLugar.PAGABA_ANTES
        }.maxByOrNull { it.lugar.masReciente }

    val cambioDeLugar: Boolean get() = lugarAnterior != null

    /** Todos, para pintar el mapa. */
    val todos: List<LugarClasificado> get() = lugares + transferencias + sueltos

    /** El lugar que contiene el último cobro. */
    val lugarDelUltimo: LugarClasificado?
        get() = ultimoCobro?.let { u ->
            todos.firstOrNull { l ->
                l.lugar.mediciones.any {
                    it.pagoId == u.pagoId
                }
            }
        }

    /**
     * **Los puntos del encuadre al entrar**: el principal, los otros lugares con
     * 3 o más cobros y el último contacto — **nunca los sueltos**, ni siquiera
     * si el último contacto cayó en uno: un solo punto a 1,000 km dejaría la
     * calle del cliente del tamaño de un píxel.
     */
    val encuadreDeEntrada: List<Punto>
        get() {
            val base = (lugares + transferencias).map { it.centro }
            val ultimo = lugarDelUltimo?.takeIf { it.clase != ClaseDeLugar.SUELTO }?.centro
            return (base + listOfNotNull(ultimo)).distinct()
        }

    companion object {
        /** A partir de cuántos cobros un lugar deja de ser suelto. */
        const val MINIMO_PARA_LUGAR: Int = 3

        fun de(agrupados: LugaresDelCliente): MapaDelCliente {
            val puerta = agrupados.laPuerta
            val todas = agrupados.lugares.flatMap { it.mediciones }
            fun distancia(l: LugarAgrupado): Double? =
                if (puerta == null || l === puerta) null else l.centro.distanciaA(puerta.centro)
            val clasificados = agrupados.lugares.map { l ->
                LugarClasificado(l, claseDe(l, puerta), distancia(l))
            }
            val cards = clasificados.filter {
                it.clase != ClaseDeLugar.SUELTO && it.clase != ClaseDeLugar.TRANSFERENCIAS
            }.sortedBy { ORDEN.indexOf(it.clase) }
            return MapaDelCliente(
                lugares = cards,
                transferencias = clasificados.filter { it.clase == ClaseDeLugar.TRANSFERENCIAS },
                sueltos = clasificados.filter { it.clase == ClaseDeLugar.SUELTO },
                ultimoCobro = todas.maxByOrNull { it.fecha }
            )
        }

        private val ORDEN = listOf(
            ClaseDeLugar.DONDE_MAS_PAGA,
            ClaseDeLugar.OTRO_LUGAR,
            ClaseDeLugar.PAGABA_ANTES,
            ClaseDeLugar.COMPARTIDO
        )

        /**
         * La regla de clasificación. El principal nunca es suelto (un cliente con
         * dos cobros en total sigue teniendo dónde más paga); cualquier otro
         * lugar con menos de [MINIMO_PARA_LUGAR] cobros lo es.
         *
         * "Pagaba aquí antes" es **por fechas, no por distancia**: su último
         * cobro es anterior al primero del principal. Es la misma señal que
         * `LugaresDelCliente.pareceMudanza`, contada contra el principal.
         */
        internal fun claseDe(l: LugarAgrupado, puerta: LugarAgrupado?): ClaseDeLugar = when {
            l === puerta -> ClaseDeLugar.DONDE_MAS_PAGA
            l.conteo < MINIMO_PARA_LUGAR -> ClaseDeLugar.SUELTO
            l.esCompartido -> ClaseDeLugar.COMPARTIDO
            l.esSoloDeTransferencias -> ClaseDeLugar.TRANSFERENCIAS
            puerta != null && l.masReciente < puerta.masAntigua -> ClaseDeLugar.PAGABA_ANTES
            else -> ClaseDeLugar.OTRO_LUGAR
        }
    }
}

/** Día de la semana y franja de 2 h en que **suele** pagar en un lugar. */
data class SuelePagar(val dia: DayOfWeek, val horaInicio: Int) {
    val horaFin: Int get() = horaInicio + FRANJA_HORAS

    companion object {
        /** La franja mide dos horas. */
        const val FRANJA_HORAS: Int = 2

        /** Con menos cobros que esto no hay costumbre que estimar. */
        const val MINIMO_DE_COBROS: Int = 3

        /**
         * **La fracción que la franja tiene que concentrar: la mitad.**
         *
         * Es una estimación y se pinta como tal ("Suele pagar"), así que sólo se
         * afirma cuando el mismo día de la semana **y** la misma ventana de dos
         * horas juntan al menos la mitad de los cobros del lugar (y al menos
         * [MINIMO_DE_COBROS]). Por debajo, la costumbre no es clara y **no se
         * pinta nada**: inventar un horario manda al cobrador a una hora en la
         * que el cliente no está.
         *
         * La ventana es deslizante por hora entera (10–12, 11–13, …) y gana la
         * que más cobros junta; en empate, la que empieza en una hora con cobros.
         */
        const val FRACCION_MINIMA: Double = 0.5

        fun de(mediciones: List<MedicionDelCobro>, zona: ZoneId): SuelePagar? {
            if (mediciones.size < MINIMO_DE_COBROS) return null
            val locales = mediciones.map { it.fecha.atZone(zona) }
            var mejor: SuelePagar? = null
            var mejorCuenta = 0
            var mejorPuntaje = 0
            for (dia in DayOfWeek.entries) {
                for (h in 0..(HORAS_DEL_DIA - FRANJA_HORAS)) {
                    val delDia = locales.filter { it.dayOfWeek == dia }
                    val n = delDia.count { it.hour >= h && it.hour < h + FRANJA_HORAS }
                    // En empate gana la ventana que EMPIEZA en una hora con cobros:
                    // si todos fueron a las 14, se dice "14–16", no "13–15".
                    val puntaje = n * DESEMPATE + delDia.count { it.hour == h }
                    if (puntaje > mejorPuntaje) {
                        mejorPuntaje = puntaje
                        mejorCuenta = n
                        mejor = SuelePagar(dia, h)
                    }
                }
            }
            val suficiente = mejorCuenta >= MINIMO_DE_COBROS && mejorCuenta >= mediciones.size * FRACCION_MINIMA
            return mejor.takeIf { suficiente }
        }

        private const val HORAS_DEL_DIA = 24
        private const val DESEMPATE = 1000
    }
}

/**
 * **El abono típico: la mediana de los importes, sin interpolar.**
 *
 * Con un número par de importes se toma el de abajo de los dos del medio: el
 * valor que sale es un abono que **de verdad se dio**, no un promedio entre dos
 * que nadie pagó. `null` si ningún cobro trae importe positivo.
 */
fun abonoTipico(mediciones: List<MedicionDelCobro>): BigDecimal? {
    val importes = mediciones.mapNotNull { it.importe }.filter { it.signum() > 0 }.sorted()
    if (importes.isEmpty()) return null
    return importes[(importes.size - 1) / 2]
}

/** El cobrador que más cobró aquí; en empate, el del cobro más reciente. */
fun cobradorFrecuente(mediciones: List<MedicionDelCobro>): String? {
    if (mediciones.isEmpty()) return null
    val porCobrador = mediciones.groupBy { it.cobrador }
    return porCobrador.entries.maxWithOrNull(
        compareBy<Map.Entry<String, List<MedicionDelCobro>>> { it.value.size }
            .thenBy { e -> e.value.maxOf { it.fecha } }
    )?.key
}

/**
 * La distancia para leer parado en la banqueta: "440 m", "1.2 km", "12 km".
 *
 * Por debajo de un kilómetro se redondea a **10 m**: el GPS no da para más, y
 * un "437 m" promete una precisión que no tiene.
 */
fun distanciaLegible(metros: Double): String = when {
    metros < METROS_POR_KM -> "${(Math.round(metros / REDONDEO_M) * REDONDEO_M).coerceAtLeast(
        REDONDEO_M
    )} m"
    metros < DIEZ_KM -> String.format(java.util.Locale.US, "%.1f km", metros / METROS_POR_KM)
    else -> "%,d km".format(java.util.Locale.US, Math.round(metros / METROS_POR_KM))
}

private const val METROS_POR_KM = 1000.0
private const val DIEZ_KM = 10_000.0
private const val REDONDEO_M = 10L

/** Todas las mediciones de estos lugares (cómodo para el resumen de un grupo). */
fun List<LugarClasificado>.mediciones(): List<MedicionDelCobro> = flatMap { it.lugar.mediciones }

/** Útil para pruebas y para la hoja: el instante del cobro más reciente del lugar. */
val LugarClasificado.ultimo: Instant get() = lugar.masReciente
