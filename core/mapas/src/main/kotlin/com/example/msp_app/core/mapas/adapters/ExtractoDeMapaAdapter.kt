package com.example.msp_app.core.mapas.adapters

import com.example.msp_app.core.mapas.domain.EstadoDelExtracto
import com.example.msp_app.core.mapas.domain.ExtractoDeMapa
import com.example.msp_app.core.mapas.domain.port.ExtractoDeMapaPort
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * **Quién encola la descarga.** Una costura, para que el adaptador del puerto se
 * pueda probar sin WorkManager y sin un `Context`.
 */
interface PlanificadorDeLaDescarga {

    /**
     * Encola la descarga **restringida a red no medida**. La restricción vive
     * acá y no en el worker: quien la puso al encolar es quien puede
     * garantizarla, y un worker que la volviera a comprobar por su cuenta
     * tendría dos fuentes de verdad para la misma promesa.
     */
    fun encolarSoloConWifi()

    /** Cancela el trabajo encolado o en curso. */
    fun cancelar()
}

/**
 * **El estado del extracto, en un solo lugar.**
 *
 * `@Singleton` y en memoria: el worker escribe, la pantalla y el suelo leen. Si
 * el proceso muere, el estado se reconstruye desde el disco al volver a
 * construirse ([ExtractoDeMapaAdapter] lo siembra) — y ésa es la parte que
 * importa, porque la única verdad durable es si el `.pmtiles` verificado existe
 * o no.
 *
 * `@Singleton` y no sostiene ningún servicio de Retrofit: el kill-switch de
 * baseURL no le aplica (lo vigila `NetworkKillSwitchGuardTest`, que barre el
 * grafo entero y no una lista).
 */
@Singleton
class EstadoDelExtractoEnMemoria @Inject constructor() {

    private val flujo = MutableStateFlow<EstadoDelExtracto>(EstadoDelExtracto.SinOrigen)

    fun flujo(): Flow<EstadoDelExtracto> = flujo.asStateFlow()

    fun poner(estado: EstadoDelExtracto) {
        flujo.value = estado
    }
}

/**
 * El [ExtractoDeMapaPort] de verdad: siembra desde el disco, encola con wifi y
 * borra cuando se lo piden.
 *
 * ## Se siembra desde el disco al construirse
 *
 * Porque el estado en memoria se pierde y el archivo no. Sin esto, un cobrador
 * que bajó el mapa ayer abriría la pantalla hoy y vería "Descargar" sobre un
 * extracto que ya tiene — y bajaría 25 MB de nuevo.
 *
 * ## `extracto == null` es un estado, no un error
 *
 * El extracto se genera a mano y hoy no está publicado en ningún servidor. Sin
 * URL no hay descarga posible, y el puerto lo dice con
 * [EstadoDelExtracto.SinOrigen] en vez de ofrecer un botón que no puede
 * funcionar. El mapa que ya esté en disco —puesto por `adb push`, por ejemplo—
 * **se usa igual**: no tener de dónde bajarlo no es no tenerlo.
 */
@Singleton
class ExtractoDeMapaAdapter @Inject constructor(
    private val almacen: AlmacenDelExtracto,
    private val planificador: PlanificadorDeLaDescarga,
    private val estado: EstadoDelExtractoEnMemoria,
    private val extracto: ExtractoDeMapa?
) : ExtractoDeMapaPort {

    init {
        estado.poner(estadoEnDisco())
    }

    override fun estado(): Flow<EstadoDelExtracto> = estado.flujo()

    override suspend fun pedirLaDescarga() {
        if (extracto == null) {
            // Sin origen no hay nada que encolar. No es un error que reportar:
            // es una configuración que todavía no existe, y la pantalla ya lo
            // dice en pantalla en vez de ofrecer el botón.
            estado.poner(EstadoDelExtracto.SinOrigen)
            return
        }
        // "Esperando wifi" y no "Descargando": es la verdad hasta que el
        // sistema arranque el trabajo, y prometer una barra que no se mueve es
        // lo que termina en una llamada por teléfono.
        estado.poner(EstadoDelExtracto.EsperandoWifi)
        planificador.encolarSoloConWifi()
    }

    override suspend fun cancelarYBorrar() {
        planificador.cancelar()
        almacen.borrarTodo()
        estado.poner(
            if (extracto == null) EstadoDelExtracto.SinOrigen else EstadoDelExtracto.Ausente
        )
    }

    private fun estadoEnDisco(): EstadoDelExtracto {
        val mapa = almacen.mapaListo()
        return when {
            mapa != null -> EstadoDelExtracto.Listo(mapa)
            extracto == null -> EstadoDelExtracto.SinOrigen
            almacen.bytesBajados() > 0L -> EstadoDelExtracto.Interrumpido(almacen.avance(extracto))
            else -> EstadoDelExtracto.Ausente
        }
    }
}
