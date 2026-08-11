package com.example.msp_app.core.settings.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.example.msp_app.core.settings.DataStoreSettingsRepository
import com.example.msp_app.core.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Nombre del archivo DataStore app-scoped que respalda [SettingsRepository]
 * (spec §"`:core:settings`": "un único DataStore Preferences app-scoped").
 */
private const val SETTINGS_DATASTORE_NAME = "msp_settings"

/**
 * Expone por Hilt el `DataStore<Preferences>` de configuración y el
 * [SettingsRepository] que lo envuelve. `@Singleton` en ambos: a diferencia
 * de `NetworkModule.provideWarehousesApi` (que evita `@Singleton` sobre un
 * binding que sostiene un `ApiProvider`/baseURL mutable por el kill-switch de
 * Firestore, ver `reference_msp_app_kt_hilt_baseurl_killswitch`), ni el
 * DataStore ni el repositorio sostienen ningún estado de red — mismo
 * razonamiento que ya usan `TelemetryDatabaseModule`/`ConnectivityModule`
 * para memoizar sin riesgo.
 *
 * `provideSettingsDataStore` construye el `DataStore` una sola vez vía
 * `PreferenceDataStoreFactory.create` — a diferencia de `AppDatabase`/
 * `TelemetryDatabase` (que delegan en su propio `getInstance` porque son
 * singletons manuales pre-existentes), este módulo es la ÚNICA fuente de la
 * instancia: no hay una ruta legacy que abra el mismo archivo por fuera de
 * Hilt, así que no hace falta un `getInstance`/`setInstanceForTesting` propio
 * — los tests de este módulo instancian su propio `DataStore` de archivo
 * temporal directo (ver `DataStoreSettingsRepositoryTest`), sin pasar por
 * este módulo Hilt en absoluto.
 */
@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(SETTINGS_DATASTORE_NAME) }
        )

    @Provides
    @Singleton
    fun provideSettingsRepository(dataStore: DataStore<Preferences>): SettingsRepository =
        DataStoreSettingsRepository(dataStore)
}
