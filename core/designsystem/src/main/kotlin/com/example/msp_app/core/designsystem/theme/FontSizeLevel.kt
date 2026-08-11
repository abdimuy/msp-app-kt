package com.example.msp_app.core.designsystem.theme

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Los 3 niveles discretos de tamaño de letra que la pantalla de Configuración
 * expondrá (spec
 * `docs/superpowers/specs/2026-08-10-configuracion-tamano-letra-design.md`
 * §"Decisiones" punto 3). [nominalScale] es el factor que la raíz de
 * composición (`app/`, trabajo futuro) usará para calcular el `fontScale`
 * efectivo de [androidx.compose.ui.platform.LocalDensity] —
 * `máx(nivel elegido en la app, fontScale del OS)` (Opción C del spec: la app
 * nunca achica por debajo de lo que el teléfono ya pide). También alimenta el
 * umbral de tier que ya existe hoy en el reporte de cobranza
 * (`com.example.msp_app.feature.collectionreport.ui.tier2.resolveTier`,
 * hardcodeado contra `fontScale` del SO): `MUY_GRANDE` (2.0f) cae claramente
 * sobre ese umbral, así que elegirlo en la app basta para forzar Tier 2 aunque
 * el OS esté en 1.0f — cablear esa raíz es trabajo de un agente posterior,
 * este enum solo deja el valor listo.
 */
enum class FontSizeLevel(val nominalScale: Float) {
    NORMAL(1.0f),
    GRANDE(1.5f),
    MUY_GRANDE(2.0f)
}

/**
 * Nivel de tamaño de letra elegido en la app — la raíz de composición
 * (`app/`, trabajo futuro) lo proveerá desde
 * `SettingsRepository.fontSizeLevel` (`:core:settings`). Default
 * [FontSizeLevel.NORMAL]: a diferencia de los `LocalMsp*` internos de
 * [MspTheme] (que exigen estar envueltos y truenan si no), este local es una
 * preferencia de usuario opcional — leerlo fuera de la raíz (un `@Preview`,
 * un test que no envuelve `CompositionLocalProvider`) debe degradar a
 * "Normal", no reventar.
 */
val LocalFontSizeLevel: ProvidableCompositionLocal<FontSizeLevel> =
    staticCompositionLocalOf { FontSizeLevel.NORMAL }

/**
 * Preferencia global "deshabilitar animaciones" (`reduce_motion` en
 * `:core:settings`, trabajo futuro) — provista en la raíz de composición para
 * que cualquier pantalla migrada la consulte sin acoplarse directamente a
 * `:core:settings` (spec §"Deshabilitar animaciones"). Default `false`:
 * animaciones activas salvo que la raíz provea explícitamente la preferencia
 * guardada.
 *
 * Distinto de [rememberReducedMotionEnabled] (mismo módulo, `MspMotion.kt`):
 * ese lee la señal de ACCESIBILIDAD del sistema operativo
 * (`Settings.Global.ANIMATOR_DURATION_SCALE == 0f`); este es la preferencia
 * PROPIA de la app, elegida en la pantalla de Configuración. Combinar ambas
 * señales (p.ej. `efectivo = os || app`) es trabajo de la raíz de composición
 * cuando cablee la pantalla — no de este módulo.
 */
val LocalReduceMotion: ProvidableCompositionLocal<Boolean> =
    staticCompositionLocalOf { false }
