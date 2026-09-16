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

/** `testTag` de cada acción de contacto (llamar / whatsapp / ficha). */
const val ACCION_DE_CONTACTO_TAG: String = "pagos_cliente_accion"

/** `testTag` del botón "cómo llegar" del mapa. */
const val COMO_LLEGAR_TAG: String = "pagos_cliente_como_llegar"

/** `testTag` de la cifra "pídele hoy" — la que manda la conversación. */
const val PIDELE_HOY_TAG: String = "pagos_cliente_pidele_hoy"

/** `testTag` de un renglón de producto del cliente. */
const val FILA_DE_PRODUCTO_TAG: String = "pagos_cliente_producto"

/** `testTag` del "ver los N contactos" al pie de la bitácora. */
const val VER_LOS_CONTACTOS_TAG: String = "pagos_cliente_ver_contactos"

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

/**
 * Las tres acciones de contacto, como **iconos y no botones de texto**.
 *
 * Tres botones de texto en fila ("Llamar", "WhatsApp", "Ficha") a 360 dp dejan
 * cada uno con menos de 110 dp y el del medio parte la palabra. Como iconos con
 * su etiqueta debajo caben los tres holgados y la fila mide lo mismo.
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
    modifier: Modifier = Modifier,
    onComoLlegar: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
    ) {
        AccionDeContacto("llamar", AccionesIconos.Llamar, onLlamar, Modifier.weight(1f))
        AccionDeContacto("whatsapp", AccionesIconos.WhatsApp, onWhatsApp, Modifier.weight(1f))
        AccionDeContacto("ficha", AccionesIconos.Ficha, onFicha, Modifier.weight(1f))
        // Solo cuando NO hay mapa que pintar: ahí arriba el botón ya existe, y
        // repetirlo sería dos caminos a lo mismo a diez dp de distancia.
        if (onComoLlegar != null) {
            AccionDeContacto(
                etiqueta = "cómo llegar",
                icono = AccionesIconos.Pin,
                onClick = onComoLlegar,
                modifier = Modifier.weight(1f).testTag(COMO_LLEGAR_TAG)
            )
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

/** Un contacto de la bitácora dentro de la hoja: qué pasó y cuándo. */
@Composable
fun ContactoEnLaHoja(
    contacto: ContactoDeCobranza,
    fecha: LocalDate,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MspTheme.spacing.md, vertical = MspTheme.spacing.sm + 4.dp),
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
 * El bloque del mapa: el suelo, el pin donde se cobró la última vez, la pastilla
 * de contexto y el botón "cómo llegar".
 *
 * ## El mapa entra por un slot, y por qué
 *
 * [suelo] es lo que dibuja el mapa de verdad. Llega por parámetro porque el
 * módulo que lo pinta (`:core:mapas`, MapLibre + PMTiles) es trabajo aparte y
 * **descargable**: el teléfono puede no tener el extracto todavía. Con el slot,
 * esta pantalla se ve igual con mapa y sin él, y el día que el extracto exista no
 * hay que tocar el detalle del cliente.
 *
 * Por defecto pinta un suelo liso del tema. No es un "mapa falso": no dibuja
 * calles inventadas ni una retícula que se pueda confundir con la traza real de
 * la colonia, que sería exactamente la clase de dato falso que esta pantalla
 * evita en todos lados.
 *
 * ## La pastilla NO dice a cuántos metros, y es a propósito
 *
 * El mock dice "a 320 m · último cobro aquí". Los 320 m exigen saber dónde está
 * el teléfono AHORA, y esta app no tiene una fuente de ubicación en vivo: el
 * único punto que existe es el del último abono. Escribir una distancia sin
 * medirla sería inventar un número —y un número a medias es un dato falso, no uno
 * incompleto—, así que la pastilla dice solo lo que sí se sabe. El hueco de la
 * distancia queda listo para cuando haya de dónde medirla.
 *
 * Sin [ubicacion] no hay pin ni pastilla: un pin en el centro del cuadro se
 * leería como "es aquí" cuando nadie lo sabe. "Cómo llegar" sí se queda — abre la
 * dirección escrita, que es lo que el cobrador tiene.
 */
@Composable
fun MapaDelCliente(
    ubicacion: UbicacionDelCobro?,
    onComoLlegar: () -> Unit,
    modifier: Modifier = Modifier,
    suelo: @Composable () -> Unit = { SueloSinMapa() }
) {
    Box(modifier = modifier.fillMaxWidth().height(ALTO_DEL_MAPA)) {
        suelo()
        // El pin y el pie viven en BANDAS, no anclados a las esquinas de la misma
        // caja. Anclados se montaban uno sobre otro: el botón crecía con la escala
        // de fuente y tapaba primero la pastilla (golden `..._light_2_0`) y luego
        // el propio pin (`..._light_1_5`). Con la banda de arriba tomando el
        // espacio que sobra, el encimamiento deja de ser posible a cualquier escala.
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (ubicacion != null) PinDelCobro()
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MspTheme.spacing.sm + MspTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
            ) {
                // La pastilla solo cabe a escala normal. A las grandes desaparece en
                // vez de recortarse: "último c…" no informa, y el pin ya dice dónde.
                //
                // **Sin `weight`**: con `weight(1f, fill = false)` peleaba contra el
                // `Spacer(weight(1f))` de al lado —los dos se repartían la fila— y el
                // texto salía recortado a "último …" hasta a escala 1.0, donde sobra
                // espacio. Es literal fijo y corto: que mida lo que mide.
                if (ubicacion != null && LocalFontSizeLevel.current == FontSizeLevel.NORMAL) {
                    Text(
                        text = "último cobro aquí",
                        style = MspTheme.type.chipLabel,
                        color = MspTheme.colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(MspTheme.shapes.chip)
                            .background(MspTheme.colors.surface)
                            .padding(
                                horizontal = MspTheme.spacing.sm + MspTheme.spacing.xs,
                                vertical = MspTheme.spacing.xs + 2.dp
                            )
                    )
                }
                Spacer(Modifier.weight(1f))
                Surface(
                    onClick = onComoLlegar,
                    shape = MspTheme.shapes.chip,
                    color = MspTheme.colors.brand,
                    modifier = Modifier
                        .heightIn(min = TOQUE_DE_ACCION)
                        .testTag(COMO_LLEGAR_TAG)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs),
                        modifier = Modifier.padding(
                            horizontal = MspTheme.spacing.md,
                            vertical = MspTheme.spacing.sm
                        )
                    ) {
                        Icon(
                            imageVector = AccionesIconos.Pin,
                            contentDescription = null,
                            tint = MspTheme.colors.onBrand,
                            modifier = Modifier.size(MspTheme.spacing.md)
                        )
                        Text(
                            text = "cómo llegar",
                            style = MspTheme.type.buttonSmall,
                            color = MspTheme.colors.onBrand,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/** El suelo mientras no hay extracto de mapa: liso, sin calles inventadas. */
@Composable
private fun SueloSinMapa() {
    Box(
        modifier = Modifier.fillMaxWidth().height(
            ALTO_DEL_MAPA
        ).background(MspTheme.colors.surface2)
    )
}

/** El pin: el punto de marca dentro de su halo. */
@Composable
private fun PinDelCobro(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(HALO_DEL_PIN)
            .clip(MspTheme.shapes.chip)
            .background(MspTheme.colors.brandTint),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(PUNTO_DEL_PIN)
                .clip(MspTheme.shapes.chip)
                .background(MspTheme.colors.brand)
        )
    }
}

/** El alto del cuadro de mapa — los 130 px del mock. */
private val ALTO_DEL_MAPA = 130.dp

/** El halo del pin. */
private val HALO_DEL_PIN = 34.dp

/** El punto de marca dentro del halo. */
private val PUNTO_DEL_PIN = 14.dp
