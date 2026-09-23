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
 * **Sección "Descargas": lo opcional que ocupa megas.**
 *
 * ## Por qué vive en Configuración y no en el camino del dinero
 *
 * Bajar 43.5 MB de modelo de voz es una decisión que se toma **una vez, con
 * wifi, sentado** — no parado en una puerta con el cobro a medias. Meterla en la
 * lista de clientes o en el detalle le robaría alto a las pantallas donde el
 * alto es lo escaso, y le pondría al cobrador una decisión de megas en el
 * momento exacto en que no puede tomarla. Configuración ya es la pantalla de
 * "cosas del teléfono", ya tiene secciones y ya está en el grafo.
 *
 * ## Qué dice cada renglón, y por qué los tres datos
 *
 * **Cuánto ocupa** (el número sale del paquete del módulo, nunca escrito a
 * mano), **qué gana** (termina en "sin señal", que es lo único que le importa a
 * quien cobra en la calle) y **en qué punto está**. Los tres juntos son la
 * decisión completa; cualquiera de los tres solo obliga a entrar a la pantalla
 * para saber si vale la pena entrar.
 *
 * ## Sigue siendo una LISTA con un solo renglón
 *
 * Hubo dos: el mapa era el otro, y se fue con `:core:mapas` —47.9 MB de `.so`
 * que viajaban en el APK aunque nadie bajara las teselas—. La sección no se
 * colapsó a un renglón escrito a mano porque la forma correcta no cambió: lo que
 * pinta son las descargas que haya, y hoy hay una.
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
 * **el golden a escala 2.0 la mató**: "Todavía no se puede" —el estado del
 * renglón del mapa, que se fue con `:core:mapas`— se llevaba el ancho
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
            // El número sale del paquete que el módulo anuncia, nunca escrito a
            // mano: ésta es la sección que existe para decir la verdad sobre los
            // megas, y un peso inventado acá sería el peor lugar para mentir.
            Text(
                text = "Ocupa ${fila.megas} MB",
                style = MspTheme.type.caption,
                color = MspTheme.colors.onSurfaceMuted
            )
        }
    }
}

private fun tituloDe(cual: DescargaOpcional): String = when (cual) {
    DescargaOpcional.DICTADO -> "Dictado por voz"
}

/**
 * Qué gana el cobrador: **que la app siga sirviendo donde no hay señal**. Es lo
 * único que una descarga opcional compra, y por eso la frase termina así.
 */
private fun queGanaCon(cual: DescargaOpcional): String = when (cual) {
    DescargaOpcional.DICTADO -> "Dicta la nota sin señal"
}

/** Vocabulario del cobrador, no del programador: nadie sabe qué es "Interrumpido". */
private fun textoDelEstado(estado: EstadoDeLaDescarga): String = when (estado) {
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
    EstadoDeLaDescarga.AUSENTE -> MspTheme.colors.onSurfaceMuted
}
