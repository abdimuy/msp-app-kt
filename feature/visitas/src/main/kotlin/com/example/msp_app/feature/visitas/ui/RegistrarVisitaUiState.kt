package com.example.msp_app.feature.visitas.ui

import com.example.msp_app.feature.visitas.domain.model.BloqueoDeLaVisita
import com.example.msp_app.feature.visitas.domain.model.CapturaDeVisita
import com.example.msp_app.feature.visitas.domain.model.ContextoDeVisita
import com.example.msp_app.feature.visitas.domain.model.RecomendacionMostrada
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
    val eligiendoHora: Boolean = false
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

    /** La razón que se muestra bajo el CTA apagado. `null` cuando no hay ninguna. */
    val razonDelBloqueo: String?
        get() = bloqueos.firstOrNull()?.razon
}

/**
 * El "hoy" de un estado que todavía no cargó. No es `LocalDate.now()` —la
 * pantalla no pregunta la hora del sistema— ni `LocalDate.EPOCH`, que es de
 * Java 9 y este `minSdk` llega ahí solo por desugaring. Con la pantalla
 * cargando no se pinta ni un chip de fecha, así que este valor nunca se
 * muestra; existe para que el tipo no sea anulable.
 */
private val EPOCA: LocalDate = LocalDate.of(1970, 1, 1)
