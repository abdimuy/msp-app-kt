package com.example.msp_app.core.speech.ui

/**
 * Las rutas de `:core:speech`.
 *
 * **Sólo la cadena, sin `NavGraphBuilder`.** Este módulo no depende de
 * `androidx.navigation.compose` y no tiene por qué: el precedente de un
 * `:core:*` con pantalla es `:core:appgate`, cuya `VersionBlockedScreen` la
 * monta `:app`. Lo que sí tiene que ser compartido es **la cadena**, porque la
 * escribe `:app` al registrar el destino y la lee `:feature:configuracion` al
 * navegar: una cadena repetida en dos módulos es un destino que no resuelve
 * esperando a que alguien renombre uno de los dos.
 *
 * Vive en su propio archivo, **separada de [DescargaDelDictadoConectada]**, y no
 * es cosmética: `CadaPantallaSeAlcanzaDesdeElGrafoTest` recorre el grafo de
 * "quién nombra a quién" por ARCHIVO, así que juntarlas haría que la cadena
 * —que Configuración nombra siempre— arrastrara al composable cableado al
 * conjunto alcanzable aunque nadie lo montara. O sea: la pantalla volvería a
 * poder quedarse sin puerta con el test en verde.
 */
object DictadoRutas {

    /** La pantalla que decide si se bajan los megas del modelo de alta fidelidad. */
    const val DESCARGA: String = "dictado/descarga"
}
