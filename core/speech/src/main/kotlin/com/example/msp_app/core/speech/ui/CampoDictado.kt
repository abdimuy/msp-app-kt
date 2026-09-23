package com.example.msp_app.core.speech.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.speech.domain.EstadoDelDictado
import com.example.msp_app.core.speech.domain.GrabacionDictada

/** `testTag` de la caja del campo. Es la que se mide para probar que no salta. */
const val DICTADO_CAMPO_TAG: String = "dictado_campo"

/** `testTag` del campo de texto que se dicta y se corrige. */
const val DICTADO_TEXTO_TAG: String = "dictado_texto"

/** `testTag` del botón de micrófono. */
const val DICTADO_MICROFONO_TAG: String = "dictado_microfono"

/** `testTag` del renglón del audio adjunto. */
const val DICTADO_AUDIO_TAG: String = "dictado_audio"

/** `testTag` del aspa que quita el audio adjunto. */
const val DICTADO_QUITAR_AUDIO_TAG: String = "dictado_quitar_audio"

/** `testTag` del renglón ámbar que explica por qué no se puede dictar. */
const val DICTADO_AVISO_TAG: String = "dictado_aviso"

/**
 * **El campo con borde vivo (estilo C).**
 *
 * ## Lo que este campo NO es
 *
 * No es un modo conversación. No hay orbe, no hay pantalla completa, no hay
 * onda que ocupe el teléfono. El cobrador está parado en una puerta con el
 * cliente enfrente: lo que necesita es que la nota se llene sola mientras
 * habla, no una experiencia.
 *
 * ## Nada salta (principio 12), y así es como
 *
 * El anillo de [GROSOR] está **siempre** ahí: en reposo pintado en `outline`,
 * dictando pintado con el gradiente que gira. El campo mide exactamente lo
 * mismo en los dos estados, así que encender el micrófono no empuja ni un pixel
 * de lo que está abajo. La tentación —dibujar el borde solo al dictar— es
 * justamente la que empuja el contenido.
 *
 * Las barritas y el cronómetro tampoco empujan: viven en una columna de ancho
 * fijo ([COLUMNA_DEL_MICROFONO]) que está reservada también en reposo.
 *
 * ## El borde respeta movimiento reducido (principio 13)
 *
 * Con [LocalReduceMotion] en `true` el gradiente **no gira**: se queda en su
 * ángulo inicial. Sigue siendo un borde de colores —o sea que sigue diciendo
 * "te estoy escuchando"— sin el movimiento. Apagarlo del todo dejaría al
 * cobrador sin saber si el micrófono está abierto.
 *
 * ## Las barritas responden al VOLUMEN, no al reloj
 *
 * El mock las anima con `@keyframes` y retardos escalonados: se mueven aunque
 * nadie hable. Acá su altura es una función pura de
 * [EstadoDelDictado.Escuchando.nivel] y del índice, lo que consigue dos cosas
 * de una — dicen la verdad (callado = barritas bajas) y el golden es
 * determinista sin congelar ninguna animación.
 *
 * ## El texto es texto normal, editable
 *
 * [BasicTextField], no un resultado cerrado. Se toca, se corrige, y el
 * micrófono **agrega al final** en vez de reemplazar: dictar dos veces es
 * dictar dos frases, no perder la primera.
 */
@Composable
@Suppress("LongParameterList") // es un campo con audio, aviso y micrófono: son sus partes.
fun CampoDictado(
    etiqueta: String,
    marcador: String,
    texto: String,
    estado: EstadoDelDictado,
    puedeDictar: Boolean,
    grabacion: GrabacionDictada?,
    aviso: String?,
    habilitado: Boolean,
    onTexto: (String) -> Unit,
    onMicrofono: () -> Unit,
    onQuitarAudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MspTheme.colors
    val escuchando = estado as? EstadoDelDictado.Escuchando
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(DICTADO_CAMPO_TAG)
                .clip(MspTheme.shapes.field)
                .bordeVivo(activo = estado !is EstadoDelDictado.Reposo)
                .padding(GROSOR)
                .background(colors.surface2, MspTheme.shapes.field)
                .padding(MspTheme.spacing.sm)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)) {
                Column(modifier = Modifier.weight(1f)) {
                    Encabezado(etiqueta, estado)
                    CuerpoDelCampo(
                        texto = texto,
                        parcial = escuchando?.parcial.orEmpty(),
                        marcador = marcador,
                        habilitado = habilitado,
                        onTexto = onTexto
                    )
                }
                ColumnaDelMicrofono(
                    escuchando = escuchando,
                    puedeDictar = puedeDictar,
                    habilitado = habilitado,
                    onMicrofono = onMicrofono
                )
            }
        }
        if (grabacion != null) {
            RenglonDelAudio(grabacion = grabacion, onQuitar = onQuitarAudio)
        }
        if (aviso != null) {
            AvisoDelDictado(aviso)
        }
    }
}

/**
 * El encabezado del campo: la etiqueta y, mientras se escucha, el cronómetro.
 *
 * La etiqueta cambia de texto y de color al escuchar —"Escuchando" en `brand`—
 * y **no** de tamaño: es la misma línea, con el mismo alto, así que el cuerpo
 * de abajo no se mueve.
 */
@Composable
private fun Encabezado(etiqueta: String, estado: EstadoDelDictado) {
    val colors = MspTheme.colors
    val escuchando = estado as? EstadoDelDictado.Escuchando
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = when {
                escuchando != null -> "Escuchando"
                estado is EstadoDelDictado.Transcribiendo -> "Pasando a texto"
                else -> etiqueta
            },
            style = MspTheme.type.caption,
            color = if (estado is EstadoDelDictado.Reposo) {
                colors.onSurfaceMuted
            } else {
                colors.brand
            },
            modifier = Modifier.weight(1f)
        )
        if (escuchando != null) {
            Text(
                text = cronometro(escuchando.transcurridoMs),
                style = MspTheme.type.caption,
                color = colors.onSurfaceMuted
            )
        }
    }
}

/**
 * El texto confirmado y, detrás, lo que el motor todavía puede cambiar.
 *
 * El parcial va **gris y en una línea aparte**, no concatenado dentro del
 * `BasicTextField`: meterlo en el `value` dejaría al cobrador corrigiendo un
 * texto que el motor va a reescribir en el siguiente evento — y perdiendo la
 * corrección. Con whisper el parcial llega siempre vacío y esta línea no existe.
 */
@Composable
private fun CuerpoDelCampo(
    texto: String,
    parcial: String,
    marcador: String,
    habilitado: Boolean,
    onTexto: (String) -> Unit
) {
    val colors = MspTheme.colors
    // **El alto mínimo es lo que dice qué es esto.** Sin él el campo medía UNA
    // línea y, con el micrófono de 56 dp al lado, la caja entera se leía como
    // una barra con un marcador gris adentro — el dueño lo reportó como que el
    // campo de la nota "ni se ve". Un campo de nota tiene que verse como un
    // espacio donde cabe una frase, y la forma de decirlo es el tamaño. Es un
    // mínimo, no un alto fijo: una nota más larga lo sigue estirando.
    Box(modifier = Modifier.heightIn(min = ALTO_DE_LA_NOTA)) {
        if (texto.isEmpty() && parcial.isEmpty()) {
            Text(text = marcador, style = MspTheme.type.body, color = colors.onSurfaceMuted)
        }
        BasicTextField(
            value = texto,
            onValueChange = onTexto,
            enabled = habilitado,
            textStyle = LocalTextStyle.current
                .merge(MspTheme.type.body)
                .merge(TextStyle(color = colors.onSurface)),
            cursorBrush = SolidColor(colors.onSurface),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(DICTADO_TEXTO_TAG)
        )
    }
    if (parcial.isNotEmpty()) {
        Text(text = parcial, style = MspTheme.type.body, color = colors.onSurfaceMuted)
    }
}

/**
 * **Las barritas y el botón, dentro de UNA sola caja de [TOQUE].**
 *
 * Las barritas viven en la holgura de arriba del área tocable, no en un renglón
 * propio encima de ella. El golden de la pantalla completa destapó por qué
 * importa: con su propio renglón, el botón quedaba 18dp más abajo que el primer
 * renglón del texto y el campo se leía como una caja medio vacía con un
 * micrófono flotando en el fondo.
 *
 * La caja entera recibe el toque —56dp de alto por 56 de ancho, por encima de
 * los 50 que pide el repo— así que tocar las barritas también suelta el
 * micrófono, que es lo que un dedo espera.
 *
 * El ancho es **fijo** y está reservado también en reposo: si midiera lo que su
 * contenido, el texto de al lado se reacomodaría al empezar a dictar
 * (principio 12).
 */
@Composable
private fun ColumnaDelMicrofono(
    escuchando: EstadoDelDictado.Escuchando?,
    puedeDictar: Boolean,
    habilitado: Boolean,
    onMicrofono: () -> Unit
) {
    if (!puedeDictar) {
        // Sin dictado no hay columna **ni hueco**: reservar el ancho de un
        // control que nunca va a existir dejaría la nota angosta para siempre.
        return
    }
    val colors = MspTheme.colors
    Column(
        modifier = Modifier
            .size(TOQUE)
            .testTag(DICTADO_MICROFONO_TAG)
            .pointerInput(escuchando, habilitado) {
                detectTapGestures { if (habilitado) onMicrofono() }
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.height(ALTO_DE_LAS_BARRITAS)) {
            if (escuchando != null) Barritas(escuchando.nivel)
        }
        Box(
            modifier = Modifier
                .size(MICROFONO_VISIBLE)
                // Tinte de marca en reposo, marca SÓLIDA al escuchar. El mock
                // pinta el botón encendido casi negro (`#141A18`) y el golden en
                // oscuro lo desmintió: negro sobre fondo oscuro con un glifo
                // blanco encima deja el control invisible. `brand`/`onBrand`
                // contrasta en los dos temas, y además es el token que este repo
                // reserva para lo protagónico.
                .background(
                    if (escuchando != null) colors.brand else colors.brandTint,
                    MspTheme.shapes.control
                ),
            contentAlignment = Alignment.Center
        ) {
            // Micrófono en reposo, cuadrado al escuchar. El golden de la primera
            // vuelta destapó por qué importa: con un círculo, el botón encendido
            // decía "grabar" mientras ya grababa.
            Icon(
                imageVector = if (escuchando != null) {
                    DictadoIconos.Detener
                } else {
                    DictadoIconos.Microfono
                },
                // La descripción dice la ACCIÓN y cambia con el estado: es lo que
                // un lector de pantalla tiene que anunciar.
                contentDescription = if (escuchando != null) {
                    "detener el dictado"
                } else {
                    "dictar la nota"
                },
                tint = if (escuchando != null) colors.onBrand else colors.brand,
                modifier = Modifier.size(GLIFO)
            )
        }
    }
}

/**
 * Nueve barritas cuya altura sale del volumen.
 *
 * El perfil (más altas al centro) es lo que hace que se lea como una voz y no
 * como una barra de progreso. Función pura: mismo nivel, mismos píxeles.
 */
@Composable
private fun Barritas(nivel: Float) {
    val colors = MspTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ESPACIO_DE_BARRITA)
    ) {
        repeat(BARRITAS) { indice ->
            Box(
                modifier = Modifier
                    .width(ANCHO_DE_BARRITA)
                    .height(altoDeBarrita(indice, nivel))
                    .background(colors.brand, MspTheme.shapes.chip)
            )
        }
    }
}

/**
 * **El audio, que se queda.** Es la regla dura del dictado hecha pixel: si la
 * transcripción se equivocó con un apodo, el hecho sigue acá.
 *
 * El aspa quita el adjunto de ESTA captura; no borra nada que ya se haya
 * guardado.
 */
@Composable
private fun RenglonDelAudio(grabacion: GrabacionDictada, onQuitar: () -> Unit) {
    val colors = MspTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TOQUE)
            .background(colors.surface2, MspTheme.shapes.control)
            .padding(horizontal = MspTheme.spacing.sm)
            .testTag(DICTADO_AUDIO_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        Text(
            text = "Audio de la nota",
            style = MspTheme.type.caption,
            color = colors.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = cronometro(grabacion.duracionMs),
            style = MspTheme.type.caption,
            color = colors.onSurfaceMuted
        )
        Box(
            modifier = Modifier
                .size(TOQUE)
                .testTag(DICTADO_QUITAR_AUDIO_TAG)
                .pointerInput(grabacion.id) { detectTapGestures { onQuitar() } },
            contentAlignment = Alignment.Center
        ) {
            Text(text = "Quitar", style = MspTheme.type.caption, color = colors.brand)
        }
    }
}

/**
 * **Ámbar, nunca rojo** (principio 14): no se perdió trabajo. Sin permiso, sin
 * motor o con el micrófono ocupado, la nota se escribe a mano y se guarda
 * igual — el aviso dice qué pasó, no que algo se rompió.
 */
@Composable
private fun AvisoDelDictado(aviso: String) {
    val colors = MspTheme.colors
    Text(
        text = aviso,
        style = MspTheme.type.caption,
        color = colors.statusPartial,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.statusPartialTint, MspTheme.shapes.control)
            .padding(MspTheme.spacing.sm)
            .testTag(DICTADO_AVISO_TAG)
    )
}

/** El grosor del anillo. El mismo en los dos estados: es lo que impide el salto. */
private val GROSOR = 2.dp

private val ANCHO_DE_BARRITA = 3.dp

private val ESPACIO_DE_BARRITA = 2.dp

/** Alto mínimo tocable del repo. El design system ya pide 56dp; ése manda. */
private val TOQUE = 56.dp

/**
 * **Lo que mide la nota vacía**: alrededor de tres renglones de `body`.
 *
 * No es una cifra de gusto. Con una sola línea el campo quedaba más bajo que el
 * micrófono de [TOQUE] que lleva al lado, y una caja que sólo es tan alta como
 * su control se lee como un botón con texto, no como un espacio para escribir.
 * Tres renglones es lo que hace falta para que se lea como lo que es.
 */
private val ALTO_DE_LA_NOTA = 84.dp

private val MICROFONO_VISIBLE = 38.dp

/** El lado del glifo dentro del botón. */
private val GLIFO = 20.dp
