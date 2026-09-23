package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.ui.AccionesIconos

/** `testTag` de la hoja que pregunta qué abrir al tocar un cobro de hoy. */
const val HOJA_DEL_CONTACTO_TAG: String = "pagos_hoja_contacto"

/** `testTag` del velo de esa hoja. */
const val VELO_DEL_CONTACTO_TAG: String = "pagos_hoja_contacto_velo"

/** `testTag` de la opción que abre el mapa. */
const val OPCION_UBICACION_TAG: String = "pagos_hoja_contacto_ubicacion"

/** `testTag` de la opción que abre el ticket. */
const val OPCION_TICKET_TAG: String = "pagos_hoja_contacto_ticket"

/** Título de la hoja. Pregunta qué abrir, no qué hacer: las dos opciones sólo muestran. */
const val QUE_ABRIR: String = "¿Qué abrir?"

/** El pie del título: por qué esta fila pregunta y las otras no. */
const val EL_ULTIMO_COBRO_DE_HOY: String = "El último cobro de hoy"

/** La opción del mapa — lo que el toque hacía antes, ahora dicho con su nombre. */
const val VER_UBICACION: String = "Ubicación"

/**
 * La opción del papel. "Ticket" y no "Reimprimir": quién decide si el papel sale
 * es la pantalla del ticket, con la regla del día que ya está tomada — abrir
 * siempre se puede, imprimir no.
 */
const val VER_TICKET: String = "Ticket"

/**
 * **¿Qué quieres abrir: la ubicación o el ticket?**
 *
 * Sale sólo sobre el renglón donde la reimpresión tiene sentido —el cobro de hoy
 * que además es el último de esa cuenta—, y quién lo decide no es esta pieza
 * sino [com.example.msp_app.feature.pagos.domain.ToqueDelContacto], que es
 * dominio puro y se prueba sin pantalla. Ver su KDoc para el porqué de la
 * condición.
 *
 * ## Por qué una hoja y no un diálogo
 *
 * Porque es lo que el módulo ya usa para elegir entre varias cosas:
 * [HojaDeAbono] pregunta *"¿a cuál cuenta?"* con esta misma forma —velo que
 * consume el toque, hoja pegada abajo, opciones de alto tocable—. Dos formas de
 * preguntar en la misma pantalla serían dos gramáticas para el mismo gesto.
 *
 * ## Sin "Continuar", al revés que [HojaDeAbono]
 *
 * Aquélla confirma **a dónde entra dinero** y por eso separa elegir de
 * continuar: un radio mal tocado sería un abono en la cuenta equivocada. Aquí
 * las dos opciones sólo **abren una pantalla**; un segundo toque para confirmar
 * lo que ya se puede deshacer con la flecha de volver es peaje, no seguridad.
 *
 * ## El `navigationBarsPadding()` no es opcional
 *
 * Misma trampa que [HojaDeAbono]: la hoja arranca pegada al borde de abajo y se
 * invoca FUERA del `Column` con `systemBarsPadding()` de la pantalla, así que
 * sin este inset la última opción queda debajo de la barra de navegación de
 * Android y su toque **no entra al proceso**. Va DESPUÉS del `background` —como
 * `BlurredActionBar.kt:126`— para que suba el contenido y no el fondo.
 */
@Composable
fun HojaDelContacto(
    onVerUbicacion: () -> Unit,
    onVerTicket: () -> Unit,
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(VELO)
                .pointerInput(Unit) { detectTapGestures { onCerrar() } }
                .testTag(VELO_DEL_CONTACTO_TAG)
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // `background(color, shape)` y NO `clip(shape) + background`:
                // con la forma de esquinas DESIGUALES, el `clip` manda el
                // hit-test por `Outline.Rounded` -> `isInPath` -> `Path.op`, que
                // sin gráficos nativos deja los toques de los hijos en cero. Es
                // el mismo hallazgo medido que documenta `HojaDeConfirmacion`, y
                // aquí se midió otra vez: con el `clip`, tocar "Ticket" no
                // llamaba a nadie.
                .background(MspTheme.colors.surface, FORMA_DE_LA_HOJA)
                // Un `pointerInput` INERTE en vez de un `detectTapGestures` vacío:
                // sólo hace que la hoja sea alcanzable por el hit-test, y con eso
                // ningún toque se cuela a la pantalla que quedó debajo.
                //
                // **No es lo que arregla el toque de las opciones** — eso lo hace
                // el `background(color, shape)` de arriba. Se midió aparte, sobre
                // `HojaDeAbono` (`LaHojaDelAbonoDejaElegirCuentaTest`): un
                // `detectTapGestures {}` vacío en este mismo lugar NO se come el
                // toque de los hijos. El precedente de `HojaDeConfirmacion` —"un
                // gesto de padre le gana a sus descendientes"— es real pero
                // describe otra cosa: allá el gesto vivía en el VELO, que
                // envolvía a la hoja y tenía un `onTap` de verdad. Se escribe así
                // para que nadie deduzca de aquí una regla que no se sostiene.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent()
                        }
                    }
                }
                .navigationBarsPadding()
                .padding(MspTheme.spacing.md)
                .testTag(HOJA_DEL_CONTACTO_TAG)
        ) {
            Text(
                text = QUE_ABRIR,
                style = MspTheme.type.cardTitle,
                color = MspTheme.colors.onSurface
            )
            Spacer(Modifier.height(MspTheme.spacing.xs))
            Text(
                text = EL_ULTIMO_COBRO_DE_HOY,
                style = MspTheme.type.caption,
                color = MspTheme.colors.onSurfaceMuted
            )
            Spacer(Modifier.height(MspTheme.spacing.md))
            OpcionDelContacto(
                texto = VER_UBICACION,
                icono = AccionesIconos.Pin,
                onElegir = onVerUbicacion,
                modifier = Modifier.testTag(OPCION_UBICACION_TAG)
            )
            Spacer(Modifier.height(MspTheme.spacing.sm))
            OpcionDelContacto(
                texto = VER_TICKET,
                icono = AccionesIconos.Archivo,
                onElegir = onVerTicket,
                modifier = Modifier.testTag(OPCION_TICKET_TAG)
            )
        }
    }
}

/**
 * Una de las dos opciones.
 *
 * El glifo va delante y no decora: las dos filas tienen texto corto y a escala
 * grande el icono es lo que las distingue de un vistazo, antes de leer.
 */
@Composable
private fun OpcionDelContacto(
    texto: String,
    icono: ImageVector,
    onElegir: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onElegir,
        shape = MspTheme.shapes.field,
        color = MspTheme.colors.surface2,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TOQUE_DE_LA_OPCION)
    ) {
        Row(
            modifier = Modifier.padding(MspTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm + MspTheme.spacing.xs)
        ) {
            Icon(
                imageVector = icono,
                contentDescription = null,
                tint = MspTheme.colors.brand,
                modifier = Modifier.size(GLIFO)
            )
            Text(
                text = texto,
                style = MspTheme.type.listTitle,
                color = MspTheme.colors.onSurface
            )
        }
    }
}

/** El velo que tapa la pantalla mientras la hoja está arriba — el de [HojaDeAbono]. */
private val VELO = Color(0x99000000)

/** La hoja redondea sólo arriba: abajo se pega al borde de la pantalla. */
private val FORMA_DE_LA_HOJA = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)

/** Alto mínimo de una opción — la regla de 50 dp del repo, no los 48 de Material. */
private val TOQUE_DE_LA_OPCION = 50.dp

private val GLIFO = 20.dp
