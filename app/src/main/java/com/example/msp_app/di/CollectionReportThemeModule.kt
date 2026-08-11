package com.example.msp_app.di

import com.example.msp_app.data.collectionreport.ThemeControllerReportThemePort
import com.example.msp_app.feature.collectionreport.domain.port.ReportThemePort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Cablea el puerto [ReportThemePort] del reporte de cobranza a su implementación real
 * [ThemeControllerReportThemePort]. Vive en `:app` —no en `:feature:collectionReport`— por la
 * misma razón que [CollectionReportUserCycleModule] cablea `UserCyclePort` ahí y
 * [ConfiguracionThemeModule] cablea `AppThemePort`: `ThemeController` es de `:app`, fuera del
 * alcance de cualquier módulo feature.
 *
 * SIN `@Singleton` a propósito (mismo criterio que [ConfiguracionThemeModule]):
 * [ThemeControllerReportThemePort] no sostiene ningún estado propio (delega TODO en el objeto
 * `ThemeController`, que ya es el singleton real) — instanciarlo una vez por inyección es tan
 * barato como memoizarlo.
 */
@Module
@InstallIn(SingletonComponent::class)
object CollectionReportThemeModule {

    @Provides
    fun provideReportThemePort(): ReportThemePort = ThemeControllerReportThemePort()
}
