package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.graphics.Color
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
import com.example.msp_app.feature.pagos.domain.model.PesoDeLaSenal
import com.example.msp_app.feature.pagos.domain.model.SenalDeFicha
import java.time.format.DateTimeFormatter

/** `testTag` de la tarjeta de la ficha — la sección "lo que hay que saber". */
const val TARJETA_DE_LA_FICHA_TAG: String = "pagos_ficha_tarjeta"

/** `testTag` del afordante de la barra superior, que abre la hoja de edición. */
const val EDITAR_FICHA_TAG: String = "pagos_ficha_editar"

/**
 * `testTag` del TEXTO del afordante — lo que la pastilla está diciendo.
 *
 * Aparte del de la pastilla porque cuando ésta no es tocable (ficha ilegible)
 * el `Surface` no fusiona a su hijo, y el mismo texto de una advertencia
 * aparece a la vez aquí y en el chip de la tarjeta.
 */
const val AFORDANTE_TEXTO_TAG: String = "pagos_ficha_afordante_texto"

/** `testTag` del campo de nota libre dentro de la hoja. */
const val NOTA_DE_LA_FICHA_TAG: String = "pagos_ficha_nota"

/** Prefijo del `testTag` de cada chip del catálogo en la HOJA, más el ordinal. */
const val SENAL_TAG: String = "pagos_ficha_senal_"

/**
 * Prefijo del `testTag` de cada chip en la TARJETA, más el ordinal.
 *
 * Distinto del de la hoja a propósito: dos nodos con el mismo tag hacen que
 * `onNodeWithTag` truene por ambigüedad, y la etiqueta de una señal marcada
 * aparece a la vez en la pastilla de la barra y en la tarjeta.
 */
const val CHIP_DE_FICHA_TAG: String = "pagos_ficha_chip_"

/** `testTag` del CTA que guarda la ficha. */
const val GUARDAR_FICHA_TAG: String = "pagos_ficha_guardar"

/**
 * Alto mínimo tocable. Copia deliberada del `TOQUE` privado de los vecinos de
 * este paquete: el piso del plan es 50px y estos 56dp lo cumplen con margen.
 */
private val TOQUE = 56.dp

/**
 * **El afordante de la ficha, en la barra superior.** Cuesta **cero dp
 * verticales**: la barra ya existía, mide 56dp por su botón de atrás, y le
 * sobra el ancho.
 *
 * ## Por qué aquí y no como sección arriba de "sus ventas"
 *
 * Porque ahí **tapaba el dinero**. La primera versión puso la tarjeta completa
 * entre el saldo y "sus ventas" y a escala 2.0 no quedaba ni una venta visible
 * sin desplazar; el golden `promesa_sin_fecha` perdió de vista justo el renglón
 * que existe para proteger. El dinero es **por lo que el cobrador abre esta
 * pantalla**; un dato de conocimiento no puede taparlo. Que la ficha sea menos
 * prominente es aceptable — que el saldo no se vea, no.
 *
 * La salida es la misma de la Task 22 con la cámara: colgar el afordante de una
 * fila que ya estaba. Y como cuesta cero, **agregar señales al catálogo no
 * vuelve a empujar nada**: esta pastilla es de una sola línea, siempre.
 *
 * ## Y una advertencia sí grita
 *
 * Si hay una señal [PesoDeLaSenal.ADVIERTE] marcada, la pastilla la pinta con
 * su etiqueta, en color de peligro, **en lo único de la pantalla que se ve
 * siempre sin desplazar**. Ésa es la diferencia entre una advertencia y un dato:
 * *"hay perro"* tiene que llegarle al cobrador antes de que abra el portón, no
 * después de que baje cuatro secciones.
 *
 * Con [ficha] en `null` —no se pudo leer— la pastilla se pinta apagada y **no
 * es tocable**: abrir el editor sobre una lectura fallida invitaría a guardar
 * una ficha en blanco encima de la buena.
 */
@Composable
fun AfordanteDeLaFicha(
    ficha: FichaDelCliente?,
    onEditar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MspTheme.colors
    val advertencia = ficha?.advertencias?.firstOrNull()
    val texto = when {
        ficha == null -> "ficha ilegible"
        advertencia != null -> etiquetaDe(advertencia)
        ficha.vacia -> "anotar ficha"
        else -> "ver ficha"
    }
    val contenido = when {
        ficha == null -> colors.onSurfaceMuted
        advertencia != null -> colors.danger
        else -> colors.brand
    }
    val fondo = when {
        ficha == null -> colors.surface
        advertencia != null -> colors.dangerTint
        else -> colors.brandTint
    }
    Pastilla(
        texto = texto,
        contenido = contenido,
        fondo = fondo,
        onClick = if (ficha == null) null else onEditar,
        modifier = modifier.testTag(EDITAR_FICHA_TAG)
    )
}

/**
 * La pastilla de la barra. `maxLines = 1` y elipsis **no son cosmética**: dos
 * renglones harían crecer la fila de navegación y el afordante dejaría de
 * costar cero dp, que es la única razón por la que vive ahí.
 */
@Composable
private fun Pastilla(
    texto: String,
    contenido: Color,
    fondo: Color,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val cuerpo: @Composable () -> Unit = {
        Box(
            modifier = Modifier.padding(horizontal = MspTheme.spacing.md),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = texto,
                style = MspTheme.type.captionStrong,
                color = contenido,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(AFORDANTE_TEXTO_TAG)
            )
        }
    }
    if (onClick == null) {
        Surface(
            modifier = modifier.heightIn(min = TOQUE),
            shape = MspTheme.shapes.chip,
            color = fondo,
            content = cuerpo
        )
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier
                .heightIn(min = TOQUE)
                .widthIn(min = TOQUE),
            shape = MspTheme.shapes.chip,
            color = fondo,
            content = cuerpo
        )
    }
}

/**
 * La sección **"lo que hay que saber"**: el catálogo cerrado y la nota libre.
 *
 * ## Dónde cae, medido
 *
 * Va **al fondo, entre "últimos contactos" y "datos del cliente"** — el mismo
 * lugar donde la Task 16 puso su antecesora, así que **no empuja un solo dp**
 * de lo que estaba arriba. `LaFichaSeVeYSeTocaTest` lo mide: el tope de "sus
 * ventas" es idéntico con la ficha llena, vacía o ilegible, y la primera venta
 * se ve sin desplazar en las tres escalas.
 *
 * Lo que sí se ve arriba —sin costar alto— es [AfordanteDeLaFicha], y con él
 * la advertencia, que es lo único de la ficha que no puede esperar a que el
 * cobrador baje.
 *
 * ## Los tres estados, que no se aplanan
 *
 * - [ficha] `null` — **no se pudo leer**. Se dice, y el editor queda cerrado:
 *   una ficha en blanco editable sobre una lectura fallida invita a escribir
 *   encima de lo que sí estaba guardado.
 * - [ficha] vacía — no hay nada anotado. Se invita a anotar.
 * - [ficha] con contenido — se pinta, advertencias primero.
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
        LabelDeSeccion("lo que hay que saber")
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
                            FilaDeSenales(ficha.enOrden)
                        }
                        ficha.nota?.let {
                            Text(
                                text = it,
                                style = MspTheme.type.body,
                                color = MspTheme.colors.onSurface,
                                // La tarjeta muestra un asomo, no la nota
                                // entera: con el tope de 500 caracteres una
                                // nota larga son ~diez renglones. El texto
                                // completo vive en el editor, a un toque. NADA
                                // que deba gritar vive aquí: las advertencias
                                // son chips y suben a la barra superior.
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

/** Los chips de las señales marcadas, en modo lectura. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilaDeSenales(senales: List<SenalDeFicha>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
    ) {
        senales.forEach { senal ->
            Surface(
                modifier = Modifier
                    .heightIn(min = MspTheme.spacing.lg + MspTheme.spacing.sm)
                    .testTag(CHIP_DE_FICHA_TAG + senal.ordinal),
                shape = MspTheme.shapes.chip,
                color = fondoDe(senal)
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
                        color = contenidoDe(senal)
                    )
                }
            }
        }
    }
}

/** El color del contenido de una señal — lo decide su [SenalDeFicha.peso]. */
@Composable
private fun contenidoDe(senal: SenalDeFicha): Color =
    if (senal.peso == PesoDeLaSenal.ADVIERTE) MspTheme.colors.danger else MspTheme.colors.brand

/** El fondo de una señal — lo decide su [SenalDeFicha.peso]. */
@Composable
private fun fondoDe(senal: SenalDeFicha): Color = if (senal.peso == PesoDeLaSenal.ADVIERTE) {
    MspTheme.colors.dangerTint
} else {
    MspTheme.colors.brandTint
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
        // "la app" y no "la máquina": el conteo por ventana horaria todavía no
        // existe, y prometerle al cobrador un lector que no está escrito es la
        // misma falsedad que un KDoc inventado. Lo que SÍ lee estas señales hoy
        // es la pantalla — la pastilla de la barra y el color del chip.
        Text(
            text = "esto lo lee la app",
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
 * El catálogo cerrado, entero y siempre visible, **advertencias primero**.
 *
 * Se pintan **todos** los valores y no solo los marcados: un catálogo cerrado
 * de seis cabe completo en la hoja, y verlo completo es lo que hace que marcar
 * sea elegir de una lista corta en vez de recordar qué existía.
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
        SenalDeFicha.EN_ORDEN_DE_PANTALLA.forEach { senal ->
            val marcada = senal in senales
            val acento = contenidoDe(senal)
            Surface(
                onClick = { onSenal(senal) },
                enabled = habilitado,
                modifier = Modifier
                    .heightIn(min = TOQUE)
                    .widthIn(min = TOQUE)
                    .testTag(SENAL_TAG + senal.ordinal),
                shape = MspTheme.shapes.chip,
                color = if (marcada) fondoDe(senal) else MspTheme.colors.surface2,
                // El anillo del chip elegido. No es adorno: el fondo tint y el
                // `surface2` se parecen demasiado en oscuro, y el color solo
                // nunca puede ser el portador del significado.
                border = if (marcada) BorderStroke(1.5.dp, acento) else null
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = MspTheme.spacing.sm),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = etiquetaDe(senal),
                        style = MspTheme.type.chipLabel,
                        color = when {
                            marcada -> acento
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
 * **El recorte lo hace el dominio**, no este campo:
 * [FichaDelCliente.recorta] es la única forma de cortar la nota en todo el
 * código. Un `take(NOTA_MAX)` local aquí fue exactamente el defecto — dejaba la
 * guarda de pares suplentes del dominio sin ningún camino real que la
 * alcanzara, y su prueba pasaba con el error puesto.
 *
 * El contador aparece **solo cuando queda poco** (los últimos 50 caracteres):
 * un "0/500" permanente es ruido en una pantalla que ya está llena, y el dato
 * solo importa cerca del tope.
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
                onValueChange = { onCambio(FichaDelCliente.recorta(it)) },
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
