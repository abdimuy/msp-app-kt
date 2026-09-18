package com.example.msp_app.data.speech

import com.example.msp_app.core.speech.domain.port.TemaDeLaAppPort
import com.example.msp_app.ui.theme.ThemeController

/**
 * Implementación real de [TemaDeLaAppPort] (`:core:speech`), cableada por
 * `SpeechThemeModule` en `:app` — calcada de
 * [com.example.msp_app.data.pagos.ThemeControllerTemaDeLaAppAdapter] y de
 * [com.example.msp_app.data.collectionreport.ThemeControllerReportThemePort],
 * que hacen exactamente esto para sus módulos: el tema real de la app
 * (`ThemeController`) vive en `:app`, así que el módulo cruza la frontera hacia
 * él en vez de importarlo.
 *
 * **No expone el estado del tema y eso es el puerto, no un olvido.** El de
 * `:feature:pagos` puentea `ThemeController.isDarkMode` con `snapshotFlow`
 * porque su encabezado pinta el glifo sol/luna; la pantalla de descarga del
 * dictado todavía no lo pinta, así que este adaptador no tiene ni `Flow` ni
 * `CoroutineScope` que sostener.
 *
 * `ThemeController.toggle()` resuelve y persiste — el objeto ya es el singleton
 * real, así que este adaptador no guarda nada propio.
 */
class ThemeControllerTemaDeLaAppAdapter : TemaDeLaAppPort {

    override fun alternar() {
        ThemeController.toggle()
    }
}
