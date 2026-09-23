package com.example.msp_app.di

import com.example.msp_app.core.speech.domain.port.TemaDeLaAppPort
import com.example.msp_app.data.speech.ThemeControllerTemaDeLaAppAdapter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Cablea el [TemaDeLaAppPort] de `:core:speech` a su implementación real
 * [ThemeControllerTemaDeLaAppAdapter].
 *
 * **Módulo nuevo y no una línea en `SpeechModule`** porque `SpeechModule` vive
 * DENTRO de `:core:speech` y es `internal`: no puede ver `ThemeController`, que
 * es de `:app`. Es la misma frontera que ya obligó a [CollectionReportThemeModule]
 * y a [ConfiguracionThemeModule] a existir aquí en vez de dentro de su feature.
 *
 * No lo pide un botón sol/luna —la pantalla de descarga no lo pinta todavía—
 * sino `MspThemeRevealHost`, que necesita poder pedir el flip DESPUÉS de haber
 * grabado el frame viejo.
 *
 * SIN `@Singleton`, mismo criterio que sus tres vecinos de tema:
 * [ThemeControllerTemaDeLaAppAdapter] no sostiene estado propio (delega TODO en
 * el objeto `ThemeController`, que ya es el singleton real).
 */
@Module
@InstallIn(SingletonComponent::class)
object SpeechThemeModule {

    @Provides
    fun provideTemaDeLaAppPort(): TemaDeLaAppPort = ThemeControllerTemaDeLaAppAdapter()
}
