package com.example.msp_app.core.mapas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.component.MspCard
import com.example.msp_app.core.designsystem.component.MspPrimaryFieldButton
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.mapas.domain.ATRIBUCION_DE_OSM
import com.example.msp_app.core.mapas.domain.EstadoDelExtracto
import com.example.msp_app.core.mapas.domain.ExtractoDeMapa
import com.example.msp_app.core.mapas.domain.megas

/** `testTag` del CTA que empieza la descarga. */
const val DESCARGAR_MAPA_TAG: String = "mapa_descargar"

/** `testTag` del CTA que cancela y borra. */
const val BORRAR_MAPA_TAG: String = "mapa_borrar"

/** `testTag` de la barra de avance. */
const val AVANCE_DEL_MAPA_TAG: String = "mapa_avance"

/**
 * **La pantalla que le dice la verdad al cobrador sobre los megas del mapa.**
 *
 * Misma forma que `DescargaDelDictadoScreen` de `:core:speech`, porque es la
 * misma conversación: **cuánto ocupa**, **cuándo se descarga** y **qué gana**.
 * Los tres salen del código —el peso, de [ExtractoDeMapa.tamanoBytes]; el "solo
 * con wifi", de la restricción `UNMETERED` que el planificador pone de verdad— y
 * ninguno está escrito a mano.
 *
 * Provee su propio [MspTheme] porque se abre desde un `NavHost` que no lo pone
 * (ver `CadaPantallaMspProveeSuTemaTest`).
 */
@Composable
fun DescargaDelMapaScreen(
    paquete: ExtractoDeMapa?,
    estado: EstadoDelExtracto,
    onDescargar: () -> Unit,
    onBorrar: () -> Unit,
    modifier: Modifier = Modifier
) {
    MspTheme {
        Box(modifier = modifier.fillMaxSize().background(MspTheme.colors.background)) {
            DescargaDelMapaContenido(paquete, estado, onDescargar, onBorrar)
        }
    }
}

/**
 * El cuerpo sin tema, que es lo que los goldens capturan: envolverlo dos veces
 * pintaría el tema de la captura sobre el de la pantalla y el golden dejaría de
 * decir qué color usa la pantalla de verdad.
 */
@Composable
fun DescargaDelMapaContenido(
    paquete: ExtractoDeMapa?,
    estado: EstadoDelExtracto,
    onDescargar: () -> Unit,
    onBorrar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(MspTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.md)
    ) {
        Text(
            text = "Mapa de la ruta",
            style = MspTheme.type.screenTitle,
            color = MspTheme.colors.onSurface
        )
        TarjetaDeLaOferta(paquete, estado, onDescargar, onBorrar)
        if (estado is EstadoDelExtracto.Descargando || estado is EstadoDelExtracto.EsperandoWifi) {
            TarjetaDelAvance(estado)
        }
        PieDeLosDosModos()
        CreditoDeLosDatos()
    }
}

@Composable
private fun TarjetaDeLaOferta(
    paquete: ExtractoDeMapa?,
    estado: EstadoDelExtracto,
    onDescargar: () -> Unit,
    onBorrar: () -> Unit
) {
    MspCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(MspTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
        ) {
            Text(
                text = "Las calles de la ruta",
                style = MspTheme.type.cardTitle,
                color = MspTheme.colors.onSurface
            )
            Text(
                text = "El valle de Tehuacán y el corredor a Ciudad Serdán, " +
                    "que es donde se cobra",
                style = MspTheme.type.caption,
                color = MspTheme.colors.onSurfaceMuted
            )
            if (paquete == null) {
                // Sin origen no hay botón. Ofrecer "Descargar" sobre una URL que
                // no existe sería un afordante que miente, y el cobrador
                // tocaría hasta cansarse.
                Hecho("Todavía no hay de dónde descargarlo")
            } else {
                // El peso sale del paquete. Un "25" escrito a mano se despegaría
                // del archivo en cuanto alguien regenere el extracto.
                Hecho("Ocupa ${megas(paquete.tamanoBytes)} MB en el teléfono")
                Hecho("Se descarga una vez, solo con wifi")
                Hecho("Después funciona sin señal, siempre")
            }
            // El aire ANTES del botón es mayor que el que separa dos hechos: el
            // botón es otro bloque, no el cuarto renglón de la lista. Sin botón
            // no hay aire que poner: el golden `sin_origen` mostró una franja
            // vacía al pie de la tarjeta, que se lee como algo que no cargó.
            if (hayBoton(paquete, estado)) {
                Spacer(modifier = Modifier.height(MspTheme.spacing.sm))
                Boton(paquete, estado, onDescargar, onBorrar)
            }
        }
    }
}

/**
 * ¿Hay algo que ofrecer? Sin origen publicado, no: un "Descargar" contra una URL
 * que no existe es un afordante que miente.
 */
private fun hayBoton(paquete: ExtractoDeMapa?, estado: EstadoDelExtracto): Boolean = when {
    estado is EstadoDelExtracto.Listo -> true
    paquete == null || estado is EstadoDelExtracto.SinOrigen -> false
    else -> estado is EstadoDelExtracto.Ausente || estado is EstadoDelExtracto.Interrumpido
}

@Composable
private fun Boton(
    paquete: ExtractoDeMapa?,
    estado: EstadoDelExtracto,
    onDescargar: () -> Unit,
    onBorrar: () -> Unit
) {
    when {
        estado is EstadoDelExtracto.Listo -> MspPrimaryFieldButton(
            text = "Borrar del teléfono",
            onClick = onBorrar,
            modifier = Modifier.fillMaxWidth().testTag(BORRAR_MAPA_TAG)
        )

        paquete == null || estado is EstadoDelExtracto.SinOrigen -> Unit

        estado is EstadoDelExtracto.Ausente || estado is EstadoDelExtracto.Interrumpido ->
            MspPrimaryFieldButton(
                // "Continuar" y no "Descargar" cuando ya hay megas bajados:
                // decirle "Descargar" a quien lleva 12 MB sugiere que los
                // pierde, y es justo lo que no pasa.
                text = if (estado is EstadoDelExtracto.Interrumpido) {
                    "Continuar la descarga"
                } else {
                    "Descargar"
                },
                onClick = onDescargar,
                modifier = Modifier.fillMaxWidth().testTag(DESCARGAR_MAPA_TAG)
            )

        else -> Unit // mientras baja, el CTA vive en la tarjeta de avance
    }
}

@Composable
private fun TarjetaDelAvance(estado: EstadoDelExtracto) {
    val descargando = estado as? EstadoDelExtracto.Descargando
    MspCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(MspTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
        ) {
            // APILADO, no en una fila: el golden del dictado a escala 2.0
            // destapó que "Descargando" y los megas en la misma fila se
            // encimaban. La misma tarjeta, la misma lección.
            Text(
                text = if (descargando != null) "Descargando" else "Esperando wifi",
                style = MspTheme.type.cardTitle,
                color = MspTheme.colors.onSurface
            )
            if (descargando != null) {
                Text(
                    text = descargando.avance.enMegas,
                    style = MspTheme.type.caption,
                    color = MspTheme.colors.onSurfaceMuted
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ALTO_DE_LA_BARRA)
                    .background(MspTheme.colors.progressTrack, MspTheme.shapes.chip)
                    .testTag(AVANCE_DEL_MAPA_TAG)
            ) {
                if (descargando != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(descargando.avance.fraccion)
                            .height(ALTO_DE_LA_BARRA)
                            .background(MspTheme.colors.brand, MspTheme.shapes.chip)
                    )
                }
            }
            Text(
                text = "Puedes seguir usando la app",
                style = MspTheme.type.caption,
                color = MspTheme.colors.onSurfaceMuted
            )
        }
    }
}

/**
 * El pie. **Nunca se quita**: es lo único que dice que la pantalla del cliente
 * sirve igual sin mapa.
 */
@Composable
private fun PieDeLosDosModos() {
    Column(verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)) {
        Text(
            text = "Sin mapa también sabes dónde es",
            style = MspTheme.type.bodyStrong,
            color = MspTheme.colors.onSurface
        )
        Text(
            text = "El pin sale del último cobro, no del mapa, y «cómo llegar» " +
                "abre la dirección escrita. Sin descargar, el cuadro queda liso: " +
                "no dibuja calles que nadie midió",
            style = MspTheme.type.caption,
            color = MspTheme.colors.onSurfaceMuted
        )
    }
}

/**
 * **El crédito de los datos, también acá.**
 *
 * En el bloque del mapa lo pinta [AtribucionDeOpenStreetMap] porque ahí se ven
 * los datos. Acá se repite —y repetir normalmente está prohibido (principio 5)—
 * porque ésta es la pantalla donde el cobrador decide bajar 25 MB, y de quién
 * son los datos que va a bajar es parte de la decisión. La licencia es lo que
 * hace que esto no necesite llave ni proveedor.
 */
@Composable
private fun CreditoDeLosDatos() {
    Text(
        text = "$ATRIBUCION_DE_OSM · datos abiertos bajo ODbL",
        style = MspTheme.type.caption,
        color = MspTheme.colors.onSurfaceMuted
    )
}

/** Un hecho de la lista, con su punto. */
@Composable
private fun Hecho(texto: String) {
    Row(
        modifier = Modifier.heightIn(min = MspTheme.spacing.lg),
        // Al TOPE y no al centro: a escala 2.0 el hecho ocupa dos renglones y un
        // punto centrado quedaría flotando entre los dos.
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        Box(
            modifier = Modifier
                .padding(top = MspTheme.spacing.sm)
                .size(PUNTO)
                .background(MspTheme.colors.brand, MspTheme.shapes.chip)
        )
        Text(
            text = texto,
            style = MspTheme.type.body,
            color = MspTheme.colors.onSurface
        )
    }
}

private val ALTO_DE_LA_BARRA = 6.dp

private val PUNTO = 6.dp
