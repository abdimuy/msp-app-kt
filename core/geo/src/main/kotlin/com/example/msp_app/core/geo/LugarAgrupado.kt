package com.example.msp_app.core.geo

import java.time.Instant

/**
 * **Un lugar: varias mediciones que son el mismo sitio.**
 *
 * Lo que esta clase promete es lo que se puede sostener con los datos, y nada
 * más. En particular **no promete ser la puerta**: ese título lo otorga
 * [LugaresDelCliente], y sólo cuando se lo ha ganado.
 */
data class LugarAgrupado(
    /** Todas las mediciones que cayeron en este lugar, transferencias incluidas. */
    val mediciones: List<MedicionDelCobro>,
    /**
     * El centro, promediando **sólo las mediciones que no son transferencia**
     * cuando hay alguna; si el lugar es puramente de transferencias, el promedio
     * de todas (es lo que hay, y dibujarlo es más honesto que no dibujar nada).
     */
    val centro: Punto,
    /**
     * `true` cuando este lugar cae donde **otros clientes distintos** también
     * tienen mediciones — la tienda, la casa de un cobrador, un punto fijo de
     * cobro. Ver [IndiceDePuntosCompartidos].
     *
     * Un lugar compartido **se dibuja igual que los demás** y **nunca se
     * esconde**. Lo único que no puede es llamarse "la puerta".
     */
    val esCompartido: Boolean,
    /**
     * Cuántos **clientes distintos** comparten este punto, y cuántos
     * **cobradores distintos** cobran ahí. Sólo tienen valor cuando
     * [esCompartido]; si no, valen 1 y 1.
     *
     * Son los dos números que la hoja enseña **en vez de un nombre**. Un punto de
     * 381 clientes y 30 cobradores es casi seguro la tienda; uno de 48 clientes y
     * un solo cobrador es casi seguro el punto de ese cobrador. **Pero la app no
     * lo dice**, porque no lo sabe: enseña los dos números y el cobrador —que
     * conoce el lugar— saca la conclusión mejor que nosotros.
     */
    val clientesQueLoComparten: Int,
    val cobradoresQueCobranAqui: Int
) {
    /** Cuántas mediciones tiene, incluidas las transferencias. */
    val conteo: Int get() = mediciones.size

    /**
     * Cuántas mediciones cuentan para ser la puerta: las que **no** son
     * transferencia. Ver el KDoc de [MedicionDelCobro.esTransferencia].
     */
    val conteoDeDomicilio: Int get() = mediciones.count { !it.esTransferencia }

    /** `true` cuando aquí no entró un solo pago que no fuera transferencia. */
    val esSoloDeTransferencias: Boolean get() = conteoDeDomicilio == 0

    /** La medición más reciente. Es lo que decide qué tan sólido se pinta. */
    val masReciente: Instant get() = mediciones.maxOf { it.fecha }

    /** La medición más antigua. Con [masReciente] delata una mudanza. */
    val masAntigua: Instant get() = mediciones.minOf { it.fecha }

    /** Las ventas que aportaron mediciones a este lugar. */
    val ventas: Set<Int> get() = mediciones.mapTo(mutableSetOf()) { it.ventaId }

    /** Los cobradores que midieron aquí. */
    val cobradores: Set<String> get() = mediciones.mapTo(mutableSetOf()) { it.cobrador }

    /**
     * **El radio del círculo de precisión, en metros — o `null` cuando no se
     * puede calcular con honestidad.**
     *
     * Es el percentil 95 de la distancia al [centro] de las mediciones que
     * cuentan como domicilio. Medido el 2026-09-24 sobre clientes reales: la
     * mediana de este número es **24.4 m**, y su p90 entre clientes es **43.6 m**
     * — o sea que **varía de verdad**, y por eso vale la pena dibujarlo: apretado
     * y ancho significan cosas distintas.
     *
     * ## Por qué `null` por debajo de [MINIMO_PARA_EL_CIRCULO] mediciones
     *
     * Con dos o tres puntos la dispersión **no es una medición, es ruido**. Un
     * círculo chico calculado sobre tres mediciones que por casualidad cayeron
     * juntas dice "confíe en esto" sin tener con qué respaldarlo, y el cobrador
     * toca una puerta equivocada.
     *
     * La regla del dueño es explícita: *"un círculo de precisión que se vea más
     * apretado de lo que la dispersión justifica miente"*. **Si no se puede
     * calcular con honestidad, no se dibuja.** `null` no es un hueco que haya que
     * rellenar con un valor por omisión: es la respuesta.
     */
    val radioDeConfianzaM: Double?
        get() {
            val deDomicilio = mediciones.filter { !it.esTransferencia }
            if (deDomicilio.size < MINIMO_PARA_EL_CIRCULO) return null
            val distancias = deDomicilio.map { centro.distanciaA(it.punto) }.sorted()
            return distancias.percentil(PERCENTIL_DEL_CIRCULO)
        }

    companion object {
        /**
         * **Cinco mediciones.** Por debajo de esto no se dibuja círculo de
         * precisión. Ver el KDoc de [radioDeConfianzaM] para el porqué; no es un
         * número de estilo y bajarlo convierte el círculo en una afirmación sin
         * respaldo.
         */
        const val MINIMO_PARA_EL_CIRCULO: Int = 5

        /** El círculo cubre el 95 % de las mediciones del lugar. */
        const val PERCENTIL_DEL_CIRCULO: Double = 0.95
    }
}

/**
 * El percentil [p] de una lista **ya ordenada**, por el método del vecino más
 * cercano (sin interpolar).
 *
 * Sin interpolación a propósito: el valor que sale **es una distancia que de
 * verdad se midió**, no un promedio entre dos. Sobre 5 u 8 puntos, interpolar
 * fabrica una cifra intermedia que ninguna medición respalda — y este número
 * termina siendo el radio de un círculo que no puede mentir.
 */
internal fun List<Double>.percentil(p: Double): Double {
    require(isNotEmpty()) { "percentil de una lista vacía" }
    val indice = ((size - 1) * p).toInt().coerceIn(0, size - 1)
    return this[indice]
}
