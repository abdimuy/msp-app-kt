@file:Suppress(
    "TooManyFunctions"
) // una pieza por banda de la hoja continua; juntarlas no las haria mas legibles.

package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.core.designsystem.component.MspCard
import com.example.msp_app.core.designsystem.component.MspMoneyText
import com.example.msp_app.core.designsystem.component.MspProgressBar
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.model.ProductoDeVenta
import com.example.msp_app.feature.pagos.domain.model.ResumenDelCliente
import com.example.msp_app.feature.pagos.domain.model.VentaDelCliente
import com.example.msp_app.feature.pagos.ui.AccionesIconos
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** `testTag` de la pastilla de atrasos del bloque de saldo. */
const val ATRASOS_DEL_CLIENTE_TAG: String = "pagos_cliente_atrasos"

/**
 * `testTag` de cada acción de contacto (llamar / whatsapp / ficha / cómo llegar).
 *
 * **Las cuatro llevan el mismo**, que es el que deja contarlas y medirles el
 * toque. A una acción concreta se la nombra por su etiqueta —`onNodeWithText`—,
 * que es lo que el cobrador ve: hubo un `COMO_LLEGAR_TAG` aparte y no servía,
 * porque se encadenaba con éste sobre el mismo nodo y uno de los dos quedaba sin
 * efecto.
 */
const val ACCION_DE_CONTACTO_TAG: String = "pagos_cliente_accion"

/** `testTag` de la cifra de parcialidad del bloque de saldo. */
const val PARCIALIDAD_DEL_CLIENTE_TAG: String = "pagos_cliente_parcialidad"

/**
 * `testTag` del bloque **entero** del saldo total: el rótulo, la cifra y la
 * pastilla de atrasos.
 *
 * Existe porque el criterio del dueño se enuncia sobre el bloque y no sobre el
 * rótulo: *"el `SALDO TOTAL` tiene que caber entero sobre el dock"*. Midiendo
 * sólo el rótulo la aserción daba verde con la cifra cortada por la mitad, que
 * es exactamente el defecto que hay que cazar.
 */
const val SALDO_DEL_CLIENTE_TAG: String = "pagos_cliente_saldo"

/** `testTag` de un renglón de producto del cliente. */
const val FILA_DE_PRODUCTO_TAG: String = "pagos_cliente_producto"

/** `testTag` del "ver los N contactos" al pie de la bitácora. */
const val VER_LOS_CONTACTOS_TAG: String = "pagos_cliente_ver_contactos"

/** `testTag` del cuadro de la puerta — el mapa, o las señas cuando no hay mapa. */
const val CUADRO_DE_LA_PUERTA_TAG: String = "pagos_cliente_cuadro_puerta"

/**
 * Lo que anuncia el cuadro cuando se puede tocar.
 *
 * Un `clickable` sin etiqueta es un control invisible para TalkBack. La
 * etiqueta se queda aunque el cuadro ya tenga texto adentro: el `clickable` no
 * fusiona a sus descendientes, así que sin esto el control seguiría anunciándose
 * mudo y la calle se leería como un párrafo suelto, no como el destino del
 * toque.
 */
internal const val VER_LA_UBICACION = "Ver la ubicación"

private val DIA_Y_MES: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", BUSINESS_LOCALE)

/**
 * Una **hoja continua**: una tarjeta cuyo interior son secciones separadas por
 * un hairline **de canto a canto**.
 *
 * ## Por qué no son tarjetas sueltas
 *
 * La pantalla anterior apilaba una tarjeta por bloque, con aire entre ellas. Con
 * seis bloques eso son seis bordes, seis radios y cinco huecos, y el resultado es
 * la pila de fichas que el dueño llamó "se ve medio rara". Agrupar los bloques
 * que hablan del mismo tema —identidad, dinero, ventas— en UNA hoja deja tres
 * objetos en vez de seis, y el separador interno sigue diciendo dónde termina un
 * bloque y empieza el siguiente.
 *
 * **El separador va de canto a canto y no sangrado.** Un hairline con margen se
 * lee como "estas dos cosas son hermanas"; uno completo se lee como "aquí cambia
 * el tema", que es lo que estos separadores dicen. Por eso las secciones traen su
 * propio padding en vez de que lo ponga la hoja: si el padding fuera de la hoja,
 * el separador no podría llegar al borde.
 */
@Composable
fun HojaContinua(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    MspCard(modifier = modifier.fillMaxWidth(), shape = MspTheme.shapes.card) {
        Column(content = content)
    }
}

/**
 * Una sección dentro de [HojaContinua], con su padding y —salvo la primera— su
 * hairline de canto a canto encima.
 *
 * El separador va ARRIBA y no abajo para que la última sección no cierre con una
 * línea suelta contra el borde de la tarjeta.
 */
@Composable
fun SeccionDeHoja(
    modifier: Modifier = Modifier,
    primera: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (!primera) Separador()
        Column(
            modifier = Modifier.padding(MspTheme.spacing.md),
            content = content
        )
    }
}

/**
 * El label de una hoja, con el padding que lo pega al primer renglón de abajo.
 *
 * No usa [LabelDeSeccion] porque aquél está pensado para vivir SUELTO entre
 * tarjetas y trae aire arriba y abajo. Dentro de una hoja el aire de abajo sobra:
 * el label y el primer renglón son el mismo bloque, y separarlos los haría ver
 * como dos cosas.
 */
@Composable
fun TituloDeHoja(texto: String, modifier: Modifier = Modifier) {
    Text(
        text = texto.uppercase(BUSINESS_LOCALE),
        style = MspTheme.type.eyebrow,
        color = MspTheme.colors.onSurfaceMuted,
        modifier = modifier.padding(
            start = MspTheme.spacing.md,
            end = MspTheme.spacing.md,
            top = MspTheme.spacing.md
        )
    )
}

/**
 * El renglón de identidad: el racimo de estados de sus cuentas, la zona al lado
 * y, debajo, **la dirección escrita**.
 *
 * El racimo aquí **no repite** lo que dicen los chips de "sus ventas": está a una
 * pantalla de distancia, no a unos píxeles, y contesta de un vistazo la pregunta
 * con la que el cobrador abre esta pantalla ("¿cómo viene esta puerta?") sin
 * tener que desplazar. Es la misma razón por la que en la LISTA sí se quitó: allá
 * el racimo y los chips compartían tarjeta.
 *
 * ## La dirección escrita SIEMPRE se ve, y ésta es la regla dura
 *
 * Palabras del dueño, mirando el aparato: *"eso no se puede quitar nunca,
 * siempre se tiene que ver la dirección escrita"*. No es una preferencia de
 * layout: es con lo que el cobrador encuentra la casa, y esta pantalla se abre
 * parado en la banqueta.
 *
 * ### Lo que se creyó en `f8621920`, y por qué era razonable
 *
 * Aquel commit retiró este renglón y dejó escrito, textualmente:
 *
 * > *"No se pierde nada de lo que este KDoc defendía: la dirección se ve más, no
 * > menos, y entera."*
 *
 * **La frase es falsa y el dueño la refutó desde el teléfono.** Se deja aquí, no
 * se borra, porque el razonamiento que la produjo sí era razonable y hay que
 * poder verlo: con el fixture que había —`calle = "C. Hidalgo 214"`,
 * `ciudad = "Centro"`— el cuadro de abajo efectivamente decía la dirección
 * completa, en dos renglones y más grande, y el golden lo confirmaba.
 *
 * ### Por qué era falsa: `calle` NO es "la calle"
 *
 * Medido en el productor, no supuesto. `Sale.CALLE` llega de
 * `DIRS_CLIENTES.CALLE` de Microsip, y msp-api la **compone**
 * (`internal/ventas/infra/microsip/cliente_writer.go`, `buildCalle`):
 *
 * ```
 * NOMBRE_CALLE + " " + NUM_EXT + "\n" + COLONIA + ", " + POBLACION
 * ```
 *
 * O sea: calle, número, colonia y población, en un campo de varios renglones —
 * por eso el código viejo de `:app` la pinta con `.replace("\n", " ")`. El
 * fixture de las pruebas trae *"C. Hidalgo 214"*, que es una dirección que no
 * existe en producción, y **eso fue lo que escondió el defecto**.
 *
 * Con la cadena real, el cuadro de abajo no puede decirla entera: mide 130 dp a
 * escala normal y **40 dp a `GRANDE` y `MUY_GRANDE`**, donde le cabe un solo
 * renglón con elipsis. La mitad que se pierde es justo la colonia. Y media
 * dirección no es una dirección incompleta: es una dirección equivocada.
 *
 * ### Dónde vive ahora, y cómo se resolvió la duplicación
 *
 * La queja que originó `f8621920` era legítima: el cuadro repetía las **dos**
 * cadenas de este bloque —dirección y ruta— en el mismo orden, y eso se lee como
 * un error de copiado. Se resuelve por el otro lado, que es la opción que el
 * dueño puso primero en su lista: **el cuadro deja de repetir la ciudad**. Su
 * línea de apoyo pasa a decir sólo la ruta (ver [SenasDelFondo]), que es
 * exactamente la forma que tenía antes de `f8621920` y de la que nunca se quejó.
 *
 * El reparto queda así, y cada pieza dice algo distinto:
 * - **Acá**: la dirección escrita **entera** —calle, número, colonia, ciudad—,
 *   en `captionStrong` sobre `onSurface`, con [RENGLONES_DE_LA_DIRECCION]
 *   renglones. Es el dato, y está a todas las escalas.
 * - **El cuadro**: el bloque de calle en grande, para confirmar de un vistazo
 *   *"¿es aquí?"* parado en la puerta. Es el vistazo, y puede recortarse.
 *
 * Entre un dibujo y un dato cede el dibujo — el mismo criterio que ya tenía
 * escrito el criterio de arriba cuando bajó el cuadro a 40 dp para pagar este renglón.
 *
 * **La zona se queda donde estaba.** Es el encabezado de la tarjeta —dice de qué
 * ruta es la puerta, no dónde está—.
 *
 * ## Lo que cuesta
 *
 * Un renglón de `captionStrong` (dos si la dirección no cabe en uno) más
 * `spacing.xs`. `LaFichaSeVeYSeTocaTest` lo cobra: todo lo que crece acá empuja
 * la hoja del dinero. Se midió antes de darlo por bueno, y la medición está en
 * el mensaje del commit.
 */
@Composable
fun BloqueDeIdentidad(
    estados: List<androidx.compose.ui.graphics.vector.ImageVector>,
    colores: List<Pair<Color, Color>>,
    zona: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
        ) {
            estados.forEachIndexed { indice, icono ->
                val (fondo, contenido) = colores[indice]
                Box(
                    modifier = Modifier
                        .size(CUADRO_DEL_RACIMO)
                        .clip(MspTheme.shapes.chip9)
                        .background(fondo),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icono,
                        contentDescription = null,
                        tint = contenido,
                        modifier = Modifier.size(CUADRO_DEL_RACIMO * 0.6f)
                    )
                }
            }
            // La zona es contexto: dice de qué ruta es la puerta, no dónde está. Se
            // queda chica, apagada y de un solo renglón.
            Text(
                text = zona.ifBlank { SIN_DATO },
                style = MspTheme.type.caption,
                color = MspTheme.colors.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ZONA_DEL_CLIENTE_TAG)
            )
        }
        // **La dirección ya no se pinta acá: la dice el telón, sobre el mapa.**
        //
        // Tuvo su renglón propio a todo el ancho desde que el dueño reportó que
        // *"ni se ve casi"*, y ese arreglo sigue siendo correcto — lo que cambió
        // es dónde. Con el telón diciéndola completa arriba, repetirla en esta
        // tarjeta es exactamente la duplicación que él ya había rechazado una
        // vez: *"se lee como un error de copiado"*.
        //
        // La regla dura —*"eso no se puede quitar nunca, siempre se tiene que
        // ver la dirección escrita"*— **se sigue cumpliendo**, y por eso este
        // cambio es legítimo y no una regresión a `f8621920`: se ve entera, sin
        // desplazar, y con un "Ver más" explícito cuando no cabe en dos
        // renglones. Ver `TelonDelNombre`.
    }
}

/**
 * `testTag` de la zona del renglón de identidad.
 *
 * Existe porque la ruta se dice **en dos lugares** —acá como encabezado y en la
 * línea de apoyo del cuadro de la puerta—, y un `onNodeWithText` a secas
 * encuentra los dos y falla por ambigüedad.
 */
const val ZONA_DEL_CLIENTE_TAG: String = "pagos_cliente_zona"

/** Una acción de la fila: su etiqueta, su glifo y qué hace. */
private data class AccionDelCliente(
    val etiqueta: String,
    val icono: ImageVector,
    val onClick: () -> Unit
)

/**
 * Las tres acciones de contacto, como **iconos y no botones de texto**.
 *
 * Botones de texto en fila ("Llamar", "WhatsApp", "Cómo llegar") a 360 dp dejan
 * cada uno poco ancho y parten la palabra. Como iconos con su etiqueta debajo
 * caben, y la fila mide lo mismo.
 *
 * ## Eran cuatro: "Ficha" se fue al dock
 *
 * El dueño pidió que las Notas de la puerta se noten, y el dock tenía su tercer
 * espacio **diseñado y vacío** desde el rediseño (el "⋯" que ocupaba ese hueco
 * en el detalle de venta era el único que lo usaba, y ya se quitó). Mudarlas
 * ahí cuesta **cero dp verticales** —el dock ya existe—
 * y de paso gana el distintivo, que esta fila de iconos no podía dar: acá el
 * botón se ve igual con la puerta en blanco y con algo anotado.
 *
 * ## "Cómo llegar" está SIEMPRE, y por qué cambió
 *
 * Antes era condicional: aparecía acá sólo cuando el bloque de mapa de arriba no
 * se pintaba, porque con mapa el botón ya vivía dentro del cuadro. Ese bloque se
 * fue con `:core:mapas` —el renderizador pesaba 47.9 MB de `.so` en cuatro ABIs
 * y viajaba aunque nadie bajara las teselas—, así que ya no hay un segundo
 * camino con el cual chocar: la acción es una sola y vive acá.
 *
 * Lo que abre es la app de mapas del teléfono, con la coordenada del último
 * cobro cuando se midió una (`DetalleCliente.ultimoCobroAqui`) y la dirección
 * escrita cuando no. Ver `IntentAccionesExternasAdapter`.
 *
 * ## Cuatro en una fila a 1.0, DOS POR RENGLÓN a las escalas grandes
 *
 * **Medido en el golden, no supuesto.** Con la cuarta acción, un cuarto del
 * ancho deja ~71 dp por celda, y a 1.5 eso parte "whatsapp" —que no tiene
 * espacio donde quebrarse— en `whatsap` + una `p` huérfana; a 2.0 queda
 * `whats` + `app`. Una etiqueta partida a mitad de palabra no se lee como texto
 * acomodado, se lee como una falta de ortografía de la app, que es exactamente
 * lo que [AccionDeContacto] ya había decidido evitar cuando eligió dos renglones
 * antes que un truncado.
 *
 * Así que antes de partir, **apilar** (principio 9): a `GRANDE` y `MUY_GRANDE`
 * las cuatro se acomodan dos por renglón, cada celda pasa a ~146 dp y las cuatro
 * etiquetas entran enteras. A `NORMAL` caben las cuatro en una fila —el golden
 * `pagos_cliente_light_1_0` lo muestra con aire de sobra— y no se toca.
 *
 * El toque es de [TOQUE_DE_ACCION] y no de los 44 dp del mock: la regla del repo
 * son **50 dp mínimos** y es más estricta que los 48 de Material. Lo que crece es
 * la caja tocable; el cuadro de color se queda en 44, así que el dibujo es el del
 * mock y el dedo tiene el área que la regla exige.
 */
@Composable
fun AccionesDelCliente(
    onLlamar: () -> Unit,
    onWhatsApp: () -> Unit,
    onComoLlegar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val acciones = listOf(
        AccionDelCliente("Llamar", AccionesIconos.Llamar, onLlamar),
        AccionDelCliente("WhatsApp", AccionesIconos.WhatsApp, onWhatsApp),
        AccionDelCliente("Cómo llegar", AccionesIconos.Pin, onComoLlegar)
    )
    // DOS por renglón fijo, no `size / 2`. Con cuatro acciones las dos fórmulas
    // daban lo mismo; con tres, `size / 2` da UNA por renglón y la fila pasa de
    // dos renglones a TRES — más alto, que es lo único que no sobra en esta
    // pantalla. Con el dos fijo, 4 se parte en [2,2] y 3 en [2,1]: dos renglones
    // en los dos casos, el mismo alto de siempre.
    val porRenglon = if (LocalFontSizeLevel.current == FontSizeLevel.NORMAL) {
        acciones.size
    } else {
        POR_RENGLON_APILADO
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
    ) {
        acciones.chunked(porRenglon).forEach { renglon ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
            ) {
                renglon.forEach { accion ->
                    AccionDeContacto(
                        etiqueta = accion.etiqueta,
                        icono = accion.icono,
                        onClick = accion.onClick,
                        modifier = Modifier.weight(1f)
                    )
                }
                // El renglón incompleto se rellena con aire, no se deja corto:
                // sin esto la única celda de [2,1] se comería el ancho entero y
                // "Cómo llegar" quedaría del doble de ancho que "Llamar".
                repeat(porRenglon - renglon.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun AccionDeContacto(
    etiqueta: String,
    icono: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = TOQUE_DE_ACCION)
            .testTag(ACCION_DE_CONTACTO_TAG),
        shape = MspTheme.shapes.control,
        color = Color.Transparent
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = MspTheme.spacing.xs)
        ) {
            Box(
                modifier = Modifier
                    .size(CUADRO_DE_ACCION)
                    .clip(MspTheme.shapes.control)
                    .background(MspTheme.colors.brandTint),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icono,
                    contentDescription = null,
                    tint = MspTheme.colors.brand,
                    modifier = Modifier.size(GLIFO_DE_ACCION)
                )
            }
            Spacer(Modifier.height(MspTheme.spacing.xs))
            // Dos renglones antes que perder una letra: a escala MUY_GRANDE,
            // "whatsapp" recortado a "whatsap" no se lee como texto cortado, se lee
            // como una falta de ortografía de la app.
            Text(
                text = etiqueta,
                style = MspTheme.type.caption,
                color = MspTheme.colors.onSurfaceMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * El saldo total con la pastilla de atrasos a la derecha.
 *
 * ## La pastilla se APILA antes que truncarse
 *
 * "12 atrasos" recortado a "12 atr…" sigue diciendo doce. Recortado a "Al" —que
 * es lo que pasaba con el ancho que quedaba a escalas grandes— **dice algo
 * falso**, no algo incompleto. Por eso [widthIn] deja que empuje y, cuando no
 * cabe junto al monto, el `Row` la baja de renglón en vez de comérsela.
 *
 * Y va en **ámbar** (`statusPartial`), no en rojo: el rojo de esta app significa
 * "no cae" / "se negó", y traer atrasos no es lo mismo que negarse a pagar.
 */
@Composable
fun SaldoDelCliente(
    saldo: Money,
    atrasos: Int,
    modifier: Modifier = Modifier,
    ocultos: Boolean = false
) {
    Column(modifier = modifier.fillMaxWidth().testTag(SALDO_DEL_CLIENTE_TAG)) {
        Text(
            text = "saldo total".uppercase(BUSINESS_LOCALE),
            style = MspTheme.type.overline,
            color = MspTheme.colors.onSurfaceMuted
        )
        Spacer(Modifier.height(MspTheme.spacing.xs + 2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
        ) {
            MspMoneyText(
                amount = saldo.amount,
                masked = ocultos,
                style = MspTheme.type.amountHero,
                color = MspTheme.colors.onSurface,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (atrasos > 0) {
                Text(
                    text = if (atrasos == 1) "1 atraso" else "$atrasos atrasos",
                    style = MspTheme.type.chipLabel,
                    color = MspTheme.colors.statusPartial,
                    maxLines = 1,
                    modifier = Modifier
                        .widthIn(min = 0.dp)
                        .clip(MspTheme.shapes.chip)
                        .background(MspTheme.colors.statusPartialTint)
                        .padding(
                            horizontal = MspTheme.spacing.sm + MspTheme.spacing.xs,
                            vertical = MspTheme.spacing.xs + 1.dp
                        )
                        .testTag(ATRASOS_DEL_CLIENTE_TAG)
                )
            }
        }
    }
}

/**
 * Las dos cifras del pie del saldo: **parcialidad · últ. pago**.
 *
 * ## Por qué ya no son "suele dar" y "pídele hoy"
 *
 * El dueño las marcó como que "no sirven para nada" y pidió la parcialidad en
 * su lugar. La parcialidad sale de
 * [com.example.msp_app.feature.pagos.application.CargarDetalleCliente.resumenDe]
 * — la SUMA de la parcialidad de sus cuentas activas, la misma con la que ya
 * se arma [ResumenDelCliente.ritmo] — así que no es una cifra nueva, es una
 * que ya se calculaba y no se pintaba.
 *
 * La etiqueta ("parcialidad") y el formato de moneda son los mismos que usa
 * el pie del detalle de VENTA (`PieDeLaVenta` en `DetalleVentaScreen.kt`, vía
 * [MspMoneyText] → `formatMoneyMxn`): no se inventa un formato nuevo, y
 * [MspMoneyText] además respeta [ocultos], que ese pie no necesita porque el
 * detalle de venta no trae el interruptor de privacidad.
 *
 * ## Por qué ahora sí reusa la pieza compartida
 *
 * Este bloque armaba su propia fila de dos porque [TresDatos] fijaba TRES
 * celdas y era lo único que había. Desde que el dueño le quitó el conteo de
 * abonos al pie del detalle de venta, **dos bloques distintos necesitan la
 * fila de dos**, así que la decisión de layout —fila mientras quepa, apilada a
 * `GRANDE` y `MUY_GRANDE`— vive en [DosDatos], al lado de [TresDatos] y con el
 * mismo criterio. Dos copias de esa regla es cómo se termina con una pantalla
 * que se apila y otra que no.
 */
@Composable
fun CifrasDelCliente(
    resumen: ResumenDelCliente,
    modifier: Modifier = Modifier,
    ocultos: Boolean = false
) {
    DosDatos(
        modifier = modifier,
        primero = { celda ->
            CifraDelCliente(
                clave = "parcialidad",
                monto = resumen.parcialidad,
                ocultos = ocultos,
                modifier = celda.testTag(PARCIALIDAD_DEL_CLIENTE_TAG)
            )
        },
        segundo = { celda ->
            CifraDelCliente(
                clave = "últ. pago",
                texto = resumen.ultimoPago?.let { DIA_Y_MES.format(it) } ?: SIN_DATO,
                modifier = celda
            )
        }
    )
}

@Composable
private fun CifraDelCliente(
    clave: String,
    modifier: Modifier = Modifier,
    monto: Money? = null,
    texto: String? = null,
    color: Color = MspTheme.colors.onSurface,
    ocultos: Boolean = false
) {
    Column(modifier = modifier) {
        Text(
            text = clave.uppercase(BUSINESS_LOCALE),
            style = MspTheme.type.overline,
            color = MspTheme.colors.onSurfaceMuted,
            maxLines = 1
        )
        Spacer(Modifier.height(MspTheme.spacing.xs))
        when {
            monto != null -> MspMoneyText(
                amount = monto.amount,
                masked = ocultos,
                style = MspTheme.type.kvValue,
                color = color
            )

            else -> Text(
                text = texto ?: SIN_DATO,
                style = MspTheme.type.kvValue,
                color = color,
                maxLines = 1
            )
        }
    }
}

/**
 * El ritmo del cliente: la tira de doce semanas con su cuenta a la derecha y, al
 * pie, el día en que le toca la ruta.
 *
 * La tira es [TiraDeRitmo] —literalmente la misma que pinta la pantalla de
 * venta—, no una copia: dos tiras con dos escalas de color que se despegan sería
 * el mismo dato contando dos historias.
 *
 * El "N de 12" es además el portador que **no es color**: sin él, la tira sola
 * dejaría el significado en manos del verde y el gris.
 */
@Composable
fun RitmoDelCliente(
    resumen: ResumenDelCliente,
    diaDeRuta: String,
    frecuencia: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                text = "ritmo · ${resumen.semanasTotales} semanas".uppercase(BUSINESS_LOCALE),
                style = MspTheme.type.overline,
                color = MspTheme.colors.onSurfaceMuted,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${resumen.semanasCumplidas} de ${resumen.semanasTotales}",
                style = MspTheme.type.caption,
                color = MspTheme.colors.onSurfaceMuted
            )
        }
        Spacer(Modifier.height(MspTheme.spacing.sm + MspTheme.spacing.xs))
        TiraDeRitmo(resumen.ritmo)
        Spacer(Modifier.height(MspTheme.spacing.sm + MspTheme.spacing.xs))
        Text(
            text = diaDeLaRuta(diaDeRuta, frecuencia),
            style = MspTheme.type.caption,
            color = MspTheme.colors.onSurfaceMuted,
            maxLines = 1
        )
    }
}

/**
 * "Su día es jueves · semanal", o solo lo que se sepa.
 *
 * Con el día vacío NO se escribe "su día es —": una frase con un hueco se lee
 * como un defecto de la app. Se dice solo la frecuencia, y si tampoco hay
 * frecuencia no se dice nada.
 */
private fun diaDeLaRuta(dia: String, frecuencia: String): String {
    val partes = listOfNotNull(
        dia.takeIf { it.isNotBlank() }?.let { "Su día es ${it.lowercase(BUSINESS_LOCALE)}" },
        frecuencia.takeIf { it.isNotBlank() }?.lowercase(BUSINESS_LOCALE)
    )
    return partes.joinToString(" · ")
}

/**
 * `testTag` de una fila de venta dentro del detalle de cliente.
 *
 * Vivía en `TarjetasDeDinero.kt`, junto a `FilaDeVenta` — la tarjeta que esta
 * fila reemplazó y que se retiró por no tener ya ningún llamador. Se muda aquí,
 * al lado de su único usuario, para que no quede una constante huérfana en un
 * archivo que no la usa.
 */
const val FILA_DE_VENTA_TAG: String = "pagos_fila_venta"

/**
 * Una venta dentro de la hoja: nombre y saldo arriba, chip de estado y atrasos
 * abajo, y la barra de avance al pie.
 *
 * **La venta se nombra por su producto, no por su folio.** "V-5021" no le dice
 * nada a alguien parado en una puerta; "Refrigerador Mabe" es como el cobrador y
 * el cliente llaman a esa cuenta. El folio solo aparece cuando no hay
 * descripción, que es el único caso en que sigue siendo lo mejor que hay.
 *
 * **La fila entera es el destino** y no lleva ningún control dentro: un
 * `clickable` anidado obligaría a apuntar, y esta pantalla se usa con el teléfono
 * en una mano.
 */
@Composable
fun VentaEnLaHoja(
    venta: VentaDelCliente,
    onAbrir: () -> Unit,
    modifier: Modifier = Modifier,
    ocultos: Boolean = false
) {
    Surface(
        onClick = onAbrir,
        color = Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TOQUE_DE_ACCION)
            .testTag(FILA_DE_VENTA_TAG)
    ) {
        Column(modifier = Modifier.padding(MspTheme.spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
            ) {
                Text(
                    text = venta.descripcion.ifBlank { venta.folio },
                    style = MspTheme.type.listTitle,
                    color = MspTheme.colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                MspMoneyText(
                    amount = venta.saldo.amount,
                    masked = ocultos,
                    style = MspTheme.type.amountSale,
                    color = MspTheme.colors.onSurface
                )
            }
            Spacer(Modifier.height(MspTheme.spacing.sm + 1.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
            ) {
                ChipDeEstado(venta.estado)
                Spacer(Modifier.weight(1f))
                if (venta.atrasos > 0) {
                    Text(
                        text = if (venta.atrasos == 1) "1 atraso" else "${venta.atrasos} atrasos",
                        style = MspTheme.type.chipLabel,
                        color = MspTheme.colors.statusPartial,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(MspTheme.shapes.chip)
                            .background(MspTheme.colors.statusPartialTint)
                            .padding(
                                horizontal = MspTheme.spacing.sm + MspTheme.spacing.xs,
                                vertical = MspTheme.spacing.xs + 1.dp
                            )
                    )
                }
            }
            Spacer(Modifier.height(MspTheme.spacing.sm + MspTheme.spacing.xs))
            MspProgressBar(
                progress = venta.avance,
                height = 4.dp,
                fillColor = MspTheme.colors.heroProgressFill,
                trackColor = MspTheme.colors.progressTrack
            )
        }
    }
}

/**
 * Un producto de la casa: caja y nombre.
 *
 * **Sin importe a la derecha, aunque el dato ya exista.** Lo que el cobrador
 * necesita aquí es reconocer qué hay en esa casa; el precio de cada mueble vive
 * en el detalle de su venta, junto al saldo con el que tiene sentido compararlo.
 * Ponerlo también acá sería una tercera cifra de dinero compitiendo con el saldo
 * y con la parcialidad, que son las que mandan la conversación.
 */
@Composable
fun ProductoDelCliente(producto: ProductoDeVenta, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MspTheme.spacing.md, vertical = MspTheme.spacing.sm + 4.dp)
            .testTag(FILA_DE_PRODUCTO_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm + MspTheme.spacing.xs)
    ) {
        Icon(
            imageVector = AccionesIconos.Producto,
            contentDescription = null,
            tint = MspTheme.colors.onSurfaceMuted,
            modifier = Modifier.size(GLIFO_DE_ACCION)
        )
        Text(
            text = producto.nombre,
            style = MspTheme.type.listTitle,
            color = MspTheme.colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * "Ver los N contactos ›" al pie de la bitácora — o "Ver 1 contacto" en
 * singular: "Ver los 1 contactos" lee mal y es el mismo defecto que ya se
 * cerró en el subtítulo de la bitácora (`"N contactos"` vs `"1 contacto"`).
 */
@Composable
fun VerLosContactos(cuantos: Int, onVer: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onVer,
        color = Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TOQUE_DE_ACCION)
            .testTag(VER_LOS_CONTACTOS_TAG)
    ) {
        Row(
            modifier = Modifier.padding(MspTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
        ) {
            Text(
                text = if (cuantos == 1) "Ver 1 contacto" else "Ver los $cuantos contactos",
                style = MspTheme.type.captionStrong,
                color = MspTheme.colors.brand
            )
            Icon(
                imageVector = AccionesIconos.Chevron,
                contentDescription = null,
                tint = MspTheme.colors.brand,
                modifier = Modifier.size(MspTheme.spacing.md)
            )
        }
    }
}

/** El cuadro de estado del racimo de identidad. */
private val CUADRO_DEL_RACIMO = 22.dp

/** El cuadro de color de una acción — los 44 dp del mock. */
private val CUADRO_DE_ACCION = 44.dp

/** El glifo dentro de un cuadro de acción. */
private val GLIFO_DE_ACCION = 18.dp

/**
 * El área tocable mínima de esta pantalla.
 *
 * **50 dp, no los 44 del dibujo.** La regla del repo es más estricta que los 48
 * de Material y no se baja: lo que crece es la caja que recibe el dedo, no el
 * cuadro pintado. La mide
 * `CadaPantallaDeCobranzaRespetaLaBarraDeEstadoTest`, no un golden — en
 * Robolectric un golden no puede ver un área tocable.
 */
private val TOQUE_DE_ACCION = 50.dp

/**
 * Cuántas acciones caben por renglón cuando la escala obliga a apilar.
 *
 * Dos, y **fijo**: ver el comentario en [AccionesDelCliente]. Es lo que mantiene
 * la fila en dos renglones con tres acciones y con cuatro. La fórmula anterior
 * —`acciones.size / 2`— daba lo mismo con cuatro y **una por renglón** con tres,
 * o sea tres renglones y una fila más alta.
 */
private const val POR_RENGLON_APILADO = 2

// ---------------------------------------------------------------------------
// LA SEÑA DEL FONDO SE FUE, Y LA REEMPLAZÓ EL TELÓN
//
// Era el chip "Punto medido", la calle en grande y la ruta en tenue, plantados
// sobre la parte alta del fondo. Sobrevivió a tres recortes del mapa y en cada
// uno perdió un renglón: primero la ruta, luego el chip, hasta quedarse con la
// calle sola en 54 dp.
//
// Desde el 2026-09-25 el fondo mide 300 dp y lleva `TelonDelNombre` abajo, que
// dice el NOMBRE y la dirección COMPLETA con su "Ver más". La seña decía menos
// en el mismo sitio, así que dejarla habría sido pintar la calle dos veces a
// dos dedos de distancia — la duplicación que el dueño ya rechazó una vez.
//
// Lo que NO se fue es el criterio que la encogió tres veces, porque sigue
// mandando en cualquier cosa que se ponga sobre el mapa:
//
//   **Entre un dibujo y un dato, cede el dibujo.**
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// EL CHIP "PUNTO MEDIDO" SE RETIRÓ, Y ACÁ QUEDA POR QUÉ
//
// Era un pin y dos palabras en color de marca sobre `brandTint`, y decía algo
// concreto: *esta puerta está medida*, o sea que "cómo llegar" va a dejar al
// cobrador en la puerta exacta y no en la calle. Nunca afirmaba **dónde** —eso
// habría sido mentira sobre una banda de texto—, y sin coordenada no se pintaba.
//
// Lo que lo sacó fue el espacio, no el argumento. De fondo, la seña dispone de
// **54 dp** —los 118 del fondo menos los 64 que se lleva el renglón del nombre,
// que flota encima del mapa— y el chip (29 dp) más la calle (25) no entran. En
// el golden `pagos_cliente_light_1_0` se vio la calle cortada por la mitad.
//
// Puestos a dejar uno se queda la calle. **No es una elección nueva**: es el
// criterio que este archivo ya había escrito para las escalas grandes —*"el
// vistazo a la puerta sirve más que el contexto"*— aplicado ahora también a
// `NORMAL`, porque el fondo cedió alto para que el `SALDO TOTAL` no quedara
// debajo de la barra.
//
// **Y lo que decía no se perdió donde importa.** Con punto medido, lo que se ve
// en el fondo es el mapa con su pin; sin punto, el fondo es la dirección en
// grande y muy tenue (ver `FondoSinPunto`). La distinción entre una puerta
// medida y una sin medir sigue estando a la vista sin abrir nada — cambió el
// portador, no el dato. El día que el fondo recupere alto, esto es un `git
// show` y veinte líneas.
// ---------------------------------------------------------------------------

/**
 * Los datos de la puerta que la hoja de identidad perdió en el rediseño: **el
 * aval y la última visita**.
 *
 * ## Por qué esto es un arreglo y no una función nueva
 *
 * La versión anterior de la pantalla los pintaba en una sección "datos del
 * cliente" (`git show ec47f007:…/DetalleClienteScreen.kt`, líneas 300-309). El
 * rediseño a hoja continua siguió un mock que ya los había perdido, y se fueron
 * sin que nadie lo notara:
 * [com.example.msp_app.feature.pagos.domain.model.DetalleCliente.aval] y
 * `ultimaVisita` quedaron con **cero usos** en la pantalla. Los dos contestan
 * preguntas que se hacen parado en la puerta: *"¿a quién le llamo si no
 * contesta?"* y *"¿hace cuánto vine?"*.
 *
 * Es el mismo error que el principio 25 del brief manda evitar —leer el KDoc
 * antes de rediseñar— cometido por la otra puerta: leyendo el mock en vez del
 * modelo.
 *
 * ## Por qué renglones compactos y NO filas clave/valor con hairline
 *
 * **Medido, no elegido por gusto.** La primera versión usaba [FilaClaveValor]
 * dentro de su propia [SeccionDeHoja], que es la anatomía normal de esta hoja.
 * Costaba ~161 dp a `MUY_GRANDE` —32 de padding de sección, 24 de padding
 * vertical por fila y el texto escalado— y con eso `LaFichaSeVeYSeTocaTest` se
 * puso rojo: *"el dinero termina en 760.0.dp y el dock empieza en 672.0.dp"*,
 * **88 dp tapado** a `GRANDE` y a `MUY_GRANDE`.
 *
 * Todo lo que se agrega a la hoja de identidad empuja la hoja del dinero, que
 * es por lo que el cobrador abrió esta pantalla. Así que estos dos datos viven
 * **dentro de la sección de identidad**, en el mismo registro tipográfico que
 * zona y dirección: un renglón cada uno, etiqueta apagada y valor encima del
 * texto. Cuestan un tercio y dicen lo mismo.
 *
 * ## El teléfono del aval solo cuando existe
 *
 * Hoy es `null` siempre: no existe la columna, y su KDoc lo documenta con la
 * consulta que lo verificó. Una fila permanentemente en "—" es exactamente el
 * ruido que este rediseño quitó, así que la fila no se pinta mientras el dato no
 * exista, en vez de rellenarla con el teléfono del CLIENTE —que ya está en el
 * encabezado y no es a quien se llama—.
 */
@Composable
fun DatosDeLaPuerta(
    aval: String,
    telefonoAval: String?,
    ultimaVisita: LocalDate?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
    ) {
        DatoDeLaPuerta("Aval o responsable", aval)
        telefonoAval?.let { DatoDeLaPuerta("Teléfono del aval", it) }
        // `d MMM`, el mismo formato corto que ya usan el último contacto y el
        // último pago de esta hoja. Una fecha larga competiría con el saldo.
        DatoDeLaPuerta(
            clave = "Última visita",
            valor = ultimaVisita?.let { DIA_Y_MES.format(it) } ?: SIN_DATO
        )
    }
}

/**
 * Un dato de la puerta en UN renglón: la etiqueta apagada, el valor con el peso.
 *
 * Sin columna de ancho fijo (los 140 dp de [FilaClaveValor]): a `MUY_GRANDE` esa
 * columna se come más de un tercio del ancho y deja el valor partido. Acá la
 * etiqueta mide lo que mide y el valor toma lo que sobra.
 */
@Composable
private fun DatoDeLaPuerta(clave: String, valor: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
    ) {
        Text(
            text = clave,
            style = MspTheme.type.caption,
            color = MspTheme.colors.onSurfaceMuted,
            maxLines = 1
        )
        Text(
            text = valor.ifBlank { SIN_DATO },
            style = MspTheme.type.captionStrong,
            color = MspTheme.colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ---------------------------------------------------------------------------
// LO QUE EL CUADRO DE LA PUERTA DEJÓ ESCRITO ANTES DE IRSE AL FONDO
//
// El bloque de 100 dp (40 a las escalas grandes) que vivía dentro de la hoja de
// identidad ya no existe: el mapa pasó a ser el FONDO de la pantalla —ver
// `FondoDeLaPuerta`— y dejó de ocupar alto propio en la pila. Lo que NO se va
// con él es el criterio que lo bajó tres veces, porque sigue mandando:
//
//   **Entre un dibujo y un dato, cede el dibujo.**
//
// Los tres recortes que lo probaron, con sus dp, para que nadie los vuelva a
// descubrir a mano:
//
//   130 → 96 fijo   el cuadro no cabía a GRANDE/MUY_GRANDE
//                   dinero en 690 contra dock en 672
//   96  → 56        el dock creció al apilar tres celdas
//                   dinero en 652 contra dock en 608
//   56  → 40        volvió el renglón de dirección
//                   dinero en 633.5 contra dock en 620
//   130 → 100 a NORMAL   dirección REAL del padrón, dos renglones
//                   dinero en 681.0 contra dock en 655.0
//
// El mismo criterio es el que fijó los 152 dp del fondo nuevo contra los 261
// que pedía el mock. No cambió el principio: cambió a qué dibujo se le aplica.
//
// Va como comentario y no como constante a propósito: una constante de texto en
// un archivo de UI la recoge `CadaTextoDeUsuarioEmpiezaEnMayusculaTest` como si
// fuera texto de pantalla, y esto no lo lee nadie más que quien edite el
// archivo.
// ---------------------------------------------------------------------------

/**
 * El aire entre el chip, la calle y la línea de apoyo: los 10 px del mockup.
 *
 * No es `spacing.sm` (8) ni `spacing.md` (16): con 8 el chip se pega a la calle
 * y las tres señas se leen como un bloque; con 16 la banda de 130 dp se queda
 * sin margen arriba y abajo. El mockup fija 10 y es el número que deja las tres
 * cosas como tres cosas.
 */
/** Lo que dice el chip cuando la puerta tiene coordenada de un cobro real. */
