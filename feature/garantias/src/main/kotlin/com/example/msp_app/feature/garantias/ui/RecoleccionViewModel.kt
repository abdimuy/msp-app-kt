package com.example.msp_app.feature.garantias.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.feature.garantias.application.FalloDeCaptura
import com.example.msp_app.feature.garantias.application.LeerDetalleDeFolio
import com.example.msp_app.feature.garantias.application.RecolectarGarantia
import com.example.msp_app.feature.garantias.application.ResultadoDeCaptura
import com.example.msp_app.feature.garantias.di.GarantiasIoDispatcher
import com.example.msp_app.feature.garantias.domain.AccionesDeRecoleccion
import com.example.msp_app.feature.garantias.domain.model.AccionDeGarantia
import com.example.msp_app.feature.garantias.domain.model.CatalogoDeGarantias
import com.example.msp_app.feature.garantias.domain.model.FotoDeCaptura
import com.example.msp_app.feature.garantias.domain.model.OrigenDeFolio
import com.example.msp_app.feature.garantias.domain.model.RecoleccionDeGarantia
import com.example.msp_app.feature.garantias.domain.model.Ubicacion
import com.example.msp_app.feature.garantias.domain.port.ArchivosDeGarantiaPort
import com.example.msp_app.feature.garantias.domain.port.CamaraDeGarantiaPort
import com.example.msp_app.feature.garantias.domain.port.CatalogoDeGarantiasPort
import com.example.msp_app.feature.garantias.domain.port.UbicacionDeGarantiaPort
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lo que pinta la recolección de un artículo.
 *
 * [acciones] sale del catálogo del servidor o del respaldo de la captura
 * ([AccionesDeRecoleccion]); [accionElegida] es la etapa de destino que el
 * cobrador tocó. [catalogoVacio] sólo cambia el texto: con catálogo se dice qué
 * se puede hacer, sin catálogo se declara que el servidor todavía no lo publica.
 *
 * [ubicacion] puede ser `null` sin bloquear: a diferencia de la entrega, la
 * recolección sin GPS se guarda igual (D5).
 */
data class RecoleccionUiState(
    val cargando: Boolean = true,
    val etapa: String = ETAPA_INICIAL,
    val acciones: List<AccionDeGarantia> = emptyList(),
    val accionElegida: String? = null,
    val catalogoVacio: Boolean = true,
    val fotos: List<FotoDeCaptura> = emptyList(),
    val destinoDeFoto: String? = null,
    val ubicacion: Ubicacion? = null,
    val pidiendoUbicacion: Boolean = false,
    val guardando: Boolean = false,
    val fallo: FalloDeCaptura? = null,
    val falloTecnico: Boolean = false,
    val guardado: Boolean = false
) {
    /** Sin acción elegida no hay etapa de destino y el servidor respondería 422. */
    val sePuedeGuardar: Boolean
        get() = accionElegida != null && !guardando

    companion object {
        /**
         * La etapa con la que arranca la pantalla cuando el detalle remoto no se
         * puede leer. Es la primera etapa del servidor
         * (`internal/garantias/domain/transiciones.go`) y la única de la que el
         * respaldo ofrece salida.
         */
        const val ETAPA_INICIAL: String = "registrado"
    }
}

/**
 * La recolección de un artículo: fotos, GPS opcional y la acción que el
 * catálogo declara válida.
 *
 * ## La cámara vive acá y se lanza desde la ruta
 *
 * [tomarFoto] sólo publica [RecoleccionUiState.destinoDeFoto]: el contrato
 * `TakePicture()` lo lanza el wrapper de la ruta, que es la capa que conoce
 * `android.net.Uri`. Así este ViewModel se prueba sin cámara ni Robolectric.
 *
 * ## Cancelar borra el crudo
 *
 * [fotoCancelada] llama igual a `archivar`, que descarta el archivo crudo de la
 * caché y devuelve `null`; si por una excepción devolviera una foto, se borra
 * con [ArchivosDeGarantiaPort]. No se agrega un método nuevo al puerto: el
 * puerto ya sabe limpiar su propio crudo.
 */
@HiltViewModel
@Suppress(
    "LongParameterList",
    "TooManyFunctions"
) // colaboradores del puerto + una función por control.
class RecoleccionViewModel @Inject constructor(
    private val leerDetalle: LeerDetalleDeFolio,
    private val catalogo: CatalogoDeGarantiasPort,
    private val recolectar: RecolectarGarantia,
    private val camara: CamaraDeGarantiaPort,
    private val ubicacionPort: UbicacionDeGarantiaPort,
    private val archivos: ArchivosDeGarantiaPort,
    savedStateHandle: SavedStateHandle,
    @GarantiasIoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    /** La referencia **local** del folio que se está recolectando. */
    val folioRef: String = checkNotNull(savedStateHandle.get<String>(GarantiasV2Rutas.ARG_FOLIO)) {
        "RecoleccionViewModel sin ${GarantiasV2Rutas.ARG_FOLIO} en el SavedStateHandle"
    }

    /** La referencia **local** del artículo que se está recolectando. */
    val articuloRef: String =
        checkNotNull(savedStateHandle.get<String>(GarantiasV2Rutas.ARG_ARTICULO)) {
            "RecoleccionViewModel sin ${GarantiasV2Rutas.ARG_ARTICULO} en el SavedStateHandle"
        }

    private val mutable = MutableStateFlow(RecoleccionUiState())
    val state: StateFlow<RecoleccionUiState> = mutable.asStateFlow()

    /**
     * Lee la etapa del artículo y las acciones que el catálogo ofrece.
     *
     * Una base o una red que fallan no dejan la pantalla en blanco: sin detalle
     * remoto se cae a la etapa inicial, que es la única de la que el respaldo
     * sabe salir. Tampoco se inventan acciones: si el catálogo está vacío, el
     * respaldo de la captura es el que decide.
     */
    @Suppress("TooGenericExceptionCaught") // el detalle remoto es un adorno; la captura sigue.
    fun cargar() {
        mutable.value = mutable.value.copy(cargando = true)
        viewModelScope.launch {
            val detalle = try {
                withContext(io) { leerDetalle.ejecutar(folioRef) }
            } catch (_: Exception) {
                null
            }
            val etapa = detalle?.articulos
                ?.firstOrNull { it.articuloRef == articuloRef }
                ?.remoto?.etapa
                ?.takeIf { it.isNotBlank() }
                ?: RecoleccionUiState.ETAPA_INICIAL
            val catalogoLeido = try {
                withContext(io) { catalogo.leer() }
            } catch (_: Exception) {
                CatalogoDeGarantias.SIN_CATALOGO
            }
            val origen = detalle?.folio?.origen ?: OrigenDeFolio.PISO
            val acciones = AccionesDeRecoleccion.acciones(catalogoLeido, etapa, origen)
            mutable.value = mutable.value.copy(
                cargando = false,
                etapa = etapa,
                acciones = acciones,
                accionElegida = acciones.singleOrNull()?.etapaDestino,
                catalogoVacio = catalogoLeido.estaVacio
            )
        }
    }

    /** El cobrador eligió qué acción mandar. */
    fun elegirAccion(etapaDestino: String) {
        mutable.value = mutable.value.copy(accionElegida = etapaDestino, fallo = null)
    }

    /** Pide un `content://` nuevo para la cámara; la ruta lo lanza. */
    fun tomarFoto() {
        if (mutable.value.destinoDeFoto != null) return
        mutable.value = mutable.value.copy(destinoDeFoto = camara.nuevoDestino(), fallo = null)
    }

    /** La cámara contestó OK: se archiva la foto y se suma a la recolección. */
    fun fotoTomada() {
        val destino = mutable.value.destinoDeFoto ?: return
        mutable.value = mutable.value.copy(destinoDeFoto = null)
        viewModelScope.launch {
            val foto = withContext(io) { camara.archivar(destino) }
            mutable.value = if (foto != null) {
                mutable.value.copy(fotos = mutable.value.fotos + foto)
            } else {
                mutable.value.copy(fallo = FalloDeCaptura.FOTO_NO_GUARDADA)
            }
        }
    }

    /** La cámara se canceló: se descarta el crudo y no se suma ninguna foto. */
    fun fotoCancelada() {
        val destino = mutable.value.destinoDeFoto ?: return
        mutable.value = mutable.value.copy(destinoDeFoto = null)
        viewModelScope.launch {
            val foto = withContext(io) { camara.archivar(destino) }
            if (foto != null) withContext(io) { archivos.borrar(foto.rutaArchivo) }
        }
    }

    /** Quita una foto de la recolección y borra su archivo: todavía no está en cola. */
    fun quitarFoto(clave: String) {
        val actual = mutable.value
        val foto = actual.fotos.firstOrNull { it.clave == clave } ?: return
        mutable.value = actual.copy(fotos = actual.fotos.filterNot { it.clave == clave })
        viewModelScope.launch { withContext(io) { archivos.borrar(foto.rutaArchivo) } }
    }

    /** Pide el GPS una vez; si no lo hay, la recolección se guarda igual. */
    fun pedirUbicacion() {
        if (mutable.value.pidiendoUbicacion) return
        mutable.value = mutable.value.copy(pidiendoUbicacion = true)
        viewModelScope.launch {
            val ubicacion = ubicacionPort.ubicacionActual()
            mutable.value = mutable.value.copy(pidiendoUbicacion = false, ubicacion = ubicacion)
        }
    }

    /**
     * Encola la recolección con sus fotos.
     *
     * El caso de uso valida el contrato del servidor; acá sólo se traduce el
     * resultado a estado. Una base que no abre no puede dejar la pantalla
     * colgada con "Guardando": se anota como fallo técnico.
     */
    @Suppress("TooGenericExceptionCaught") // escribir la cola no puede colgar la captura.
    fun guardar() {
        val actual = mutable.value
        if (actual.guardando) return
        val etapa = actual.accionElegida
        if (etapa == null) {
            mutable.value = actual.copy(fallo = FalloDeCaptura.SIN_ACCION)
            return
        }
        mutable.value = actual.copy(guardando = true, fallo = null, falloTecnico = false)
        viewModelScope.launch {
            val resultado = try {
                withContext(io) {
                    recolectar.ejecutar(
                        RecoleccionDeGarantia(
                            folioRef = folioRef,
                            articuloRef = articuloRef,
                            etapaDestino = etapa,
                            ubicacion = actual.ubicacion,
                            fotos = actual.fotos
                        )
                    )
                }
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(guardando = false, falloTecnico = true)
                return@launch
            }
            mutable.value = when (resultado) {
                is ResultadoDeCaptura.Encolado -> mutable.value.copy(guardando = false, guardado = true)
                is ResultadoDeCaptura.Bloqueado ->
                    mutable.value.copy(guardando = false, fallo = resultado.fallo)
            }
        }
    }

    /** La ruta ya volvió al detalle; se suelta la señal para no repetirla. */
    fun guardadoNavegado() {
        mutable.value = mutable.value.copy(guardado = false)
    }
}
