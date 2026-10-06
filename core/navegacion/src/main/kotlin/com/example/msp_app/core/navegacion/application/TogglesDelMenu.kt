package com.example.msp_app.core.navegacion.application

import com.example.msp_app.core.navegacion.domain.port.PrivacidadDelMenuPort
import com.example.msp_app.core.navegacion.domain.port.TemaDelMenuPort
import javax.inject.Inject

/**
 * Los dos puertos detrás de los toggles de la cabecera del menú — sol/luna
 * ([TemaDelMenuPort]) y el ojo de privacidad ([PrivacidadDelMenuPort]) —
 * agrupados en una sola clase para que
 * [com.example.msp_app.core.navegacion.ui.MenuLateralViewModel] no cruce el
 * umbral `LongParameterList` de detekt al sumar
 * [ZonaDelUsuarioPort][com.example.msp_app.core.navegacion.domain.port.ZonaDelUsuarioPort]
 * a los tres puertos del brief. Mismo precedente exacto —misma forma, mismo
 * motivo— que `feature.pagos.application.TogglesDeCabecera`; no se reusa esa
 * clase porque `:core:navegacion` no depende de `:feature:pagos`.
 *
 * `@Inject constructor` sin `@Provides`: Hilt la arma sola en cuanto los dos
 * puertos tienen binding (los provee el módulo de `:app` de la Task 8).
 */
class TogglesDelMenu @Inject constructor(
    val tema: TemaDelMenuPort,
    val privacidad: PrivacidadDelMenuPort
)
