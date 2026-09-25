package com.example.msp_app.feature.ubicacion.domain.port

import com.example.msp_app.core.geo.MedicionDelCobro
import com.example.msp_app.core.geo.PuntoDeLaRuta

/**
 * De dónde salen los puntos.
 *
 * **Dos lecturas y no una**, y la segunda es la que suele sorprender: para
 * saber si un lugar es la puerta de este cliente hay que mirar a **los demás
 * clientes**. Un punto donde cobran a otros cinco no es la puerta de nadie, y
 * eso no se puede deducir de los datos de un solo cliente.
 */
interface PuntosPort {

    /** Los abonos medidos de este cliente. */
    suspend fun medicionesDe(clienteId: Int): List<MedicionDelCobro>

    /**
     * El cliente dueño de una venta, o `null` si no hay abonos suyos aquí.
     *
     * Lo necesita el detalle de venta, que conoce la cuenta y no al cliente.
     */
    suspend fun clienteDeVenta(ventaId: Int): Int?

    /**
     * **Todos** los puntos que el teléfono tiene de su zona, de todos los
     * clientes. Alimenta `IndiceDePuntosCompartidos`.
     *
     * El teléfono sólo ve su zona, y eso no lo hace equivocarse: sus clientes
     * *son* clientes de la empresa, así que **un ≥5 local es un ≥5 real**. Lo
     * único que pierde es alcance — medido, el 97 % de los puntos compartidos
     * que ve el servidor.
     */
    suspend fun puntosDeLaRuta(): List<PuntoDeLaRuta>
}
