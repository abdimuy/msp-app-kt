package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.model.ContactoDeCobranza
import com.example.msp_app.feature.pagos.domain.model.UbicacionDelCobro
import com.example.msp_app.feature.pagos.ui.AccionesIconos

/**
 * Lo que anuncia una fila que lleva al mapa.
 *
 * Un `clickable` sin etiqueta es un control invisible para TalkBack: la fila
 * tiene texto, pero el texto dice *qué pasó*, no *a dónde lleva tocarla*.
 *
 * `internal` porque quien lo pinta es [ContactoEnLinea], que vive en otro
 * archivo, y quien lo afirma es su test. Vivía aquí cuando eran DOS filas —la
 * de la bitácora y la del detalle— y aquí se queda: el literal y [abridorDe]
 * son la misma regla, *se toca si y solo si hay punto medido*, y separarlos es
 * cómo se termina con una pantalla que deja tocar y otra que no.
 */
internal const val VER_DONDE_FUE: String = "Ver dónde fue"

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
 * El hueco existe para que la caja del renglón mida lo mismo con punto y sin
 * él: sin reservarlo, la fila con mapa sería más ancha que sus vecinas y la
 * columna de la derecha bailaría renglón a renglón. Es decorativo
 * (`contentDescription = null`) porque lo que la fila hace ya lo dice su
 * etiqueta de acción.
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
