package com.example.msp_app.feature.collectionreport.domain.port

import kotlinx.coroutines.flow.Flow

/**
 * Puerto hacia el tema GLOBAL de la app (`ThemeController`, `:app`) — mismo patrón que
 * [UserCyclePort] (interfaz aquí, adapter real en `:app`, cableado por un `@Module` de `:app`,
 * ver `CollectionReportUserCycleModule`/`FirebaseUserCycleAdapter`) y que
 * `feature.configuracion.domain.port.AppThemePort` (misma necesidad, otro feature — no se
 * reusa ese puerto aquí porque `:feature:collectionReport` no depende de
 * `:feature:configuracion`, ni debería: cada feature cruza la frontera hacia `:app` por su
 * cuenta, mismo criterio que ya separa `UserCyclePort` de cualquier otro puerto de sesión).
 *
 * Fix del bug "el tema del reporte se resetea al navegar": antes de este puerto,
 * `CollectionReportUiState.darkTheme` era un espejo LOCAL (`CollectionReportViewModel.toggleTheme`
 * solo flippeaba el `StateFlow` propio) desacoplado de `ThemeController` — por diseño en su
 * momento (ver historial de `ThemeController.statusBarAppearanceDark`), pero eso significa que
 * salir y volver a entrar al reporte, o matar el proceso, siempre reiniciaba a claro. Este
 * puerto conecta el reporte al tema REAL y persistido de la app.
 */
interface ReportThemePort {

    /**
     * Tema oscuro vigente — reactivo, refleja cualquier cambio (el toggle del reporte, el de
     * Configuración, o el sistema operativo si el modo global es Automático). YA RESUELTO:
     * la implementación real resuelve `ThemeMode.SYSTEM` contra el `isSystemInDarkTheme()`
     * vigente antes de emitir, así este puerto nunca expone el enum de 3 vías, solo el
     * booleano final que la UI necesita.
     */
    val isDark: Flow<Boolean>

    /** Lectura síncrona del tema vigente — usada para sembrar el estado inicial del ViewModel. */
    fun currentIsDark(): Boolean

    /**
     * Alterna el tema GLOBAL de la app (persistido por la implementación real) — equivalente
     * al toggle binario legado (Claro ⇄ Oscuro fijos), consistente con cualquier otro toggle
     * de tema de la app (drawer, `IconButton` sol/luna).
     */
    fun toggle()
}
