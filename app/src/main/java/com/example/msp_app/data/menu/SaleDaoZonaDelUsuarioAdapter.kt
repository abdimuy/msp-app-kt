package com.example.msp_app.data.menu

import com.example.msp_app.core.database.dao.sale.SaleDao
import com.example.msp_app.core.navegacion.domain.port.ZonaDelUsuarioPort
import com.example.msp_app.core.sync.cobranza.CobranzaSyncProvider
import com.example.msp_app.core.sync.cobranza.UserContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * El nombre de zona del usuario para la cabecera del menú (ruling R3 del
 * controlador, 2026-10-06): `SaleDao.observarNombreDeZona(zonaId)` (Task 6)
 * sobre la zona VIGENTE del cobrador.
 *
 * ## De dónde sale la zona (documentado con archivo:línea, como pide R3)
 *
 * NO se lee de `AuthViewModel.userData` — ese `StateFlow` vive dentro de una
 * instancia de `ViewModel` que no es inyectable en un `@Module` de Hilt
 * (`AuthViewModel` ni siquiera es `@HiltViewModel`,
 * `features/auth/viewModels/AuthViewModel.kt:38`). Se usa en cambio
 * [CobranzaSyncProvider.userContextFlow] — la MISMA zona que ya consume el
 * resto de cobranza (`core/sync/cobranza/CobranzaSyncProvider.kt:30`):
 * `AppNavigation.kt:345-358` empuja `UserContext(zona = authedUserData.ZONA_CLIENTE_ID, …)`
 * ahí cada vez que cambia el usuario autenticado o su `ZONA_CLIENTE_ID`
 * (`User.kt:15`), y sólo cuando `ZONA_CLIENTE_ID > 0`. Reusar esa fuente en vez
 * de una segunda lectura de Firestore evita un SEGUNDO camino para la misma
 * zona que podría desincronizarse del que usa el sync.
 *
 * `[zona > 0]` se repite aquí como defensa en profundidad: el día que algún
 * otro llamador de [CobranzaSyncProvider.setContext] no respete el gate de
 * `AppNavigation`, este adaptador sigue sin ofrecer un nombre para una zona
 * inválida.
 */
class SaleDaoZonaDelUsuarioAdapter(
    private val saleDao: SaleDao,
    private val userContextFlow: Flow<UserContext?> = CobranzaSyncProvider.userContextFlow
) : ZonaDelUsuarioPort {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun nombreDeZona(): Flow<String?> = userContextFlow
        .map { contexto -> contexto?.zona?.takeIf { it > 0 } }
        .distinctUntilChanged()
        .flatMapLatest { zonaId ->
            if (zonaId == null) flowOf(null) else saleDao.observarNombreDeZona(zonaId)
        }
}
