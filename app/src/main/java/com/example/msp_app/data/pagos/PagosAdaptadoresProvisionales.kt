package com.example.msp_app.data.pagos

import com.example.msp_app.feature.pagos.domain.OrdenDeLaLista
import com.example.msp_app.feature.pagos.domain.port.CercaniaPort
import com.example.msp_app.feature.pagos.domain.port.OrdenGuardado
import com.example.msp_app.feature.pagos.domain.port.OrdenGuardadoPort
import java.time.LocalDate

/**
 * **Provisional: la siguiente tarea pone el adaptador real** de [CercaniaPort]
 * (posición del cobrador vs. centroide de pagos más cercano por venta).
 *
 * Existe sólo para que el grafo de Hilt de `:app` compile: `ListaDeClientesViewModel`
 * (Task de la lista — orden, título, buscador, 2026-10-05) ya pide este puerto por
 * constructor, y sin un binding ningún test ni la app arrancan. Mapa vacío
 * siempre — [OrdenDeLaLista.MAS_CERCA] ya sabe degradar a orden de cobranza con
 * eso, así que este stub no deja a la pantalla en un estado que no sepa pintar.
 */
class CercaniaPortProvisional : CercaniaPort {
    override suspend fun distanciasPorVenta(): Map<Int, Double> = emptyMap()
}

/**
 * **Provisional: la siguiente tarea pone el adaptador real** de
 * [OrdenGuardadoPort] (persistencia "durante el día" del orden elegido en la
 * hoja "Ordenar por").
 *
 * Mismo motivo que [CercaniaPortProvisional]: sin binding, el grafo de Hilt no
 * compila. Sin estado propio — [leer] siempre devuelve `null`, [guardar] no
 * hace nada — así que el orden elegido nunca sobrevive a que el ViewModel se
 * destruya, hasta que exista el adaptador de verdad.
 */
class OrdenGuardadoPortProvisional : OrdenGuardadoPort {
    override suspend fun leer(): OrdenGuardado? = null

    override suspend fun guardar(orden: OrdenDeLaLista, dia: LocalDate) = Unit
}
