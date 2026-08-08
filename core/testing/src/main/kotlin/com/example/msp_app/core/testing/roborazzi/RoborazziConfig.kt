package com.example.msp_app.core.testing.roborazzi

/**
 * Configuración compartida de Roborazzi (screenshot testing) para todos los
 * módulos de la migración multi-módulo. Placeholder de Plan 1: solo fija las
 * opciones de comparación/umbral que Plan 3 va a consumir cuando agregue los
 * primeros goldens — este módulo no genera ni compara ninguna captura todavía.
 *
 * `changeThreshold` es la fracción de píxeles distintos (0.0–1.0) tolerada
 * antes de que Roborazzi marque un test como fallido por diferencia visual.
 */
object RoborazziConfig {
    const val CHANGE_THRESHOLD = 0.01f
}
