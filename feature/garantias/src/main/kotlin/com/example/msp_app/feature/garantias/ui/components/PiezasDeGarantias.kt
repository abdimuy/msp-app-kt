package com.example.msp_app.feature.garantias.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.garantias.ui.TextosDeGarantias
import java.io.ByteArrayOutputStream

/**
 * El mínimo tocable del repo (**50dp**, más estricto que los 48 de Material).
 *
 * No hay constante compartida en `:core:designsystem`: la compuerta
 * `CadaPantallaDeCobranzaRespetaLaBarraDeEstadoTest` la cobra sobre el grafo
 * real y un control más bajo deja al cobrador tocando el borde sin que pase
 * nada.
 */
private val ALTO_TOCABLE_MINIMO = 50.dp

/** El grosor de la línea de la firma; nombrado para no sembrar un `3` suelto. */
private val GROSOR_DEL_TRAZO = 3.dp

/** El borde del pad de firma. */
private val BORDE_DEL_PAD = 1.dp

/** Calidad del PNG; `100` es sin pérdida y es lo que el servidor espera. */
private const val CALIDAD_PNG = 100

/**
 * El encabezado de una pantalla de la captura: "Volver" y el título.
 *
 * El botón de volver es un control de texto y no un `Button` de Material a
 * propósito: mide el mínimo tocable y no compite con el CTA de la pantalla. La
 * cadena que pinta el fondo vive en `GarantiasV2Screen`, así que acá no hay
 * `.background(...)` que dispare la compuerta del inset.
 */
@Composable
fun EncabezadoDeGarantia(titulo: String, onAtras: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        Box(
            modifier = Modifier
                .heightIn(min = ALTO_TOCABLE_MINIMO)
                .clip(MspTheme.shapes.chip)
                .clickable(onClick = onAtras)
                .padding(horizontal = MspTheme.spacing.sm),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = TextosDeGarantias.VOLVER,
                style = MspTheme.type.buttonSmall,
                color = MspTheme.colors.brand
            )
        }
        Text(
            text = titulo,
            style = MspTheme.type.screenTitleCompact,
            color = MspTheme.colors.onSurface
        )
    }
}

/**
 * Un aviso de la captura: lo que faltó, lo que no se pudo leer o por qué el
 * botón está apagado.
 *
 * [esError] elige el rojo; lo demás es ámbar. Nunca se pinta sólo el color: el
 * texto dice qué pasó.
 */
@Composable
fun AvisoDeGarantia(texto: String, modifier: Modifier = Modifier, esError: Boolean = false) {
    val colors = MspTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MspTheme.shapes.tile)
            .background(if (esError) colors.dangerTint else colors.statusPendingTint)
            .padding(MspTheme.spacing.md)
    ) {
        Text(
            text = texto,
            style = MspTheme.type.caption,
            color = if (esError) colors.danger else colors.onSurfaceMuted
        )
    }
}

/** Una bandeja o una lista sin nada que mostrar. */
@Composable
fun EstadoVacioDeGarantia(titulo: String, pie: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(MspTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        Text(
            text = titulo,
            style = MspTheme.type.cardTitle,
            color = MspTheme.colors.onSurface
        )
        Text(
            text = pie,
            style = MspTheme.type.caption,
            color = MspTheme.colors.onSurfaceMuted
        )
    }
}

/** Una pareja etiqueta/valor de una ficha (el domicilio, el estado de cuenta). */
@Composable
fun FilaDeDato(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = MspTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        Text(
            text = etiqueta,
            style = MspTheme.type.kvLabel,
            color = MspTheme.colors.onSurfaceMuted
        )
        Text(
            text = valor,
            style = MspTheme.type.kvValue,
            color = MspTheme.colors.onSurface
        )
    }
}

/** El rótulo que abre una sección de una pantalla. */
@Composable
fun EtiquetaDeSeccion(texto: String, modifier: Modifier = Modifier) {
    Text(
        text = texto,
        modifier = modifier,
        style = MspTheme.type.sectionHeader,
        color = MspTheme.colors.onSurface
    )
}

/**
 * Un campo de texto de la captura.
 *
 * [marcador] es la ayuda que se ve con el campo vacío; [lineas] mayor que uno
 * convierte el campo en multilínea (la descripción del folio). El teclado se
 * elige por [teclado] —numérico para el código postal, por ejemplo.
 */
@Composable
fun CampoDeGarantia(
    etiqueta: String,
    valor: String,
    onValor: (String) -> Unit,
    modifier: Modifier = Modifier,
    marcador: String = "",
    lineas: Int = 1,
    teclado: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onValor,
        modifier = modifier.fillMaxWidth(),
        label = { Text(text = etiqueta) },
        placeholder = if (marcador.isBlank()) {
            null
        } else {
            { Text(text = marcador) }
        },
        singleLine = lineas == 1,
        minLines = lineas,
        keyboardOptions = KeyboardOptions(keyboardType = teclado),
        shape = MspTheme.shapes.field
    )
}

/**
 * El estado del pad de firma, levantado fuera del composable para que la
 * pantalla pueda pedir el PNG al guardar.
 *
 * ## Por qué la capa sólo se graba con trazos
 *
 * [aPng] captura la `GraphicsLayer` del pad con `toImageBitmap()`, que en
 * Robolectric no es confiable. Con el pad vacío no hay nada que capturar, así
 * que [PadDeFirma] dibuja su contenido pelado (`drawContent()`) y las capturas
 * Roborazzi del formulario no tocan la capa. Es el mismo criterio que ya usa
 * `MspThemeRevealHost` para su rama animada.
 */
@Stable
class EstadoDelPadDeFirma {

    /** La capa del pad, enganchada por [PadDeFirma] al componerse. */
    internal var capa: GraphicsLayer? = null

    /** ¿El cliente ya trazó algo? Sin trazos no hay firma que capturar. */
    var hayTrazos: Boolean by mutableStateOf(false)
        private set

    private val trazos = mutableStateListOf<Path>()

    /** Los trazos vivos, en orden de dibujo. */
    internal val lineas: List<Path>
        get() = trazos

    /** El cobrador borró la firma: se tira todo y el pad vuelve al marcador. */
    fun limpiar() {
        trazos.clear()
        hayTrazos = false
    }

    internal fun empezar(x: Float, y: Float) {
        val trazo = Path()
        trazo.moveTo(x, y)
        trazos.add(trazo)
        hayTrazos = true
    }

    internal fun continuar(x: Float, y: Float) {
        trazos.lastOrNull()?.lineTo(x, y)
    }

    /**
     * Los bytes PNG de lo dibujado, o `null` si no hay trazos o la captura no
     * está disponible (Robolectric). La entrega sin firma se bloquea antes de
     * llegar acá; este `null` es la red de seguridad.
     *
     * Es `suspend` porque `GraphicsLayer.toImageBitmap()` lo es: la captura de
     * la capa espera al siguiente frame. El llamador la pide dentro de su
     * corrutina al tocar "Guardar entrega".
     */
    suspend fun aPng(): ByteArray? {
        val capa = capa ?: return null
        if (!hayTrazos) return null
        return runCatching {
            val bitmap = capa.toImageBitmap().asAndroidBitmap()
            val salida = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, CALIDAD_PNG, salida)
            salida.toByteArray()
        }.getOrNull()
    }
}

/** Crea y recuerda un [EstadoDelPadDeFirma] a lo largo de la recomposición. */
@Composable
fun rememberEstadoDelPadDeFirma(): EstadoDelPadDeFirma =
    androidx.compose.runtime.remember { EstadoDelPadDeFirma() }

/**
 * El pad donde el cliente firma con el dedo.
 *
 * El trazo se captura con `detectDragGestures` sobre el mismo `Box` que lo
 * pinta, así que el dedo y la tinta quedan alineados. La `GraphicsLayer` se
 * graba **sólo cuando hay trazos** (ver [EstadoDelPadDeFirma]).
 */
@Composable
fun PadDeFirma(estado: EstadoDelPadDeFirma, modifier: Modifier = Modifier) {
    val capa = rememberGraphicsLayer()
    estado.capa = capa
    val colorDelTrazo = MspTheme.colors.onSurface
    Box(
        modifier = modifier
            .clip(MspTheme.shapes.field)
            .background(MspTheme.colors.surface)
            .border(BORDE_DEL_PAD, MspTheme.colors.outline, MspTheme.shapes.field)
            .pointerInput(estado) {
                detectDragGestures(
                    onDragStart = { estado.empezar(it.x, it.y) },
                    onDrag = { cambio, _ ->
                        cambio.consume()
                        estado.continuar(cambio.position.x, cambio.position.y)
                    }
                )
            }
            .drawWithContent {
                if (estado.hayTrazos) {
                    capa.record { this@drawWithContent.drawContent() }
                    drawLayer(capa)
                } else {
                    drawContent()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pincel = Stroke(
                width = GROSOR_DEL_TRAZO.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
            estado.lineas.forEach { drawPath(it, color = colorDelTrazo, style = pincel) }
        }
        if (!estado.hayTrazos) {
            Text(
                text = TextosDeGarantias.FIRMA_MARCADOR,
                style = MspTheme.type.caption,
                color = MspTheme.colors.onSurfaceMuted
            )
        }
    }
}
