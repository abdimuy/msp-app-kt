package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme

/** `testTag` del bloque de un mes — el contenedor entero, encabezado y filas. */
const val BLOQUE_DEL_MES_TAG: String = "pagos_mes_bloque"

/**
 * **El fondo que separa un mes del siguiente.**
 *
 * El defecto que esto cierra: la bitácora pintaba el mes con un encabezado en
 * versalitas de 12 sp y una **raya de ancho completo** entre el nombre y el
 * subtotal. El dueño la vio y dijo *"no me gusta esta raya enorme ahí"* — y
 * tenía razón por algo más que el gusto: esa raya es el trazo más largo de la
 * pantalla y se lleva la mirada, mientras el nombre del mes —lo que de verdad
 * separa— va en el tamaño más chico del renglón.
 *
 * ## El separador ya no se dibuja: se deja de dibujar
 *
 * Lo que parte los meses aquí es el **borde del bloque**: el mes vive dentro de
 * un contenedor de esquinas redondeadas, y entre un bloque y el siguiente queda
 * un hueco de [MspTheme.spacing.md] donde no se pinta nada. Un canto y un hueco
 * separan más que una línea, y no compiten con nada por la mirada. No hay una
 * sola raya nueva en este archivo.
 *
 * ## El tono alterna, y por eso son dos roles y no un color nuevo
 *
 * [tonoDelMes] va y viene entre `surface` y `background`: el mes más reciente
 * —índice 0— sale sobre `surface`, el de abajo sobre `background`, y así. Son
 * los dos roles que la paleta ya tiene para "la hoja" y "la página", así que el
 * contraste entre ellos es el mismo que la app ya usa en cada tarjeta, y
 * funciona en los dos temas sin inventar un hex: en claro son `#FFFFFF` contra
 * `#F4F6F5`, en oscuro `#141917` contra el negro puro.
 *
 * **No se usó el azul a propósito.** En esta app el azul es el color de las
 * acciones —CTAs, pastillas encendidas—, y un mes no se toca.
 *
 * **Asume que la lista vive sobre `MspTheme.colors.background`**, que es donde
 * están sus dos llamadores (`BitacoraScreen` y la línea del detalle de venta).
 * Sobre una hoja blanca la alternancia se leería al revés; ahí el agrupamiento
 * es por cercanía —*Hoy · Esta semana*—, no por mes, y este bloque no se monta.
 */
@Composable
fun tonoDelMes(indice: Int): Color =
    if (indice % 2 == 0) MspTheme.colors.surface else MspTheme.colors.background

/**
 * El bloque de un mes **entero**, para quien pinta los meses en un `Column`
 * normal — el detalle de venta.
 *
 * La bitácora no puede usarlo: su lista va perezosa y cada fila es un `item`
 * del `LazyColumn`, así que allá el fondo se aplica tramo por tramo con
 * [tramoDelMes]. Las dos rutas comparten [tonoDelMes] y [formaDelTramo], que es
 * lo que impide que un mes se vea de dos formas según la pantalla.
 */
@Composable
fun BloqueDelMes(
    indice: Int,
    modifier: Modifier = Modifier,
    contenido: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .tramoDelMes(indice = indice, arriba = true, abajo = true)
            .testTag(BLOQUE_DEL_MES_TAG),
        content = contenido
    )
}

/**
 * Un **tramo** del bloque de un mes: el fondo del mes [indice], redondeado sólo
 * por los cantos que de verdad son canto.
 *
 * Existe para el `LazyColumn` de la bitácora, donde el encabezado y cada fila
 * son `item`s separados y no hay un contenedor común que pintar. Cada tramo
 * pinta su propio pedazo del mismo color; como se tocan sin hueco, el resultado
 * en pantalla es un solo bloque continuo. [arriba] redondea el canto de arriba
 * y abre el hueco contra el mes anterior; [abajo] redondea el de abajo y deja
 * aire antes de que el fondo se corte.
 *
 * **Pinta, no recorta.** Usa `Modifier.background`, que dibuja detrás y **nunca
 * recorta a sus hijos**, en vez de `clip` + `background`. Es deliberado: el
 * encargo del dueño fue *"no tocarás los items de los pagos"*, y un `clip` de
 * 18 dp de radio sí puede morder la esquina de un renglón. Con `background` la
 * fila se mide y se pinta exactamente igual que antes de que existiera este
 * archivo — lo cobra `LosMesesSeDistinguenTest`.
 *
 * Tampoco mete padding horizontal: el fondo va a todo el ancho que le den, así
 * que el renglón no se corre ni un dp hacia adentro.
 */
@Composable
fun Modifier.tramoDelMes(indice: Int, arriba: Boolean = false, abajo: Boolean = false): Modifier =
    this
        // ANTES del fondo: es el hueco ENTRE bloques, y va sin pintar.
        .padding(top = if (arriba) MspTheme.spacing.md else 0.dp)
        .background(color = tonoDelMes(indice), shape = formaDelTramo(arriba, abajo))
        // DESPUÉS del fondo: es aire DENTRO del bloque, debajo de la última
        // fila, para que el color no se corte pegado al renglón.
        .padding(bottom = if (abajo) MspTheme.spacing.sm else 0.dp)

/** Esquinas redondas sólo donde el tramo es canto del bloque. */
private fun formaDelTramo(arriba: Boolean, abajo: Boolean): Shape = RoundedCornerShape(
    topStart = if (arriba) RADIO_DEL_BLOQUE else 0.dp,
    topEnd = if (arriba) RADIO_DEL_BLOQUE else 0.dp,
    bottomEnd = if (abajo) RADIO_DEL_BLOQUE else 0.dp,
    bottomStart = if (abajo) RADIO_DEL_BLOQUE else 0.dp
)

/**
 * El radio del bloque, **el mismo que `MspTheme.shapes.sectionCard`**.
 *
 * Se repite el dp en vez de leer el token porque el token es un `Shape` y un
 * `Shape` no dice cuánto mide: el bloque necesita redondear **unas** esquinas y
 * no todas, y para eso hace falta el número. Si `sectionCard` cambia, esto
 * cambia con él.
 */
private val RADIO_DEL_BLOQUE = 18.dp
