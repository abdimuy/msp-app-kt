package com.example.msp_app.core.speech.ui

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
import com.example.msp_app.core.speech.domain.EstadoDelModelo
import com.example.msp_app.core.speech.domain.ModeloDeDictado
import com.example.msp_app.core.speech.domain.megas

/** `testTag` del CTA que empieza la descarga. */
const val DESCARGAR_TAG: String = "dictado_descargar"

/** `testTag` del CTA que cancela y borra. */
const val BORRAR_MODELO_TAG: String = "dictado_borrar_modelo"

/** `testTag` de la barra de avance. */
const val AVANCE_TAG: String = "dictado_avance"

/**
 * **La pantalla que le dice la verdad al cobrador sobre los megas.**
 *
 * Tres hechos y ninguno escondido: **cuánto ocupa**, **cuándo se descarga** y
 * **qué gana**. Los tres salen del código —el peso, de
 * [ModeloDeDictado.tamanoBytes]; el "solo con wifi", de la restricción
 * `UNMETERED` que el planificador pone de verdad— y ninguno está escrito a
 * mano.
 *
 * ## El pie que dice que sin descargar también se dicta
 *
 * Es la parte del mock que más importa y la más fácil de borrar por parecer
 * "texto de relleno": sin ella, la pantalla sugiere que el dictado **depende**
 * de bajar 43.5 MB, y un cobrador con datos contados decidiría que la app no
 * dicta. La app vive en dos modos a la vez y esta pantalla tiene que decirlo.
 *
 * Provee su propio [MspTheme] porque se abre desde el `NavHost` de `:app`, que
 * no lo pone (ver `CadaPantallaMspProveeSuTemaTest`).
 */
@Composable
fun DescargaDelDictadoScreen(
    modelo: ModeloDeDictado,
    estado: EstadoDelModelo,
    onDescargar: () -> Unit,
    onBorrar: () -> Unit,
    modifier: Modifier = Modifier
) {
    MspTheme {
        Box(modifier = modifier.fillMaxSize().background(MspTheme.colors.background)) {
            DescargaDelDictadoContenido(modelo, estado, onDescargar, onBorrar)
        }
    }
}

/**
 * El cuerpo sin tema, que es lo que los goldens capturan: envolverlo dos veces
 * pintaría el tema de la captura sobre el de la pantalla y el golden dejaría de
 * decir qué color usa la pantalla de verdad.
 */
@Composable
fun DescargaDelDictadoContenido(
    modelo: ModeloDeDictado,
    estado: EstadoDelModelo,
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
            text = "Dictado por voz",
            style = MspTheme.type.screenTitle,
            color = MspTheme.colors.onSurface
        )
        TarjetaDeLaOferta(modelo, estado, onDescargar, onBorrar)
        if (estado is EstadoDelModelo.Descargando || estado is EstadoDelModelo.EsperandoWifi) {
            TarjetaDelAvance(estado)
        }
        PieDeLosDosModos()
    }
}

@Composable
private fun TarjetaDeLaOferta(
    modelo: ModeloDeDictado,
    estado: EstadoDelModelo,
    onDescargar: () -> Unit,
    onBorrar: () -> Unit
) {
    MspCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(MspTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
        ) {
            Text(
                text = "Alta fidelidad",
                style = MspTheme.type.cardTitle,
                color = MspTheme.colors.onSurface
            )
            Text(
                text = "Entiende mejor el habla de la calle, los nombres y los apodos",
                style = MspTheme.type.caption,
                color = MspTheme.colors.onSurfaceMuted
            )
            // El peso sale del modelo. Un "44 MB" escrito a mano se despegaría
            // del archivo el día que se cambie de `tiny` a `base`, y ésta es la
            // pantalla donde ese número no se puede equivocar.
            Hecho("Ocupa ${megas(modelo.tamanoBytes)} MB en el teléfono")
            Hecho("Se descarga una vez, solo con wifi")
            Hecho("Después funciona sin señal, siempre")
            // El aire ANTES del botón es mayor que el que separa dos hechos:
            // el botón es otro bloque, no el cuarto renglón de la lista
            // (principio 3).
            Spacer(modifier = Modifier.height(MspTheme.spacing.sm))
            when (estado) {
                EstadoDelModelo.Listo -> MspPrimaryFieldButton(
                    text = "Borrar del teléfono",
                    onClick = onBorrar,
                    modifier = Modifier.fillMaxWidth().testTag(BORRAR_MODELO_TAG)
                )

                EstadoDelModelo.Ausente, is EstadoDelModelo.Interrumpido -> MspPrimaryFieldButton(
                    // "Continuar" y no "Descargar" cuando ya hay megas bajados:
                    // decirle "Descargar" a quien lleva 20 MB sugiere que los
                    // pierde, y es justo lo que no pasa.
                    text = if (estado is EstadoDelModelo.Interrumpido) {
                        "Continuar la descarga"
                    } else {
                        "Descargar"
                    },
                    onClick = onDescargar,
                    modifier = Modifier.fillMaxWidth().testTag(DESCARGAR_TAG)
                )

                else -> Unit // mientras baja, el CTA vive en la tarjeta de avance
            }
        }
    }
}

@Composable
private fun TarjetaDelAvance(estado: EstadoDelModelo) {
    val descargando = estado as? EstadoDelModelo.Descargando
    MspCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(MspTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
        ) {
            // APILADO, no en una fila. El golden a escala 2.0 destapó por qué:
            // con el título y los megas en la misma fila, "Descargando" se
            // partía en "Descargan / do" y el "27 de 43.5 MB" se le encimaba.
            // Principio 9 — antes de truncar (o de encimar), apilar.
            Text(
                // "Esperando wifi" es la verdad hasta que el sistema arranque
                // el trabajo. Pintar "Descargando" con la barra clavada en cero
                // es lo que termina en una llamada por teléfono.
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
                    .testTag(AVANCE_TAG)
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
 * El pie. **Nunca se quita**: es lo único que dice que el dictado existe sin
 * bajar nada.
 */
@Composable
private fun PieDeLosDosModos() {
    Column(verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)) {
        Text(
            text = "Sin descargar también puedes dictar",
            style = MspTheme.type.bodyStrong,
            color = MspTheme.colors.onSurface
        )
        Text(
            text = "La app usa el reconocimiento que ya trae el teléfono. " +
                "Es más rápido y no ocupa espacio, pero se equivoca más con los apodos",
            style = MspTheme.type.caption,
            color = MspTheme.colors.onSurfaceMuted
        )
    }
}

/** Un hecho de la lista, con su punto. Tres, y los tres caben en un renglón. */
@Composable
private fun Hecho(texto: String) {
    Row(
        modifier = Modifier.heightIn(min = MspTheme.spacing.lg),
        // Al TOPE y no al centro: a escala 2.0 el hecho ocupa dos renglones y
        // un punto centrado quedaba flotando entre los dos, como si no fuera de
        // ninguno. Un bullet se alinea con su primer renglón.
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
