package com.example.msp_app.data.menu

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.msp_app.core.navegacion.domain.port.SeccionesPlegadasPort
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

internal object MenuLateralKeys {
    val PLEGADAS = stringSetPreferencesKey("menu_secciones_plegadas")
}

/**
 * Qué secciones dejó plegadas el usuario, en el archivo propio `menu_lateral`
 * (decisión D3 del plan, mismo molde que `DataStoreOrdenGuardadoAdapter`). Un
 * archivo ilegible degrada a "nada plegado", nunca lanza.
 */
class DataStoreSeccionesPlegadasAdapter(
    private val dataStore: DataStore<Preferences>
) : SeccionesPlegadasPort {

    override fun plegadas(): Flow<Set<String>> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it[MenuLateralKeys.PLEGADAS] ?: emptySet() }

    override suspend fun setPlegada(seccionId: String, plegada: Boolean) {
        dataStore.edit { prefs ->
            val actuales = prefs[MenuLateralKeys.PLEGADAS] ?: emptySet()
            prefs[MenuLateralKeys.PLEGADAS] = if (plegada) actuales + seccionId else actuales - seccionId
        }
    }
}
