package com.example.msp_app.core.datos

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import java.time.LocalDate

/**
 * Arma el documento diario. LISTA BLANCA de campos ([CAMPOS_PERMITIDOS]): nada de
 * rutas, identificadores, nombres ni datos del dispositivo. El sobre de
 * `RemoteLogger` ya trae `userId`, `userEmail` y `appVersion`; aquí no se agrega nada de eso.
 */
object ConstructorDeReporte {
    const val MAX_SERVICIOS = 10

    val CAMPOS_PERMITIDOS: Set<String> = setOf(
        "fecha", "version", "totalBytes", "movilBytes", "wifiBytes", "otraRedBytes",
        "primerPlanoBytes", "segundoPlanoBytes", "servicios", "mapaFirebaseYOtrosBytes",
        "fotosSubidas", "fotosBytes"
    )

    fun construir(dia: UsoDelDia, version: String): Map<String, Any?> {
        val porServicio = dia.endpoints.entries.groupBy {
            ClasificadorDeServicio.servicioDe(
                it.key
            )
        }
            .map { (nombre, lista) ->
                ServicioAgregado(
                    nombre,
                    lista.sumOf { it.value.rx },
                    lista.sumOf { it.value.tx },
                    lista.sumOf { it.value.peticiones }
                )
            }
            .sortedByDescending { it.bajada + it.subida }
            .take(MAX_SERVICIOS)
        val fotos = listOfNotNull(dia.endpoints[ClasificadorDeServicio.CLAVE_FOTOS])
        val mapa = if (dia.total > 0) maxOf(0L, dia.total - dia.desglose) else 0L
        return linkedMapOf(
            "fecha" to dia.fecha,
            "version" to version,
            "totalBytes" to dia.total,
            "movilBytes" to dia.movil,
            "wifiBytes" to dia.wifi,
            "otraRedBytes" to dia.otraRed,
            "primerPlanoBytes" to dia.primerPlano,
            "segundoPlanoBytes" to dia.segundoPlano,
            "servicios" to porServicio.map {
                linkedMapOf<String, Any?>(
                    "nombre" to it.nombre,
                    "bajada" to it.bajada,
                    "subida" to it.subida,
                    "peticiones" to it.peticiones
                )
            },
            "mapaFirebaseYOtrosBytes" to mapa,
            "fotosSubidas" to fotos.sumOf { it.peticiones },
            "fotosBytes" to fotos.sumOf { it.tx }
        )
    }

    private data class ServicioAgregado(val nombre: String, val bajada: Long, val subida: Long, val peticiones: Long)
}

object PlanDeReporte {
    const val DIAS_ATRAS = 7

    /** Días con datos, anteriores a [hoy], hasta [DIAS_ATRAS] atrás, sin reportar; del más viejo al más nuevo. */
    fun pendientes(
        hoy: LocalDate,
        dias: Map<String, UsoDelDia>,
        reportados: Set<String>
    ): List<String> {
        val desde = hoy.minusDays(DIAS_ATRAS.toLong()).toString()
        val hasta = hoy.toString()
        return dias.values
            .filter { it.fecha >= desde && it.fecha < hasta && it.fecha !in reportados && it.tieneDatos }
            .map { it.fecha }
            .sorted()
    }
}

/** Destino del reporte. `false` = no se pudo entregar (se reintenta después). */
fun interface EmisorDeReporte {
    fun emitir(datos: Map<String, Any?>): Boolean
}

/**
 * Manda los días pendientes. Marca el día como reportado ANTES de emitir y lo
 * desmarca si el emisor dice que no pudo: así un worker doble no duplica, y
 * sin red se reintenta después.
 */
class ReporteDeUsoDeDatos(
    private val servicio: ServicioDeUsoDeDatos,
    private val emisor: EmisorDeReporte,
    private val version: String,
    private val clock: AppClock = AppClock.System
) {
    suspend fun enviarPendientes(): Int {
        servicio.restaurar()
        val dias = servicio.registro.instantanea().associateBy { it.fecha }
        val hoy = AppTime.todayInBusinessZone(clock)
        val pendientes = PlanDeReporte.pendientes(
            hoy,
            dias,
            dias.keys.filter(servicio::fueReportado).toSet()
        )
        var enviados = 0
        for (fecha in pendientes) {
            val dia = dias.getValue(fecha)
            servicio.marcarReportado(fecha)
            val ok = try {
                emitirSinRiesgo(ConstructorDeReporte.construir(dia, version))
            } catch (e: kotlinx.coroutines.CancellationException) {
                servicio.desmarcarReportado(fecha)
                throw e
            }
            if (ok) {
                enviados++
            } else {
                servicio.desmarcarReportado(fecha)
                break
            }
        }
        return enviados
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun emitirSinRiesgo(datos: Map<String, Any?>): Boolean = try {
        emisor.emitir(datos)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        false // un emisor que falla = no entregado: se reintenta después
    }
}
