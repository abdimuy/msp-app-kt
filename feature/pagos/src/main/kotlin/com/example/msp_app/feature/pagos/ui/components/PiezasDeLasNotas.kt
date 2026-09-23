package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.SugerenciasDeLaNota
import com.example.msp_app.feature.pagos.domain.etiquetaDe
import com.example.msp_app.feature.pagos.domain.model.SenalDeFicha

/**
 * `testTag` del aviso de la hoja — quién llena las Notas y dónde se quedan.
 *
 * Con tag propio porque la afirmación que hay que poder cobrar es **que el aviso
 * está SIEMPRE**, en los tres estados de la hoja. Buscarlo por su texto ataría
 * el test a la redacción exacta, y la redacción va a cambiar el día que las
 * Notas se sincronicen.
 */
const val AVISO_DE_LAS_NOTAS_TAG: String = "pagos_ficha_aviso"

/** Prefijo del `testTag` de cada casilla ofrecida a partir de la prosa, más el ordinal. */
const val SUGERENCIA_TAG: String = "pagos_ficha_sugerencia_"

/**
 * Alto mínimo tocable. Copia file-private deliberada, igual que en sus vecinos
 * de este paquete: el piso del plan es 50px y estos 56dp lo cumplen con margen.
 */
private val TOQUE = 56.dp

/**
 * **Quién llena esto y dónde se queda.** Siempre visible, en los tres estados de
 * la hoja.
 *
 * Antes no había nada que lo dijera. Una hoja con un catálogo y un campo de
 * texto no dice **de quién es** lo que hay adentro: el cobrador podía leerla
 * como un dato que le llegó de la oficina, y entonces ni la llena ni confía en
 * ella. El dueño lo pidió explícito — que se note que la llena el cobrador y
 * que queda guardada para él.
 *
 * ## Dice la verdad sobre dónde se guarda, y hoy la verdad es "en este teléfono"
 *
 * **Las Notas no salen del aparato.** Verificado con control positivo: hay dos
 * coincidencias de `ficha` en `api`/`dto`/`sync`/`outbox` y las dos son un
 * *fake* de test, contra 175 para pagos y 128 para visitas. Si borra datos o
 * cambia de teléfono, se pierde todo — incluidas *"hay perro"* y *"no ir
 * solo"*, que son seguridad de quien toca el portón.
 *
 * Decirlo es lo único honesto mientras no esté sincronizado, y es lo que hace
 * que el cobrador sepa que **no puede tratarlo como respaldo**. El día que la
 * sincronización llegue a producción, esta línea cambia — y hasta entonces
 * **no se puede prometer lo que no está escrito**.
 */
@Composable
internal fun AvisoDeLasNotas() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AVISO_DE_LAS_NOTAS_TAG),
        shape = MspTheme.shapes.control,
        color = MspTheme.colors.surface2
    ) {
        Text(
            text = "Esto lo anotas tú y se queda en este teléfono",
            style = MspTheme.type.caption,
            color = MspTheme.colors.onSurfaceMuted,
            modifier = Modifier.padding(MspTheme.spacing.sm)
        )
    }
}

/**
 * **Lo que ya es dato, que deje de ser prosa.**
 *
 * Si la nota dice *"hay perro"* y la casilla `HAY_PERRO` está sin marcar, se
 * ofrece. Importa porque las dos mitades **no valen lo mismo**: lo que está en
 * el catálogo lo lee la pantalla —lo pinta en rojo, lo sube a la barra
 * superior, lo pone en el distintivo del dock—, y lo que está en la prosa sólo
 * lo lee quien abra las Notas y las lea enteras. *"Hay perro"* escrito en un
 * renglón cuarenta no le llega al cobrador antes de que abra el portón.
 *
 * **Ofrece, no decide.** El cobrador toca o no toca. Es lo que hace barato un
 * falso positivo —un toque para ignorarlo— y gratis un falso negativo: la nota
 * sigue ahí, escrita, y nadie perdió nada. Por eso
 * [SugerenciasDeLaNota] puede ser generosa con el vocabulario y conservadora
 * con las negaciones.
 *
 * **Y no borra la prosa.** Marcar la casilla no le quita a la nota su frase: el
 * cobrador escribió *"hay perro bravo en el patio de atrás"* y el matiz es
 * suyo. Lo que se agrega es la mitad que el código puede leer.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CasillasQueLaNotaSugiere(
    nota: String,
    senales: Set<SenalDeFicha>,
    habilitado: Boolean,
    onSenal: (SenalDeFicha) -> Unit
) {
    val sugeridas = SugerenciasDeLaNota.para(nota, senales)
    if (sugeridas.isEmpty()) return
    Text(
        text = "Eso también es una casilla",
        style = MspTheme.type.caption,
        color = MspTheme.colors.onSurfaceMuted
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
    ) {
        sugeridas.forEach { senal ->
            Surface(
                onClick = { onSenal(senal) },
                enabled = habilitado,
                modifier = Modifier
                    .heightIn(min = TOQUE)
                    .widthIn(min = TOQUE)
                    .testTag(SUGERENCIA_TAG + senal.ordinal),
                shape = MspTheme.shapes.chip,
                color = MspTheme.colors.surface2,
                // Punteado no se puede, así que el anillo va en el color del
                // contenido y el relleno queda plano: se tiene que leer como
                // "todavía no está marcada" y no como una más del catálogo.
                border = BorderStroke(1.dp, contenidoDe(senal))
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = MspTheme.spacing.sm),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+ " + etiquetaDe(senal),
                        style = MspTheme.type.chipLabel,
                        color = if (habilitado) {
                            contenidoDe(senal)
                        } else {
                            MspTheme.colors.onSurfaceMuted
                        },
                        maxLines = 2
                    )
                }
            }
        }
    }
}
