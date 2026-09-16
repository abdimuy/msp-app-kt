package com.example.msp_app.core.mapas.ui

/**
 * Las rutas de `:core:mapas`.
 *
 * **Sólo la cadena, sin `NavGraphBuilder`** — por la misma razón que
 * `DictadoRutas` en `:core:speech`: el módulo no depende de
 * `androidx.navigation.compose`, `:app` registra el destino (como ya hace con
 * `VersionBlockedScreen` de `:core:appgate`) y `:feature:configuracion` navega
 * hacia él. La cadena la comparten los tres, escrita una sola vez.
 *
 * Vive en su propio archivo, **separada de [DescargaDelMapaConectada]**: ver el
 * KDoc de `DictadoRutas`, donde está escrito por qué juntarlas ablandaría
 * `CadaPantallaSeAlcanzaDesdeElGrafoTest`.
 */
object MapasRutas {

    /** La pantalla que decide si se bajan los megas del extracto de la ruta. */
    const val DESCARGA: String = "mapa/descarga"
}
