package com.example.msp_app.features.sales.data


import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.draftDataStore: DataStore<Preferences> by preferencesDataStore(name = "sale_draft")

class DraftManager(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    companion object {
        private val DRAFT_KEY = stringPreferencesKey("sale_draft_data")
    }

    /**
     * Guarda el borrador de la venta
     */
    suspend fun saveDraft(draftData: DraftData) {
        context.draftDataStore.edit { preferences ->
            preferences[DRAFT_KEY] = json.encodeToString(DraftData.serializer(), draftData)
        }
    }

    /**
     * Obtiene el borrador guardado como Flow
     */
    fun getDraftFlow(): Flow<DraftData?> {
        return context.draftDataStore.data.map { preferences ->
            preferences[DRAFT_KEY]?.let { jsonString ->
                try {
                    json.decodeFromString<DraftData>(jsonString)
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
        }
    }

    /**
     * Obtiene el borrador guardado de forma suspendida
     */
    suspend fun getDraft(): DraftData? {
        return try {
            context.draftDataStore.data.map { preferences ->
                preferences[DRAFT_KEY]?.let { jsonString ->
                    json.decodeFromString<DraftData>(jsonString)
                }
            }.first()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Elimina el borrador guardado
     */
    suspend fun clearDraft() {
        context.draftDataStore.edit { preferences ->
            preferences.remove(DRAFT_KEY)
        }
    }

    /**
     * Verifica si existe un borrador
     */
    fun hasDraft(): Flow<Boolean> {
        return context.draftDataStore.data.map { preferences ->
            preferences[DRAFT_KEY] != null
        }
    }
}