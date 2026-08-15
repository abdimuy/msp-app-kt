package com.example.msp_app.di

import com.example.msp_app.BuildConfig
import com.example.msp_app.core.appgate.AppBuildInfo
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provee a `:core:appgate` la única cosa que ese módulo no puede saber por sí
 * mismo: **qué APK es éste**. Un módulo de librería tiene su propio
 * `BuildConfig` (el de la librería, siempre vacío de `versionCode`), así que la
 * versión instalada y `DEBUG` solo los conoce la raíz de composición — mismo
 * criterio que [ConfiguracionThemeModule] con `AppThemePort`.
 *
 * `BuildConfig.DEBUG` es, además, la **exención de build** de la compuerta:
 * quien compila la app nunca queda atrapado por el bloqueo que está tocando
 * (ver `isVersionGateExempt`).
 */
@Module
@InstallIn(SingletonComponent::class)
object AppGateBuildInfoModule {

    @Provides
    @Singleton
    fun provideAppBuildInfo(): AppBuildInfo = AppBuildInfo(
        versionCode = BuildConfig.VERSION_CODE,
        versionName = BuildConfig.VERSION_NAME,
        debugBuild = BuildConfig.DEBUG
    )
}
