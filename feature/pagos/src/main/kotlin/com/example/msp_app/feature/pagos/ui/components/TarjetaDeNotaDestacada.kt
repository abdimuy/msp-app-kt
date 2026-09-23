package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.core.designsystem.component.MspCard
import com.example.msp_app.core.designsystem.theme.MspTheme

/** `testTag` de la tarjeta de nota destacada — la que va ARRIBA del saldo. */
const val NOTA_DESTACADA_TAG: String = "pagos_nota_destacada"

/** `testTag` del botón que abre la hoja de edición desde la tarjeta. */
const val EDITAR_NOTA_DESTACADA_TAG: String = "pagos_nota_destacada_editar"

/** `testTag` de la antigüedad de la nota, a la derecha del rótulo. */
const val ANTIGUEDAD_DE_LA_NOTA_TAG: String = "pagos_nota_destacada_antiguedad"

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
 * ## Sigue siendo un ASOMO, no la nota entera
 *
 * [RENGLONES_DE_LA_NOTA] recorta igual que antes, y por el mismo motivo: con el
 * tope de 500 caracteres una nota larga son ~diez renglones, y diez renglones
 * arriba del saldo es exactamente lo que el test del dinero prohíbe. El texto
 * completo vive en el editor, a un toque del botón de esta misma tarjeta.
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
                antiguedad = antiguedad
            )
            Text(
                text = nota,
                style = MspTheme.type.listTitle,
                color = MspTheme.colors.onSurface,
                maxLines = RENGLONES_DE_LA_NOTA,
                overflow = TextOverflow.Ellipsis
            )
            if (onEditar != null) {
                BotonDeEditarNota(
                    onEditar = onEditar,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

/**
 * El rótulo y, a su derecha, qué tan vieja es la nota.
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
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RenglonDelRotulo(rotulo: String, antiguedad: String?) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
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
}

/**
 * El botón que abre la hoja de edición — **la misma** que abre el dock.
 *
 * Va en azul de marca sobre `surface` y no en el ámbar de la tarjeta, y no se
 * contradice con la regla de arriba: lo que no puede ir de azul es la NOTA,
 * porque no es una acción. Esto sí lo es, y el azul es como esta app dice
 * "esto se toca". En ámbar sobre ámbar además no se leería como control.
 */
@Composable
private fun BotonDeEditarNota(onEditar: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onEditar,
        modifier = modifier
            .heightIn(min = ALTO_TOCABLE)
            .widthIn(min = ALTO_TOCABLE)
            .testTag(EDITAR_NOTA_DESTACADA_TAG),
        shape = MspTheme.shapes.control,
        color = MspTheme.colors.surface
    ) {
        Box(
            modifier = Modifier.padding(horizontal = MspTheme.spacing.md),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Editar",
                style = MspTheme.type.captionStrong,
                color = MspTheme.colors.brand
            )
        }
    }
}

/**
 * Cuántos renglones de la nota se asoman en la tarjeta. Los mismos cuatro que
 * se asomaban al fondo: subirla no era licencia para que creciera.
 */
private const val RENGLONES_DE_LA_NOTA = 4

/**
 * Piso tocable del botón. 50 dp es el piso del repo —el mismo de
 * `ALTO_DEL_ALCANCE` en `DetalleVentaScreen`— y no los 56 dp del dock: esta
 * tarjeta vive ARRIBA del saldo y cada dp de acá se lo quita al dinero.
 */
private val ALTO_TOCABLE = 50.dp
