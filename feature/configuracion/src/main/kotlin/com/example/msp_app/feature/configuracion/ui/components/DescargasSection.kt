package com.example.msp_app.feature.configuracion.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.configuracion.ui.DescargaOpcional
import com.example.msp_app.feature.configuracion.ui.EstadoDeLaDescarga
import com.example.msp_app.feature.configuracion.ui.FilaDeDescarga

/** Prefijo del `testTag` de cada renglón; se completa con el nombre del enum. */
const val DESCARGA_TAG_PREFIJO = "msp_configuracion_descarga_"

/** El `testTag` del renglón de [cual]. */
fun tagDeLaDescarga(cual: DescargaOpcional): String = DESCARGA_TAG_PREFIJO + cual.name.lowercase()

/**
 * Alto mínimo tocable de un renglón. El repo pide **>=50 dp** por control, más
 * estricto que los 48 de Material, y no se baja: si un test lo cobra, sube la
 * implementación.
 *
 * `CadaPantallaDeCobranzaRespetaLaBarraDeEstadoTest` barre el grafo de cobranza
 * y esta sección no vive ahí, así que el toque lo mide
 * `DescargasSectionTest` dentro de este módulo.
 */
private val TOQUE_MINIMO = 50.dp

/**
 * **Sección "Descargas": las dos cosas opcionales que ocupan megas.**
 *
 * ## Por qué vive en Configuración y no en el camino del dinero
 *
 * Bajar 43.5 MB de modelo de voz o 25.5 MB de mapa es una decisión que se toma
 * **una vez, con wifi, sentado** — no parado en una puerta con el cobro a
 * medias. Meterla en la lista de clientes o en el detalle le robaría alto a las
 * pantallas donde el alto es lo escaso, y le pondría al cobrador una decisión de
 * megas en el momento exacto en que no puede tomarla. Configuración ya es la
 * pantalla de "cosas del teléfono", ya tiene secciones y ya está en el grafo.
 *
 * ## Qué dice cada renglón, y por qué los tres datos
 *
 * **Cuánto ocupa** (el número sale del paquete del módulo, nunca escrito a
 * mano), **qué gana** (las dos frases terminan en "sin señal", que es lo único
 * que le importa a quien cobra en la calle) y **en qué punto está**. Los tres
 * juntos son la decisión completa; cualquiera de los tres solo obliga a entrar a
 * la pantalla para saber si vale la pena entrar.
 *
 * ## El renglón sin origen
 *
 * El extracto de mapa **no está publicado en ningún servidor** todavía, así que
 * hoy el renglón del mapa cae en [EstadoDeLaDescarga.SIN_ORIGEN]: sin peso —no
 * hay paquete del cual sacarlo— y con "Todavía no se puede" en lugar del estado.
 * El renglón **sigue abriendo** su pantalla, y eso no es ofrecer algo que no se
 * puede hacer: lo que hay detrás es la explicación (de dónde saldría, y que el
 * pin del cliente funciona igual sin mapa), no un botón de descarga. La pantalla
 * tampoco pinta uno.
 */
@Composable
fun DescargasSection(
    descargas: List<FilaDeDescarga>,
    onAbrir: (DescargaOpcional) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Descargas",
            style = MspTheme.type.sectionHeader,
            color = MspTheme.colors.onSurfaceMuted
        )
        Spacer(Modifier.height(MspTheme.spacing.sm))
        descargas.forEachIndexed { indice, fila ->
            if (indice > 0) Spacer(Modifier.height(MspTheme.spacing.sm))
            RenglonDeDescarga(fila = fila, onAbrir = { onAbrir(fila.cual) })
        }
    }
}

/**
 * Un renglón: **una tarjeta, un destino** (principio 4). El `onClick` va en la
 * [Surface] entera y no hay ningún otro control adentro, así que no existe el
 * clickable dentro de otro clickable.
 *
 * ## APILADO, nunca el estado a la derecha del título
 *
 * La primera versión ponía el título a la izquierda y el estado a la derecha, y
 * **el golden a escala 2.0 la mató**: "Todavía no se puede" se lleva el ancho
 * que pide y le dejó al título una columna de tres letras —"Ma / pa / de / la /
 * rut / a"—, con "Ve las calles sin señal" partido en cuatro renglones debajo.
 * Es exactamente la lección que `TarjetaDelAvance` de `:core:speech` ya había
 * pagado con el mismo tipo de golden: **antes de truncar (o de encimar),
 * apilar** (principio 9). Apilado no hay reparto de ancho que negociar y las
 * tres escalas se comportan igual.
 */
@Composable
private fun RenglonDeDescarga(fila: FilaDeDescarga, onAbrir: () -> Unit) {
    Surface(
        onClick = onAbrir,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TOQUE_MINIMO)
            .testTag(tagDeLaDescarga(fila.cual)),
        shape = MspTheme.shapes.tile,
        color = MspTheme.colors.surface
    ) {
        Column(
            modifier = Modifier.padding(MspTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
        ) {
            Text(
                text = tituloDe(fila.cual),
                style = MspTheme.type.cardTitle,
                color = MspTheme.colors.onSurface
            )
            // El estado va PEGADO al título y no al pie: es la segunda pregunta
            // que se hace quien abre esto ("¿qué es?" y "¿ya lo tengo?"), y el
            // color lo hace legible de un vistazo sin tener que leer la tarjeta.
            Text(
                text = textoDelEstado(fila.estado),
                style = MspTheme.type.chipLabel,
                color = colorDelEstado(fila.estado)
            )
            Text(
                text = queGanaCon(fila.cual),
                style = MspTheme.type.caption,
                color = MspTheme.colors.onSurfaceMuted
            )
            // Sin paquete no hay peso: un "0 MB" sería un número inventado, y
            // ésta es la sección que existe para decir la verdad sobre los megas.
            if (fila.megas != null) {
                Text(
                    text = "Ocupa ${fila.megas} MB",
                    style = MspTheme.type.caption,
                    color = MspTheme.colors.onSurfaceMuted
                )
            }
        }
    }
}

private fun tituloDe(cual: DescargaOpcional): String = when (cual) {
    DescargaOpcional.DICTADO -> "Dictado por voz"
    DescargaOpcional.MAPA -> "Mapa de la ruta"
}

/**
 * Qué gana el cobrador. Las dos frases terminan igual a propósito: lo que las
 * dos descargas compran es **la misma cosa**, que la app siga sirviendo donde no
 * hay señal.
 */
private fun queGanaCon(cual: DescargaOpcional): String = when (cual) {
    DescargaOpcional.DICTADO -> "Dicta la nota sin señal"
    DescargaOpcional.MAPA -> "Ve las calles sin señal"
}

/** Vocabulario del cobrador, no del programador: nadie sabe qué es "SinOrigen". */
private fun textoDelEstado(estado: EstadoDeLaDescarga): String = when (estado) {
    EstadoDeLaDescarga.SIN_ORIGEN -> "Todavía no se puede"
    EstadoDeLaDescarga.AUSENTE -> "Sin descargar"
    EstadoDeLaDescarga.ESPERANDO_WIFI -> "Esperando wifi"
    EstadoDeLaDescarga.DESCARGANDO -> "Descargando"
    EstadoDeLaDescarga.INTERRUMPIDA -> "A medias"
    EstadoDeLaDescarga.LISTA -> "Listo"
}

/**
 * El color del ESTADO, que nunca es el de la selección (principio 8): acá no se
 * elige nada, así que `brand` no aparece.
 *
 * Ámbar y **nunca rojo** para "A medias": una descarga cortada **no perdió lo
 * bajado** —se reanuda con `Range`— y pintar de rojo algo que no se perdió
 * convierte un aviso en una alarma (principio 14).
 */
@Composable
private fun colorDelEstado(estado: EstadoDeLaDescarga): Color = when (estado) {
    EstadoDeLaDescarga.LISTA -> MspTheme.colors.statusPaid
    EstadoDeLaDescarga.DESCARGANDO, EstadoDeLaDescarga.ESPERANDO_WIFI ->
        MspTheme.colors.statusInfo

    EstadoDeLaDescarga.INTERRUMPIDA -> MspTheme.colors.statusPartial
    EstadoDeLaDescarga.SIN_ORIGEN, EstadoDeLaDescarga.AUSENTE -> MspTheme.colors.onSurfaceMuted
}
