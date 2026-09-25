package com.example.msp_app.core.geo

/**
 * **Los lugares de un cliente, y cuál de ellos —si alguno— es la puerta.**
 *
 * ## La regla del título, que es la razón de ser de esta clase
 *
 * "La puerta" **no es el grupo más grande**. Es el grupo más grande **que se lo
 * ha ganado**, y se lo gana cumpliendo dos condiciones:
 *
 *  1. **No está marcado como compartido.** Un punto donde cobran a otros cinco
 *     clientes no es la puerta de ninguno de ellos (ver
 *     [IndiceDePuntosCompartidos]).
 *  2. **Tiene al menos una medición que no sea transferencia.** Una
 *     transferencia se registra donde está el cobrador, así que un lugar hecho
 *     sólo de transferencias no es evidencia del domicilio de nadie (ver
 *     [MedicionDelCobro.esTransferencia]).
 *
 * Y el tamaño se mide con [LugarAgrupado.conteoDeDomicilio], no con el conteo
 * total: las transferencias **se dibujan pero no votan**.
 *
 * ## Cuando nadie se lo gana
 *
 * [laPuerta] queda en `null` y la pantalla lo dice. **No se degrada al grupo
 * mayor "porque algo hay que enseñar"**: eso sería exactamente la mentira que
 * esta clase existe para no cometer, sólo que en silencio. Los lugares se
 * dibujan todos igual —nada se esconde, nada se borra— y lo único que falta es
 * el título.
 *
 * Medido el 2026-09-24: le pasa al **6.4 %** de los clientes con tres o más
 * mediciones, y a **53 clientes (0.61 %)** cuyos abonos son todos
 * transferencias.
 */
data class LugaresDelCliente(
    /** Todos los lugares, de mayor a menor. Se dibujan todos, siempre. */
    val lugares: List<LugarAgrupado>,
    /**
     * El lugar que se ganó el título, o `null` si ninguno lo hizo. Ver el KDoc
     * de la clase.
     */
    val laPuerta: LugarAgrupado?
) {
    /** `true` cuando hay mediciones pero ninguna sirve para afirmar un domicilio. */
    val sinPuertaMedida: Boolean get() = lugares.isNotEmpty() && laPuerta == null

    /**
     * **`true` cuando los lugares cuentan la historia de una mudanza.**
     *
     * Dos lugares de domicilio con al menos [MINIMO_PARA_UNA_ERA] mediciones cada
     * uno **cuyas fechas no se traslapan**: uno se acabó y el otro empezó. No es
     * ruido de GPS —para eso están los 30 m— sino dos épocas distintas.
     *
     * Medido el 2026-09-24: le pasa a cerca del **5 %** de los clientes, del
     * orden de 350-400 personas en la cartera activa. El dueño lo señaló como lo
     * de más valor de toda la pantalla, y **tiene que verse sin que el cobrador
     * filtre nada**: es el caso en que hoy la app le enseña la dirección vieja
     * con cara de certeza.
     */
    val pareceMudanza: Boolean
        get() {
            val eras = lugares
                .filter { !it.esCompartido && !it.esSoloDeTransferencias }
                .filter { it.conteo >= MINIMO_PARA_UNA_ERA }
                .sortedBy { it.masAntigua }
            if (eras.size < 2) return false
            return eras.zipWithNext().any { (anterior, siguiente) ->
                anterior.masReciente < siguiente.masAntigua
            }
        }

    companion object {
        /**
         * Tres mediciones para que un lugar cuente como "una época".
         *
         * Con una o dos, un par de lecturas malas del GPS en el mismo mes se
         * leería como una mudanza, y anunciar una mudanza que no pasó es tan
         * caro como no anunciar la que sí.
         */
        const val MINIMO_PARA_UNA_ERA: Int = 3

        /**
         * Arma los lugares de un cliente y reparte el título.
         *
         * [compartidos] se construye con los puntos de **toda la zona** que el
         * teléfono tiene, no sólo con los de este cliente: un punto sólo se puede
         * saber compartido mirando a los demás.
         */
        fun de(
            mediciones: List<MedicionDelCobro>,
            compartidos: IndiceDePuntosCompartidos
        ): LugaresDelCliente {
            val lugares = AgrupadorDeLugares.agrupar(mediciones, compartidos)
            val puerta = lugares
                .filter { !it.esCompartido && !it.esSoloDeTransferencias }
                .maxWithOrNull(
                    compareBy<LugarAgrupado> { it.conteoDeDomicilio }.thenBy { it.masReciente }
                )
            return LugaresDelCliente(lugares = lugares, laPuerta = puerta)
        }
    }
}
