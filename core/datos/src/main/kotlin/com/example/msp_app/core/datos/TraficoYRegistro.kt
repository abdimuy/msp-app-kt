package com.example.msp_app.core.datos

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Lo que el interceptor necesita del registro. Interfaz mínima: una excepción de
 * cualquier implementación NUNCA debe tocar la petición (la protege el interceptor).
 */
interface ContadorDeBytes {
    fun peticion(clave: String)
    fun bytes(clave: String, rx: Long, tx: Long)
}

private class CeldaViva {
    val rx = AtomicLong()
    val tx = AtomicLong()
    val peticiones = AtomicLong()
    fun foto() = UsoDeEndpoint(rx.get(), tx.get(), peticiones.get())
}

private class DiaVivo {
    val rxTotal = AtomicLong()
    val txTotal = AtomicLong()
    val movil = AtomicLong()
    val wifi = AtomicLong()
    val otraRed = AtomicLong()
    val primerPlano = AtomicLong()
    val segundoPlano = AtomicLong()
    val endpoints = ConcurrentHashMap<String, CeldaViva>()

    fun celda(clave: String, max: Int): CeldaViva {
        endpoints[clave]?.let { return it }
        val k = if (endpoints.size >= max) ClasificadorDeServicio.CLAVE_OTROS else clave
        return endpoints.getOrPut(k) { CeldaViva() }
    }
}

/**
 * Acumulador en memoria, sin locks bloqueantes: contadores atómicos sobre mapas
 * concurrentes. Lo escriben los hilos de red y el muestreador; lo lee el guardado
 * periódico. El día se toma del reloj en la zona de negocio.
 */
class RegistroDeUso(
    private val clock: AppClock = AppClock.System,
    private val maxEndpoints: Int = MAX_ENDPOINTS
) : ContadorDeBytes {

    private val dias = ConcurrentHashMap<String, DiaVivo>()

    private class DiaEnCache(val minuto: Long, val fecha: String)

    @Volatile private var cache: DiaEnCache? = null

    private fun hoy(): String {
        val ahora = clock.now()
        val minuto = Math.floorDiv(ahora.toEpochMilli(), MS_POR_MINUTO)
        cache?.takeIf { it.minuto == minuto }?.let { return it.fecha }
        val fecha = AppTime.todayInBusinessZone(clock).toString()
        cache = DiaEnCache(minuto, fecha)
        return fecha
    }

    private fun dia(fecha: String): DiaVivo = dias[fecha] ?: dias.getOrPut(fecha) { DiaVivo() }

    override fun peticion(clave: String) {
        dia(hoy()).celda(clave, maxEndpoints).peticiones.incrementAndGet()
    }

    override fun bytes(clave: String, rx: Long, tx: Long) {
        if (rx == 0L && tx == 0L) return
        val c = dia(hoy()).celda(clave, maxEndpoints)
        if (rx != 0L) c.rx.addAndGet(rx)
        if (tx != 0L) c.tx.addAndGet(tx)
    }

    /** Reparte un delta de `TrafficStats` (rx + tx) a la red y plano vigentes. */
    fun trafico(rx: Long, tx: Long, red: TipoDeRed, primerPlano: Boolean) {
        if (rx <= 0 && tx <= 0) return
        val d = dia(hoy())
        val suma = rx + tx
        d.rxTotal.addAndGet(rx)
        d.txTotal.addAndGet(tx)
        when (red) {
            TipoDeRed.MOVIL -> d.movil
            TipoDeRed.WIFI -> d.wifi
            TipoDeRed.OTRA, TipoDeRed.SIN_RED -> d.otraRed
        }.addAndGet(suma)
        (if (primerPlano) d.primerPlano else d.segundoPlano).addAndGet(suma)
    }

    /** Días ordenados de más reciente a más antiguo. */
    fun instantanea(): List<UsoDelDia> = dias.entries
        .map { (fecha, d) ->
            UsoDelDia(
                fecha = fecha,
                rxTotal = d.rxTotal.get(),
                txTotal = d.txTotal.get(),
                movil = d.movil.get(),
                wifi = d.wifi.get(),
                otraRed = d.otraRed.get(),
                primerPlano = d.primerPlano.get(),
                segundoPlano = d.segundoPlano.get(),
                endpoints = d.endpoints.mapValues { it.value.foto() }
            )
        }
        .sortedByDescending { it.fecha }

    /** SUMA lo persistido sobre lo ya contado en memoria (nunca reemplaza). */
    fun restaurar(guardados: List<UsoDelDia>) {
        guardados.forEach { g ->
            val d = dia(g.fecha)
            d.rxTotal.addAndGet(g.rxTotal)
            d.txTotal.addAndGet(g.txTotal)
            d.movil.addAndGet(g.movil)
            d.wifi.addAndGet(g.wifi)
            d.otraRed.addAndGet(g.otraRed)
            d.primerPlano.addAndGet(g.primerPlano)
            d.segundoPlano.addAndGet(g.segundoPlano)
            g.endpoints.forEach { (k, e) ->
                val c = d.celda(k, maxEndpoints)
                c.rx.addAndGet(e.rx)
                c.tx.addAndGet(e.tx)
                c.peticiones.addAndGet(e.peticiones)
            }
        }
    }

    /** Retención: los últimos [DIAS_DE_RETENCION] días contando hoy. */
    fun podar() {
        val corte: LocalDate = AppTime.todayInBusinessZone(
            clock
        ).minusDays((DIAS_DE_RETENCION - 1).toLong())
        val corteTexto = corte.toString()
        dias.keys.filter { it < corteTexto }.forEach { dias.remove(it) }
    }

    companion object {
        const val DIAS_DE_RETENCION = 30
        const val MAX_ENDPOINTS = 200
        private const val MS_POR_MINUTO = 60_000L
    }
}
