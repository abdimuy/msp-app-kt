package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.core.designsystem.component.MspMoneyText
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.model.ContactoDeCobranza
import com.example.msp_app.feature.pagos.domain.model.EstadoDelPeriodo
import com.example.msp_app.feature.pagos.domain.model.UbicacionDelCobro
import com.example.msp_app.feature.pagos.ui.AccionesIconos
import com.example.msp_app.feature.pagos.ui.EstadoCuentaUi
import java.time.format.DateTimeFormatter

/** `testTag` de una fila de la bitácora de contactos. */
const val FILA_DE_CONTACTO_TAG: String = "pagos_fila_contacto"

/**
 * Lo que anuncia una fila que lleva al mapa.
 *
 * Un `clickable` sin etiqueta es un control invisible para TalkBack: la fila
 * tiene texto, pero el texto dice *qué pasó*, no *a dónde lleva tocarla*.
 *
 * `internal` y no `private` porque las DOS filas de contacto —ésta y
 * [ContactoEnLaHoja], la del detalle— hacen lo mismo y tienen que anunciarlo
 * igual. Dos literales habrían divergido en cuanto alguien retocara uno.
 */
internal const val VER_DONDE_FUE: String = "Ver dónde fue"

private val DIA_Y_MES: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", BUSINESS_LOCALE)

/**
 * Una línea de "últimos contactos" (`.ct`): la fecha, un punto del color del
 * estado, la etiqueta, la frase textual del cliente si la hubo, el monto cuando
 * la línea es un abono, y el pin cuando ese contacto trae punto medido.
 *
 * El punto de color NO va solo: la etiqueta a su derecha dice lo mismo en
 * palabras.
 *
 * ## Tocable SOLO con punto medido
 *
 * [onVerUbicacion] abre el mapa grande centrado en el punto de ESTE contacto.
 * Sin punto no hay nada que abrir, y entonces el `clickable` **no existe** — no
 * es un `onClick` vacío: un control que se ve tocable y no hace nada es
 * justamente el defecto que ningún golden fotografía. Es el mismo trato que ya
 * le da [CuadroDeLaPuerta] al cuadro de la puerta.
 *
 * ## El afordante, y por qué su hueco se reserva siempre
 *
 * El pin ([AccionesIconos.Pin], el mismo glifo de "cómo llegar" y del cuadro de
 * la puerta) se pinta en `brand` al cierre del renglón. Dice dos cosas a la vez:
 * *"de esta vez sí se sabe dónde fue"* y *"tócame"*. Va en `brand` y no en el
 * color del estado porque el color del estado ya está dicho por el punto de la
 * izquierda, y repetirlo ahí lo volvería decoración (principios 5 y 8).
 *
 * El **hueco se reserva aunque no haya pin**: si apareciera y desapareciera, la
 * columna de dinero se correría de renglón en renglón según si ese día hubo
 * señal, y una columna de importes que no alinea se lee como un defecto. Lo que
 * cambia entre una fila y otra es la tinta, no la caja.
 *
 * El alto mínimo es [ALTO_TOCABLE] **siempre**, no solo cuando la fila es
 * tocable: el ritmo de la lista no puede depender de si el GPS estaba prendido
 * el día de ese contacto. Y así los 50 dp del principio 11 se cumplen por
 * construcción en toda fila que pueda volverse tocable.
 */
@Composable
fun FilaDeContacto(
    contacto: ContactoDeCobranza,
    modifier: Modifier = Modifier,
    ocultos: Boolean = false,
    onVerUbicacion: ((UbicacionDelCobro) -> Unit)? = null
) {
    val trato = EstadoCuentaUi.tratoDe(
        EstadoDelPeriodo(
            estado = contacto.estado,
            abonoDelPeriodo = Money.ZERO,
            parcialidad = Money.ZERO
        )
    )
    val color = EstadoCuentaUi.contenidoDe(trato, MspTheme.colors)
    // La fila recibe una lambda que PIDE el punto y entrega una que ya lo lleva:
    // así el único lugar que decide si hay algo que abrir es este, y el llamador
    // no tiene que repetir la pregunta en cada renglón.
    val abrir = abridorDe(contacto, onVerUbicacion)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (abrir != null) {
                    Modifier
                        .clickable(onClick = abrir)
                        .semantics { contentDescription = VER_DONDE_FUE }
                } else {
                    Modifier
                }
            )
            .testTag(FILA_DE_CONTACTO_TAG)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ALTO_TOCABLE)
                .padding(vertical = MspTheme.spacing.sm + MspTheme.spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
        ) {
            Text(
                text = DIA_Y_MES.format(AppTime.toBusinessDate(contacto.fecha)),
                style = MspTheme.type.caption,
                color = MspTheme.colors.onSurfaceMuted,
                modifier = Modifier.width(56.dp)
            )
            Box(
                modifier = Modifier
                    .padding(top = MspTheme.spacing.xs + 2.dp)
                    .size(6.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(color)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contacto.etiqueta,
                    style = MspTheme.type.bodyStrong,
                    color = MspTheme.colors.onSurface
                )
                if (contacto.nota != null) {
                    Text(
                        text = "“${contacto.nota}”",
                        style = MspTheme.type.caption,
                        color = MspTheme.colors.onSurfaceMuted
                    )
                }
            }
            if (contacto.importe != null) {
                MspMoneyText(
                    amount = contacto.importe.amount,
                    masked = ocultos,
                    style = MspTheme.type.amountInline,
                    color = MspTheme.colors.onSurface
                )
            }
            PinDelContacto(hayPunto = contacto.ubicacion != null)
        }
        Separador()
    }
}

/**
 * La lambda de toque de una fila de contacto, o `null` cuando no hay nada que
 * abrir.
 *
 * Existe para que la regla —*se toca si y solo si hay punto medido*— viva en UN
 * lugar y la compartan las dos filas. Repartirla entre la fila y su llamador es
 * cómo se termina con una pantalla que ya no deja tocar y otra que sí.
 */
internal fun abridorDe(
    contacto: ContactoDeCobranza,
    onVerUbicacion: ((UbicacionDelCobro) -> Unit)?
): (() -> Unit)? {
    val punto = contacto.ubicacion ?: return null
    val ver = onVerUbicacion ?: return null
    return { ver(punto) }
}

/**
 * El afordante de "esta fila lleva al mapa": el pin, o su hueco.
 *
 * El hueco existe para que la caja del renglón mida lo mismo con punto y sin él
 * — ver el KDoc de [FilaDeContacto]. Es decorativo (`contentDescription = null`)
 * porque lo que la fila hace ya lo dice su etiqueta de acción.
 */
@Composable
internal fun PinDelContacto(hayPunto: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(GLIFO_DEL_PIN)) {
        if (hayPunto) {
            Icon(
                imageVector = AccionesIconos.Pin,
                contentDescription = null,
                tint = MspTheme.colors.brand,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * El "ver los N contactos" / "ver los N abonos" del mock (`.allof`).
 *
 * [ALTO_TOCABLE] es piso, no relleno: con el padding solo, la fila medía ~48dp
 * a `FontSizeLevel.NORMAL` —debajo del piso de 50px del plan— porque el texto
 * es `captionStrong`. Un `heightIn` lo garantiza a cualquier escala de fuente,
 * que es lo que el padding no puede prometer.
 */
@Composable
fun VerTodos(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MspTheme.colors.surface
) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ALTO_TOCABLE),
        shape = MspTheme.shapes.control,
        // [color] existe para cuando la fila va DENTRO de una tarjeta: `surface`
        // sobre `surface` es un borde invisible y la fila deja de leerse como
        // tocable. Quien la anide pasa `surface2`.
        color = color
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MspTheme.spacing.md),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = texto,
                style = MspTheme.type.captionStrong,
                color = MspTheme.colors.brand
            )
        }
    }
}

/**
 * Piso de alto de cualquier fila tocable de estas pantallas. El plan pide
 * >=50px; `MspSpacing.touchTarget` ya defiende el acuerdo por el extremo alto
 * (56dp) y es el mismo valor que usan los botones del dock.
 */
private val ALTO_TOCABLE = 56.dp

/**
 * El pin del cierre del renglón.
 *
 * Los 16 dp del chevron de `VerLosContactos`, que es el otro afordante de
 * navegación de estas listas: dos glifos que significan "esto lleva a algún
 * lado" no pueden pesar distinto en la misma pantalla.
 */
private val GLIFO_DEL_PIN = 16.dp
