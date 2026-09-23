package com.example.msp_app.core.speech.adapters

import com.example.msp_app.core.speech.domain.EstadoDelModelo
import com.example.msp_app.core.speech.domain.ModeloDeDictado
import com.example.msp_app.core.speech.domain.port.ModeloDeDictadoPort
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
    fun encolarSoloConWifi(modelo: ModeloDeDictado)

    /** Cancela el trabajo encolado o en curso. */
    fun cancelar()
}

/**
 * **El estado del modelo, en un solo lugar.**
 *
 * `@Singleton` y en memoria: el worker escribe, la pantalla lee. Si el proceso
 * muere, el estado se reconstruye desde el disco al volver a construirse
 * ([ModeloDeDictadoAdapter] lo siembra) — y ésa es la parte que importa, porque
 * la única verdad durable es si el `.bin` verificado existe o no.
 */
@Singleton
class EstadoDelModeloEnMemoria @Inject constructor() {

    private val flujo = MutableStateFlow<EstadoDelModelo>(EstadoDelModelo.Ausente)

    fun flujo(): Flow<EstadoDelModelo> = flujo.asStateFlow()

    fun poner(estado: EstadoDelModelo) {
        flujo.value = estado
    }
}

/**
 * El [ModeloDeDictadoPort] de verdad: siembra desde el disco, encola con wifi y
 * borra cuando se lo piden.
 *
 * ## Se siembra desde el disco al construirse
 *
 * Porque el estado en memoria se pierde y el archivo no. Sin esto, un cobrador
 * que bajó el modelo ayer abriría la pantalla hoy y vería "Descargar" sobre un
 * modelo que ya tiene — y bajaría 75 MB de nuevo.
 */
@Singleton
class ModeloDeDictadoAdapter @Inject constructor(
    private val almacen: AlmacenDelModelo,
    private val planificador: PlanificadorDeLaDescarga,
    private val estado: EstadoDelModeloEnMemoria,
    private val modelo: ModeloDeDictado,
    private val nativo: MotorWhisperNativo
) : ModeloDeDictadoPort {

    /**
     * Le pregunta al motor, no a una constante. `MotorWhisperNativo.cargada` es
     * `false` mientras la librería no viaje en el APK —hoy, siempre— y pasa a
     * `true` sola el día que exista, sin que nadie voltee una bandera.
     */
    override val motorDisponible: Boolean
        get() = nativo.cargada

    init {
        estado.poner(
            when {
                almacen.rutaDelModeloListo() != null -> EstadoDelModelo.Listo
                almacen.bytesBajados() > 0L -> EstadoDelModelo.Interrumpido(almacen.avance(modelo))
                else -> EstadoDelModelo.Ausente
            }
        )
    }

    override fun estado(): Flow<EstadoDelModelo> = estado.flujo()

    override suspend fun pedirLaDescarga() {
        // "Esperando wifi" y no "Descargando": es la verdad hasta que el
        // sistema arranque el trabajo, y prometer una barra que no se mueve es
        // lo que termina en una llamada por teléfono.
        estado.poner(EstadoDelModelo.EsperandoWifi)
        planificador.encolarSoloConWifi(modelo)
    }

    override suspend fun cancelarYBorrar() {
        planificador.cancelar()
        almacen.borrarTodo()
        estado.poner(EstadoDelModelo.Ausente)
    }
}
