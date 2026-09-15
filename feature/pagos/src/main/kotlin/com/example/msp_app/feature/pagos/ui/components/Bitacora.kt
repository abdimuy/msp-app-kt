package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.core.designsystem.component.MspMoneyText
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.model.ContactoDeCobranza
import com.example.msp_app.feature.pagos.domain.model.EstadoDelPeriodo
import com.example.msp_app.feature.pagos.ui.EstadoCuentaUi
import java.time.format.DateTimeFormatter

/** `testTag` de una fila de la bitácora de contactos. */
const val FILA_DE_CONTACTO_TAG: String = "pagos_fila_contacto"

private val DIA_Y_MES: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", BUSINESS_LOCALE)

/**
 * Una línea de "últimos contactos" (`.ct`): la fecha, un punto del color del
 * estado, la etiqueta, la frase textual del cliente si la hubo, y el monto
 * cuando la línea es un abono.
 *
 * El punto de color NO va solo: la etiqueta a su derecha dice lo mismo en
 * palabras.
 */
@Composable
fun FilaDeContacto(
    contacto: ContactoDeCobranza,
    modifier: Modifier = Modifier,
    ocultos: Boolean = false
) {
    val trato = EstadoCuentaUi.tratoDe(
        EstadoDelPeriodo(
            estado = contacto.estado,
            abonoDelPeriodo = Money.ZERO,
            parcialidad = Money.ZERO
        )
    )
    val color = EstadoCuentaUi.contenidoDe(trato, MspTheme.colors)
    Column(modifier = modifier.fillMaxWidth().testTag(FILA_DE_CONTACTO_TAG)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
        }
        Separador()
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
