@file:Suppress(
    "TooManyFunctions"
) // una pieza por banda de la hoja continua; juntarlas no las haria mas legibles.

package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.core.designsystem.component.MspCard
import com.example.msp_app.core.designsystem.component.MspMoneyText
import com.example.msp_app.core.designsystem.component.MspProgressBar
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.model.ContactoDeCobranza
import com.example.msp_app.feature.pagos.domain.model.ProductoDeVenta
import com.example.msp_app.feature.pagos.domain.model.ResumenDelCliente
import com.example.msp_app.feature.pagos.domain.model.UbicacionDelCobro
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

/** `testTag` de la cifra "pídele hoy" — la que manda la conversación. */
const val PIDELE_HOY_TAG: String = "pagos_cliente_pidele_hoy"

/** `testTag` de un renglón de producto del cliente. */
const val FILA_DE_PRODUCTO_TAG: String = "pagos_cliente_producto"

/** `testTag` del "ver los N contactos" al pie de la bitácora. */
const val VER_LOS_CONTACTOS_TAG: String = "pagos_cliente_ver_contactos"

/** `testTag` de un renglón de contacto dentro de la hoja del detalle. */
const val CONTACTO_EN_LA_HOJA_TAG: String = "pagos_cliente_contacto"

/** `testTag` del cuadro de la puerta — el mapa, o el dibujo cuando no hay mapa. */
const val CUADRO_DE_LA_PUERTA_TAG: String = "pagos_cliente_cuadro_puerta"

/**
 * Lo que anuncia el cuadro cuando se puede tocar.
 *
 * Un `clickable` sin etiqueta es un control invisible para TalkBack, y este no
 * tiene texto propio del cual heredarla: son dos glifos decorativos.
 */
private const val VER_LA_UBICACION = "Ver la ubicación"

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
 * El renglón de identidad: el racimo de estados de sus cuentas y, al lado, zona
 * y dirección.
 *
 * El racimo aquí **no repite** lo que dicen los chips de "sus ventas": está a una
 * pantalla de distancia, no a unos píxeles, y contesta de un vistazo la pregunta
 * con la que el cobrador abre esta pantalla ("¿cómo viene esta puerta?") sin
 * tener que desplazar. Es la misma razón por la que en la LISTA sí se quitó: allá
 * el racimo y los chips compartían tarjeta.
 *
 * Zona y dirección van en un solo renglón, recortado con elipsis: es contexto, y
 * partirlo en dos líneas le daría el peso de un dato principal.
 */
@Composable
fun BloqueDeIdentidad(
    estados: List<androidx.compose.ui.graphics.vector.ImageVector>,
    colores: List<Pair<Color, Color>>,
    zonaYDireccion: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
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
        Text(
            text = zonaYDireccion.ifBlank { SIN_DATO },
            style = MspTheme.type.caption,
            color = MspTheme.colors.onSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Una acción de la fila: su etiqueta, su glifo y qué hace. */
private data class AccionDelCliente(
    val etiqueta: String,
    val icono: ImageVector,
    val onClick: () -> Unit
)

/**
 * Las cuatro acciones de contacto, como **iconos y no botones de texto**.
 *
 * Cuatro botones de texto en fila ("Llamar", "WhatsApp", "Ficha", "Cómo llegar")
 * a 360 dp dejan cada uno con menos de 85 dp y parten la palabra. Como iconos
 * con su etiqueta debajo caben, y la fila mide lo mismo.
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
    onFicha: () -> Unit,
    onComoLlegar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val acciones = listOf(
        AccionDelCliente("llamar", AccionesIconos.Llamar, onLlamar),
        AccionDelCliente("whatsapp", AccionesIconos.WhatsApp, onWhatsApp),
        AccionDelCliente("ficha", AccionesIconos.Ficha, onFicha),
        AccionDelCliente("cómo llegar", AccionesIconos.Pin, onComoLlegar)
    )
    val porRenglon = if (LocalFontSizeLevel.current == FontSizeLevel.NORMAL) {
        acciones.size
    } else {
        acciones.size / 2
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
    Column(modifier = modifier.fillMaxWidth()) {
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
 * Las tres cifras del pie del saldo: **suele dar · pídele hoy · últ. pago**.
 *
 * "Pídele hoy" va en `brand` y las otras dos en el color del texto. No es
 * decoración: de los tres, es el único que dice qué HACER, y el color de marca es
 * el que esta app reserva para lo protagónico. Pintar los tres iguales dejaría al
 * cobrador leyendo tres números para encontrar el que importa.
 *
 * Reusa [TresDatos], así que a escala GRANDE y MUY_GRANDE los tres se apilan en
 * vez de salirse de la pantalla.
 */
@Composable
fun CifrasDelCliente(
    resumen: ResumenDelCliente,
    modifier: Modifier = Modifier,
    ocultos: Boolean = false
) {
    TresDatos(
        modifier = modifier,
        primero = { celda ->
            CifraDelCliente(
                clave = "suele dar",
                monto = resumen.sueleDar,
                ocultos = ocultos,
                modifier = celda
            )
        },
        segundo = { celda ->
            CifraDelCliente(
                clave = "pídele hoy",
                monto = resumen.pideleHoy,
                color = MspTheme.colors.brand,
                ocultos = ocultos,
                modifier = celda.testTag(PIDELE_HOY_TAG)
            )
        },
        tercero = { celda ->
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
        dia.takeIf { it.isNotBlank() }?.let { "su día es ${it.lowercase(BUSINESS_LOCALE)}" },
        frecuencia.takeIf { it.isNotBlank() }?.lowercase(BUSINESS_LOCALE)
    )
    return partes.joinToString(" · ")
}

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
 * y con "pídele hoy", que son las que mandan la conversación.
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
 * Un contacto de la bitácora dentro de la hoja: qué pasó, cuándo, y el pin
 * cuando de esa vez se sabe dónde.
 *
 * Es la hermana chica de [FilaDeContacto] —la de la bitácora completa— y hace lo
 * mismo con el toque: [onVerUbicacion] abre el mapa grande centrado en el punto
 * de ESTE contacto, y **sin punto el `clickable` no existe**. Las razones —por
 * qué no un `onClick` vacío, por qué el pin va en `brand`, y por qué su hueco se
 * reserva aunque no haya pin— están escritas una sola vez, en el KDoc de
 * [FilaDeContacto].
 *
 * Lo único que difiere es el piso de alto: aquí es [TOQUE_DE_ACCION], los 50 dp
 * que esta pantalla ya declara para todo lo tocable, y no los 56 de la bitácora.
 * No es aflojar la regla —50 es la regla del repo, más estricta que los 48 de
 * Material— sino no gastar 18 dp de más en la hoja que compite con el dinero, que
 * es lo que `LaFichaSeVeYSeTocaTest` mide.
 */
@Composable
fun ContactoEnLaHoja(
    contacto: ContactoDeCobranza,
    fecha: LocalDate,
    modifier: Modifier = Modifier,
    onVerUbicacion: ((UbicacionDelCobro) -> Unit)? = null
) {
    val abrir = abridorDe(contacto, onVerUbicacion)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (abrir != null) {
                    Modifier
                        .clickable(onClick = abrir)
                        .semantics { contentDescription = VER_DONDE_FUE }
                } else {
                    Modifier
                }
            )
            .heightIn(min = TOQUE_DE_ACCION)
            .padding(horizontal = MspTheme.spacing.md, vertical = MspTheme.spacing.sm + 4.dp)
            .testTag(CONTACTO_EN_LA_HOJA_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        Text(
            text = contacto.etiqueta.replaceFirstChar { it.titlecase(BUSINESS_LOCALE) },
            style = MspTheme.type.listTitle,
            color = MspTheme.colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = DIA_Y_MES.format(fecha),
            style = MspTheme.type.caption,
            color = MspTheme.colors.onSurfaceMuted
        )
        PinDelContacto(hayPunto = contacto.ubicacion != null)
    }
}

/** "Ver los N contactos ›" al pie de la bitácora. */
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
                text = "ver los $cuantos contactos",
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
 * **El cuadro de la puerta: el mapa cuando se puede, el dibujo cuando no.**
 *
 * ## Por qué vuelve, y por qué vuelve sin botón adentro
 *
 * El bloque se fue entero con `:core:mapas` (`5417e65e`) porque sin renderizador
 * habría quedado un rectángulo gris de 130 dp que no enseña nada. El dueño lo
 * vio en vidrio y pidió lo contrario: *"tiene que ser un mapa o un dibujo"*. Una
 * banda vacía en medio de la hoja se lee como una pantalla a medio cargar.
 *
 * Vuelve **sin el botón "cómo llegar" adentro**. Aquél era el segundo camino al
 * mismo intent, y cuál se pintaba lo decidía un dato: el cobrador veía una
 * pantalla distinta según si el abono anterior se registró con ubicación o sin
 * ella, para una acción que siempre se puede hacer. `5417e65e` lo cerró dejándolo
 * como cuarta acción permanente de [AccionesDelCliente], y eso se conserva.
 *
 * [onVerUbicacion] **no es ese segundo camino**: "cómo llegar" sale de la app a
 * navegar, y esto abre el mapa completo DENTRO de la app, para ver la puerta con
 * zoom antes de arrancar. Son dos trabajos distintos y dos destinos distintos.
 * Solo se puede tocar cuando hay punto medido: sin él no hay nada que mostrar.
 *
 * ## Las DOS capas, y por qué el dibujo va abajo y no "en vez de"
 *
 * El dibujo se pinta **siempre**, como piso. [suelo] —el mapa de verdad, que
 * cablea `:app`— se pinta encima y solo cuando hay [ubicacion]. Si el mapa
 * pinta, tapa el dibujo; si no pinta, el dibujo queda a la vista.
 *
 * Eso no es defensa por si acaso: **está medido**. Instalado en el SM-A256E, el
 * mapa no pintó ni una tesela —`Authorization failure … StatusCode=
 * INVALID_ARGUMENT`, la llave no autorizaba el paquete de esa build— y lo que se
 * veía era la retícula gris con el logo de Google, o sea *un mapa que no cargó*,
 * que es exactamente lo que este cuadro existe para no ser. El mismo caso se da
 * en la calle sin señal la primera vez que se abre una puerta nueva. Con el
 * dibujo abajo, el peor caso es el estado aceptable y no el prohibido.
 *
 * Quien decide cuándo el mapa se deja ver es el propio [suelo] (ver
 * `SueloDelUltimoCobro` en `:app`): se mantiene invisible hasta que el SDK avisa
 * que terminó de renderizar. Sin temporizadores y sin adivinar.
 *
 * ## El mapa vive en `:app` y entra por una ranura
 *
 * `:feature:pagos` no declara `play-services-maps` y no debe declararla: vive en
 * `:app` (`app/build.gradle.kts:348-350`), igual que la llave del manifiesto.
 * Principio 17 del brief: cuando el adaptador necesita algo que solo vive en
 * `:app`, la ranura se queda en el módulo y quien la cierra es `:app`.
 *
 * Y es lo que mantiene **deterministas los goldens**: un mapa real trae red y
 * bitmaps, y ninguno de los dos entra a `captureRoboImage`. El golden fotografía
 * el dibujo, que es lo que este módulo dibuja de verdad.
 *
 * ## El pin del dibujo es un SÍMBOLO, no una coordenada
 *
 * Con [ubicacion] medida el dibujo lleva un pin sobre la casa. No dice *dónde*
 * está la puerta —sobre un dibujo no hay dónde— sino que **hay un punto medido**,
 * y eso el cobrador lo usa: "cómo llegar" lo va a dejar en la puerta exacta y no
 * en la calle. Es la distinción que el KDoc del bloque viejo ya dejó escrita, y
 * la razón por la que con teselas el pin lo pinta el mapa: ahí un pin que no cae
 * exacto sobre el objetivo de la cámara miente, y el golden midió **21 dp** de
 * corrimiento, unos 24 metros a zoom 17.
 *
 * ## Qué NO dibuja
 *
 * **Nada de calles ni de retícula.** El mock resolvía este cuadro con un
 * degradado, una cuadrícula de 34 px y una barra rotada -7° haciendo de calle
 * (`docs/design/mocks/cliente-y-venta.html:151-156`). Una retícula se confunde
 * con la traza real de la colonia y una barra rotada se lee como una avenida que
 * existe: las dos son dato falso dibujado, y además indistinguibles de un mapa
 * roto. El dibujo tiene que **verse como dibujo**.
 *
 * **Y no dice "a 320 m".** Esa distancia exige saber dónde está el teléfono
 * AHORA y la app no tiene ubicación en vivo. Un número a medias es un dato
 * falso, no uno incompleto (principio 9).
 */
@Composable
fun CuadroDeLaPuerta(
    ubicacion: UbicacionDelCobro?,
    modifier: Modifier = Modifier,
    onVerUbicacion: (() -> Unit)? = null,
    suelo: (@Composable () -> Unit)? = null
) {
    val abrir = onVerUbicacion.takeIf { ubicacion != null }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(altoDelCuadro())
            .background(MspTheme.colors.surface2)
            .then(
                if (abrir != null) {
                    Modifier
                        .clickable(onClick = abrir)
                        .semantics { contentDescription = VER_LA_UBICACION }
                } else {
                    Modifier
                }
            )
            .testTag(CUADRO_DE_LA_PUERTA_TAG)
    ) {
        DibujoDeLaPuerta(ubicacion)
        // El mapa, encima del dibujo y solo con punto medido. Sin punto no hay
        // dónde centrarlo, y centrarlo en cualquier otra cosa diría "es aquí"
        // sobre una puerta que nadie midió.
        if (ubicacion != null) suelo?.invoke()
    }
}

/**
 * El dibujo: una casa, y un pin encima cuando la puerta tiene punto medido.
 *
 * Los dos glifos son [androidx.compose.ui.graphics.vector.ImageVector]
 * construidos a mano con el mismo helper que el resto de los iconos del módulo,
 * así que pesan lo que pesan unas constantes `String`. No es un detalle de
 * estilo: el único PNG decorativo del repo (`res/drawable-nodpi/map_layout.png`,
 * el "mapa ilustrado" de la pantalla legada de venta) ocupa **1 863 417 B**, el
 * 15 % de un APK de release de 11.59 MB — más que todo el SDK de Maps junto.
 */
@Composable
private fun DibujoDeLaPuerta(ubicacion: UbicacionDelCobro?) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (ubicacion != null) {
            Icon(
                imageVector = AccionesIconos.Pin,
                contentDescription = null,
                tint = MspTheme.colors.brand,
                modifier = Modifier.size(PIN_DEL_CUADRO)
            )
            Spacer(Modifier.height(MspTheme.spacing.xs))
        }
        Icon(
            imageVector = AccionesIconos.Casa,
            contentDescription = null,
            tint = MspTheme.colors.onSurfaceMuted,
            modifier = Modifier.size(CASA_DEL_CUADRO)
        )
    }
}

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
        DatoDeLaPuerta("aval o responsable", aval)
        telefonoAval?.let { DatoDeLaPuerta("teléfono del aval", it) }
        // `d MMM`, el mismo formato corto que ya usan el último contacto y el
        // último pago de esta hoja. Una fecha larga competiría con el saldo.
        DatoDeLaPuerta(
            clave = "última visita",
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

/**
 * El alto del cuadro de ubicación: **130 dp del mock, y MENOS a las escalas
 * grandes**.
 *
 * ## Por qué encoge, al revés que su antecesor
 *
 * Aquél *crecía* con la escala porque tenía dentro un botón "cómo llegar" que
 * escalaba con la tipografía y llegaba a tapar el pin a `MUY_GRANDE` (golden
 * `pagos_mapa_con_atribucion_light_2_0`). Ese botón ya no vive aquí —es la
 * cuarta acción de la fila de abajo— y la pastilla se esconde fuera de `NORMAL`,
 * así que a las escalas grandes **dentro del cuadro no queda nada que leer**: es
 * decoración.
 *
 * Y la decoración es lo que tiene que ceder. Todo lo que vive en la hoja de
 * identidad empuja la hoja del dinero, que es por lo que el cobrador abrió esta
 * pantalla, y `LaFichaSeVeYSeTocaTest` lo cobra en dp: con los 130 fijos el
 * dinero terminaba en 690 dp contra un dock que empieza en 672 —**18 dp
 * tapado**— a `GRANDE` y a `MUY_GRANDE`. Con [CUADRO_APRETADO] sobran 20.
 *
 * El número no sale de un gusto: sale de esa medición. Subirlo vuelve a tapar el
 * saldo, y la regla del repo es subir la implementación, no bajar el test.
 */
@Composable
private fun altoDelCuadro(): Dp = when (LocalFontSizeLevel.current) {
    FontSizeLevel.NORMAL -> CUADRO_DEL_MOCK
    else -> CUADRO_APRETADO
}

/** Los 130 dp del mock, a escala normal. */
private val CUADRO_DEL_MOCK = 130.dp

/** Lo que mide a `GRANDE` y `MUY_GRANDE`, donde el cuadro ya no lleva texto. */
private val CUADRO_APRETADO = 96.dp

/**
 * La casa: grande y en el gris del texto secundario.
 *
 * Se probó primero con `colors.outline` —el color del hairline— y en el golden
 * la casa **desaparecía**: quedaba una mancha que se lee como un artefacto del
 * render, no como un dibujo. Un dibujo que no se ve no responde a *"tiene que
 * ser un mapa o un dibujo"*.
 */
private val CASA_DEL_CUADRO = 56.dp

/**
 * El pin: más chico que la casa y en el color de marca.
 *
 * El tamaño y el color son los que lo hacen leerse como una **marca sobre** la
 * casa y no como un segundo dibujo al lado. Es el único elemento del cuadro que
 * depende de un dato.
 */
private val PIN_DEL_CUADRO = 28.dp
