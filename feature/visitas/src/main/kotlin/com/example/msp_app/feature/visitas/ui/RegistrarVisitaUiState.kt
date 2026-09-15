package com.example.msp_app.feature.visitas.ui

import com.example.msp_app.core.common.cobranza.domain.VisitScope
import com.example.msp_app.feature.visitas.domain.ComprobantesDeVisita
import com.example.msp_app.feature.visitas.domain.model.BloqueoDeLaVisita
import com.example.msp_app.feature.visitas.domain.model.CapturaDeVisita
import com.example.msp_app.feature.visitas.domain.model.ComprobanteDeVisita
import com.example.msp_app.feature.visitas.domain.model.ContextoDeVisita
import com.example.msp_app.feature.visitas.domain.model.DestinoDeFoto
import com.example.msp_app.feature.visitas.domain.model.RecomendacionMostrada
import com.example.msp_app.feature.visitas.domain.model.VentaParaVisitar
import java.time.LocalDate

/** Por qué la pantalla no pudo abrirse. */
enum class ErrorDeLaVisita(val mensaje: String) {
    /** El teléfono ya no tiene cuentas de ese cliente. */
    CLIENTE_NO_ESTA("ese cliente no está"),

    /** Room falló al leer. Se ofrece reintentar. */
    FALLO_LA_CARGA("no se pudo cargar")
}

/** Por qué la visita no quedó registrada. */
enum class FalloDeLaVisita(val mensaje: String) {
    CLIENTE_NO_ESTA("ese cliente ya no está"),
    SIN_COBRADOR("falta el cobrador"),
    NO_SE_PUDO_GUARDAR("no se pudo guardar")
}

/**
 * Por qué la foto no se adjuntó. **Ninguno de los tres impide registrar la
 * visita** — por eso el aviso es ámbar y no rojo (el rojo en esta pantalla
 * significa "esto no se puede guardar", que es otra cosa).
 */
enum class FalloDeLaFoto {
    /** La cámara falló, el permiso se negó o la compresión reventó. */
    NO_SE_PUDO_TOMAR,

    /** El archivo no es de un tipo que el servidor acepte. */
    TIPO_NO_PERMITIDO,

    /** Ya hay [com.example.msp_app.feature.visitas.domain.ComprobantesDeVisita.MAXIMO]. */
    YA_NO_CABEN,

    /**
     * La foto terminó de comprimirse **después** de que la escritura ya tomó
     * los comprobantes. No falló nada; simplemente no alcanzó a entrar.
     */
    LLEGO_TARDE
}

/**
 * El estado de la pantalla de registrar visita.
 *
 * [hoy] viaja en el estado y no se lee de `LocalDate.now()` dentro de un
 * composable: los chips de fecha y la guarda de "esa fecha ya pasó" dependen de
 * él, y un golden que preguntara la hora del sistema cambiaría de día en día.
 *
 * [bloqueos] los calcula el ViewModel con `ReglasDeLaVisita` — la pantalla no
 * decide si algo se puede guardar, solo lo pinta. Es la misma función que vuelve
 * a evaluar el caso de uso antes de escribir.
 */
data class RegistrarVisitaUiState(
    val cargando: Boolean = true,
    val error: ErrorDeLaVisita? = null,
    val contexto: ContextoDeVisita? = null,
    val recomendacion: RecomendacionMostrada? = null,
    val captura: CapturaDeVisita = CapturaDeVisita(),
    val hoy: LocalDate = EPOCA,
    val bloqueos: List<BloqueoDeLaVisita> = listOf(BloqueoDeLaVisita.SIN_RESULTADO),
    val guardando: Boolean = false,
    val registrada: String? = null,
    val fallo: FalloDeLaVisita? = null,
    /** El calendario de "otro día" está abierto. */
    val eligiendoDia: Boolean = false,
    /** El reloj de "otra hora" está abierto. */
    val eligiendoHora: Boolean = false,
    /**
     * Las fotos adjuntas, en orden de captura (Task 23).
     *
     * Van **fuera** de [captura] a propósito: `onResultado` limpia la captura al
     * cambiar de desenlace —una promesa arrastrada es un compromiso que el
     * cliente no hizo— y las fotos no son parte de ese compromiso; el cobrador
     * fotografió la puerta y sigue fotografiada aunque cambie de opinión sobre
     * qué pasó. Además `ReglasDeLaVisita` solo mira [captura], así que estando
     * aquí ninguna regla puede convertir una foto en un bloqueo.
     */
    val comprobantes: List<ComprobanteDeVisita> = emptyList(),
    /** Hay un destino listo y la cámara tiene que abrirse. `null` = nada en vuelo. */
    val destinoDeFoto: DestinoDeFoto? = null,
    /** Por qué la última foto no se adjuntó. Nunca apaga el CTA. */
    val falloDeLaFoto: FalloDeLaFoto? = null
) {
    /**
     * ¿El CTA está vivo? Si esto es `false` el botón se pinta apagado **y** no
     * hace nada: un control que se ve vivo y no responde es la mentira que la
     * Task 18 tuvo que arreglar dos veces.
     */
    val sePuedeGuardar: Boolean
        get() = contexto != null &&
            bloqueos.isEmpty() &&
            !guardando &&
            registrada == null

    /** ¿Se puede seguir capturando? Con la visita ya registrada, no. */
    val sePuedeCapturar: Boolean
        get() = contexto != null && !guardando && registrada == null

    /**
     * ¿Se puede adjuntar OTRA foto? Mismo gate que capturar, más el tope.
     *
     * El tope se pregunta aquí y **también** al pedir la foto: la pantalla apaga
     * el botón, y el ViewModel vuelve a mirar porque un control apagado no es
     * una invariante.
     */
    val sePuedeAgregarFoto: Boolean
        get() = sePuedeCapturar && comprobantes.size < ComprobantesDeVisita.MAXIMO

    /** La razón que se muestra bajo el CTA apagado. `null` cuando no hay ninguna. */
    val razonDelBloqueo: String?
        get() = bloqueos.firstOrNull()?.razon

    /**
     * ¿La pantalla pregunta por cuáles cuentas? Solo cuando el desenlace es de
     * una cuenta **y hay más de una de dónde escoger**: con una sola, la sección
     * sería un control de una opción ya elegida.
     */
    val pideCuentas: Boolean
        get() = captura.resultado?.alcance == VisitScope.VENTA &&
            (contexto?.ventas?.size ?: 0) > 1

    /** ¿Las casillas de cuenta son casillas, o un solo botón de opción? */
    val variasCuentas: Boolean
        get() = captura.resultado?.admiteVariasCuentas == true

    /** ¿Están marcadas TODAS las cuentas del cliente? */
    val todasLasCuentasMarcadas: Boolean
        get() = contexto?.ventas.orEmpty().let {
            it.isNotEmpty() && captura.cuentas.containsAll(it.map(VentaParaVisitar::ventaId))
        }

    /**
     * La línea bajo el CTA. **Un solo renglón que dice dos cosas distintas según
     * el estado del botón**, que es lo que le deja coste vertical cero:
     *
     * - apagado → por qué no se puede guardar;
     * - encendido → qué va a quedar escrito.
     *
     * Lo segundo importa más de lo que parece ahora que una captura puede
     * escribir varias visitas: "se guardan 2 visitas, una por cuenta" es la
     * única señal de que desmarcar una casilla cambia lo que pasa, y decirla
     * después sería decirla tarde.
     */
    val pieDelCta: String?
        get() = when {
            razonDelBloqueo != null -> razonDelBloqueo
            registrada != null -> null
            captura.resultado?.alcance == VisitScope.CLIENTE -> aplicaATodaLaPuerta()
            captura.cuentas.size > 1 -> "se guardan ${captura.cuentas.size} visitas, una por cuenta"
            captura.cuentas.size == 1 -> "se guarda 1 visita, de esa cuenta"
            else -> null
        }

    /** "Aplica a sus 3 cuentas" — el alcance del desenlace, contado. */
    private fun aplicaATodaLaPuerta(): String {
        val cuantas = contexto?.ventas?.size ?: 0
        return if (cuantas == 1) "aplica a su única cuenta" else "aplica a sus $cuantas cuentas"
    }
}

/**
 * El "hoy" de un estado que todavía no cargó. No es `LocalDate.now()` —la
 * pantalla no pregunta la hora del sistema— ni `LocalDate.EPOCH`, que es de
 * Java 9 y este `minSdk` llega ahí solo por desugaring. Con la pantalla
 * cargando no se pinta ni un chip de fecha, así que este valor nunca se
 * muestra; existe para que el tipo no sea anulable.
 */
private val EPOCA: LocalDate = LocalDate.of(1970, 1, 1)
