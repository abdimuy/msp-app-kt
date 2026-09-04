package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.etiquetaDe
import com.example.msp_app.feature.pagos.domain.model.FichaDelCliente
import com.example.msp_app.feature.pagos.domain.model.SenalDeFicha
import java.time.format.DateTimeFormatter

/** `testTag` de la tarjeta de la ficha — la sección "lo que hay que saber". */
const val TARJETA_DE_LA_FICHA_TAG: String = "pagos_ficha_tarjeta"

/** `testTag` del afordante que abre la hoja de edición. */
const val EDITAR_FICHA_TAG: String = "pagos_ficha_editar"

/** `testTag` del campo de nota libre dentro de la hoja. */
const val NOTA_DE_LA_FICHA_TAG: String = "pagos_ficha_nota"

/** Prefijo del `testTag` de cada chip del catálogo, más el ordinal de la señal. */
const val SENAL_TAG: String = "pagos_ficha_senal_"

/** `testTag` del CTA que guarda la ficha. */
const val GUARDAR_FICHA_TAG: String = "pagos_ficha_guardar"

/**
 * Alto mínimo tocable. Copia deliberada del `TOQUE` privado de los vecinos de
 * este paquete: el piso del plan es 50px y estos 56dp lo cumplen con margen.
 */
private val TOQUE = 56.dp

/**
 * La sección **"lo que hay que saber"**: el catálogo cerrado y la nota libre.
 *
 * ## Dónde cae, y por qué se midió antes de decidirlo
 *
 * Va **arriba de "sus ventas"**, no al final de la pantalla. La ficha es lo que
 * se lee **antes** de tocar la puerta —*"está en la noche"*, *"atiende otra
 * persona"*—, así que enterarse después de bajar por el saldo, las ventas, la
 * liquidación y la bitácora es enterarse tarde. La Task 22 descubrió tarde que
 * su sección quedaba debajo de la línea de flotación; aquí la posición se
 * **mide** en `LaFichaSeVeYSeTocaTest`, no se supone.
 *
 * ## El afordante de edición no estrena renglón
 *
 * Abrir el editor cuelga de dos cosas que **ya existían**: la fila del rótulo
 * de sección —que crece del ~38dp que ya medía al piso tocable de 56dp, no los
 * ~64dp que costaría un botón propio con su separación— y **la tarjeta
 * completa**, que es el blanco grande y no agrega nada. Es la salida que la
 * Task 22 encontró para la cámara: el afordante viaja con algo que ya estaba.
 *
 * ## Los tres estados, que no se aplanan
 *
 * - [ficha] `null` — **no se pudo leer**. Se dice, y el editor queda cerrado:
 *   una ficha en blanco editable sobre una lectura fallida invita a escribir
 *   encima de lo que sí estaba guardado.
 * - [ficha] vacía — no hay nada anotado. Se invita a anotar.
 * - [ficha] con contenido — se pinta.
 *
 * [notaDeLaVenta] es OTRA cosa y por eso se pinta aparte, con su propia
 * etiqueta: viene del servidor en `sales.NOTAS`, se reescribe en cada
 * sincronización y el cobrador no la puede editar.
 */
@Composable
fun SeccionDeLaFicha(
    ficha: FichaDelCliente?,
    notaDeLaVenta: String?,
    onEditar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        RotuloConAccion(
            rotulo = "lo que hay que saber",
            accion = if (ficha == null) null else if (ficha.vacia) "anotar" else "editar",
            onAccion = onEditar
        )
        Tarjeta(
            modifier = Modifier.testTag(TARJETA_DE_LA_FICHA_TAG),
            onClick = if (ficha == null) null else onEditar
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)) {
                when {
                    ficha == null -> Text(
                        text = "no se pudo leer la ficha",
                        style = MspTheme.type.body,
                        color = MspTheme.colors.statusPartial
                    )

                    ficha.vacia -> Text(
                        text = "sin ficha — anota lo que sirva mañana",
                        style = MspTheme.type.body,
                        color = MspTheme.colors.onSurfaceMuted
                    )

                    else -> {
                        if (ficha.senales.isNotEmpty()) {
                            FilaDeSenales(ficha.senales)
                        }
                        ficha.nota?.let {
                            Text(
                                text = it,
                                style = MspTheme.type.body,
                                color = MspTheme.colors.onSurface,
                                // La tarjeta muestra un asomo, no la nota
                                // entera: con el tope de 500 caracteres una
                                // nota larga son ~diez renglones y empujaría
                                // "sus ventas" fuera de pantalla. El texto
                                // completo vive en el editor, a un toque.
                                maxLines = RENGLONES_DE_ASOMO,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                ficha?.actualizada?.let { cuando ->
                    // Cuándo se anotó, y no quién: el nombre del cobrador no
                    // está en esta pantalla y la fecha es lo que decide si el
                    // dato todavía sirve. Una ficha de hace dos años se lee
                    // distinto que la de la semana pasada.
                    Text(
                        text = "anotada el " + DIA_Y_MES.format(AppTime.toBusinessDate(cuando)),
                        style = MspTheme.type.caption,
                        color = MspTheme.colors.onSurfaceMuted
                    )
                }
                notaDeLaVenta?.let {
                    Separador()
                    Text(
                        text = "de la venta",
                        style = MspTheme.type.caption,
                        color = MspTheme.colors.onSurfaceMuted
                    )
                    Text(
                        text = it,
                        style = MspTheme.type.body,
                        color = MspTheme.colors.onSurfaceMuted
                    )
                }
            }
        }
    }
}

/**
 * El rótulo de sección con su afordante a la derecha, en el MISMO renglón.
 *
 * Mismo tipo y color que [LabelDeSeccion] —es el mismo rótulo— pero la fila
 * entera es tocable y respeta el piso de 56dp. Con [accion] en `null` no hay
 * nada que tocar y la fila vuelve a ser un rótulo y nada más.
 */
@Composable
private fun RotuloConAccion(rotulo: String, accion: String?, onAccion: () -> Unit) {
    val contenido: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MspTheme.spacing.md, bottom = MspTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = rotulo,
                style = MspTheme.type.overline,
                color = MspTheme.colors.onSurfaceMuted,
                modifier = Modifier.weight(1f)
            )
            accion?.let {
                Text(
                    text = it,
                    style = MspTheme.type.captionStrong,
                    color = MspTheme.colors.brand,
                    modifier = Modifier.testTag(EDITAR_FICHA_TAG)
                )
            }
        }
    }
    if (accion == null) {
        contenido()
    } else {
        Surface(
            onClick = onAccion,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TOQUE),
            color = MspTheme.colors.background
        ) { contenido() }
    }
}

/** Los chips de las señales marcadas, en modo lectura. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilaDeSenales(senales: Set<SenalDeFicha>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
    ) {
        // Se recorre el CATÁLOGO y no el conjunto: así el orden de los chips es
        // el del enum —estable entre corridas y entre clientes— y no el de
        // iteración de un `Set`, que no promete ninguno.
        SenalDeFicha.entries.filter { it in senales }.forEach { senal ->
            Surface(
                modifier = Modifier.heightIn(min = MspTheme.spacing.lg + MspTheme.spacing.sm),
                shape = MspTheme.shapes.chip,
                color = MspTheme.colors.brandTint
            ) {
                Box(
                    modifier = Modifier.padding(
                        horizontal = MspTheme.spacing.sm,
                        vertical = MspTheme.spacing.xs
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = etiquetaDe(senal),
                        style = MspTheme.type.chipLabel,
                        color = MspTheme.colors.brand
                    )
                }
            }
        }
    }
}

/**
 * El cuerpo de la hoja de edición, **sin** el `ModalBottomSheet` que lo
 * envuelve.
 *
 * Extraído por la misma razón que `CuerpoDeLaHojaDeImpresion` en
 * `:feature:collectionReport`: Roborazzi captura la ventana raíz y no el
 * `Popup` donde Material monta la hoja, así que capturar la hoja completa
 * daría un golden en blanco.
 */
@Composable
fun CuerpoDeLaFicha(
    senales: Set<SenalDeFicha>,
    nota: String,
    guardando: Boolean,
    fallo: Boolean,
    onSenal: (SenalDeFicha) -> Unit,
    onNota: (String) -> Unit,
    onGuardar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MspTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        Text(
            text = "lo que hay que saber",
            style = MspTheme.type.cardTitle,
            color = MspTheme.colors.onSurface
        )
        Text(
            text = "esto lo lee la máquina",
            style = MspTheme.type.caption,
            color = MspTheme.colors.onSurfaceMuted
        )
        CatalogoDeSenales(senales = senales, habilitado = !guardando, onSenal = onSenal)
        Text(
            text = "esto lo lee quien venga mañana",
            style = MspTheme.type.caption,
            color = MspTheme.colors.onSurfaceMuted
        )
        CampoDeLaNota(nota = nota, habilitado = !guardando, onCambio = onNota)
        if (fallo) {
            Text(
                text = "no se pudo guardar",
                style = MspTheme.type.bodyStrong,
                color = MspTheme.colors.statusOverdue
            )
        }
        BotonDeGuardarFicha(guardando = guardando, onGuardar = onGuardar)
        Spacer(Modifier.height(MspTheme.spacing.md))
    }
}

/**
 * El catálogo cerrado, entero y siempre visible.
 *
 * Se pintan **todos** los valores y no solo los marcados: un catálogo cerrado
 * de cuatro cabe completo en pantalla, y verlo completo es lo que hace que
 * marcar sea elegir de una lista corta en vez de recordar qué existía.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CatalogoDeSenales(
    senales: Set<SenalDeFicha>,
    habilitado: Boolean,
    onSenal: (SenalDeFicha) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
    ) {
        SenalDeFicha.entries.forEach { senal ->
            val marcada = senal in senales
            Surface(
                onClick = { onSenal(senal) },
                enabled = habilitado,
                modifier = Modifier
                    .heightIn(min = TOQUE)
                    .widthIn(min = TOQUE)
                    .testTag(SENAL_TAG + senal.ordinal),
                shape = MspTheme.shapes.chip,
                color = if (marcada) MspTheme.colors.brandTint else MspTheme.colors.surface2,
                // El anillo del chip elegido. No es adorno: el fondo tint y el
                // `surface2` se parecen demasiado en oscuro, y el color solo
                // nunca puede ser el portador del significado.
                border = if (marcada) BorderStroke(1.5.dp, MspTheme.colors.brand) else null
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = MspTheme.spacing.sm),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = etiquetaDe(senal),
                        style = MspTheme.type.chipLabel,
                        color = when {
                            marcada -> MspTheme.colors.brand
                            habilitado -> MspTheme.colors.onSurface
                            else -> MspTheme.colors.onSurfaceMuted
                        },
                        maxLines = 2
                    )
                }
            }
        }
    }
}

/**
 * La nota libre, con su contador.
 *
 * El contador aparece **solo cuando queda poco** (los últimos 50 caracteres):
 * un "0/500" permanente es ruido en una pantalla que ya está llena, y el dato
 * solo importa cerca del tope. El corte duro lo hace
 * [FichaDelCliente.limpia] en el dominio; aquí se avisa antes de llegar.
 */
@Composable
private fun CampoDeLaNota(nota: String, habilitado: Boolean, onCambio: (String) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TOQUE),
        shape = MspTheme.shapes.control,
        color = MspTheme.colors.surface2
    ) {
        Column(modifier = Modifier.padding(MspTheme.spacing.sm)) {
            BasicTextField(
                value = nota,
                onValueChange = { onCambio(it.take(FichaDelCliente.NOTA_MAX)) },
                enabled = habilitado,
                textStyle = LocalTextStyle.current
                    .merge(MspTheme.type.body)
                    .merge(TextStyle(color = MspTheme.colors.onSurface)),
                cursorBrush = SolidColor(MspTheme.colors.onSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(NOTA_DE_LA_FICHA_TAG),
                decorationBox = { campo ->
                    if (nota.isEmpty()) {
                        Text(
                            text = "trabaja de noche, atiende la suegra…",
                            style = MspTheme.type.body,
                            color = MspTheme.colors.onSurfaceMuted
                        )
                    }
                    campo()
                }
            )
            if (nota.length >= FichaDelCliente.NOTA_MAX - AVISO_DE_TOPE) {
                Text(
                    text = "${nota.length}/${FichaDelCliente.NOTA_MAX}",
                    style = MspTheme.type.caption,
                    color = MspTheme.colors.onSurfaceMuted,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * El CTA. Azul de marca porque es lo protagónico de la hoja; apagado mientras
 * guarda **y sin `onClick`**: un botón vivo que no hace nada es la mentira que
 * la Task 18 tuvo que arreglar dos veces.
 */
@Composable
private fun BotonDeGuardarFicha(guardando: Boolean, onGuardar: () -> Unit) {
    Surface(
        onClick = onGuardar,
        enabled = !guardando,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TOQUE)
            .testTag(GUARDAR_FICHA_TAG),
        shape = MspTheme.shapes.control,
        color = if (guardando) MspTheme.colors.surface2 else MspTheme.colors.brand
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = if (guardando) "guardando" else "guardar",
                style = MspTheme.type.buttonLarge,
                color = if (guardando) MspTheme.colors.onSurfaceMuted else MspTheme.colors.onBrand
            )
        }
    }
}

/** Cuántos renglones de la nota se asoman en la tarjeta. */
private const val RENGLONES_DE_ASOMO = 4

/** Cuántos caracteres antes del tope aparece el contador. */
private const val AVISO_DE_TOPE = 50

/** `d MMM` — el mismo formato corto que ya usa el pie del saldo. */
private val DIA_Y_MES: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", BUSINESS_LOCALE)
