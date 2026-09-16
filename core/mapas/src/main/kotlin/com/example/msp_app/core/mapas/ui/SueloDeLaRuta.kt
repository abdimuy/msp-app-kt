package com.example.msp_app.core.mapas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.mapas.domain.MapaDeLaRuta
import com.example.msp_app.core.mapas.domain.PuntoDelMapa

/** `testTag` del suelo liso — el que se pinta cuando no hay extracto. */
const val SUELO_LISO_TAG: String = "mapa_suelo_liso"

/** `testTag` del lienzo del mapa. Lo lleva el suelo, no MapLibre. */
const val LIENZO_TAG: String = "mapa_lienzo"

/**
 * **El suelo del bloque de mapa: o el mapa de verdad, o nada inventado.**
 *
 * ## La costura, y por qué está justo acá
 *
 * [lienzo] es lo único de esta función que necesita GL. Todo lo demás —el suelo
 * liso, la atribución, la decisión de cuál de los dos se pinta— es Compose puro
 * y se fotografía determinista en Robolectric. Un golden que incluyera a
 * MapLibre no podría existir: Robolectric no tiene GL, así que o el test no
 * corre o corre pintando un hueco, y un verde que no mira nada es peor que un
 * rojo.
 *
 * Cortar acá deja del lado probado **las tres cosas que pueden estar mal**:
 * que sin extracto se dibuje algo, que con extracto falte la atribución, y que
 * el bloque cambie de tamaño entre los dos estados. Del lado no probado queda
 * una sola: cómo se ven los píxeles de las calles, que ningún test de JVM podría
 * haber visto de todas formas.
 *
 * ## Sin extracto no se inventa nada
 *
 * El suelo liso **no es un mapa degradado, es la ausencia de mapa dicha con
 * honestidad**. No dibuja calles, ni una retícula, ni un patrón que se pueda
 * confundir con la traza de la colonia. Un cobrador mirando una retícula
 * inventada creería estar viendo la manzana, y el dato falso es peor que el dato
 * ausente.
 *
 * Y **sin atribución**: donde no se muestran datos de OpenStreetMap no hay nada
 * que atribuir, y un crédito sobre un rectángulo liso sería ruido.
 *
 * ## El pin SÍ se pinta acá, y ésa fue una corrección
 *
 * La cámara se centra en [punto] y los gestos están apagados, así que **el
 * centro de esta caja ES el punto medido**. Por eso el pin se dibuja acá,
 * centrado: la coincidencia queda garantizada por la geometría y no por que dos
 * módulos mantengan la misma aritmética. La primera versión lo dejaba en manos
 * de `:feature:pagos`, que lo centra en su banda superior, y el golden midió
 * **21 dp de error** — a zoom 17, unos 24 metros.
 *
 * ## [lienzo] no tiene valor por omisión, a propósito
 *
 * Un default `{ m, p -> LienzoDeMapLibre(m, p) }` habría obligado a que el
 * lienzo real se trague sus propios fallos —no hay de dónde sacar `Telemetry`
 * dentro de un parámetro por omisión—, y la norma de errores prohíbe
 * exactamente eso. Sin default, quien arma el lienzo es [SueloDeLaRutaConectado],
 * que sí tiene el puerto de telemetría a mano.
 */
@Composable
fun SueloDeLaRuta(
    mapa: MapaDeLaRuta?,
    punto: PuntoDelMapa?,
    lienzo: @Composable (MapaDeLaRuta, PuntoDelMapa?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (mapa == null) {
        SueloLiso(modifier)
        return
    }
    Box(modifier = modifier.fillMaxSize().testTag(LIENZO_TAG)) {
        lienzo(mapa, punto)
        // El pin va al CENTRO EXACTO de esta caja, que es donde la cámara puso
        // el punto. Ver el KDoc de [PinDelMapa]: la primera versión lo pintaba
        // la feature en su banda superior y quedaba 21 dp arriba, o sea ~24 m.
        if (punto != null) {
            PinDelMapa(modifier = Modifier.align(Alignment.Center))
        }
        // Arriba a la izquierda: el pin está en el centro, la pastilla de
        // contexto abajo a la izquierda y "cómo llegar" abajo a la derecha. Es
        // la única esquina libre, y la atribución tiene que caber SIEMPRE.
        AtribucionDeOpenStreetMap(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(MspTheme.spacing.xs)
        )
    }
}

/**
 * El suelo mientras no hay extracto: liso, del color del tema, sin calles
 * inventadas. Es el mismo `surface2` que el estilo del mapa usa para la tierra,
 * así que el bloque no cambia de fondo cuando el mapa aparece.
 */
@Composable
private fun SueloLiso(modifier: Modifier = Modifier) {
    // El fondo va en un modificador INTERNO, no encadenado al `modifier` que
    // entra. No es cuestión de estilo:
    // `CadaPantallaDeCobranzaRespetaLaBarraDeEstadoTest` reconoce "la raíz de una
    // pantalla" por un `fillMaxSize()` + `background(MspTheme.colors.…)`
    // encadenados sobre el modifier entrante, y le exige consumir el inset de la
    // barra de estado. Este bloque mide 130 dp y no es una pantalla: escribirlo
    // así deja la guarda estricta en vez de agregarle una excepción.
    Box(modifier = modifier.fillMaxSize().testTag(SUELO_LISO_TAG)) {
        Box(Modifier.fillMaxSize().background(MspTheme.colors.surface2))
    }
}
