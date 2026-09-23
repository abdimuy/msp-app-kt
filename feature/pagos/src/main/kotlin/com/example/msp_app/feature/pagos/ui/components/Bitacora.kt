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
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.ToqueDelContacto
import com.example.msp_app.feature.pagos.domain.model.ContactoDeCobranza
import com.example.msp_app.feature.pagos.domain.model.UbicacionDelCobro
import com.example.msp_app.feature.pagos.ui.AccionesIconos
import java.time.LocalDate

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
 * **Lo que una fila necesita para saber si su toque pregunta o abre el mapa.**
 *
 * Viaja en un objeto y no en tres parámetros sueltos de [ContactoEnLinea] por lo
 * mismo que `AccionesDeLaLinea` y `AccionesDeLaFicha`: la fila ya recibe varias
 * lambdas, y el juego completo se pasa de una pieza en cada una de las TRES
 * pantallas que pintan contactos.
 *
 * **Su default deja la fila exactamente como estaba.** Con [contactos] vacía
 * ningún contacto puede ser "el último cobro de su cuenta", así que
 * [ToqueDelContacto.de] devuelve siempre `MAPA` o `NADA` — no hace falta una
 * segunda copia de esa regla aquí. Es lo que deja montar la fila en un
 * `@Preview`, en un golden o en un test que no sabe nada del ticket.
 *
 * @property contactos la línea de tiempo que la pantalla está pintando — la
 *   MISMA lista, no una preparada aparte.
 * @property hoy el día de negocio de la carga, del `AppClock` inyectado del caso
 *   de uso. Nunca leído desde dentro de un `@Composable`.
 * @property onPreguntar qué hacer cuando el toque significa "pregunta": la
 *   pantalla abre [HojaDelContacto] sobre sí misma. `null` cuando no hay hoja
 *   que abrir, y entonces el toque cae al mapa — un renglón que hoy se toca
 *   nunca se vuelve inerte por faltar esta lambda.
 * @property onVerTicket qué hacer cuando el toque abre el ticket **sin**
 *   preguntar: el cobro de hoy que cierra su cuenta y se capturó sin punto
 *   medido ([ToqueDelContacto.TICKET]). `null` deja ese renglón como estaba,
 *   o sea sin tocar: sin punto y sin ticket no queda nada que abrir.
 */
@Immutable
data class ToqueDeLaFila(
    val contactos: List<ContactoDeCobranza> = emptyList(),
    val hoy: LocalDate = LocalDate.EPOCH,
    val onPreguntar: ((ContactoDeCobranza) -> Unit)? = null,
    val onVerTicket: ((ContactoDeCobranza) -> Unit)? = null
)

/**
 * La lambda de toque de una fila de contacto, o `null` cuando no hay nada que
 * abrir.
 *
 * Existe para que las dos mitades de la regla —*se toca si y solo si hay punto
 * medido*, y *se pregunta si y solo si es el cobro de hoy que cierra su
 * cuenta*— vivan en UN lugar y las compartan las tres pantallas. Repartirlas
 * entre la fila y sus llamadores es cómo se termina con un gesto que significa
 * una cosa en la bitácora y otra en el detalle.
 *
 * El veredicto sale de [ToqueDelContacto], que es dominio puro y se prueba sin
 * pantalla; aquí sólo se cablea cada resultado a su lambda. `PREGUNTAR` sin
 * [ToqueDeLaFila.onPreguntar] cae al mapa a propósito — ver el KDoc de ese
 * campo.
 */
internal fun abridorDe(
    contacto: ContactoDeCobranza,
    onVerUbicacion: ((UbicacionDelCobro) -> Unit)?,
    toque: ToqueDeLaFila = ToqueDeLaFila()
): ToqueResuelto {
    val mapa: () -> ToqueResuelto = {
        val punto = contacto.ubicacion
        if (punto != null && onVerUbicacion != null) {
            ToqueResuelto({ onVerUbicacion(punto) }, VER_DONDE_FUE)
        } else {
            ToqueResuelto(null, VER_DONDE_FUE)
        }
    }
    val ticket: () -> ToqueResuelto = {
        val ver = toque.onVerTicket
        if (ver != null) {
            ToqueResuelto({ ver(contacto) }, VER_EL_TICKET)
        } else {
            // Sin lambda de ticket y sin punto no queda nada que abrir: el
            // renglón se queda como estaba, inerte.
            mapa()
        }
    }
    return when (ToqueDelContacto.de(contacto, toque.contactos, toque.hoy)) {
        ToqueDelContacto.NADA -> ToqueResuelto(null, VER_DONDE_FUE)
        ToqueDelContacto.MAPA -> mapa()
        ToqueDelContacto.TICKET -> ticket()
        ToqueDelContacto.PREGUNTAR -> {
            val pregunta = toque.onPreguntar
            if (pregunta != null) {
                ToqueResuelto({ pregunta(contacto) }, ELEGIR_QUE_ABRIR)
            } else {
                mapa()
            }
        }
    }
}

/**
 * El toque de una fila ya resuelto: qué hace y qué anuncia.
 *
 * Los dos juntos y no por separado porque **son el mismo veredicto**: una fila
 * que abre la hoja y le dice a TalkBack *"Ver dónde fue"* miente sobre a dónde
 * lleva, y es exactamente la clase de desajuste que aparece cuando el anuncio se
 * calcula en otro lado. [abrir] en `null` significa que la fila no se toca, y
 * entonces [anuncio] no se usa.
 */
internal data class ToqueResuelto(val abrir: (() -> Unit)?, val anuncio: String)

/**
 * Lo que anuncia la fila que, al tocarla, pregunta qué abrir.
 *
 * Dice **qué va a pasar**, no qué se va a ver: es lo único que distingue este
 * toque del de las demás filas para quien no ve la hoja aparecer.
 */
internal const val ELEGIR_QUE_ABRIR: String = "Elegir qué abrir"

/**
 * Lo que anuncia la fila que, al tocarla, abre el ticket y nada más.
 *
 * Es el cobro de hoy que se capturó sin punto ([ToqueDelContacto.TICKET]). No
 * puede decir *"Ver dónde fue"* —no hay dónde— ni *"Elegir qué abrir"* —no hay
 * nada que elegir—: las dos serían una promesa falsa para quien navega sin ver
 * la pantalla.
 */
internal const val VER_EL_TICKET: String = "Ver el ticket"

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
