package com.example.msp_app.feature.compartirproductos.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.feature.compartirproductos.android.AlmacenDeTemporales
import com.example.msp_app.feature.compartirproductos.android.GeneradorDeTarjetas
import com.example.msp_app.feature.compartirproductos.android.IntentDeCompartir
import com.example.msp_app.feature.compartirproductos.android.PreferenciasEnSharedPrefs
import com.example.msp_app.feature.compartirproductos.domain.ArmadorDeTarjeta
import com.example.msp_app.feature.compartirproductos.domain.ArticuloParaCompartir
import com.example.msp_app.feature.compartirproductos.domain.AvisoDeCatalogo
import com.example.msp_app.feature.compartirproductos.domain.FilaDePrecio
import com.example.msp_app.feature.compartirproductos.domain.PreferenciasDeCompartir
import com.example.msp_app.feature.compartirproductos.domain.SeleccionDePrecios
import com.example.msp_app.feature.compartirproductos.domain.TextoDelMensaje
import com.example.msp_app.feature.compartirproductos.render.DibujanteDeTarjeta
import com.example.msp_app.feature.compartirproductos.render.FotoDeProducto
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lo que la hoja inferior muestra. */
data class EstadoDeCompartir(
    val visible: Boolean = false,
    val articulos: List<ArticuloParaCompartir> = emptyList(),
    val seleccion: SeleccionDePrecios = SeleccionDePrecios(),
    /** Solo con UN producto: el título editable. Con varios cada uno usa el recordado o el automático. */
    val titulo: String = "",
    val vistaPrevia: Bitmap? = null,
    val generando: Boolean = false,
    val hechas: Int = 0,
    val avisoPreciosViejos: Boolean = false,
    val error: String? = null
) {
    val esUno: Boolean get() = articulos.size == 1
}

sealed interface EventoDeCompartir {
    /** Ya están las tarjetas: abrir WhatsApp (o el selector). */
    data class Lanzar(val intent: Intent) : EventoDeCompartir
}

/**
 * Cerebro de la hoja «Compartir»: carga las casillas y títulos recordados, dibuja la vista previa
 * de la primera tarjeta, y al confirmar genera las tarjetas fuera del hilo principal y entrega el intent.
 */
class CompartirViewModel(
    private val generador: GeneradorDeTarjetas,
    private val dibujante: DibujanteDeTarjeta,
    private val preferencias: PreferenciasDeCompartir,
    private val crearIntent: (List<File>, String) -> Intent,
    private val reloj: AppClock = AppClock.System,
    private val trabajo: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoDeCompartir())
    val estado: StateFlow<EstadoDeCompartir> = _estado

    private val _eventos = Channel<EventoDeCompartir>(Channel.BUFFERED)
    val eventos = _eventos.receiveAsFlow()

    private var vistaPreviaJob: Job? = null

    /**
     * Abre la hoja con [articulos]. [catalogoActualizadoEn] es la marca de última actualización, si existe.
     * E-APP-183: `:app` la lee de `FechaDelCatalogo`; sin marca (instalación nueva sin sincronizar) pasa `null` y el aviso no sale.
     */
    fun abrir(articulos: List<ArticuloParaCompartir>, catalogoActualizadoEn: Instant? = null) {
        if (articulos.isEmpty()) return
        val titulo = if (articulos.size == 1) {
            preferencias.tituloEditado(articulos.single().id) ?: ArmadorDeTarjeta.tituloAutomatico(articulos.single())
        } else {
            ""
        }
        _estado.value = EstadoDeCompartir(
            visible = true,
            articulos = articulos,
            seleccion = preferencias.seleccionDePrecios(),
            titulo = titulo,
            avisoPreciosViejos = AvisoDeCatalogo.desactualizado(catalogoActualizadoEn, reloj.now())
        )
        dibujarVistaPrevia(esperar = false)
    }

    fun cambiarCasilla(fila: FilaDePrecio, marcada: Boolean) {
        _estado.update { it.copy(seleccion = it.seleccion.con(fila, marcada)) }
        dibujarVistaPrevia()
    }

    fun cambiarTitulo(texto: String) {
        _estado.update { it.copy(titulo = texto.take(ArmadorDeTarjeta.LARGO_MAXIMO_DE_TITULO)) }
        dibujarVistaPrevia()
    }

    fun cerrar() {
        vistaPreviaJob?.cancel()
        // E-APP-182: La vista previa NO se recicla: Compose puede estar dibujándola un cuadro más y un Bitmap
        // reciclado truena al pintarse. Pesa ~1 MB; la recoge el recolector.
        _estado.value = EstadoDeCompartir()
    }

    /** Guarda lo elegido, genera las tarjetas y emite [EventoDeCompartir.Lanzar]. */
    @Suppress(
        "TooGenericExceptionCaught"
    ) // cualquier fallo al dibujar/escribir se avisa en la hoja; nunca tumba la app
    fun compartir() {
        val actual = _estado.value
        if (!actual.visible || actual.generando || actual.articulos.isEmpty()) return
        guardarPreferencias(actual)
        _estado.update { it.copy(generando = true, hechas = 0, error = null) }
        viewModelScope.launch {
            val ahora = reloj.now()
            try {
                val archivos = withContext(trabajo) {
                    generador.generar(
                        actual.articulos,
                        actual.seleccion,
                        ahora,
                        tituloSolo = actual.titulo.takeIf {
                            actual.esUno
                        }
                    ) { hechas, _ ->
                        _estado.update { it.copy(hechas = hechas) }
                    }
                }
                val texto = TextoDelMensaje.armar(actual.articulos, actual.seleccion, ahora)
                _eventos.send(EventoDeCompartir.Lanzar(crearIntent(archivos, texto)))
                cerrar()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "no se pudieron generar las tarjetas", e)
                _estado.update { it.copy(generando = false, error = MENSAJE_ERROR) }
            } catch (e: OutOfMemoryError) {
                // E-APP-184: sin memoria para el bitmap de 1080×1920 (teléfono de gama baja): se avisa, no se cierra la app.
                Log.w(TAG, "sin memoria al generar las tarjetas", e)
                _estado.update { it.copy(generando = false, error = MENSAJE_SIN_MEMORIA) }
            }
        }
    }

    private fun guardarPreferencias(actual: EstadoDeCompartir) {
        preferencias.guardarSeleccionDePrecios(actual.seleccion)
        if (actual.esUno) {
            val articulo = actual.articulos.single()
            val editado = actual.titulo.trim()
            // Igual al automático = no hay nada que recordar: si luego cambia el nombre, cambia el título.
            val aRecordar = editado.takeIf {
                it.isNotEmpty() && it != ArmadorDeTarjeta.tituloAutomatico(articulo)
            }
            preferencias.guardarTituloEditado(articulo.id, aRecordar)
        }
    }

    @Suppress(
        "TooGenericExceptionCaught"
    ) // sin vista previa la hoja sigue sirviendo: solo se queda el indicador
    private fun dibujarVistaPrevia(esperar: Boolean = true) {
        vistaPreviaJob?.cancel()
        vistaPreviaJob = viewModelScope.launch {
            if (esperar) delay(ESPERA_DE_TECLEO_MS)
            val actual = _estado.value
            val primero = actual.articulos.firstOrNull() ?: return@launch
            val chica = try {
                withContext(trabajo) {
                    val titulo = if (actual.esUno) actual.titulo else preferencias.tituloEditado(primero.id)
                    val tarjeta = ArmadorDeTarjeta.armar(primero, actual.seleccion, titulo, reloj.now())
                    val foto = FotoDeProducto.cargar(primero.fotoPath)
                    val grande = dibujante.dibujar(tarjeta, foto)
                    foto?.recycle()
                    Bitmap.createScaledBitmap(grande, ANCHO_VISTA, ALTO_VISTA, true).also {
                        if (it !== grande) grande.recycle()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "no se pudo dibujar la vista previa", e)
                null
            }
            _estado.update { it.copy(vistaPrevia = chica) }
        }
    }

    companion object {
        private const val TAG = "CompartirProductos"
        const val ESPERA_DE_TECLEO_MS = 250L
        const val ANCHO_VISTA = 405
        const val ALTO_VISTA = 720
        const val MENSAJE_ERROR = "No se pudieron preparar las imágenes"
        const val MENSAJE_SIN_MEMORIA = "No hay memoria para preparar las imágenes. Cierra otras apps e inténtalo de nuevo"

        fun factory(contexto: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras
                ): T {
                    val app = contexto.applicationContext as Application
                    val preferencias = PreferenciasEnSharedPrefs(app)
                    val dibujante = DibujanteDeTarjeta(app)
                    val almacen = AlmacenDeTemporales(app.cacheDir)
                    return CompartirViewModel(
                        generador = GeneradorDeTarjetas(dibujante, almacen, preferencias),
                        dibujante = dibujante,
                        preferencias = preferencias,
                        crearIntent = { archivos, texto ->
                            IntentDeCompartir.paraEsteTelefono(
                                app,
                                archivos.map { IntentDeCompartir.uriDe(app, it) },
                                texto
                            )
                        }
                    ) as T
                }
            }
    }
}
