package com.example.msp_app.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/**
 * Los 3 modos de tema que la pantalla de Configuración expone (spec
 * `docs/superpowers/specs/2026-08-10-configuracion-tamano-letra-design.md`
 * §"Tema y privacidad"): Claro / Oscuro fijos, o Automático (sigue al tema del
 * sistema operativo). Antes de este modo, [ThemeController] solo conocía un
 * booleano [ThemeController.isDarkMode] — [themeMode] es la fuente de verdad
 * nueva; [isDarkMode] sigue existiendo como el booleano YA RESUELTO (para
 * `MODE.SYSTEM`, resuelto contra el `fontScale`/tema del sistema reportado por
 * la raíz de composición vía [ThemeController.updateSystemDarkMode]) — así
 * ningún llamador legacy (`DrawerContainer`, pantallas del paquete `features`) que
 * lea `ThemeController.isDarkMode` directo necesita cambiar.
 */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

object ThemeController {
    private const val PREFS_NAME = "theme_prefs"

    // Clave legacy (booleano) — se sigue escribiendo en paralelo a [KEY_THEME_MODE] por si
    // una versión vieja de la app (rollback) vuelve a leer esta clave; se sigue LEYENDO en
    // [init] solo como fallback de migración cuando [KEY_THEME_MODE] todavía no existe.
    private const val KEY_DARK_MODE = "dark_mode"
    private const val KEY_THEME_MODE = "theme_mode"

    private var prefsInitialized = false
    private lateinit var prefs: SharedPreferences

    /** Modo elegido en Configuración — persiste entre reinicios (ver [init]/[applyThemeMode]). */
    var themeMode: ThemeMode by mutableStateOf(ThemeMode.LIGHT)
        private set

    // Último `isSystemInDarkTheme()` reportado por la raíz de composición (`MainActivity`,
    // vía [updateSystemDarkMode]) — [ThemeController] es un objeto plano, no `@Composable`, así
    // que no puede leerlo por sí mismo; lo necesita para resolver [isDarkMode] cuando
    // [themeMode] == [ThemeMode.SYSTEM].
    private var systemDarkMode: Boolean = false

    /** Booleano YA RESUELTO (ver KDoc de [ThemeMode]) — lo que todo llamador legacy consume. */
    var isDarkMode by mutableStateOf(false)
        private set

    /**
     * Apariencia de los íconos de la barra de sistema (status/nav bar, ver
     * `MainActivity.onCreate` — `WindowInsetsControllerCompat.isAppearanceLight*`). Sigue a
     * [isDarkMode] por defecto (mismo flip que el resto de la app vía [toggle]). Cualquier
     * pantalla puede reportar su tema activo con [reportStatusBarAppearanceDark] sin pasar por
     * `MainActivity` directo — p. ej. el reporte de cobranza (`:feature:collectionReport`) lo
     * hace en cada cambio de `CollectionReportUiState.darkTheme` (que hoy espeja [isDarkMode]
     * vía `ReportThemePort`, ver KDoc de `CollectionReportViewModel.toggleTheme`; el reporte de
     * antes tenía un tema LOCAL desacoplado que podía divergir de [isDarkMode] — de ahí que este
     * mecanismo exista aparte de simplemente leer [isDarkMode] directo) — así los íconos del
     * sistema quedan correctos apenas la pantalla monta, sin esperar al próximo cambio de
     * [isDarkMode] (fix defecto visual: íconos de la barra de estado invisibles al entrar en
     * oscuro).
     */
    var statusBarAppearanceDark by mutableStateOf(false)
        private set

    fun init(context: Context) {
        if (prefsInitialized) return
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedMode = prefs.getString(KEY_THEME_MODE, null)
            ?.let { raw -> runCatching { ThemeMode.valueOf(raw) }.getOrNull() }
        // Migración: instalaciones previas a Configuración solo tenían el booleano legacy.
        themeMode = storedMode
            ?: if (prefs.getBoolean(KEY_DARK_MODE, false)) ThemeMode.DARK else ThemeMode.LIGHT
        isDarkMode = resolveDark()
        statusBarAppearanceDark = isDarkMode
        prefsInitialized = true
    }

    /** Cambia el modo de tema (Configuración, spec §"Tema y privacidad") y lo persiste. */
    fun applyThemeMode(mode: ThemeMode) {
        themeMode = mode
        isDarkMode = resolveDark()
        statusBarAppearanceDark = isDarkMode
        persist(mode)
    }

    /**
     * La raíz de composición (`MainActivity`) reporta aquí el `isSystemInDarkTheme()` vigente
     * en cada cambio (el sistema operativo cambió de tema, o la app recién arrancó) — solo
     * tiene efecto sobre [isDarkMode]/[statusBarAppearanceDark] cuando [themeMode] ==
     * [ThemeMode.SYSTEM]; en Claro/Oscuro fijos, el tema del sistema es irrelevante.
     */
    fun updateSystemDarkMode(systemDark: Boolean) {
        if (systemDarkMode == systemDark) return
        systemDarkMode = systemDark
        if (themeMode == ThemeMode.SYSTEM) {
            isDarkMode = resolveDark()
            statusBarAppearanceDark = isDarkMode
        }
    }

    /**
     * Toggle binario legacy (drawer, `IconButton` sol/luna) — sigue funcionando: alterna entre
     * Claro/Oscuro fijos según el [isDarkMode] YA RESUELTO. Tocarlo mientras [themeMode] ==
     * [ThemeMode.SYSTEM] "fija" el tema al opuesto del que el sistema mostraba en ese momento,
     * saliendo de Automático — mismo comportamiento intuitivo que cualquier toggle binario que
     * convive con un modo de 3 vías.
     */
    fun toggle() {
        applyThemeMode(if (isDarkMode) ThemeMode.LIGHT else ThemeMode.DARK)
    }

    /** Ver KDoc de [statusBarAppearanceDark]. */
    fun reportStatusBarAppearanceDark(dark: Boolean) {
        statusBarAppearanceDark = dark
    }

    private fun resolveDark(): Boolean = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDarkMode
    }

    private fun persist(mode: ThemeMode) {
        prefs.edit {
            putString(KEY_THEME_MODE, mode.name)
            // Clave legacy en paralelo (ver KDoc de KEY_DARK_MODE) — SYSTEM se guarda como el
            // booleano YA RESUELTO en el momento de guardar, no como un tercer valor (la clave
            // legacy no puede expresar "automático").
            putBoolean(
                KEY_DARK_MODE,
                mode == ThemeMode.DARK || (mode == ThemeMode.SYSTEM && isDarkMode)
            )
        }
    }
}
