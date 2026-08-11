package com.example.msp_app.core.settings

import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import kotlinx.coroutines.flow.Flow

/**
 * Persistencia cross-cutting de las 3 preferencias que la pantalla de
 * Configuración expone (spec
 * `docs/superpowers/specs/2026-08-10-configuracion-tamano-letra-design.md`
 * §"`:core:settings` (nuevo)"). Vive en `:core:settings` — no en
 * `:feature:configuracion` — para que la raíz de composición (`app/`, para el
 * override de `LocalDensity`) y cualquier otra pantalla que enmascare cifras
 * puedan depender de esto sin depender de la pantalla.
 *
 * El tema NO vive aquí: `ThemeController` (existente) sigue siendo la fuente
 * de verdad para `isDarkMode` (ver spec §"Tema y privacidad" — persistirlo
 * acá solo si `ThemeController` resulta no persistir entre reinicios, decisión
 * de un agente posterior).
 *
 * Todos los `Flow` son "robustos ante valores corruptos": un valor guardado
 * que no se puede interpretar (enum desconocido, tipo incorrecto) cae al
 * default, nunca lanza — ver [DataStoreSettingsRepository] para el mecanismo.
 */
interface SettingsRepository {
    /** Nivel de tamaño de letra elegido en la app. Default [FontSizeLevel.NORMAL]. */
    val fontSizeLevel: Flow<FontSizeLevel>

    suspend fun setFontSizeLevel(level: FontSizeLevel)

    /** Preferencia global "ocultar cifras" (ojo de privacidad). Default `false`. */
    val privacyMasked: Flow<Boolean>

    suspend fun setPrivacyMasked(masked: Boolean)

    /** Preferencia global "deshabilitar animaciones" (reduce-motion). Default `false`. */
    val reduceMotion: Flow<Boolean>

    suspend fun setReduceMotion(enabled: Boolean)
}
