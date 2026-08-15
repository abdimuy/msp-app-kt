package com.example.msp_app.features.cities

import android.app.Application
import com.example.msp_app.core.cache.BaseOfflineCache
import com.example.msp_app.core.viewmodel.BaseOfflineViewModel
import com.example.msp_app.data.cache.CitiesCache
import com.example.msp_app.data.models.city.City
import com.example.msp_app.data.models.city.normalizeCiudad

/**
 * ViewModel del catálogo de ciudades, con el mismo soporte offline que
 * [com.example.msp_app.features.zones.ZonesViewModel]: fetch con fallback
 * automático a cache, estado offline, refresh manual.
 *
 * A diferencia de `ZonesViewModel` **no** guarda la ciudad seleccionada: la
 * selección vive en el formulario de la venta, porque ciudad y estado tienen que
 * quedar juntos en el mismo estado que se persiste al borrador. Un `selectedCity`
 * aquí sería una segunda copia de la verdad, y es justo la separación que produce
 * clientes con ciudad de un estado y estado de otro.
 *
 * [repository] y [citiesCache] se inyectan con valor por omisión para poder pasar
 * fakes en tests. `@JvmOverloads` es obligatorio: sin él Kotlin no genera el
 * constructor `(Application)` que busca la factory de `viewModel()`.
 */
class CitiesViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: CitiesRepository = CitiesRepository(),
    citiesCache: CitiesCache = CitiesCache.getInstance(application.applicationContext)
) : BaseOfflineViewModel<City>(application) {

    override val cache: BaseOfflineCache<City> = citiesCache

    override suspend fun fetchFromNetwork(): Result<List<City>> {
        return repository.getCities()
    }

    /**
     * Busca en los datos ya cargados la ciudad cuyo nombre corresponde a [nombre],
     * comparando con [normalizeCiudad] — el mismo criterio con el que el servidor
     * decide si la ciudad capturada está en el catálogo.
     *
     * Sirve para reconciliar un borrador restaurado: el texto guardado vuelve a
     * quedar ligado a su fila (y por lo tanto a su estado) sin que el vendedor
     * tenga que reelegirla.
     */
    fun findByName(nombre: String): City? {
        if (nombre.isBlank()) return null
        val objetivo = normalizeCiudad(nombre)
        return getCurrentData().firstOrNull { normalizeCiudad(it.ciudad) == objetivo }
    }
}
