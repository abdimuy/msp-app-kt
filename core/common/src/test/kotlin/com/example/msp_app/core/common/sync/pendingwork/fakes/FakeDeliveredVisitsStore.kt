package com.example.msp_app.core.common.sync.pendingwork.fakes

import com.example.msp_app.core.common.sync.pendingwork.domain.ports.DeliveredVisitsStore

/**
 * Fake escrito a mano del lado local de la reconciliación inversa: estado
 * público y una lista por cada escritura, sin MockK (regla del repo).
 *
 * Como el DAO real, [confirmar] devuelve CUÁNTAS cambió de verdad: las de
 * [retenidas] (comprobante sin subir) no cuentan. Un fake que devolviera
 * siempre `ids.size` volvería inerte la aserción sobre `confirmadas`.
 */
class FakeDeliveredVisitsStore(
    entregadas: List<String> = emptyList(),
    private val failOnRead: Throwable? = null,
    private val failOnConfirmar: Throwable? = null,
    private val failOnDevolver: Throwable? = null,
    private val retenidas: Set<String> = emptySet()
) : DeliveredVisitsStore {

    var entregadas: List<String> = entregadas

    /** El `desde` que recibió cada lectura. */
    val lecturas: MutableList<String> = mutableListOf()

    /** Ids que de verdad quedaron confirmadas, en orden. */
    val confirmadas: MutableList<String> = mutableListOf()

    /** El `ahora` de cada llamada a [confirmar]. */
    val confirmadasEn: MutableList<String> = mutableListOf()

    /** Ids devueltas a pendiente, en orden. */
    val devueltas: MutableList<String> = mutableListOf()

    override suspend fun entregadasDesde(desde: String): List<String> {
        lecturas += desde
        failOnRead?.let { throw it }
        return entregadas
    }

    override suspend fun confirmar(ids: List<String>, ahora: String): Int {
        failOnConfirmar?.let { throw it }
        confirmadasEn += ahora
        val cambiadas = ids.filterNot { it in retenidas }
        confirmadas += cambiadas
        return cambiadas.size
    }

    override suspend fun devolverAPendiente(ids: List<String>): Int {
        failOnDevolver?.let { throw it }
        devueltas += ids
        return ids.size
    }
}
