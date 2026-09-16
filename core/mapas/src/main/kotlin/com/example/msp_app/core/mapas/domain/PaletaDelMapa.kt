package com.example.msp_app.core.mapas.domain

/**
 * Los colores del mapa, en hexadecimal `#RRGGBB`.
 *
 * Los arma `ui/` a partir de `MspTheme.colors` — el mapa **no tiene paleta
 * propia**. Una segunda tabla de colores al lado del design system se
 * desincroniza el día que alguien cambie el tema, y el mapa quedaría siendo la
 * única superficie de la app que no obedece al toggle de oscuro.
 */
data class PaletaDelMapa(
    val tierra: String,
    val suelo: String,
    val agua: String,
    val edificios: String,
    val calles: String
)
