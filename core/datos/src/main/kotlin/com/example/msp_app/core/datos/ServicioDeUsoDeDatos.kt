package com.example.msp_app.core.datos

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Orquesta restaurar, muestrear y guardar. Se pierde a lo más lo ocurrido desde
 * el último guardado si el proceso muere (el desglose por servicio; el total
 * se recupera porque la base de `TrafficStats` se guarda junto con los datos).
 */
class ServicioDeUsoDeDatos(
    val registro: RegistroDeUso,
    val muestreador: MuestreadorDeTrafico,
    private val almacen: AlmacenDeUsoDeDatos
) {
    private val mutex = Mutex()
    private val reportados: MutableSet<String> = ConcurrentHashMap.newKeySet()

    @Volatile private var restaurado = false

    /**
     * Lo guardado que no se pueda leer (JSON con fecha nula, etc.) se descarta y se empieza de
     * cero: es una medición, jamás debe cerrar la app al arrancar. Siempre queda `restaurado`,
     * si no [guardar] nunca volvería a escribir.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun restaurar() = mutex.withLock {
        if (restaurado) return@withLock
        try {
            val estado = almacen.leer()
            if (estado != null) {
                registro.restaurar(estado.dias)
                muestreador.restaurarUltima(estado.ultimaMuestra)
                reportados += estado.reportados
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Ilegible: se descarta.
        }
        restaurado = true
    }

    /** No escribe hasta haber restaurado: un guardado temprano pisaría lo anterior. */
    suspend fun guardar() = mutex.withLock {
        if (!restaurado) return@withLock
        registro.podar()
        // Bajo el candado del muestreador solo la instantánea; el disco, fuera (H2).
        val (dias, ultima) = muestreador.congelado { u -> registro.instantanea() to u }
        val corte = dias.minOfOrNull { it.fecha }
        val recientes = reportados.filter { corte == null || it >= corte }.sorted()
        almacen.escribir(EstadoPersistido(dias, ultima, recientes))
    }

    fun fueReportado(fecha: String): Boolean = fecha in reportados

    suspend fun marcarReportado(fecha: String) {
        reportados += fecha
        guardarForzado()
    }

    suspend fun desmarcarReportado(fecha: String) {
        reportados -= fecha
        guardarForzado()
    }

    // El reporte marca ANTES de enviar: debe quedar en disco aunque no haya días con datos que podar.
    private suspend fun guardarForzado() = mutex.withLock {
        if (!restaurado) return@withLock
        val (dias, ultima) = muestreador.congelado { u -> registro.instantanea() to u }
        almacen.escribir(EstadoPersistido(dias, ultima, reportados.sorted()))
    }

    /** Muestrea y guarda cada [intervaloMs] mientras viva el proceso. */
    @Suppress("TooGenericExceptionCaught")
    fun iniciarCiclo(scope: CoroutineScope, intervaloMs: Long = INTERVALO_MS): Job = scope.launch {
        restaurar()
        while (true) {
            try {
                muestreador.muestrear()
                guardar()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Medición: un fallo de disco no puede tumbar nada.
            }
            delay(intervaloMs)
        }
    }

    /** Muestrea y guarda ya (al pasar a segundo plano). */
    @Suppress("TooGenericExceptionCaught")
    suspend fun guardarAhora() {
        try {
            muestreador.muestrear()
            guardar()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Idem.
        }
    }

    companion object {
        const val INTERVALO_MS = 60_000L
    }
}
