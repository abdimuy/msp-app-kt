@file:Suppress(
    "TooManyFunctions"
) // una pieza por cuadro de la rejilla; juntarlas escondería cuál pinta qué estado.

package com.example.msp_app.feature.pagos.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.Comprobantes
import com.example.msp_app.feature.pagos.domain.model.ComprobanteDelAbono
import com.example.msp_app.feature.pagos.domain.model.Miniatura
import com.example.msp_app.feature.pagos.ui.AccionesIconos
import com.example.msp_app.feature.pagos.ui.FalloDeLaFoto
import com.example.msp_app.feature.pagos.ui.IntentoFallido
import com.example.msp_app.feature.pagos.ui.OrigenDeLaFoto

/** `testTag` de la línea de comprobantes dentro de la hoja de confirmación. */
const val COMPROBANTES_EN_HOJA_TAG: String = "pagos_abono_comprobantes_hoja"

/** `testTag` de la sección de comprobantes de la pantalla de abono. */
const val COMPROBANTES_TAG: String = "pagos_abono_comprobantes"

/** `testTag` del **único** cuadro que agrega: el «+». */
const val AGREGAR_FOTO_TAG: String = "pagos_abono_agregar_foto"

/** Prefijo del `testTag` del tache que quita un cuadro; se completa con su id. */
const val QUITAR_FOTO_TAG: String = "pagos_abono_quitar_foto_"

/** Prefijo del `testTag` del cuadro ámbar de un intento que no entró; lleva su id. */
const val FALLO_FOTO_TAG: String = "pagos_abono_fallo_foto_"

/** `testTag` de la hoja que pregunta de dónde sale el comprobante. */
const val HOJA_DE_ORIGEN_TAG: String = "pagos_abono_hoja_origen"

/** `testTag` del velo de esa hoja. Tocarlo la cierra sin elegir. */
const val VELO_DE_ORIGEN_TAG: String = "pagos_abono_velo_origen"

/** Prefijo del `testTag` de cada renglón de la hoja; se completa con el origen. */
const val ORIGEN_TAG: String = "pagos_abono_origen_"

/**
 * Alto mínimo tocable. Copia deliberada del `TOQUE` privado de
 * `PiezasDelAbono.kt`: el plan pide >=50dp y el token del design system (56dp)
 * va por encima. No se declara, **se mide** en `AbonoSeVeYSeTocaTest`.
 */
private val TOQUE = 56.dp

/** Cuántos cuadros caben a lo ancho. Tres, como el mock. */
private const val COLUMNAS = 3

/** El hueco entre cuadros, y el mismo a lo alto que a lo ancho. */
private val HUECO = 8.dp

/** El recuadro tintado que lleva el glifo de cada renglón de la hoja. */
private val CAJA_DEL_GLIFO = 42.dp

/** El círculo VISIBLE del tache de un cuadro. Su área tocable es mayor; ver [TacheDelCuadro]. */
private val TACHE_VISIBLE = 28.dp

/** Lo que el tache se separa del borde del cuadro. */
private val MARGEN_DEL_TACHE = 4.dp

/**
 * **Los comprobantes del abono: una rejilla de miniaturas con un solo «+».**
 *
 * ## Qué cambió, y por qué
 *
 * Antes esto era una lista de renglones de texto que decían "foto 1" y "foto 2"
 * — una interfaz de fotos donde **nunca se veía la foto**— y tenía **tres**
 * formas de agregar: un botón en la fila del método, una pastilla al pie y la
 * lista misma. Cuando algo fallaba, el aviso decía "ese archivo no se acepta" sin
 * decir **cuál**, porque con tres puertas de entrada y un aviso suelto no había
 * forma de decirlo.
 *
 * La rejilla arregla las tres cosas de una: el cuadro **es** la foto, hay **un**
 * cuadro que agrega, y el que no entró se pinta **en su propio cuadro**.
 *
 * ## Las miniaturas NO usan un cargador de imágenes
 *
 * `coil` carga asíncrono y con caché, y un golden de Roborazzi sobre una imagen
 * que a veces llegó y a veces no deja de significar algo. Acá los píxeles llegan
 * ya decodificados en el estado ([Miniatura]) —el puerto los saca con
 * `BitmapFactory` + `inSampleSize`, **fuera del hilo principal**— y este
 * composable es puro sobre datos. La misma entrada da siempre el mismo pixel.
 *
 * ## El cuadro que falló es ÁMBAR, nunca rojo
 *
 * Ninguno de los tres motivos impide registrar el abono, y el rojo en esta
 * pantalla ya significa "esto no se puede registrar" (el bloqueo duro). Pintar
 * una foto fallida en rojo diría que el dinero está en riesgo, y no lo está: es
 * el mismo criterio del Ruling AM para el abono corto.
 *
 * El orden lo manda el mock: el «+» **primero y siempre en el mismo lugar**, para
 * que no se mueva conforme se llenan los cuadros. Desaparece —y solo entonces—
 * cuando ya no caben más.
 */
@Composable
fun SeccionDeComprobantes(
    comprobantes: List<ComprobanteDelAbono>,
    miniaturas: Map<String, Miniatura>,
    intentos: List<IntentoFallido>,
    puedeAgregar: Boolean,
    onAgregar: () -> Unit,
    onQuitar: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(COMPROBANTES_TAG),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        Text(
            text = "Fotos y archivos",
            style = MspTheme.type.cardTitle,
            color = MspTheme.colors.onSurface,
            modifier = Modifier.padding(top = MspTheme.spacing.sm)
        )
        val cuadros: List<@Composable () -> Unit> = buildList {
            if (puedeAgregar) add { CuadroDeAgregar(onAgregar) }
            comprobantes.forEach { comprobante ->
                add {
                    CuadroDeComprobante(
                        comprobante = comprobante,
                        miniatura = miniaturas[comprobante.id],
                        onQuitar = { onQuitar(comprobante.id) }
                    )
                }
            }
            intentos.forEach { intento ->
                add { CuadroFallido(intento = intento, onQuitar = { onQuitar(intento.id) }) }
            }
        }
        Rejilla(cuadros)
    }
}

/**
 * Los cuadros, de tres en tres.
 *
 * `chunked` y `Row`s y no un `LazyVerticalGrid`: esta sección vive dentro de una
 * columna que ya hace `verticalScroll`, y anidar un contenedor que scrollea en el
 * mismo eje revienta en tiempo de ejecución. La última fila se rellena con huecos
 * del mismo peso para que sus cuadros midan lo mismo que los de arriba — sin eso,
 * una fila de un solo cuadro lo pinta del ancho entero de la pantalla.
 */
@Composable
private fun Rejilla(cuadros: List<@Composable () -> Unit>) {
    Column(verticalArrangement = Arrangement.spacedBy(HUECO)) {
        cuadros.chunked(COLUMNAS).forEach { fila ->
            Row(horizontalArrangement = Arrangement.spacedBy(HUECO)) {
                fila.forEach { cuadro ->
                    Box(modifier = Modifier.weight(1f)) { cuadro() }
                }
                repeat(COLUMNAS - fila.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * **El único afordante que agrega.** Cuadrado, punteado y en el color de marca:
 * se lee como un hueco que espera algo, no como un botón más de la pantalla.
 *
 * Dice "Agregar" y no solo "+": un signo suelto no dice qué agrega. No dice
 * "Tomar foto" —como lo decía la variante descartada— porque desde acá se puede
 * llegar también a la galería y a un PDF, y prometer solo la cámara escondería
 * las otras dos.
 */
@Composable
private fun CuadroDeAgregar(onAgregar: () -> Unit) {
    val colors = MspTheme.colors
    Surface(
        onClick = onAgregar,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .testTag(AGREGAR_FOTO_TAG),
        shape = MspTheme.shapes.tile,
        color = colors.brandTint,
        border = BorderStroke(1.5.dp, colors.brand)
    ) {
        Column(
            modifier = Modifier.padding(MspTheme.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(
                MspTheme.spacing.xs,
                Alignment.CenterVertically
            ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                // La descripción dice la ACCIÓN, no el dibujo: es lo que un
                // lector de pantalla tiene que anunciar.
                contentDescription = "Agregar foto",
                tint = colors.brand,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = "Agregar",
                style = MspTheme.type.captionStrong,
                color = colors.brand,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Un comprobante puesto. Enseña la foto cuando hay píxeles que enseñar, y su
 * glifo cuando no los hay — un PDF, o una decodificación que falló.
 *
 * El glifo **no es un aviso**: el comprobante está adjunto y va a viajar con la
 * visita igual. Por eso va en el gris de la superficie y no en ámbar.
 */
@Composable
private fun CuadroDeComprobante(
    comprobante: ComprobanteDelAbono,
    miniatura: Miniatura?,
    onQuitar: () -> Unit
) {
    val colors = MspTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MspTheme.shapes.tile)
            .background(colors.surface2)
    ) {
        if (miniatura != null) {
            Miniatura(miniatura)
        } else {
            SinVistaPrevia(esPdf = comprobante.mime == PDF)
        }
        TacheDelCuadro(
            tag = QUITAR_FOTO_TAG + comprobante.id,
            sobreFoto = miniatura != null,
            onQuitar = onQuitar,
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}

/**
 * Los píxeles, convertidos a `ImageBitmap` **una sola vez por miniatura**.
 *
 * El `remember` lleva la [Miniatura] de clave: mientras sea la misma instancia
 * no se vuelve a armar el `Bitmap` en cada recomposición, que a cinco cuadros y
 * 320×320 píxeles cada uno sería medio megabyte de basura por scroll.
 *
 * `Bitmap.createBitmap` no puede fallar acá: [Miniatura] valida en su `init` que
 * el buffer alcance, y eso ocurre en el adaptador, donde el fallo todavía se
 * puede reportar sin tumbar la composición.
 */
@Composable
private fun Miniatura(miniatura: Miniatura) {
    val imagen = remember(miniatura) {
        Bitmap.createBitmap(
            miniatura.pixeles,
            miniatura.ancho,
            miniatura.alto,
            Bitmap.Config.ARGB_8888
        ).asImageBitmap()
    }
    Image(
        bitmap = imagen,
        // Sin descripción: el cuadro no aporta nada que un lector de pantalla
        // pueda anunciar —es la foto que el cobrador acaba de tomar— y lo que sí
        // se puede hacer con él (quitarlo) ya lo anuncia su tache.
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
    )
}

/** El cuadro de un comprobante sin foto que enseñar: un PDF, o un archivo que ya no está. */
@Composable
private fun SinVistaPrevia(esPdf: Boolean) {
    val colors = MspTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MspTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (esPdf) AccionesIconos.Archivo else AccionesIconos.Galeria,
            contentDescription = null,
            tint = colors.onSurfaceMuted,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = if (esPdf) "PDF" else "Adjunto",
            style = MspTheme.type.caption,
            color = colors.onSurfaceMuted,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * El cuadro de lo que **no** entró, en ámbar y en el lugar donde habría quedado.
 *
 * Dos o tres palabras y nada más: el cuadro mide unos 104dp y a escala 2.0 una
 * frase entera se cortaría, y un texto a medias es un dato falso, no uno
 * incompleto. Lo que el cobrador necesita saber acá es si vuelve a intentar, si
 * el archivo no sirve, o si ya no caben más — y **cuál** de los que eligió fue,
 * que es lo que contesta el cuadro por estar donde está.
 */
@Composable
private fun CuadroFallido(intento: IntentoFallido, onQuitar: () -> Unit) {
    val colors = MspTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MspTheme.shapes.tile)
            .background(colors.statusPartialTint)
            .border(1.5.dp, colors.statusPartial, MspTheme.shapes.tile)
            .testTag(FALLO_FOTO_TAG + intento.id)
    ) {
        // Sin glifo de advertencia, y eso se midió: a escala 2.0 el ícono
        // quedaba al lado del tache, dos redondeles compitiendo arriba del
        // cuadro, y le robaba el renglón que el texto necesitaba para no
        // cortarse. El relleno ámbar, el borde ámbar y el texto ámbar ya dicen
        // "esto no entró" tres veces; una cuarta es ruido (principio 5).
        Text(
            text = textoDe(intento.motivo),
            style = MspTheme.type.captionStrong,
            color = colors.statusPartial,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxSize()
                .wrapContentHeight(Alignment.CenterVertically)
                .padding(horizontal = MspTheme.spacing.xs)
        )
        TacheDelCuadro(
            tag = QUITAR_FOTO_TAG + intento.id,
            sobreFoto = false,
            onQuitar = onQuitar,
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}

/** Por qué no entró. Dos o tres palabras: es lo que cabe en un cuadro a escala 2.0. */
private fun textoDe(motivo: FalloDeLaFoto): String = when (motivo) {
    FalloDeLaFoto.NO_SE_PUDO_TOMAR -> "No se adjuntó"
    FalloDeLaFoto.TIPO_NO_PERMITIDO -> "No se acepta"
    FalloDeLaFoto.YA_NO_CABEN -> "Ya no caben"
}

/**
 * El tache que quita un cuadro.
 *
 * ## El área tocable es más grande que el círculo, a propósito
 *
 * El repo exige >=50dp tocables y el mock dibuja el círculo de 24. Lo que sube es
 * la **implementación**, nunca el mínimo: el círculo se queda en 28dp y la caja
 * que recibe el toque mide [TOQUE]. El precio es que el rincón superior derecho
 * del cuadro quita la foto; se paga porque **no hay ninguna otra acción en el
 * cuadro** que se pueda perder por eso — el cuadro no abre nada, así que no hay
 * toque ajeno al que robarle.
 *
 * [sobreFoto] decide el color: encima de una foto el círculo va oscuro y
 * translúcido (la única forma de que se vea sobre cualquier imagen), y encima de
 * un cuadro liso va en la superficie, donde el negro sería una mancha.
 */
@Composable
private fun TacheDelCuadro(
    tag: String,
    sobreFoto: Boolean,
    onQuitar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MspTheme.colors
    Box(
        modifier = modifier
            .size(TOQUE)
            .testTag(tag)
            // `pointerInput` y no un `Surface(onClick)`: la caja tocable es
            // transparente salvo por el círculo, y un `Surface` pintaría su
            // propio fondo sobre la foto.
            .pointerInput(tag) { detectTapGestures { onQuitar() } },
        contentAlignment = Alignment.TopEnd
    ) {
        Box(
            modifier = Modifier
                .padding(MARGEN_DEL_TACHE)
                .size(TACHE_VISIBLE)
                .background(
                    if (sobreFoto) TINTA_SOBRE_LA_FOTO else colors.surface,
                    MspTheme.shapes.chip
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Clear,
                contentDescription = "Quitar",
                tint = if (sobreFoto) Color.White else colors.onSurfaceMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * El círculo del tache **sobre una foto**: tinta fija, no un token del tema.
 *
 * Medido en el golden: con `colors.background` translúcido, en claro el círculo
 * salía casi blanco con el aspa blanca encima — el control desaparecía. Debajo
 * hay una FOTO, no una superficie de la app, y una foto no cambia de color con
 * el tema; lo único que garantiza contraste en los dos temas y sobre cualquier
 * imagen es el par oscuro/blanco. Es el `#0A0F0DAD` del mock, tal cual.
 */
private val TINTA_SOBRE_LA_FOTO = Color(0xFF0A0F0D).copy(alpha = 0.68f)

/** El MIME del único adjunto que no es una imagen. */
/** El tipo del recibo en PDF, leído del dominio: una sola cadena para los dos lados. */
private val PDF = Comprobantes.PDF

/**
 * **La hoja del «+»: de dónde sale el comprobante.**
 *
 * Se dibuja DENTRO de la composición —velo + hoja abajo— y no en un
 * `ModalBottomSheet`, por el mismo motivo que [HojaDeConfirmacion]: así vive en el mismo árbol que la captura (una rotación la
 * recompone en vez de perderla en otra ventana) y el golden la captura entera,
 * con velo y todo, como el mock la enseña.
 *
 * Las tres opciones llevan su explicación porque las tres se comportan distinto y
 * el nombre solo no lo dice: la cámara **sale de la app**, la galería deja
 * escoger **varias** de un tirón, y el explorador es el único que alcanza un PDF
 * —y cobranza acepta PDF a propósito, porque los recibos SAT llegan así.
 *
 * El pie cuenta los espacios que quedan, y sale de
 * [Comprobantes.MAXIMO] — no de un número escrito a mano.
 */
@Composable
fun HojaDeOrigenDelComprobante(
    espaciosLibres: Int,
    onOrigen: (OrigenDeLaFoto) -> Unit,
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MspTheme.colors
    Column(modifier = modifier.fillMaxSize()) {
        // El velo es HERMANO de la hoja, no su padre: con el velo envolviéndola,
        // su `detectTapGestures` se queda con el toque destinado a los renglones.
        // Es el defecto que `HojaDeConfirmacion` ya midió y arregló así.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(colors.background.copy(alpha = ALFA_DEL_VELO))
                .testTag(VELO_DE_ORIGEN_TAG)
                .pointerInput(Unit) { detectTapGestures { onCerrar() } }
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // `background(color, shape)` y NO `clip(shape) + background`, con
                // esquinas SOLO arriba: el KDoc de [HojaDeConfirmacion]
                // `:feature:pagos` lo midió — un `clip` con esquinas desiguales
                // hace que la contención del hit-test caiga a `Path.op`, que bajo
                // Robolectric deja los toques de los renglones en cero. La hoja no
                // desborda, así que el recorte no hace falta.
                .background(colors.surface, RoundedCornerShape(topStart = RADIO, topEnd = RADIO))
                // Un `pointerInput` INERTE: no consume nada —los renglones siguen
                // respondiendo— pero hace que la hoja sea alcanzable por el
                // hit-test, y con eso ningún toque se cuela a la captura de abajo.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent()
                        }
                    }
                }
                .testTag(HOJA_DE_ORIGEN_TAG)
                .padding(MspTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
        ) {
            Agarradera()
            Text(
                text = "Agregar",
                style = MspTheme.type.detailTitle,
                color = colors.onSurface
            )
            RenglonDeOrigen(
                origen = OrigenDeLaFoto.CAMARA,
                glifo = AccionesIconos.Camara,
                titulo = "Tomar foto",
                detalle = "Se abre la cámara",
                onOrigen = onOrigen
            )
            RenglonDeOrigen(
                origen = OrigenDeLaFoto.GALERIA,
                glifo = AccionesIconos.Galeria,
                titulo = "Elegir de la galería",
                detalle = "Puedes escoger varias",
                onOrigen = onOrigen
            )
            RenglonDeOrigen(
                origen = OrigenDeLaFoto.ARCHIVO,
                glifo = AccionesIconos.Archivo,
                titulo = "Subir un archivo",
                detalle = "Imagen o PDF",
                onOrigen = onOrigen
            )
            Text(
                text = pieDeEspacios(espaciosLibres),
                style = MspTheme.type.caption,
                color = colors.onSurfaceMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** "Quedan 3 espacios de 5". Singular cuando queda uno: un plural falso se nota. */
private fun pieDeEspacios(libres: Int): String {
    val cuantos = if (libres == 1) "1 espacio" else "$libres espacios"
    return "Quedan $cuantos de ${Comprobantes.MAXIMO}"
}

@Composable
private fun RenglonDeOrigen(
    origen: OrigenDeLaFoto,
    glifo: ImageVector,
    titulo: String,
    detalle: String,
    onOrigen: (OrigenDeLaFoto) -> Unit
) {
    val colors = MspTheme.colors
    Surface(
        onClick = { onOrigen(origen) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TOQUE)
            .testTag(ORIGEN_TAG + origen.name),
        shape = MspTheme.shapes.tile,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Row(
            modifier = Modifier.padding(MspTheme.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(CAJA_DEL_GLIFO)
                    .background(colors.brandTint, MspTheme.shapes.control),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = glifo,
                    contentDescription = null,
                    tint = colors.brand,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = titulo, style = MspTheme.type.bodyStrong, color = colors.onSurface)
                Text(text = detalle, style = MspTheme.type.caption, color = colors.onSurfaceMuted)
            }
            Icon(
                imageVector = AccionesIconos.Chevron,
                contentDescription = null,
                tint = colors.onSurfaceMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** Lo opaco que va el velo. El mismo de [HojaDeConfirmacion]. */
private const val ALFA_DEL_VELO = 0.72f

/** El radio de las dos esquinas de arriba de la hoja. El mismo de [HojaDeConfirmacion]. */
private val RADIO = 24.dp

/** La agarradera del mock: dice que la hoja se puede bajar. */
@Composable
private fun Agarradera() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = MspTheme.spacing.xs),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = ANCHO_DE_LA_AGARRADERA, height = ALTO_DE_LA_AGARRADERA)
                .background(MspTheme.colors.outline, MspTheme.shapes.chip)
        )
    }
}

private val ANCHO_DE_LA_AGARRADERA = 40.dp

private val ALTO_DE_LA_AGARRADERA = 4.dp

/**
 * La línea de comprobantes **dentro de la hoja de confirmación**, entre la cifra
 * y el flujo de saldos.
 *
 * Se pinta SIEMPRE, también en cero, y ese es su trabajo entero: la hoja existe
 * para que el cobrador vea lo que va a registrar antes de registrarlo, y "sin
 * comprobante" es un dato que se puede corregir con un toque de "editar".
 * Enseñarla solo cuando hay fotos convertiría el olvido en silencio.
 *
 * Sigue siendo una LÍNEA y no una rejilla, aunque la sección de arriba ya enseñe
 * las fotos: la hoja es el paso dos de la confirmación del dinero, y meterle
 * miniaturas le robaría espacio a la cifra, al veredicto y a las bandas de
 * rareza —las tres piezas de seguridad que ningún cambio de foto puede mover.
 */
@Composable
fun ComprobantesDeLaHoja(cuantos: Int, modifier: Modifier = Modifier) {
    Text(
        text = when (cuantos) {
            0 -> "Sin comprobante"
            1 -> "1 comprobante"
            else -> "$cuantos comprobantes"
        },
        style = MspTheme.type.captionStrong,
        color = MspTheme.colors.onSurfaceMuted,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .testTag(COMPROBANTES_EN_HOJA_TAG)
    )
}
