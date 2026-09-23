package com.example.msp_app.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * **El tema oscuro elegido EN LA APP**, ya resuelto, o `null` si nadie lo
 * reportó.
 *
 * La app tiene tres modos (Claro / Oscuro / Automático, `ThemeController` de
 * `:app`) y el booleano resuelto de esos tres es lo que consume `MspappTheme`
 * para toda la app legada. Una pantalla Msp que se envuelve a sí misma con
 * `MspTheme { }` **no tenía forma de leerlo** desde un módulo de feature: caía
 * en el default `isSystemInDarkTheme()`, o sea el tema del SISTEMA OPERATIVO.
 * El resultado, medido en el emulador en las dos direcciones:
 *
 * - app en oscuro + SO en claro → **las siete pantallas de cobranza en blanco**,
 *   que es la combinación que un cobrador que trabaja de noche va a producir,
 *   porque el default de fábrica de la app es `LIGHT` y el ajuste que va a tocar
 *   es ponerla en `DARK`;
 * - app en claro + SO en oscuro → la pantalla en negro y **la barra de estado
 *   ilegible**, con el reloj oscuro sobre negro y los íconos perdidos, porque
 *   `MainActivity` fija `isAppearanceLightStatusBars` desde
 *   `ThemeController.statusBarAppearanceDark`, que sigue al tema de la APP.
 *
 * Este local cierra las dos: `MspTheme` lo lee por defecto (ver su KDoc), así que
 * toda pantalla Msp resuelve contra el tema de la app y la barra de estado
 * coincide **sin que nadie la reporte**.
 *
 * ## Por qué un local y no un puerto
 *
 * Los dos puertos de tema que ya existen —`AppThemePort` y `ReportThemePort`—
 * son de **lectura y escritura**, y existen porque esas pantallas **tienen
 * toggle propio** y necesitan escribir el estado global cruzando la frontera del
 * módulo. Estas solo leen un booleano ya resuelto. Replicar el patrón serían 3
 * archivos por módulo más el booleano viajando por el estado de siete
 * ViewModels, sus fakes y sus tests, para transportar un dato que no es de
 * dominio.
 *
 * ## Por qué esto NO es lo que prohíbe el ruling del composition root
 *
 * Lo prohibido es montar `MspTheme` en la raíz de `:app`, porque `MspTheme`
 * monta además `MaterialTheme(colorScheme, typography)` y **repintaría en
 * silencio toda la app legada**. Un `staticCompositionLocalOf<Boolean?>` no
 * monta nada y no pinta nada: es inerte para cualquier subárbol que no lo lea, y
 * los únicos que lo leen son los que ya llamaban a `MspTheme`. `MainActivity` ya
 * tiene el `CompositionLocalProvider` donde va, con otros cinco locals adentro.
 *
 * El default es `null` —y no `false`— para que un `@Preview`, un test de módulo
 * o cualquier host que no sea `:app` conserve exactamente el comportamiento de
 * antes (`isSystemInDarkTheme()`), sin que nadie tenga que proveerlo.
 */
val LocalAppDarkTheme = staticCompositionLocalOf<Boolean?> { null }

/**
 * El tema oscuro que debe usar una pantalla Msp: el de la app si alguien lo
 * reportó ([LocalAppDarkTheme]), y si no el del sistema operativo.
 */
@Composable
@ReadOnlyComposable
fun appDarkTheme(): Boolean = LocalAppDarkTheme.current ?: isSystemInDarkTheme()
