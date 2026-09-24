package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.ui.AccionesIconos

/** `testTag` del botón de tres puntos que abre el menú del dock. */
const val MENU_DEL_DOCK_TAG: String = "pagos_dock_menu"

/** `testTag` del velo que atenúa la pantalla con el menú abierto. */
const val VELO_DEL_MENU_TAG: String = "pagos_dock_menu_velo"

/** `testTag` del renglón "Condonar" dentro del menú. */
const val MENU_CONDONAR_TAG: String = "pagos_dock_menu_condonar"

/** `testTag` del renglón "Notas" dentro del menú. */
const val MENU_NOTAS_TAG: String = "pagos_dock_menu_notas"

/**
 * **Lo que vive dentro del "⋯" del dock.**
 *
 * ## Por qué hay un menú y no cuatro botones
 *
 * Decisión del dueño, vista en un mock interactivo. A la vista quedan las dos
 * acciones **diarias** —registrar abono y visita—; adentro las dos que no lo
 * son. Condonar es ~3 % de los movimientos y no merece la misma ranura que lo
 * que se hace en cada puerta, y **Notas** es medio redundante: la tarjeta de la
 * nota ya trae su propio *Editar* arriba del dinero.
 *
 * Lo que compra además es que **el dock deja de crecer**. Con cuatro celdas a
 * escala 2.0 no hay reparto que alcance —la ronda anterior ya tuvo que apilar
 * para que "Registrar abono" no se partiera a mitad de palabra—, y la quinta
 * acción que alguien pida mañana ya tiene dónde ir sin volver a tocar el
 * reparto.
 *
 * ## Cada renglón lleva su dato
 *
 * [AccionDeCondonar.cuentas] dice de cuántas cuentas se está hablando, porque
 * condonar desde el cliente **pregunta a cuál** antes de hacer nada; y
 * [AccionDeNotas] trae su distintivo, que es lo único que dice si esa puerta
 * tiene algo escrito sin abrirla.
 *
 * Los dos son opcionales: el detalle de VENTA no monta menú y sus tres botones
 * se quedan como estaban —ahí no hay ambigüedad de cuál venta, así que no hay
 * nada que preguntar.
 */
@Immutable
data class MenuDelDock(
    val condonar: AccionDeCondonar? = null,
    val notas: AccionDeNotas? = null
) {
    internal val vacio: Boolean get() = condonar == null && notas == null
}

/**
 * **Condonar, desde el cliente.**
 *
 * [cuentas] es cuántas cuentas cobrables tiene la puerta. No es adorno: con dos
 * o más, tocar esto abre la hoja **"¿A cuál cuenta?"** que ya existe para el
 * abono, y decirlo en el renglón evita que el cobrador crea que va a condonar
 * "todo". Con una sola, la hoja no aparece.
 */
@Immutable
data class AccionDeCondonar(val onAbrir: () -> Unit, val cuentas: Int)

/**
 * **El renglón de una acción del menú.**
 *
 * Ancho completo y alto de toque, no una celda angosta: adentro del menú el
 * alto ya no es escaso —el menú se cierra— y una fila ancha se lee y se toca
 * mejor que un cuadro de 78 dp.
 */
@Composable
internal fun RenglonDelMenu(
    texto: String,
    apoyo: String?,
    contenido: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    distintivo: Pair<Color, String>? = null
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TOQUE_DEL_RENGLON),
        shape = MspTheme.shapes.button,
        color = MspTheme.colors.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MspTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
        ) {
            Text(
                text = texto,
                style = MspTheme.type.buttonLarge,
                color = contenido
            )
            distintivo?.let { (color, descripcion) ->
                // El MISMO `testTag` que llevaba el punto cuando Notas era un
                // botón del dock: lo que cambió es dónde vive el renglón, no
                // qué significa el punto, y renombrarlo habría dejado los tests
                // del distintivo buscando un nodo que ya no existe en vez de
                // seguirlo a su sitio nuevo.
                Box(
                    modifier = Modifier
                        .size(PUNTO_DEL_RENGLON)
                        .clip(MspTheme.shapes.chip)
                        .background(color)
                        .semantics { contentDescription = descripcion }
                        .testTag(DISTINTIVO_DE_NOTAS_TAG)
                )
            }
            if (apoyo != null) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = apoyo,
                    style = MspTheme.type.caption,
                    color = MspTheme.colors.onSurfaceMuted
                )
            }
        }
    }
}

/**
 * **La hoja del menú, que crece desde la barra.**
 *
 * No aparece encima del dock: crece **desde** él. El degradado de la barra sube
 * con ella —la barra mide más, así que su degradado empieza más arriba— y las
 * dos se leen como una sola pieza en vez de como una tarjeta pegada encima.
 *
 * ## El escalonado, y por qué al cerrar va al revés y más rápido
 *
 * Los renglones entran **de abajo hacia arriba**, [PASO_DEL_ESCALONADO] entre
 * uno y otro: el que nace pegado al dock es el primero, así que el movimiento
 * sale de donde estaba el dedo. Al cerrar el orden se invierte y el paso se
 * acorta ([PASO_AL_CERRAR]): **abrir se siente deliberado, cerrar inmediato**.
 * Es la asimetría que tiene cualquier menú que no estorba.
 *
 * La curva es de salida suave —arranca rápido y frena— y es la misma que el
 * dueño aprobó en el mock.
 *
 * ## Con movimiento reducido no hay escalonado
 *
 * [sinMovimiento] pone todas las duraciones en cero: los renglones aparecen y
 * desaparecen, sin desplazamiento y sin espera. Es el criterio del design
 * system —*crossfade o instantáneo*— y la señal la combina
 * `rememberMspReducedMotion`, que ya mira el ajuste del sistema **y** la casilla
 * de Configuración.
 */
@Composable
internal fun HojaDelMenu(
    menu: MenuDelDock,
    abierto: Boolean,
    sinMovimiento: Boolean,
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val renglones = buildList {
        menu.condonar?.let { add(it) }
        menu.notas?.let { add(it) }
    }
    if (renglones.isEmpty()) return
    val transicion = updateTransition(targetState = abierto, label = "menu")
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        renglones.forEachIndexed { indice, accion ->
            // Desde ABAJO: el renglón pegado al dock es el índice 0 del
            // escalonado, aunque sea el último de la columna.
            val desdeAbajo = renglones.lastIndex - indice
            val avance by transicion.animateFloat(
                transitionSpec = {
                    val abriendo = targetState
                    tween(
                        durationMillis = when {
                            sinMovimiento -> 0
                            abriendo -> DURACION_AL_ABRIR
                            else -> DURACION_AL_CERRAR
                        },
                        delayMillis = when {
                            sinMovimiento -> 0
                            abriendo -> PASO_DEL_ESCALONADO * desdeAbajo
                            else -> PASO_AL_CERRAR * indice
                        },
                        easing = SALIDA_SUAVE
                    )
                },
                label = "renglon$indice"
            ) { if (it) 1f else 0f }
            if (avance <= 0f) return@forEachIndexed
            val fila = Modifier.graphicsLayer {
                alpha = avance
                translationY = (1f - avance) * DESPLAZAMIENTO.toPx()
            }
            when (accion) {
                is AccionDeCondonar -> RenglonDelMenu(
                    texto = "Condonar",
                    apoyo = if (accion.cuentas > 1) "${accion.cuentas} cuentas" else null,
                    contenido = MspTheme.colors.danger,
                    onClick = {
                        onCerrar()
                        accion.onAbrir()
                    },
                    modifier = fila.testTag(MENU_CONDONAR_TAG)
                )

                is AccionDeNotas -> RenglonDelMenu(
                    texto = "Notas",
                    apoyo = null,
                    contenido = if (accion.advierte) {
                        MspTheme.colors.danger
                    } else {
                        MspTheme.colors.onSurface
                    },
                    onClick = {
                        onCerrar()
                        accion.onAbrir()
                    },
                    modifier = fila.testTag(MENU_NOTAS_TAG),
                    distintivo = when {
                        accion.advierte -> MspTheme.colors.danger to "Con advertencia"
                        accion.conContenido -> MspTheme.colors.brand to "Con notas"
                        else -> null
                    }
                )
            }
        }
        Spacer(Modifier.height(MspTheme.spacing.xs))
    }
}

/**
 * **El velo que atenúa lo de atrás sin taparlo.**
 *
 * Más claro que el de las hojas modales ([VELO_DEL_MENU] contra el `0x99` de
 * `HojaDeAbono`) y a propósito: una hoja de dinero **tapa** porque nada de
 * atrás debe alcanzarse; este menú sólo aparta. Tocarlo cierra, que es el gesto
 * que cualquiera intenta primero.
 */
@Composable
internal fun VeloDelMenu(onCerrar: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VELO_DEL_MENU)
            .pointerInput(Unit) { detectTapGestures { onCerrar() } }
            .testTag(VELO_DEL_MENU_TAG)
    )
}

/**
 * **Los tres puntos, que giran 90° al abrir.**
 *
 * El giro no es adorno: es lo único que dice que ese botón está en su estado
 * abierto cuando el menú ya se desplegó. Con movimiento reducido el giro es
 * instantáneo, no desaparece — sigue siendo información.
 */
@Composable
internal fun BotonDelMenu(
    abierto: Boolean,
    sinMovimiento: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transicion = updateTransition(targetState = abierto, label = "puntos")
    val giro by transicion.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = if (sinMovimiento) 0 else DURACION_DEL_GIRO,
                easing = SALIDA_SUAVE
            )
        },
        label = "giro"
    ) { if (it) GRADOS_DEL_GIRO else 0f }
    Surface(
        onClick = onClick,
        modifier = modifier
            .width(ANCHO_DEL_MENU)
            .heightIn(min = TOQUE_DEL_RENGLON),
        shape = MspTheme.shapes.button,
        color = MspTheme.colors.surface
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = AccionesIconos.Puntos,
                contentDescription = if (abierto) "Cerrar el menú" else "Más acciones",
                tint = MspTheme.colors.onSurface,
                modifier = Modifier
                    .size(GLIFO_DEL_MENU)
                    .rotate(giro)
            )
        }
    }
}

/** Alto mínimo tocable de un renglón del menú — la regla de 50 dp del repo. */
private val TOQUE_DEL_RENGLON = 56.dp

/** Ancho del botón de tres puntos: cuadrado sobre el alto de toque. */
private val ANCHO_DEL_MENU = 56.dp

private val GLIFO_DEL_MENU = 20.dp

private val PUNTO_DEL_RENGLON = 8.dp

/** Cuánto sube cada renglón al entrar. */
private val DESPLAZAMIENTO = 16.dp

/** El velo del menú: atenúa, no tapa. */
private val VELO_DEL_MENU = Color(0x66000000)

/** Lo que el dueño aprobó: ~55 ms entre un renglón y el siguiente al abrir. */
private const val PASO_DEL_ESCALONADO = 55

/** Al cerrar el escalonado va al revés y más apretado. */
private const val PASO_AL_CERRAR = 25

private const val DURACION_AL_ABRIR = 260

private const val DURACION_AL_CERRAR = 140

private const val DURACION_DEL_GIRO = 220

private const val GRADOS_DEL_GIRO = 90f

/**
 * `cubic-bezier(.16, 1, .3, 1)` — la curva del mock: arranca rápido y frena
 * largo. Es la que hace que el menú se sienta "traído" y no "disparado".
 */
@Suppress("MagicNumber")
private val SALIDA_SUAVE = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
