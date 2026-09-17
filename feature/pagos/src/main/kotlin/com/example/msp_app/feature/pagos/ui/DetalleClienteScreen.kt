@file:Suppress(
    "TooManyFunctions"
) // una funcion por hoja de la pantalla; fusionarlas rehace el muro que el rediseño partio.

package com.example.msp_app.feature.pagos.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.designsystem.component.MspPrivacyEyeToggle
import com.example.msp_app.core.designsystem.component.MspThemeToggle
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.CuentaDelAbono
import com.example.msp_app.feature.pagos.domain.model.DetalleCliente
import com.example.msp_app.feature.pagos.domain.model.SenalDeFicha
import com.example.msp_app.feature.pagos.ui.components.AccionesDelCliente
import com.example.msp_app.feature.pagos.ui.components.AfordanteDeLaFicha
import com.example.msp_app.feature.pagos.ui.components.BloqueDeIdentidad
import com.example.msp_app.feature.pagos.ui.components.CifrasDelCliente
import com.example.msp_app.feature.pagos.ui.components.ContactoEnLaHoja
import com.example.msp_app.feature.pagos.ui.components.DockDeAcciones
import com.example.msp_app.feature.pagos.ui.components.HojaContinua
import com.example.msp_app.feature.pagos.ui.components.HojaDeAbono
import com.example.msp_app.feature.pagos.ui.components.HojaDeLaFicha
import com.example.msp_app.feature.pagos.ui.components.ProductoDelCliente
import com.example.msp_app.feature.pagos.ui.components.RitmoDelCliente
import com.example.msp_app.feature.pagos.ui.components.SaldoDelCliente
import com.example.msp_app.feature.pagos.ui.components.SeccionDeHoja
import com.example.msp_app.feature.pagos.ui.components.SeccionDeLaFicha
import com.example.msp_app.feature.pagos.ui.components.Separador
import com.example.msp_app.feature.pagos.ui.components.TituloDeHoja
import com.example.msp_app.feature.pagos.ui.components.VentaEnLaHoja
import com.example.msp_app.feature.pagos.ui.components.VerLosContactos
import com.example.msp_app.feature.pagos.ui.components.VerTodos

/** `testTag` del título de la pantalla — el nombre del cliente. */
const val TITULO_DE_CLIENTE_TAG: String = "pagos_titulo_cliente"

/**
 * El destino: conecta el ViewModel con el contenido puro.
 *
 * ## A qué cuenta va el abono (arreglo de esta pasada)
 *
 * Antes esto resolvía la cuenta con `cuentaQueEncabeza`, o sea
 * `ventas.firstOrNull()`: **el dinero entraba a la primera cuenta de la lista sin
 * decirlo en ninguna parte**. Con dos cuentas, el abono podía caer en la
 * equivocada y nadie se enteraba hasta que cuadraban. No era una decisión de
 * diseño, era un defecto.
 *
 * Ahora el dock no elige: pide la cuenta. Con una sola venta va directo —el flujo
 * es idéntico al de siempre—, y con dos o más abre la hoja que pregunta a cuál
 * entra, con la de atrasos preseleccionada. Ver `HojaDeAbono`.
 *
 * **Provee el tema.** `:app` nunca provee `MspTheme` —monta `MspappTheme`, el
 * Material legado— y su `NavHost` no envuelve a ningún destino: sin este bloque
 * la primera lectura de `MspTheme.colors` revienta con
 * `IllegalStateException("MspTheme ausente")` al abrir la pantalla. El
 * razonamiento completo está en el KDoc de [ListaDeClientesScreen].
 */
@Composable
fun DetalleClienteScreen(
    viewModel: DetalleClienteViewModel,
    onAtras: () -> Unit,
    onAbrirVenta: (Int) -> Unit,
    onRegistrarAbono: (Int) -> Unit,
    onRegistrarVisita: (Int, Int?) -> Unit,
    onVerContactos: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MspTheme {
        DetalleClienteContent(
            state = state,
            onAtras = onAtras,
            onAbrirVenta = onAbrirVenta,
            // El ViewModel contesta la cuenta cuando hay una sola; con dos o más
            // abre la hoja y contesta `null`, así que no se navega todavía.
            onRegistrarAbono = { viewModel.registrarAbono()?.let(onRegistrarAbono) },
            abono = AccionesDelAbono(
                onElegir = viewModel::elegirCuenta,
                onContinuar = { viewModel.confirmarCuenta()?.let(onRegistrarAbono) },
                onCerrar = viewModel::cerrarEleccionDeCuenta
            ),
            onRegistrarVisita = { onRegistrarVisita(viewModel.clienteId, null) },
            onVerContactos = { onVerContactos(viewModel.clienteId) },
            // Las tres acciones que salen de la app las resuelve el ViewModel
            // por `AccionesExternasPort`, no la navegación: `:app` no tiene por
            // qué saber cómo se marca un teléfono, y el fallo de abrirlas tiene
            // que quedar reportado (norma de errores).
            contacto = AccionesDeContacto(
                onLlamar = viewModel::marcar,
                onWhatsApp = viewModel::escribirPorWhatsApp,
                onComoLlegar = viewModel::comoLlegar
            ),
            onAlternarTema = viewModel::alternarTema,
            onAlternarPrivacidad = viewModel::alternarPrivacidad,
            fichaDelCliente = AccionesDeLaFicha(
                onEditar = viewModel::editarFicha,
                onCerrar = viewModel::cerrarFicha,
                onSenal = viewModel::alternarSenal,
                onNota = viewModel::escribirNota,
                onGuardar = viewModel::guardarFicha
            ),
            modifier = modifier
        )
    }
}

/**
 * Las tres acciones que salen de la app: llamar, WhatsApp y cómo llegar.
 *
 * Van juntas por la misma razón que [AccionesDeLaFicha]: el contenido ya recibe
 * suficientes lambdas y detekt corta en siete (`LongParameterList`).
 */
@Immutable
data class AccionesDeContacto(
    val onLlamar: () -> Unit = {},
    val onWhatsApp: () -> Unit = {},
    val onComoLlegar: () -> Unit = {}
)

/**
 * El detalle de cliente, **hoja continua** (variante B del lienzo).
 *
 * ## Qué cambió y por qué
 *
 * La versión anterior era una pila de siete tarjetas sueltas, con el racimo de
 * estados pegado al nombre y un "⋯" que abría la pantalla legada. El dueño la
 * rechazó con tres frases: *"se ve medio rara"*, *"quiero tenerla más completa"*,
 * *"que se vea como una app cara"*. Las tres tienen traducción concreta:
 *
 *  - **"Medio rara"** era el ritmo vertical. Siete tarjetas con el mismo aire
 *    entre todo se leen como cosas sueltas. Ahora son cinco hojas, y dentro de
 *    cada una el aire antes de un separador es mayor que el aire entre dos
 *    renglones del mismo bloque — que es lo que hace que un bloque se lea como
 *    un bloque.
 *  - **"Más completa"** eran datos que la base YA tenía y nadie pintaba:
 *    `IMPORTE_PAGO_PROMEDIO` ("suele dar"), `FECHA_ULT_PAGO`,
 *    `NUM_PAGOS_ATRASADOS`, el día de la ruta y los productos con su importe.
 *  - **"App cara"** son las acciones como iconos. El cuadro de mapa que también
 *    respondía a esa frase se fue con `:core:mapas`, y "cómo llegar" quedó como
 *    la cuarta de esas acciones.
 *
 * ## Sin botón de volver, y el nombre como título
 *
 * Igual que la lista: el nombre del cliente ocupa el renglón completo y a su
 * derecha van el ojo de privacidad y el cambio de tema. La flecha de atrás se
 * fue porque el gesto del sistema ya vuelve y la franja que ocupaba vale más
 * como nombre.
 *
 * ## Sin "⋯" y sin "hoy liquida todo con"
 *
 * El "⋯" abría la pantalla legada y era el único camino a ella desde aquí. Lo que
 * sí llevaba —"ver los N contactos"— tiene ahora su propio destino, así que
 * quitarlo no borró ninguna función.
 *
 * "Hoy liquida todo con" salió porque **liquidar es por venta**, contra un
 * `DOCTO_CC_ACR_ID`: una suma a nivel cliente no corresponde a ninguna operación
 * que la app pueda ejecutar. Vive dentro del detalle de cada venta.
 *
 * Composable PURO sobre [DetalleClienteUiState]: no lee puertos, no deriva
 * estados y no emite telemetría.
 */
@Composable
fun DetalleClienteContent(
    state: DetalleClienteUiState,
    onAtras: () -> Unit,
    onAbrirVenta: (Int) -> Unit,
    onRegistrarAbono: () -> Unit,
    onRegistrarVisita: () -> Unit,
    onVerContactos: () -> Unit,
    onAlternarTema: () -> Unit,
    onAlternarPrivacidad: () -> Unit,
    modifier: Modifier = Modifier,
    contacto: AccionesDeContacto = AccionesDeContacto(),
    abono: AccionesDelAbono = AccionesDelAbono(),
    fichaDelCliente: AccionesDeLaFicha = AccionesDeLaFicha()
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MspTheme.colors.background)
            // Ruling BR — DESPUÉS del `background`, para que el color siga pintándose a
            // sangre bajo la barra de estado y el inset solo baje el CONTENIDO. Sin esto la
            // app corre `enableEdgeToEdge()` y la ventana `StatusBar` del sistema queda
            // ENCIMA del encabezado y se come sus taps. La compuerta es
            // `CadaPantallaDeCobranzaRespetaLaBarraDeEstadoTest`.
            .systemBarsPadding()
    ) {
        val detalle = state.detalle
        Box(modifier = Modifier.weight(1f)) {
            when {
                state.cargando -> Cargando()
                detalle == null -> MensajeDeError(state.error, onAtras)
                else -> CuerpoDelCliente(
                    detalle = detalle,
                    ocultos = state.montosOcultos,
                    temaOscuro = state.temaOscuro,
                    onAbrirVenta = onAbrirVenta,
                    onVerContactos = onVerContactos,
                    onAlternarTema = onAlternarTema,
                    onAlternarPrivacidad = onAlternarPrivacidad,
                    contacto = contacto,
                    onEditarFicha = fichaDelCliente.onEditar
                )
            }
        }
        if (detalle != null) {
            DockDeAcciones(
                textoPrimario = "registrar abono",
                onPrimario = onRegistrarAbono,
                onVisita = onRegistrarVisita,
                // Sin "⋯": ya no hay camino a la pantalla legada desde aquí.
                onMasAcciones = null
            )
        }
    }
    HojaDeLaFicha(
        edicion = state.edicionDeLaFicha,
        onCerrar = fichaDelCliente.onCerrar,
        onSenal = fichaDelCliente.onSenal,
        onNota = fichaDelCliente.onNota,
        onGuardar = fichaDelCliente.onGuardar
    )
    val eleccion = state.eleccionDeCuenta
    if (eleccion != null && state.detalle != null) {
        HojaDeAbono(
            cuentas = CuentaDelAbono.cobrables(state.detalle.ventas),
            elegida = eleccion.elegida,
            onElegir = abono.onElegir,
            onContinuar = abono.onContinuar,
            onCerrar = abono.onCerrar,
            ocultos = state.montosOcultos
        )
    }
}

/**
 * Las tres acciones de la hoja "¿a cuál cuenta?".
 *
 * Juntas por la misma razón que [AccionesDeContacto] y [AccionesDeLaFicha]: el
 * contenido ya recibe muchas lambdas y detekt corta en siete.
 */
@Immutable
data class AccionesDelAbono(
    val onElegir: (Int) -> Unit = {},
    val onContinuar: () -> Unit = {},
    val onCerrar: () -> Unit = {}
)

/**
 * Las cinco acciones de la ficha, juntas.
 *
 * Van en un objeto y no en cinco parámetros sueltos porque
 * [DetalleClienteContent] ya recibe muchas lambdas y detekt corta ahí
 * (`LongParameterList`); además así la pantalla del golden pasa un default
 * inerte en vez de repetir cinco `{}`. Mismo criterio que
 * `AccionesDeLaVisita` en `:feature:visitas`.
 */
@Immutable
data class AccionesDeLaFicha(
    val onEditar: () -> Unit = {},
    val onCerrar: () -> Unit = {},
    val onSenal: (SenalDeFicha) -> Unit = {},
    val onNota: (String) -> Unit = {},
    val onGuardar: () -> Unit = {}
)

@Composable
private fun CuerpoDelCliente(
    detalle: DetalleCliente,
    ocultos: Boolean,
    temaOscuro: Boolean,
    onAbrirVenta: (Int) -> Unit,
    onVerContactos: () -> Unit,
    onAlternarTema: () -> Unit,
    onAlternarPrivacidad: () -> Unit,
    contacto: AccionesDeContacto,
    onEditarFicha: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MspTheme.spacing.md)
    ) {
        EncabezadoDelCliente(
            detalle = detalle,
            ocultos = ocultos,
            temaOscuro = temaOscuro,
            onAlternarTema = onAlternarTema,
            onAlternarPrivacidad = onAlternarPrivacidad,
            onEditarFicha = onEditarFicha
        )
        Spacer(Modifier.height(MspTheme.spacing.sm + MspTheme.spacing.xs))
        HojaDeIdentidad(detalle, contacto, onEditarFicha)
        Spacer(Modifier.height(MspTheme.spacing.sm + MspTheme.spacing.xs))
        HojaDeDinero(detalle, ocultos)
        Spacer(Modifier.height(MspTheme.spacing.sm + MspTheme.spacing.xs))
        HojaDeVentas(detalle, ocultos, onAbrirVenta)
        if (detalle.productos.isNotEmpty()) {
            Spacer(Modifier.height(MspTheme.spacing.sm + MspTheme.spacing.xs))
            HojaDeProductos(detalle)
        }
        if (detalle.contactos.isNotEmpty()) {
            Spacer(Modifier.height(MspTheme.spacing.sm + MspTheme.spacing.xs))
            HojaDeContactos(detalle, onVerContactos)
        }
        // La ficha vive AL FONDO, donde la puso la Task 16 y donde no empuja un
        // solo dp de lo que está arriba — que es el dinero, por lo que el
        // cobrador abrió esta pantalla. `LaFichaSeVeYSeTocaTest` lo mide. Lo que
        // sí sube, y gratis, es el afordante del encabezado: ahí es donde una
        // advertencia grita.
        SeccionDeLaFicha(
            ficha = detalle.ficha,
            notaDeLaVenta = detalle.notaDeLaVenta,
            onEditar = onEditarFicha
        )
        Spacer(Modifier.height(MspTheme.spacing.lg))
    }
}

/**
 * El nombre como título, con el ojo y el tema a su derecha — el mismo renglón que
 * la lista, para que las dos pantallas de nivel de cliente se sientan una sola.
 *
 * El nombre se recorta con elipsis en vez de partirse en dos renglones: a dos
 * líneas el encabezado empuja todo lo de abajo y el saldo deja de verse sin
 * desplazar.
 */
@Composable
private fun EncabezadoDelCliente(
    detalle: DetalleCliente,
    ocultos: Boolean,
    temaOscuro: Boolean,
    onAlternarTema: () -> Unit,
    onAlternarPrivacidad: () -> Unit,
    onEditarFicha: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = MspTheme.spacing.sm)
            // Alto FIJO, y no el que resulte del contenido. El afordante aparece y
            // desaparece con el estado de la ficha, así que sin este piso la fila
            // mediría distinto con advertencia que sin ella y **la ficha movería el
            // dinero** — que es justo lo que `LaFichaSeVeYSeTocaTest` prohíbe. El
            // valor es el alto del afordante, que es la pieza más alta de la fila.
            .heightIn(min = ALTO_DEL_ENCABEZADO),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        Text(
            text = detalle.nombre,
            style = MspTheme.type.greeting,
            color = MspTheme.colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .testTag(TITULO_DE_CLIENTE_TAG)
        )
        // El afordante va EN el renglón del título —cuesta cero dp verticales, que
        // es su razón de ser— y **solo cuando tiene algo que gritar**.
        //
        // En el golden se vio el costo de ponerlo siempre: con "ver ficha" puesto,
        // el nombre se recortaba a "Victoria Fl…". Y "ver ficha" no aporta nada
        // nuevo, porque la ficha ya tiene su icono dos filas abajo, en las
        // acciones: la pastilla era el MISMO camino dicho dos veces, comiéndose el
        // título de la pantalla.
        //
        // Lo que el icono NO puede decir es la advertencia. *"Hay perro"* y *"no ir
        // solo"* tienen que llegarle al cobrador antes de que abra el portón, y una
        // ficha ilegible tiene que avisarse para que nadie crea que está vacía. En
        // esos dos casos la pastilla aparece y el nombre cede el ancho: entre un
        // nombre entero y una advertencia a tiempo, gana la advertencia.
        if (detalle.ficha == null || detalle.ficha.advertencias.isNotEmpty()) {
            AfordanteDeLaFicha(ficha = detalle.ficha, onEditar = onEditarFicha)
        }
        MspPrivacyEyeToggle(masked = ocultos, onToggle = onAlternarPrivacidad)
        MspThemeToggle(darkTheme = temaOscuro, onToggle = onAlternarTema)
    }
}

@Composable
private fun HojaDeIdentidad(
    detalle: DetalleCliente,
    contacto: AccionesDeContacto,
    onEditarFicha: () -> Unit
) {
    val visuales = detalle.ventas.take(CUADROS_EN_EL_RACIMO).map { estadoVisualDe(it.estado) }
    HojaContinua {
        SeccionDeHoja(primera = true) {
            BloqueDeIdentidad(
                estados = visuales.map { it.icono },
                colores = visuales.map { it.fondo to it.contenido },
                zonaYDireccion = listOf(detalle.zona, detalle.direccion)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
            )
        }
        // "Cómo llegar" es una acción más de la fila, permanente. Antes vivía
        // arriba, dentro de un cuadro de mapa de 130 dp que pintaba `:core:mapas`
        // — y ese módulo se fue: su renderizador ocupaba 47.9 MB de `.so` en
        // cuatro ABIs y viajaba en el APK aunque nadie bajara las teselas. Sin
        // renderizador el cuadro sería un rectángulo gris que no enseña nada.
        //
        // Lo que NO se fue es `ultimoCobroAqui`: sigue alimentando el `geo:` que
        // arma `IntentAccionesExternasAdapter`, así que la app de mapas del
        // teléfono abre en la coordenada donde de verdad se cobró y no en una
        // dirección geocodificada. En una colonia sin numeración esa es la
        // diferencia entre llegar a la puerta y llegar a la calle.
        SeccionDeHoja {
            AccionesDelCliente(
                onLlamar = contacto.onLlamar,
                onWhatsApp = contacto.onWhatsApp,
                onFicha = onEditarFicha,
                onComoLlegar = contacto.onComoLlegar
            )
        }
    }
}

@Composable
private fun HojaDeDinero(detalle: DetalleCliente, ocultos: Boolean) {
    HojaContinua {
        SeccionDeHoja(primera = true) {
            SaldoDelCliente(
                saldo = detalle.saldoTotal,
                atrasos = detalle.resumen.atrasos,
                ocultos = ocultos
            )
        }
        SeccionDeHoja { CifrasDelCliente(detalle.resumen, ocultos = ocultos) }
        SeccionDeHoja {
            RitmoDelCliente(
                resumen = detalle.resumen,
                diaDeRuta = detalle.diaDeRuta,
                frecuencia = detalle.frecuencia
            )
        }
    }
}

@Composable
private fun HojaDeVentas(detalle: DetalleCliente, ocultos: Boolean, onAbrirVenta: (Int) -> Unit) {
    HojaContinua {
        TituloDeHoja("sus ventas")
        detalle.ventas.forEachIndexed { indice, venta ->
            if (indice > 0) Separador()
            VentaEnLaHoja(
                venta = venta,
                onAbrir = { onAbrirVenta(venta.ventaId) },
                ocultos = ocultos
            )
        }
    }
}

@Composable
private fun HojaDeProductos(detalle: DetalleCliente) {
    HojaContinua {
        TituloDeHoja("productos")
        detalle.productos.forEachIndexed { indice, producto ->
            if (indice > 0) Separador()
            ProductoDelCliente(producto)
        }
    }
}

@Composable
private fun HojaDeContactos(detalle: DetalleCliente, onVerContactos: () -> Unit) {
    HojaContinua {
        TituloDeHoja("últimos contactos")
        detalle.contactos.forEachIndexed { indice, contacto ->
            if (indice > 0) Separador()
            ContactoEnLaHoja(contacto = contacto, fecha = AppTime.toBusinessDate(contacto.fecha))
        }
        Separador()
        VerLosContactos(cuantos = detalle.totalContactos, onVer = onVerContactos)
    }
}

@Composable
internal fun Cargando() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MspTheme.colors.brand)
    }
}

@Composable
internal fun MensajeDeError(error: ErrorDeDetalle?, onAtras: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MspTheme.spacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = when (error) {
                ErrorDeDetalle.NO_ESTA_EN_EL_TELEFONO -> "no está en el teléfono"
                else -> "no se pudo abrir"
            },
            style = MspTheme.type.cardTitle,
            color = MspTheme.colors.onSurface
        )
        Spacer(Modifier.height(MspTheme.spacing.sm))
        VerTodos("volver", onAtras)
    }
}

/**
 * Cuántos cuadros de estado caben en el racimo de identidad.
 *
 * Cuatro, como antes: a 22 dp más su aire, un quinto empieza a comerse el
 * renglón de zona y dirección, que es texto y pesa más que un cuadro de más.
 */
private const val CUADROS_EN_EL_RACIMO = 4

/**
 * El alto fijo del renglón del título.
 *
 * Es el alto del afordante de la ficha —la pieza más alta de esa fila— para que
 * la fila mida lo mismo con advertencia y sin ella. Ver el comentario en
 * [EncabezadoDelCliente].
 */
private val ALTO_DEL_ENCABEZADO = 56.dp
