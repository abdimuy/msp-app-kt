package com.example.msp_app.data.visitas

import com.example.msp_app.feature.visitas.domain.port.TemaDeLaAppPort
import com.example.msp_app.ui.theme.ThemeController

/**
 * Implementación real de [TemaDeLaAppPort] (`:feature:visitas`), cableada por
 * `VisitasPortsModule` en `:app` — calcada de
 * [com.example.msp_app.data.pagos.ThemeControllerTemaDeLaAppAdapter] y de
 * [com.example.msp_app.data.collectionreport.ThemeControllerReportThemePort],
 * que hacen exactamente esto para sus módulos: el tema real de la app
 * (`ThemeController`) vive en `:app`, así que el feature cruza la frontera del
 * módulo hacia él.
 *
 * **No expone el estado del tema y eso es el puerto, no un olvido.** El de
 * `:feature:pagos` puentea `ThemeController.isDarkMode` con `snapshotFlow`
 * porque su encabezado pinta el glifo sol/luna; las pantallas de visita todavía
 * no lo pintan, así que este adaptador no tiene ni `Flow` ni `CoroutineScope`
 * que sostener.
 *
 * `ThemeController.toggle()` resuelve y persiste — el objeto ya es el singleton
 * real, así que este adaptador no guarda nada propio.
 */
class ThemeControllerTemaDeLaAppAdapter : TemaDeLaAppPort {

    override fun alternar() {
        ThemeController.toggle()
    }
}
