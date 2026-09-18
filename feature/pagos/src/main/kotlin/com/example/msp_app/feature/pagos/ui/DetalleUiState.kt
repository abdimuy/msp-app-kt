package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.Immutable
import com.example.msp_app.feature.pagos.domain.model.DetalleCliente
import com.example.msp_app.feature.pagos.domain.model.DetalleVenta
import com.example.msp_app.feature.pagos.domain.model.SenalDeFicha

/**
 * Por qué no hay pantalla que pintar. Son dos ramas y no un `null`, por la
 * misma razón que `CycleStart` distingue "no hay dato" de "no se pudo leer":
 * al cobrador se le dice una cosa distinta en cada caso, y aplanarlas fue
 * exactamente el defecto D5.
 */
enum class ErrorDeDetalle {
    /** El teléfono no tiene ese cliente / esa venta. No se reintenta. */
    NO_ESTA_EN_EL_TELEFONO,

    /** La carga falló. Se puede reintentar. */
    FALLO_LA_CARGA
}

/** Estado observable del detalle de cliente. */
@Immutable
data class DetalleClienteUiState(
    val cargando: Boolean = true,
    val detalle: DetalleCliente? = null,
    val error: ErrorDeDetalle? = null,
    /** La hoja de la ficha; `null` mientras está cerrada. */
    val edicionDeLaFicha: EdicionDeLaFicha? = null,
    /**
     * "Esconder cantidades" — la preferencia GLOBAL, la misma que mueve el ojo de
     * la lista. Se deriva del puerto en cada emisión, no se guarda acá con un
     * `copy`; ver el KDoc de `DetalleClienteViewModel.state`.
     */
    val montosOcultos: Boolean = false,
    /** Tema oscuro vigente de la app entera — lo pinta el botón sol/luna. */
    val temaOscuro: Boolean = false,
    /**
     * La hoja "¿a cuál cuenta?"; `null` mientras está cerrada.
     *
     * Solo se abre cuando el cliente tiene **dos o más** cuentas cobrables — con
     * una sola el abono va directo y esta hoja nunca existe. Ver
     * [com.example.msp_app.feature.pagos.domain.CuentaDelAbono].
     */
    val eleccionDeCuenta: EleccionDeCuenta? = null
)

/**
 * La elección de cuenta para el abono, en curso.
 *
 * [elegida] arranca en la que trae más atrasos —no en la primera de la lista,
 * que era el defecto— y cambia con cada toque. Nada se registra hasta
 * "continuar": esta hoja solo decide **a dónde** va el dinero, no lo mueve.
 */
@Immutable
data class EleccionDeCuenta(val elegida: Int?)

/** Estado observable del detalle de venta. */
@Immutable
data class DetalleVentaUiState(
    val cargando: Boolean = true,
    val detalle: DetalleVenta? = null,
    val error: ErrorDeDetalle? = null
)

/**
 * La hoja de edición de la ficha, **manejada por estado**: `null` = cerrada.
 *
 * Es una hoja y no un destino de navegación —al revés que el abono o la
 * visita— porque nada de lo que hace saca al usuario de la app: no hay cámara,
 * no hay servicio, no hay proceso que pueda morir en medio. Lo único que se
 * pierde si la app muere es un borrador de nota sin guardar, y ese mismo riesgo
 * lo corre cualquier campo de texto de la app.
 *
 * [senales] y [nota] son el **borrador**, no lo guardado: se siembran de la
 * ficha al abrir y solo llegan a la base cuando el cobrador toca "guardar".
 */
@Immutable
data class EdicionDeLaFicha(
    val senales: Set<SenalDeFicha> = emptySet(),
    val nota: String = "",
    val guardando: Boolean = false,
    /** El último guardado falló. Se dice en la hoja y la hoja NO se cierra. */
    val fallo: Boolean = false,
    /**
     * Qué tan vieja es la nota que se está editando —*"hace 3 días"*—, ya
     * redactada, o `null` si nunca se escribió.
     *
     * Llega hecha y no como fecha porque la hoja **no puede preguntar la hora**:
     * el "hoy" sale del reloj inyectado del caso de uso (`DetalleCliente.hoy`) y
     * no de dentro de un `@Composable`. Hasta ahora la edad sólo se veía en la
     * tarjeta; al editar no se veía, que es justo cuando decide si lo que está
     * escrito todavía sirve o hay que reemplazarlo.
     */
    val anotada: String? = null
)
