package com.example.msp_app.di

import com.example.msp_app.data.configuracion.ThemeControllerAppThemePort
import com.example.msp_app.feature.configuracion.domain.port.AppThemePort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Cablea el puerto [AppThemePort] de la pantalla de Configuración a su implementación real
 * [ThemeControllerAppThemePort]. Vive en `:app` —no en `:feature:configuracion`— por la misma
 * razón que [CollectionReportUserCycleModule] cablea `UserCyclePort` ahí: `ThemeController` es
 * de `:app`, fuera del alcance del módulo feature.
 *
 * SIN `@Singleton` a propósito: [ThemeControllerAppThemePort] no sostiene ningún estado propio
 * (delega TODO en el objeto `ThemeController`, que ya es el singleton real) — instanciarlo una
 * vez por inyección es tan barato como memoizarlo, y evita declarar una vida útil que no aporta
 * nada.
 */
@Module
@InstallIn(SingletonComponent::class)
object ConfiguracionThemeModule {

    @Provides
    fun provideAppThemePort(): AppThemePort = ThemeControllerAppThemePort()
}
