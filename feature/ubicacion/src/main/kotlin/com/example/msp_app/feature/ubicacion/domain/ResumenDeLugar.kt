package com.example.msp_app.feature.ubicacion.domain

import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId

/**
 * **Lo que la card de un lugar dice, en texto, sin Compose.**
 *
 * Cada renglón es opcional: lo que no se puede sostener con los datos no se
 * pinta (sin "suele pagar" si la costumbre no es clara; sin abono si no hay
 * importe; sin venta si no hay productos en el teléfono).
 */
data class ResumenDeLugar(
    val titulo: String,
    /** "Hasta may" en "Pagaba aquí antes". */
    val etiqueta: String?,
    /** "21 cobros · desde jun". */
    val lineaA: String,
    val suele: String?,
    val abono: BigDecimal?,
    val cobrador: String?,
    /** "Refrigerador Mabe 14' · último 20 sep". */
    val lineaL: String,
    val venta: String?,
    val rango: String
) {
    companion object {
        @Suppress("LongMethod")
        fun de(
            l: LugarClasificado,
            nombresDeVenta: Map<Int, String>,
            zona: ZoneId,
            ahora: Instant
        ): ResumenDeLugar {
            val m = l.lugar.mediciones
            val cobros = Textos.cobros(l.conteo)
            val distancia = l.distanciaAlPrincipalM?.let { "a ${distanciaLegible(it)}" }
            val desde = "desde ${Textos.mes(l.lugar.masAntigua, zona, ahora)}"
            val rango = Textos.rango(l.lugar.masAntigua, l.lugar.masReciente, zona, ahora)
            val venta = ventaDe(l, nombresDeVenta)
            val cobrador = cobradorFrecuente(m)?.let(Textos::nombre)
            val ultimo = Textos.dia(l.lugar.masReciente, zona, ahora)
            val suele = SuelePagar.de(m, zona)?.let(Textos::suelePagar)
            return when (l.clase) {
                ClaseDeLugar.PAGABA_ANTES -> ResumenDeLugar(
                    titulo = l.clase.titulo,
                    etiqueta = "Hasta ${Textos.mes(l.lugar.masReciente, zona, ahora)}",
                    lineaA = listOfNotNull(cobros, rango, distancia, venta).joinToString(SEP),
                    suele = null,
                    abono = null,
                    cobrador = cobrador,
                    lineaL = listOfNotNull("Último cobro aquí $ultimo", cobrador).joinToString(SEP),
                    venta = venta,
                    rango = rango
                )
                ClaseDeLugar.COMPARTIDO -> ResumenDeLugar(
                    titulo = l.clase.titulo,
                    etiqueta = null,
                    lineaA = listOfNotNull(cobros, distancia).joinToString(SEP),
                    suele = null,
                    abono = null,
                    cobrador = cobrador,
                    lineaL = "Aquí pagan ${l.lugar.clientesQueLoComparten} clientes",
                    venta = venta,
                    rango = rango
                )
                else -> ResumenDeLugar(
                    titulo = l.clase.titulo,
                    etiqueta = null,
                    lineaA = listOfNotNull(cobros, distancia, desde).joinToString(SEP),
                    suele = suele,
                    abono = abonoTipico(m),
                    cobrador = cobrador,
                    lineaL = listOfNotNull(venta, "último $ultimo").joinToString(SEP),
                    venta = venta,
                    rango = rango
                )
            }
        }

        /** La venta del lugar: su nombre si es una sola; "2 ventas" si son varias. */
        fun ventaDe(l: LugarClasificado, nombres: Map<Int, String>): String? {
            val ventas = l.lugar.mediciones.groupingBy { it.ventaId }.eachCount()
            if (ventas.size > 1) return "${ventas.size} ventas"
            return ventas.keys.firstOrNull()?.let { nombres[it] }
        }

        const val SEP: String = " · "
    }
}
