package com.example.msp_app.feature.configuracion.domain.port

import kotlinx.coroutines.flow.Flow

/**
 * Los 3 modos de tema que la sección "Apariencia" de Configuración expone
 * (spec `docs/superpowers/specs/2026-08-10-configuracion-tamano-letra-design.md`
 * §"Tema y privacidad"): Claro / Automático (sigue al sistema) / Oscuro.
 * Espejo del `ThemeMode` real (`:app`, `ThemeController`) — este módulo no
 * puede importar `:app` (dirección de dependencia invertida), así que
 * [AppThemePort] es la frontera: [com.example.msp_app.feature.configuracion.ui.ConfiguracionViewModel]
 * solo conoce este enum, nunca `ThemeController`.
 */
enum class AppThemeMode { LIGHT, DARK, SYSTEM }

/**
 * Puerto hacia el tema REAL de la app (`ThemeController`, `:app`) — mismo
 * patrón que `UserCyclePort` del reporte de cobranza (interfaz aquí, adapter
 * real vive en `:app`, cableado por un `@Module` de `:app`, ver
 * `CollectionReportUserCycleModule`/`FirebaseUserCycleAdapter`): la pantalla
 * de Configuración togglea el tema GLOBAL de la app, no un espejo local, así
 * que necesita cruzar la frontera del módulo hacia la implementación real.
 */
interface AppThemePort {
    /** Modo de tema vigente — reactivo, refleja cualquier cambio (esta pantalla u otra). */
    val themeMode: Flow<AppThemeMode>

    /** Lectura síncrona del modo vigente — usada para sembrar el estado inicial del ViewModel. */
    fun currentThemeMode(): AppThemeMode

    /** Cambia el modo de tema global de la app (persistido por la implementación real). */
    fun setThemeMode(mode: AppThemeMode)
}
