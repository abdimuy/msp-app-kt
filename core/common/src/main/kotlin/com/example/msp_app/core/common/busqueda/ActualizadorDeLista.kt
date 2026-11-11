package com.example.msp_app.core.common.busqueda

import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Textos de la lista de clientes (español, mayúscula inicial, sin punto final). */
object TextosDeLista {
    const val ACTUALIZANDO = "Actualizando…"
    const val PADRON_VACIO = "La lista de clientes aún no se ha descargado"
    const val SIN_RESULTADOS = "No se encontraron clientes"
    const val PARECIDOS = "Parecidos"
    const val PARECIDOS_AVISO = "Parecidos (revisa que sea la persona correcta)"
    const val SEGUIR_BUSCANDO = "Puedes seguir buscando en la lista guardada"
    const val ERROR_LENTA = "No se pudo actualizar: la conexión está lenta. $SEGUIR_BUSCANDO"
    const val ERROR_SIN_CONEXION = "No se pudo actualizar: sin conexión. $SEGUIR_BUSCANDO"
    const val ERROR_GENERICO = "No se pudo actualizar la lista. $SEGUIR_BUSCANDO"
    const val ERROR_LISTA_VACIA = "No se actualizó: el servidor envió la lista vacía. $SEGUIR_BUSCANDO"
    const val FORZAR_ACTUALIZACION = "Actualizar de todos modos"
    const val ERROR_LISTA_INCOMPLETA = "La lista recibida está incompleta. Se conserva la lista guardada"

    fun mostrando(mostrados: Int, total: Int): String =
        "Mostrando $mostrados de $total · escribe más para acotar"

    fun ultimaActualizacion(fecha: String): String = "Última actualización: $fecha"
}

/**
 * Botón "Actualizar lista" (E-APP-158): una descarga a la vez, con tope TOTAL de duración,
 * sin bloquear la búsqueda en la lista que ya existe.
 *
 * - **Un solo vuelo**: un segundo toque mientras corre no lanza otra descarga ([iniciar] → false).
 * - **Tope total** ([topeMs]): al vencer se cancela la descarga de verdad (la cancelación de la
 *   corrutina cancela la llamada HTTP) y queda [mensajeDeError] con [TextosDeLista.ERROR_LENTA].
 * - **La lista anterior no se toca** si la descarga falla o vence: [aplicar] solo corre con una
 *   descarga completa y su escritura es atómica (`ClienteDao.replaceAll`).
 * - **El tope cubre la descarga, no la escritura**: [aplicar] queda fuera del tope a propósito;
 *   si se cancelara a medias, la transacción de Room la revierte entera.
 * - **La cancelación se propaga**: una `CancellationException` (cierre de la pantalla) se
 *   relanza; nunca se convierte en mensaje de error.
 *
 * [aplicar] devuelve `false` cuando decidió NO reemplazar la lista (p. ej. el servidor contestó
 * vacío): se avisa con [mensajeSiNoSeAplica] (por omisión [TextosDeLista.ERROR_LISTA_VACIA]; el
 * padrón de clientes manda [TextosDeLista.ERROR_LISTA_INCOMPLETA] cuando llegó recortada) y
 * [alTerminarBien] no corre.
 */
class ActualizadorDeLista<T>(
    private val scope: CoroutineScope,
    private val topeMs: Long = TopeDeDescarga.MANUAL_MS,
    private val descargar: suspend () -> T,
    private val aplicar: suspend (T) -> Boolean,
    private val alTerminarBien: suspend () -> Unit = {},
    private val mensajeSiNoSeAplica: () -> String = { TextosDeLista.ERROR_LISTA_VACIA },
    private val forzar: (suspend (T) -> Boolean)? = null,
    private val sePuedeForzar: () -> Boolean = { false }
) {
    private val enCurso = AtomicBoolean(false)
    private val _actualizando = MutableStateFlow(false)
    private val _mensajeDeError = MutableStateFlow<String?>(null)
    private val _puedeForzar = MutableStateFlow(false)

    /** La lista descargada que [aplicar] rechazó y que [forzarActualizacion] puede aplicar sin volver a bajarla. */
    private var pendiente: T? = null

    val actualizando: StateFlow<Boolean> = _actualizando
    val mensajeDeError: StateFlow<String?> = _mensajeDeError

    /** `true` cuando la lista se rechazó por una razón que el usuario puede decidir ignorar. */
    val puedeForzar: StateFlow<Boolean> = _puedeForzar

    /** @return `true` si arrancó una descarga; `false` si ya había una en curso. */
    fun iniciar(): Boolean {
        if (!enCurso.compareAndSet(false, true)) return false
        _actualizando.value = true
        _mensajeDeError.value = null
        olvidarPendiente()
        scope.launch { correr() }
        return true
    }

    /**
     * "Actualizar de todos modos": aplica con [forzar] la MISMA lista que se rechazó (sin volver a
     * descargarla). @return `false` si no hay lista pendiente o ya hay una operación en curso.
     */
    fun forzarActualizacion(): Boolean {
        val lista = pendiente ?: return false
        val aplicarForzado = forzar ?: return false
        if (!enCurso.compareAndSet(false, true)) return false
        _actualizando.value = true
        _mensajeDeError.value = null
        olvidarPendiente()
        scope.launch {
            correrConFin { if (aplicarForzado(lista)) alTerminarBien() else avisarRechazo(lista) }
        }
        return true
    }

    fun limpiarError() {
        _mensajeDeError.value = null
        olvidarPendiente()
    }

    private fun olvidarPendiente() {
        pendiente = null
        _puedeForzar.value = false
    }

    private fun avisarRechazo(lista: T) {
        _mensajeDeError.value = mensajeSiNoSeAplica()
        if (forzar != null && sePuedeForzar()) {
            pendiente = lista
            _puedeForzar.value = true
        }
    }

    private suspend fun correr() = correrConFin {
        val descargado = conTopeTotal(topeMs) { descargar() }
        if (aplicar(descargado)) alTerminarBien() else avisarRechazo(descargado)
    }

    private suspend fun correrConFin(cuerpo: suspend () -> Unit) {
        try {
            cuerpo()
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Frontera de la UI: cualquier fallo de la descarga debe volverse un mensaje, nunca
            // una pantalla que truena. La cancelación se relanzó arriba.
            _mensajeDeError.value = mensajeDe(e)
        } finally {
            enCurso.set(false)
            _actualizando.value = false
        }
    }

    private fun mensajeDe(error: Exception): String = when (error) {
        is TiempoAgotadoException, is SocketTimeoutException -> TextosDeLista.ERROR_LENTA
        is IOException -> TextosDeLista.ERROR_SIN_CONEXION
        else -> TextosDeLista.ERROR_GENERICO
    }
}
