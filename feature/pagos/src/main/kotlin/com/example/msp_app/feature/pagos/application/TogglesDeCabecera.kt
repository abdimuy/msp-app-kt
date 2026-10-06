package com.example.msp_app.feature.pagos.application

import com.example.msp_app.feature.pagos.domain.port.PrivacidadPort
import com.example.msp_app.feature.pagos.domain.port.TemaDeLaAppPort
import javax.inject.Inject

/**
 * Los dos puertos detrás de los toggles del encabezado de la lista de
 * clientes — sol/luna ([TemaDeLaAppPort]) y el ojo de privacidad
 * ([PrivacidadPort]) — agrupados en una sola clase para que
 * [com.example.msp_app.feature.pagos.ui.ListaDeClientesViewModel] no cruce el
 * umbral `LongParameterList` de detekt al sumar
 * [ControladorDeOrden][com.example.msp_app.feature.pagos.application.ControladorDeOrden].
 * Mismo criterio de agrupar por "qué controla en pantalla", no por capa.
 *
 * `@Inject constructor` sin `@Provides`: Hilt arma esta clase sola en cuanto
 * los dos puertos tienen binding (los provee `PagosPortsModule` en `:app`).
 */
class TogglesDeCabecera @Inject constructor(
    val tema: TemaDeLaAppPort,
    val privacidad: PrivacidadPort
)
