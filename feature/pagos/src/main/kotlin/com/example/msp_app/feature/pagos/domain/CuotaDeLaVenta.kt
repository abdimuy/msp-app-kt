package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.core.common.money.Money
import java.math.BigDecimal
import kotlin.math.ceil

/**
 * **La línea base de la ruta**: el techo de lo que esta ruta paga de verdad.
 *
 * Existe para contestar una sola pregunta —*¿esta cuota es un valor absurdo
 * para esta ruta?*— y sólo se usa cuando no hay nada mejor con qué contestarla
 * (ver el escalón 3 de [CuotaDeLaVenta]).
 *
 * ## Por qué sale de los PAGOS y no de las parcialidades
 *
 * La parcialidad es justo el dato bajo sospecha. Contrastarla contra otras
 * parcialidades sería pedirle al dato dudoso que se valide con dato del mismo
 * tipo, capturado a mano por la misma pantalla de alta.
 *
 * Y hay una segunda razón, medida: la muestra de parcialidades de la ruta trae
 * **tres filas sintéticas** ($3,000, $2,000 y $1,200; las crearon probando). Los
 * 6,165 pagos, en cambio, son todos reales — esas ventas de prueba **no tienen
 * ni un solo pago**, así que no pueden contaminar esta línea base ni siquiera un
 * poco. La muestra limpia es la de pagos, por construcción y no por suerte.
 *
 * ## Por qué un percentil y no un promedio
 *
 * La distribución no es simétrica ni de una sola moda: **el 81 % de los pagos
 * son tres valores** ($100 el 37.5 %, $200 el 24.9 %, $150 el 18.4 %) y detrás
 * hay una cola larga y delgada hasta $1,500. La media y la desviación estándar
 * describen una forma de campana que este dato no tiene; un percentil no supone
 * ninguna forma.
 *
 * **[PERCENTIL] = 99 y no el máximo**, porque el máximo es UNA fila: un pago mal
 * tecleado movería la línea base de la ruta entera. El percentil 99 sobre 6,165
 * pagos necesita que se muevan ~62 filas para moverse.
 *
 * ## El mínimo de muestra, y qué pasa si no se alcanza
 *
 * Con pocos pagos un percentil 99 **es** un máximo disfrazado: con 200 filas es
 * la segunda más grande. Por eso se exigen [MINIMO_DE_PAGOS] antes de dejar que
 * la regla opine — con mil filas, el percentil 99 ya tiene diez por encima y se
 * comporta como un percentil.
 *
 * Debajo de ese mínimo **no hay línea base** (`null`) y el escalón 3 **no
 * avisa**: una ruta que todavía no ha cobrado mil veces no tiene autoridad para
 * declarar nada "absurdo", y callar es el lado conservador.
 */
data class LineaBaseDeLaRuta(
    /** El percentil [PERCENTIL] de los pagos reales de la ruta. */
    val techo: Money,
    /** Cuántos pagos la sostienen. Va en el dato para que nadie la use a ciegas. */
    val pagosMedidos: Int
) {
    companion object {

        /** El percentil que define el techo. Ver el KDoc de la clase. */
        const val PERCENTIL: Int = 99

        /** Pagos mínimos para que un percentil 99 sea un percentil y no un máximo. */
        const val MINIMO_DE_PAGOS: Int = 1_000

        /**
         * La línea base de [importes], o `null` si la muestra no alcanza.
         *
         * Los importes en cero o negativos se descartan: no son dinero que
         * entró, y meterlos sólo correría el percentil hacia abajo.
         */
        fun de(importes: List<Money>): LineaBaseDeLaRuta? {
            val pagos = importes.filter { it > Money.ZERO }.sorted()
            if (pagos.size < MINIMO_DE_PAGOS) return null
            // Método del "nearest rank": el primer valor que deja por debajo al
            // PERCENTIL% de la muestra. Se elige éste y no una interpolación
            // porque el resultado es un pago que de verdad ocurrió, y contra un
            // hecho se argumenta mejor que contra un promedio ponderado.
            val proporcion = PERCENTIL / CIEN_EN_DOBLE
            val rango = ceil(pagos.size * proporcion).toInt().coerceIn(1, pagos.size)
            return LineaBaseDeLaRuta(techo = pagos[rango - 1], pagosMedidos = pagos.size)
        }

        private const val CIEN_EN_DOBLE = 100.0
    }
}

/**
 * De dónde salió la cuota que la pantalla usa como "esperado".
 *
 * Es parte del dato y no una nota al margen: un "esperado" que a veces es la
 * parcialidad capturada y a veces la costumbre de la cuenta, **sin decir cuál**,
 * es peor que uno equivocado pero predecible. La pantalla rotula el chip con
 * esto.
 */
enum class OrigenDeLaCuota {
    /**
     * **La moda de los pagos de ESTA venta.** El escalón 1, y el que cubre al
     * 91 % de la ruta (286 de 314 ventas tienen 3 o más pagos).
     *
     * Es inmune a una parcialidad mal capturada porque no la mira: lo que esta
     * cuenta paga de verdad es un hecho, y el campo `PARCIALIDAD` es una
     * captura a mano.
     */
    COMPORTAMIENTO,

    /**
     * **La parcialidad capturada**, y nada la contradice. Es lo que había antes
     * de esta regla, y sigue siendo correcto: medido sobre las ventas con 3 o
     * más pagos, 230 de 286 pagan exactamente la parcialidad.
     */
    PARCIALIDAD,

    /**
     * **La parcialidad capturada, y se ve mal.** La pantalla NO la ofrece como
     * esperado: dice que hay que revisarla.
     *
     * El problema no es el abono del cobrador, es el dato de la venta, y
     * decirlo así convierte un aviso inútil ("abono corto: esperado $3,000,
     * este abono $600") en uno accionable.
     */
    DUDOSA
}

/**
 * La cuota que la pantalla usa como "esperado", y **de dónde salió**.
 *
 * [monto] en cero significa "no se sabe qué toca" — el mismo contrato que
 * [SeguridadDelAbono] y [AvisosDelAbono] ya tenían para `esperadoHoy`, que ahí
 * apaga toda comparación. Es lo que hace que una cuota [OrigenDeLaCuota.DUDOSA]
 * no encienda el abono corto, no prellene el teclado y no pinte chip: sin tocar
 * ninguna de esas tres piezas.
 */
data class CuotaDeLaVenta(val monto: Money, val origen: OrigenDeLaCuota) {

    /** ¿Hay una cuota que la pantalla pueda afirmar? */
    val sePuedeAfirmar: Boolean get() = origen != OrigenDeLaCuota.DUDOSA && monto > Money.ZERO

    /**
     * Lo que la pantalla usa como esperado: la cuota, o **cero cuando es
     * dudosa**. Nunca se afirma un número que no se sostiene.
     */
    val esperado: Money get() = if (sePuedeAfirmar) monto else Money.ZERO

    companion object {

        /**
         * **La precedencia de tres escalones**, de la evidencia más fuerte a la
         * más débil.
         *
         * @param parcialidad el campo `PARCIALIDAD` capturado en el alta.
         * @param pagosDeLaVenta los abonos que ESTA venta ha recibido.
         * @param lineaBase el techo de la ruta, o `null` si no hay muestra.
         *
         * ## 1. Tres o más pagos: manda el comportamiento
         *
         * La moda de los pagos de esta cuenta ([AbonoHabitual], que además
         * exige que el valor se repita). Cubre el 91 % de la ruta y no mira la
         * parcialidad para nada.
         *
         * Si hay tres pagos pero **ninguno se repite**, no hay costumbre que
         * afirmar y se cae al escalón 2 — con esos mismos pagos como contraste.
         *
         * ## 2. Uno o dos pagos: la parcialidad, contrastada contra ellos
         *
         * Se usa la parcialidad, pero se la mide contra lo poco que se ha
         * pagado. Sólo se marca dudosa cuando la diferencia es de **un orden de
         * magnitud** ([VECES_PARA_DUDAR_CON_PAGOS]): con uno o dos pagos la
         * evidencia es delgada, y pagar la mitad —o un quinto— de la cuota es
         * normal en esta ruta (el 24 % de los abonos queda por debajo de lo
         * esperado). Diez veces ya no es "abonó corto", es otro número.
         *
         * ## 3. Ningún pago: la parcialidad, contrastada contra la ruta
         *
         * Es el único caso donde una parcialidad mal capturada puede sobrevivir
         * sin que nadie la desmienta — no hay un solo pago que la contradiga. Se
         * contrasta contra [LineaBaseDeLaRuta].
         *
         * **Y aquí se es deliberadamente conservador.** Se exige que la cuota
         * supere [VECES_PARA_DUDAR_CON_LA_RUTA] veces el techo de la ruta, y no
         * apenas el techo: el percentil 99 es un techo de PAGOS sueltos, y una
         * cuota legítima puede estar por encima de casi cualquier pago suelto
         * —la ruta tiene 34 ventas con cuota de $300 a $1,000, todas
         * legítimas—. Duplicarlo deja margen para la cuota alta pero posible y
         * sólo deja fuera lo que nadie podría cubrir ni juntando dos de los
         * pagos más grandes que esta ruta ha visto jamás.
         *
         * El margen es grande **a propósito**: el caso que disparó todo esto
         * resultó ser un dato de prueba, así que esto defiende contra un caso
         * **posible**, no contra uno observado. Con esa evidencia, un aviso que
         * salte sobre una venta legítimamente grande es peor que no tenerlo.
         */
        fun de(
            parcialidad: Money,
            pagosDeLaVenta: List<AbonoPrevio>,
            lineaBase: LineaBaseDeLaRuta?
        ): CuotaDeLaVenta {
            val habitual = AbonoHabitual.de(pagosDeLaVenta)
            if (habitual != null) return CuotaDeLaVenta(habitual, OrigenDeLaCuota.COMPORTAMIENTO)
            val cobrados = pagosDeLaVenta.filter { it.importe > Money.ZERO }
            val dudosa = if (cobrados.isEmpty()) {
                laRutaLaDesmiente(parcialidad, lineaBase)
            } else {
                losPagosLaDesmienten(parcialidad, cobrados)
            }
            val origen = if (dudosa) OrigenDeLaCuota.DUDOSA else OrigenDeLaCuota.PARCIALIDAD
            return CuotaDeLaVenta(parcialidad, origen)
        }

        /** Escalón 2: el mayor pago de la cuenta queda un orden de magnitud abajo. */
        private fun losPagosLaDesmienten(parcialidad: Money, cobrados: List<AbonoPrevio>): Boolean {
            if (parcialidad <= Money.ZERO) return false
            val mayor = cobrados.maxOf { it.importe }
            return parcialidad.amount > mayor.amount.multiply(VECES_PARA_DUDAR_CON_PAGOS)
        }

        /** Escalón 3: la cuota supera con holgura el techo de lo que la ruta paga. */
        private fun laRutaLaDesmiente(parcialidad: Money, lineaBase: LineaBaseDeLaRuta?): Boolean {
            if (parcialidad <= Money.ZERO || lineaBase == null) return false
            return parcialidad.amount >
                lineaBase.techo.amount.multiply(VECES_PARA_DUDAR_CON_LA_RUTA)
        }

        /**
         * Un orden de magnitud. El bar del escalón 2: con uno o dos pagos la
         * evidencia es delgada y un abono corto es normal.
         */
        private val VECES_PARA_DUDAR_CON_PAGOS: BigDecimal = BigDecimal(10)

        /**
         * El doble del percentil 99 de los pagos. El bar del escalón 3 — ver el
         * KDoc de [de] para por qué el margen es tan ancho.
         */
        private val VECES_PARA_DUDAR_CON_LA_RUTA: BigDecimal = BigDecimal(2)
    }
}
