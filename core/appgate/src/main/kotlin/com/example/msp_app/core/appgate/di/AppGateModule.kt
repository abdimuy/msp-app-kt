package com.example.msp_app.core.appgate.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.work.WorkManager
import com.example.msp_app.core.appgate.AndroidDeviceIdProvider
import com.example.msp_app.core.appgate.DataStoreVersionGateCache
import com.example.msp_app.core.appgate.DeviceIdProvider
import com.example.msp_app.core.appgate.FirestoreMinVersionConfigSource
import com.example.msp_app.core.appgate.MinVersionConfigSource
import com.example.msp_app.core.appgate.VersionGateCache
import com.example.msp_app.core.appgate.download.AndroidNetworkStatusProvider
import com.example.msp_app.core.appgate.download.ApkDownloader
import com.example.msp_app.core.appgate.download.ApkInstaller
import com.example.msp_app.core.appgate.download.ApkVersionReader
import com.example.msp_app.core.appgate.download.NetworkStatusProvider
import com.example.msp_app.core.appgate.download.PackageManagerApkVersionReader
import com.example.msp_app.core.appgate.download.UpdateDownloadScheduler
import com.example.msp_app.core.appgate.download.UpdateFileLocator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.Call
import okhttp3.OkHttpClient

/** Archivo DataStore propio de la compuerta, separado de `msp_settings`. */
private const val APP_GATE_DATASTORE_NAME = "msp_app_gate"

/**
 * Distingue el `DataStore<Preferences>` de este módulo del que ya provee
 * `SettingsModule` de `:core:settings` **sin calificador**. Sin esto, ambos
 * bindings viven en el mismo `SingletonComponent` del `:app` y Dagger falla
 * con "DataStore<Preferences> is bound multiple times".
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppGateDataStore

/**
 * Cablea la compuerta de versión mínima.
 *
 * **Lo que este módulo NO provee:** `AppBuildInfo`. La versión instalada y
 * `BuildConfig.DEBUG` solo los conoce `:app` (un módulo de librería tiene su
 * propio `BuildConfig`, que no es el de la aplicación), así que ese binding lo
 * aporta la raíz de composición — mismo criterio con el que `:app` provee
 * `AppThemePort`/`UserCyclePort` a los módulos feature.
 *
 * `@Singleton` en todo: ninguno de estos objetos sostiene estado de red
 * mutable (el gotcha de `baseURL` congelada que documenta
 * `reference_msp_app_kt_hilt_baseurl_killswitch` no aplica acá — el
 * `OkHttpClient` de la descarga recibe la URL del APK en cada petición, no en
 * su construcción).
 */
@Module
@InstallIn(SingletonComponent::class)
object AppGateModule {

    @Provides
    @Singleton
    @AppGateDataStore
    fun provideAppGateDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(APP_GATE_DATASTORE_NAME) }
        )

    @Provides
    @Singleton
    fun provideVersionGateCache(
        @AppGateDataStore dataStore: DataStore<Preferences>
    ): VersionGateCache = DataStoreVersionGateCache(dataStore)

    /**
     * `FirebaseFirestore.getInstance()` queda dentro de una lambda perezosa:
     * el grafo se arma en el arranque y no debe exigir Firebase inicializado
     * para poder construirse (ni en producción ni en un test JVM).
     */
    @Provides
    @Singleton
    fun provideMinVersionConfigSource(): MinVersionConfigSource = FirestoreMinVersionConfigSource()

    @Provides
    @Singleton
    fun provideDeviceIdProvider(@ApplicationContext context: Context): DeviceIdProvider =
        AndroidDeviceIdProvider(context)

    @Provides
    @Singleton
    fun provideNetworkStatusProvider(@ApplicationContext context: Context): NetworkStatusProvider =
        AndroidNetworkStatusProvider(context)

    @Provides
    @Singleton
    fun provideUpdateFileLocator(
        @ApplicationContext context: Context,
        versionReader: ApkVersionReader
    ): UpdateFileLocator = UpdateFileLocator(context, versionReader)

    @Provides
    @Singleton
    fun provideApkInstaller(@ApplicationContext context: Context): ApkInstaller =
        ApkInstaller(context)

    /**
     * Lee la versión del APK descargado. Sirve para dos cosas que no se pueden
     * deducir de la configuración remota: saber que el archivo publicado no
     * alcanza el mínimo, y saber que uno ya instalado se puede borrar.
     */
    @Provides
    @Singleton
    fun provideApkVersionReader(@ApplicationContext context: Context): ApkVersionReader =
        PackageManagerApkVersionReader(context)

    /**
     * Cliente propio de la descarga: nada de reusar el de la API. Bajar 11 MB
     * por una red mala necesita timeouts largos, y ese perfil no tiene por qué
     * contaminar las llamadas normales del API.
     */
    @Provides
    @Singleton
    fun provideApkCallFactory(): Call.Factory = OkHttpClient.Builder().build()

    @Provides
    @Singleton
    fun provideApkDownloader(callFactory: Call.Factory): ApkDownloader = ApkDownloader(callFactory)

    @Provides
    @Singleton
    fun provideUpdateDownloadScheduler(
        @ApplicationContext context: Context
    ): UpdateDownloadScheduler = UpdateDownloadScheduler(WorkManager.getInstance(context))
}
