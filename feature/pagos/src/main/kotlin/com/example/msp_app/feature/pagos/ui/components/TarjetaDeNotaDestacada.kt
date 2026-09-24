package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.core.designsystem.component.MspCard
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme

/** `testTag` de la tarjeta de nota destacada — la que va ARRIBA del saldo. */
const val NOTA_DESTACADA_TAG: String = "pagos_nota_destacada"

/** `testTag` del botón que abre la hoja de edición desde la tarjeta. */
const val EDITAR_NOTA_DESTACADA_TAG: String = "pagos_nota_destacada_editar"

/** `testTag` de la antigüedad de la nota, a la derecha del rótulo. */
const val ANTIGUEDAD_DE_LA_NOTA_TAG: String = "pagos_nota_destacada_antiguedad"

/** `testTag` del párrafo de la nota — el que se recorta y se despliega. */
const val TEXTO_DE_LA_NOTA_TAG: String = "pagos_nota_destacada_texto"

/**
 * `testTag` del bloque tocable —párrafo más indicador— que despliega la nota.
 *
 * Sólo existe en el árbol cuando la nota **no cabe** en [RENGLONES_ASOMADOS];
 * contar sus nodos es cómo se afirma, sin leer píxeles, que una nota corta no
 * ofrece desplegar nada.
 */
const val ALTERNAR_LA_NOTA_TAG: String = "pagos_nota_destacada_alternar"

/** `testTag` del *"Ver más"* / *"Ver menos"* — la parte visible del control. */
const val INDICADOR_DE_LA_NOTA_TAG: String = "pagos_nota_destacada_indicador"

/**
 * **La nota, arriba y notoria.** Rótulo, antigüedad, el texto en grande y —si
 * se puede editar— el botón que abre la hoja.
 *
 * ## Por qué existe, y qué costó
 *
 * Hasta aquí la nota vivía al fondo de su pantalla y en [MspTheme.type.body]
 * (13 sp). El dueño lo dijo en vidrio: *"están hasta abajo y en diminuto, casi
 * no se ven"*. Subirla **sí cuesta**: empuja el dinero, que es por lo que el
 * cobrador abre la pantalla, y ése fue durante dos rondas el argumento para
 * dejarla abajo (ver el KDoc de [SeccionDeLaFicha] y
 * `LaFichaSeVeYSeTocaTest`). El argumento sigue siendo cierto; lo que cambió es
 * que el dueño decidió pagar ese costo para esta tarjeta.
 *
 * Lo que acota el costo es que la tarjeta **no se pinta si no hay nota**: una
 * puerta sin nada anotado no empuja ni un dp, así que lo que se paga se paga
 * sólo donde hay algo que leer.
 *
 * ## El color: ámbar de estado, y por qué NO el azul de marca
 *
 * El fondo es `statusPartialTint` y el rótulo `statusPartial` — el rol ámbar
 * que la paleta ya tiene y que los chips de estado parcial usan. **No se
 * agregó ningún color nuevo.**
 *
 * El azul de marca queda descartado a propósito: en esta app el azul es el
 * color de **las acciones** (el CTA de guardar, las pastillas encendidas, "ver
 * los N"), y una nota no es una acción. Pintarla de azul la haría competir con
 * los controles que sí se tocan.
 *
 * ## …pero el texto de la nota va en `onSurface`, no en ámbar
 *
 * Y esto es una desviación deliberada del boceto, con la medición delante:
 * `ContrastAAATest` documenta que `statusPartial` sobre `statusPartialTint`
 * **no llega ni a AA-normal en claro** (≈3.7:1) — es de los dos matices de la
 * paleta que sólo sostienen el piso de elemento no-textual (3:1). Pintar de
 * ámbar el párrafo que esta tarea existe para volver legible lo dejaría MENOS
 * legible que los 13 sp de hoy, que van en `onSurface` (≥7:1).
 *
 * Así que el ámbar carga el rótulo y la antigüedad —fragmentos cortos, donde
 * el color hace de etiqueta— y la nota va en la tinta más legible que hay:
 * `onSurface` sobre el tinte ámbar da ≈15:1 en claro y ≈13:1 en oscuro.
 *
 * ## El tamaño: [MspTheme.type.listTitle], que es el 15 sp de la escala
 *
 * El boceto pide "~15 sp, peso medio". La escala **no se inventa ni se
 * redondea** (ver el KDoc de `mspTypography`), y de los dos roles de 15 sp que
 * existen —`input` 15/Normal y `listTitle` 15/Bold— el que además pesa es
 * `listTitle`. `input` es el rol de un campo de captura y esto no se captura
 * aquí.
 *
 * ## Sigue siendo un ASOMO, y ahora un asomo de DOS renglones
 *
 * [RENGLONES_ASOMADOS] recorta a dos, no a los cuatro de la primera versión, y
 * el motivo es el mismo argumento de siempre llevado hasta el final: esta
 * tarjeta vive arriba del dinero y cada renglón de acá se lo quita al saldo.
 * Con el tope de 500 caracteres una nota larga son ~diez renglones; cuatro ya
 * eran demasiados.
 *
 * Lo que la nota larga **no** pierde es su texto: cuando no cabe, un toque en el
 * párrafo la despliega entera, en la misma tarjeta y sin salir de la pantalla, y
 * el *"Ver más"* del renglón del rótulo lo anuncia. La nota corta no ve
 * indicador ninguno — no hay nada que desplegar, y un control que no hace nada
 * es ruido con forma de control.
 *
 * **Y el indicador no cuesta un dp.** Vivió un renglón propio bajo el párrafo y
 * ahí costaba 19.0 dp: medido con la dirección real del padrón, con eso el
 * saldo volvía a taparse 15.0 dp **sólo en las puertas con nota larga** — la
 * clase de verde que pasa la prueba del caso barato y falla en la calle. En el
 * renglón del rótulo el alto ya está pagado por el botón (50 dp de piso
 * tocable), así que el indicador entra gratis y el criterio del dueño se cumple
 * con nota corta y con nota larga por igual.
 *
 * ## El botón de editar comparte renglón con el rótulo
 *
 * Y no es cosmética: **son 23.5 dp de dinero**, medidos a `NORMAL` en
 * `w360dp-h800dp`. En su propio renglón el botón costaba su alto tocable
 * (50 dp) más la separación de la columna (8 dp) y sólo hacía crecer la
 * tarjeta; en el renglón del rótulo lo único que paga es la diferencia entre su
 * alto y el del rótulo, que ya estaba ahí. Es lo que devolvió el `SALDO TOTAL`
 * arriba del dock a escala nominal — ver `LaFichaSeVeYSeTocaTest`.
 *
 * ## [onEditar] nulo es un caso real, no un default de cortesía
 *
 * En el detalle de venta la nota la escribe la oficina y llega por el servidor:
 * **no hay nada que editar**. Un botón ahí ofrecería algo que no se puede
 * hacer, así que sin [onEditar] la tarjeta no pinta ninguno.
 */
@Composable
fun TarjetaDeNotaDestacada(
    rotulo: String,
    nota: String,
    modifier: Modifier = Modifier,
    antiguedad: String? = null,
    onEditar: (() -> Unit)? = null
) {
    // Las dos banderas se reinician cuando cambia la nota —`remember(nota)`— y
    // no sólo al recomponer: editarla desde la hoja puede volverla corta, y una
    // tarjeta que se quedara "expandida" o con el indicador puesto sobre un
    // texto que ya cabe estaría mintiendo sobre que hay más.
    var expandida by remember(nota) { mutableStateOf(false) }
    var recortada by remember(nota) { mutableStateOf(false) }
    // El indicador vive en el renglón del rótulo SÓLO a escala nominal, que es
    // donde cabe y donde el dp importa. Ver [IndicadorDeLaNota].
    val enElRotulo = recortada && LocalFontSizeLevel.current == FontSizeLevel.NORMAL
    MspCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag(NOTA_DESTACADA_TAG),
        shape = MspTheme.shapes.card,
        color = MspTheme.colors.statusPartialTint
    ) {
        Column(
            modifier = Modifier.padding(MspTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
        ) {
            // Las versalitas se aplican ACÁ, en el composable público, y no
            // dentro de `RenglonDelRotulo`: así esta función es el sumidero que
            // `CadaTextoDeUsuarioEmpiezaEnMayusculaTest` deriva del código, y la
            // compuerta perdona sola el "lo que anotaste" en minúscula de sus
            // llamadores. Escondido un nivel más abajo, la compuerta no lo ve y
            // pone rojo un texto que sí se pinta en mayúsculas.
            RenglonDelRotulo(
                rotulo = rotulo.uppercase(BUSINESS_LOCALE),
                antiguedad = antiguedad,
                indicador = enElRotulo,
                expandida = expandida,
                onEditar = onEditar
            )
            // El párrafo y su indicador son UN bloque tocable, no un texto y un
            // botoncito: lo que se toca es la nota, que mide 60 dp o más, y no
            // un renglón de 12 sp al que hay que apuntar. Así el indicador puede
            // ser pequeño —cuesta menos alto— sin dejar de ser accionable.
            Column(
                modifier = if (recortada) {
                    Modifier
                        .clickable(onClickLabel = LEER_LA_NOTA) { expandida = !expandida }
                        .testTag(ALTERNAR_LA_NOTA_TAG)
                } else {
                    Modifier
                },
                verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
            ) {
                Text(
                    text = nota,
                    style = MspTheme.type.listTitle,
                    color = MspTheme.colors.onSurface,
                    maxLines = if (expandida) Int.MAX_VALUE else RENGLONES_ASOMADOS,
                    overflow = TextOverflow.Ellipsis,
                    // `hasVisualOverflow` lo dice el LAYOUT, que es el único que
                    // sabe cuántos renglones pide esta nota con esta tipografía
                    // a esta escala. Contar caracteres o `\n` aquí sería
                    // adivinarlo, y se equivocaría en cuanto cambie la escala
                    // del sistema.
                    //
                    // Sólo se mira colapsada: expandida no hay overflow que
                    // medir, y dejarlo escribir apagaría el control que se acaba
                    // de usar.
                    onTextLayout = { if (!expandida) recortada = it.hasVisualOverflow },
                    modifier = Modifier.testTag(TEXTO_DE_LA_NOTA_TAG)
                )
                if (recortada && !enElRotulo) {
                    IndicadorDeLaNota(expandida, Modifier.align(Alignment.End))
                }
            }
        }
    }
}

/**
 * El rótulo y, a su derecha, qué tan vieja es la nota **y el botón de editar**.
 *
 * La antigüedad **no se calcula acá**: llega ya dicha por
 * [com.example.msp_app.core.common.time.TiempoRelativo], que es quien sabe
 * decirla ("hoy", "ayer", "hace 3 días") y quien tiene las pruebas de esos
 * escalones. Una segunda forma de contar días sería una segunda forma de
 * equivocarse.
 *
 * El rótulo llega **ya en versalitas** desde [TarjetaDeNotaDestacada], que lo
 * sube con [BUSINESS_LOCALE] y no con la locale del teléfono —igual que
 * [LabelDeSeccion]: en es-MX los acentos se conservan—. Se hace allá y no acá
 * a propósito: ver el comentario de esa llamada.
 *
 * ## Por qué [FlowRow] y no un `Row`, que es lo que había
 *
 * Con un `Row` los dos textos se pegaban: a escala 2.0 el golden mostraba
 * **"LO QUE ANOTASTEhace…"**, sin un dp de aire y con la antigüedad recortada a
 * la mitad de una palabra. `SpaceBetween` reparte el sobrante, y cuando no hay
 * sobrante no separa nada.
 *
 * `FlowRow` lo arregla sin recortar ninguno de los dos: mientras caben en un
 * renglón se ven igual que antes —a los extremos—, y cuando no caben la
 * antigüedad **baja a un segundo renglón** en vez de comerse el rótulo. Se
 * prefiere eso a darle `weight` a uno de los dos, que es elegir cuál de los dos
 * se trunca; acá ninguno se trunca.
 *
 * ## El botón queda FUERA del `FlowRow`, y esto está medido
 *
 * La versión obvia —meterlo como un hijo más del `FlowRow`— sale **más cara** a
 * escala grande, no más barata. Medido a `GRANDE` en `w360dp-h800dp`: el botón
 * empuja al `FlowRow` a envolver, y entonces se paga el renglón del rótulo
 * (~22 dp) **más** los 50 dp del botón en el renglón de abajo: 76.5 dp de
 * encabezado contra los 22 de antes. La tarjeta crecía 21 dp justo en la escala
 * que peor viene.
 *
 * Así que el botón vive en un `Row` exterior, con el `FlowRow` tomando el ancho
 * que queda (`weight`). El alto del encabezado pasa a ser
 * `max(50 dp, lo que mida el texto)`: a `NORMAL` los tres caben en un renglón y
 * a `GRANDE` la antigüedad baja debajo del rótulo **dentro** del hueco de 50 dp
 * que el botón ya ocupaba. En las dos escalas el encabezado cuesta 50 dp, y no
 * 50 + lo que mida el texto.
 *
 * ## La antigüedad y el *"Ver más"* siguen pudiendo bajar de renglón
 *
 * Que es lo que el `FlowRow` estaba resolviendo y sigue resolviendo, ahora
 * dentro de su propio ancho. A 2.0 el rótulo mide hasta ~260 dp y la antigüedad
 * no cabe a su lado; baja, y ninguno de los dos se trunca.
 *
 * ## El *"Ver más"* va en el `Row` exterior, al lado del botón
 *
 * Y no dentro del `FlowRow`, que fue el primer intento y el golden lo rechazó:
 * ahí envolvía y quedaba **debajo del rótulo y alineado a la izquierda**, como
 * si fuera un subtítulo de *"LO QUE ANOTASTE"* en vez de un control. Al lado del
 * botón se lee como lo que es —algo que se toca— y cuesta **cero dp**, porque el
 * alto del renglón lo fija el botón (50 dp de piso tocable) y no el texto.
 *
 * A escalas grandes no cabe y se va bajo el párrafo: el porqué, con los dp, en
 * [IndicadorDeLaNota].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RenglonDelRotulo(
    rotulo: String,
    antiguedad: String?,
    indicador: Boolean,
    expandida: Boolean,
    onEditar: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
        ) {
            Text(
                text = rotulo,
                style = MspTheme.type.eyebrow,
                color = MspTheme.colors.statusPartial
            )
            if (antiguedad != null) {
                Text(
                    text = antiguedad,
                    style = MspTheme.type.caption,
                    color = MspTheme.colors.statusPartial,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(ANTIGUEDAD_DE_LA_NOTA_TAG)
                )
            }
        }
        if (indicador) IndicadorDeLaNota(expandida)
        if (onEditar != null) BotonDeEditarNota(onEditar = onEditar)
    }
}

/**
 * *"Ver más"* / *"Ver menos"* — la parte visible del control que despliega la
 * nota. Lo que se toca es el párrafo entero; esto sólo lo anuncia.
 *
 * ## Dónde se pinta, y por qué depende de la escala
 *
 * **A `NORMAL` va en el renglón del rótulo, y ahí cuesta cero dp**: el alto de
 * ese renglón lo fija el botón de editar (50 dp de piso tocable), así que un
 * texto de 12 sp más no lo mueve. Importa: bajo el párrafo costaba 19.0 dp y,
 * medido con la dirección real del padrón, con eso el saldo volvía a taparse
 * 15.0 dp **sólo en las puertas con nota larga** — la clase de verde que pasa el
 * caso barato y falla en la calle.
 *
 * **A `GRANDE` y `MUY_GRANDE` va bajo el párrafo**, y esto también salió del
 * golden y no de una sospecha. En el renglón del rótulo le quita ~90 dp de ancho
 * al `FlowRow`, y a 2.0 el rótulo se queda con ~112 dp para una palabra
 * —*"ANOTASTE"*— que mide ~150: `pagos_cliente_light_2_0` la enseñaba **cortada
 * a media palabra**, que es exactamente el defecto que el `FlowRow` se había
 * ganado el derecho a no repetir. Una palabra no tiene dónde quebrarse; la
 * única salida es devolverle el ancho.
 *
 * El intercambio es honesto porque los 19 dp que cuesta abajo **no cambian nada
 * a esas escalas**: ahí el dinero ya está tapado por aritmética —sin tarjeta el
 * saldo termina en 553.0 dp contra una banda de dock que empieza en 611.0— y
 * ningún tamaño de esta tarjeta lo arregla. Se paga donde no se cobra.
 *
 * La condición se escribe con el mismo `LocalFontSizeLevel` que ya reparten
 * `AccionesDelCliente`, `DockDeAcciones` y `SenasDeLaPuerta`: no es un patrón
 * nuevo, es el de esta pantalla.
 */
@Composable
private fun IndicadorDeLaNota(expandida: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = if (expandida) "Ver menos" else "Ver más",
        style = MspTheme.type.captionStrong,
        color = MspTheme.colors.statusPartial,
        maxLines = 1,
        modifier = modifier.testTag(INDICADOR_DE_LA_NOTA_TAG)
    )
}

/**
 * La acción que abre la hoja de edición — **la misma** que abre el dock.
 *
 * ## Esto NO es un botón con caja, y el error anterior vale escribirlo
 *
 * Hasta aquí era un `Surface` relleno de `surface` puesto encima del ámbar, y
 * el dueño lo describió como *"una basura"*: **una caja oscura de 50 dp de alto
 * recortada en la tarjeta**, tres veces el alto del renglón que la rodea. El
 * defecto de fondo no era el color sino haber hecho la **caja visible** del
 * tamaño del **área tocable**, que son dos cosas distintas.
 *
 * El área tocable sigue siendo [ALTO_TOCABLE] en los dos ejes —eso no se baja
 * nunca— pero ahora es **invisible**: `heightIn`/`widthIn` fijan el hueco, el
 * `padding` reparte el aire alrededor del texto y **no se pinta ninguna
 * superficie**. Lo único que se pinta al tocar es el ripple, y va recortado a
 * [MspTheme.shapes.control] porque el `clip` va antes del `clickable`.
 *
 * ## Por qué texto pelón y no un ícono de lápiz
 *
 * El ícono sería más angosto y se distinguiría de *"Ver más"* por forma y no
 * sólo por color, que es lo ideal. **No se puede hoy**: `MspIcons` es
 * `internal` a `:core:designsystem` y ningún módulo de la arquitectura nueva
 * importa `Icons.Filled.*` por su cuenta (sólo el `:app` legado lo hace). Meter
 * un lápiz aquí obliga a abrir el design system, y eso es un patrón nuevo que
 * se pregunta antes de crearlo.
 *
 * ## Por qué [MspTheme.type.buttonSmall] y no el `captionStrong` de *"Ver más"*
 *
 * Porque pegados y del mismo tamaño no se distinguirían, que es la otra mitad
 * de la queja. Con `buttonSmall` (14 sp / ExtraBold / azul) contra el
 * `captionStrong` (11 sp / Bold / ámbar) del indicador, los dos difieren en
 * **tamaño, peso y color** — y el orden es el correcto: *"Editar"* es el único
 * control de verdad del renglón, *"Ver más"* sólo anuncia que el párrafo de
 * abajo se toca.
 *
 * Y hay una razón medida, no sólo de gusto: **azul sobre el tinte ámbar da
 * 4.52:1 en claro y 4.25:1 en oscuro**. A 11 sp eso queda por debajo del piso
 * AA-normal (4.5:1); a 14 sp/700 el texto es "grande" y el piso que aplica es
 * 3:1, que sostiene con margen en los dos temas. Sigue estando por encima del
 * ámbar sobre ámbar que la tarjeta ya usaba y que `ContrastAAATest` documenta
 * en ≈3.7:1 en claro.
 *
 * El azul de marca se queda: lo que no puede ir de azul es la NOTA, porque no
 * es una acción. Esto sí lo es.
 *
 * ## No cuesta un dp
 *
 * El renglón sigue midiendo [ALTO_TOCABLE], que es lo que ya costaba, así que
 * los 23.5 dp que este botón le devolvió al dinero —y el `SALDO TOTAL` que
 * termina en 651.0 dp contra un dock que empieza en 655.0— no se mueven. El
 * ancho incluso baja: el relleno horizontal pasa de `md` a `sm` porque ya no
 * hay caja que rellenar, y ese ancho se lo queda el rótulo.
 */
@Composable
private fun BotonDeEditarNota(onEditar: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = ALTO_TOCABLE)
            .widthIn(min = ALTO_TOCABLE)
            .clip(MspTheme.shapes.control)
            .clickable(role = Role.Button, onClick = onEditar)
            .padding(horizontal = MspTheme.spacing.sm)
            .testTag(EDITAR_NOTA_DESTACADA_TAG),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Editar",
            style = MspTheme.type.buttonSmall,
            color = MspTheme.colors.brand,
            maxLines = 1
        )
    }
}

/**
 * Cuántos renglones de la nota se asoman **colapsada**.
 *
 * Dos, y no los cuatro que se asomaban al fondo de la pantalla: al fondo un
 * renglón de más no le quitaba nada a nadie, y arriba del saldo cada renglón de
 * `listTitle` son ~21 dp que dejan de ser dinero. Lo que no cabe en dos se lee
 * con un toque — ver [AlternarLaNota]—, así que no se pierde texto: se pierde
 * la obligación de mirarlo.
 */
private const val RENGLONES_ASOMADOS = 2

/**
 * Piso tocable del botón. 50 dp es el piso del repo —el mismo de
 * `ALTO_DEL_ALCANCE` en `DetalleVentaScreen`— y no los 56 dp del dock: esta
 * tarjeta vive ARRIBA del saldo y cada dp de acá se lo quita al dinero.
 */
private val ALTO_TOCABLE = 50.dp

/**
 * Lo que TalkBack anuncia del bloque tocable de la nota.
 *
 * *"Activar para leer la nota completa"* no serviría: el mismo toque la vuelve
 * a colapsar, y anunciar sólo una de las dos direcciones convierte el control
 * en una trampa para quien no ve la elipsis.
 */
private const val LEER_LA_NOTA = "Mostrar u ocultar la nota completa"
