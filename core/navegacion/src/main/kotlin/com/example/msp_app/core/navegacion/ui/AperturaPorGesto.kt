package com.example.msp_app.core.navegacion.ui

/** Cómo se abre el menú con el dedo. La hamburguesa abre siempre, en los dos modos. */
enum class AperturaPorGesto {
    /** Comportamiento de hoy: `ModalNavigationDrawer` con gestos en toda la pantalla. */
    DESLIZAR_EN_TODA_LA_PANTALLA,

    /** Pantallas con flecha (spec §2.2.1): sólo desde la franja superior; el borde es "atrás". */
    DESDE_LA_BARRA
}
