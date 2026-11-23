package com.example.msp_app.feature.garantias.screenshot

import androidx.compose.runtime.Composable
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.feature.garantias.application.ArticuloDelFolio
import com.example.msp_app.feature.garantias.application.DetalleDelFolio
import com.example.msp_app.feature.garantias.application.EstadoDeLaLectura
import com.example.msp_app.feature.garantias.application.FalloDeCaptura
import com.example.msp_app.feature.garantias.application.FolioEnBandeja
import com.example.msp_app.feature.garantias.domain.model.AccionDeGarantia
import com.example.msp_app.feature.garantias.domain.model.ArticuloDeGarantia
import com.example.msp_app.feature.garantias.domain.model.ClienteDeGarantia
import com.example.msp_app.feature.garantias.domain.model.DetalleDeGarantia
import com.example.msp_app.feature.garantias.domain.model.DomicilioDeGarantia
import com.example.msp_app.feature.garantias.domain.model.EstadoDeCuenta
import com.example.msp_app.feature.garantias.domain.model.EstadoLocalDeFolio
import com.example.msp_app.feature.garantias.domain.model.EventoDeGarantia
import com.example.msp_app.feature.garantias.domain.model.FolioSeguido
import com.example.msp_app.feature.garantias.domain.model.FotoDeCaptura
import com.example.msp_app.feature.garantias.domain.model.OrigenDeFolio
import com.example.msp_app.feature.garantias.domain.model.Ubicacion
import com.example.msp_app.feature.garantias.ui.AbrirFolioContenido
import com.example.msp_app.feature.garantias.ui.AbrirFolioUiState
import com.example.msp_app.feature.garantias.ui.BandejaDeGarantiasContenido
import com.example.msp_app.feature.garantias.ui.BandejaUiState
import com.example.msp_app.feature.garantias.ui.DetalleDeFolioContenido
import com.example.msp_app.feature.garantias.ui.DetalleDeFolioUiState
import com.example.msp_app.feature.garantias.ui.EntregaContenido
import com.example.msp_app.feature.garantias.ui.EntregaUiState
import com.example.msp_app.feature.garantias.ui.RecoleccionContenido
import com.example.msp_app.feature.garantias.ui.RecoleccionUiState
import com.example.msp_app.feature.garantias.ui.components.EstadoDelPadDeFirma
import org.junit.Test

class GarantiasV2ScreenshotTest : GarantiasScreenshotTest() {

    private val instante = java.time.Instant.parse("2026-11-13T10:00:00Z")

    private val folioPendiente = FolioSeguido(
        id = "folio-local-1",
        folioId = null,
        origen = OrigenDeFolio.PISO,
        estadoLocal = EstadoLocalDeFolio.PENDIENTE_ENVIO,
        abiertoEn = instante,
        actualizadoEn = instante
    )

    private val folioAbierto = FolioSeguido(
        id = "folio-local-2",
        folioId = "G-0002",
        origen = OrigenDeFolio.CLIENTE,
        estadoLocal = EstadoLocalDeFolio.ABIERTO,
        abiertoEn = instante,
        actualizadoEn = instante
    )

    private val folioRechazado = FolioSeguido(
        id = "folio-local-3",
        folioId = "G-0003",
        origen = OrigenDeFolio.PISO,
        estadoLocal = EstadoLocalDeFolio.RECHAZADO,
        abiertoEn = instante,
        actualizadoEn = instante,
        ultimoError = "Sin permiso"
    )

    private val cliente = ClienteDeGarantia(
        clienteId = 7,
        nombre = "Juan Pérez",
        ventaId = 21,
        estadoDeCuenta = EstadoDeCuenta.LIQUIDADA,
        calle = "Av. Reforma",
        ciudad = "CDMX"
    )

    private val foto = FotoDeCaptura("foto-1", "fotos/foto-1.jpg", "image/jpeg")

    private val ubicacion = Ubicacion(latitud = 19.4326, longitud = -99.1332)

    private val evento = EventoDeGarantia(
        id = "evento-1",
        tipo = "recoleccion",
        descripcion = "Artículo recogido",
        usuario = "agente",
        creadoEn = instante,
        deviceCreatedAt = instante,
        claveIdempotencia = "clave-evento-1",
        etapaDesde = "registrado",
        etapaHasta = "recolectado",
        imagenes = emptyList()
    )

    private val articuloRemoto = ArticuloDeGarantia(
        id = "articulo-servidor-1",
        rol = "original",
        clave = "K-123",
        descripcion = "Bocina",
        etapa = "registrado",
        ubicacion = "taller",
        creadoEn = instante,
        actualizadoEn = instante
    )

    private val articuloDelFolio = ArticuloDelFolio(
        remoto = articuloRemoto,
        articuloRef = "articulo-local-1"
    )

    private val detalleDisponible = DetalleDeGarantia(
        id = "folio-servidor-1",
        folio = "G-0001",
        origen = OrigenDeFolio.PISO,
        estado = "abierto",
        descripcion = "Bocina que no enciende",
        abiertoPor = "agente",
        creadoEn = instante,
        actualizadoEn = instante,
        articulos = listOf(articuloRemoto),
        eventos = listOf(evento)
    )

    private val padVacio = EstadoDelPadDeFirma()

    /**
     * Graba o compara las seis combinaciones de tema y tamaño de letra de un
     * mismo estado. El contenido se pasa una sola vez y se reutiliza en cada
     * captura: así la matriz no se copia veinte veces y un cambio de fuente o
     * de tema se ve siempre sobre la misma pantalla.
     *
     * Los nombres salen con la misma convención que las capturas sueltas
     * (`..._light_1_0`, `..._dark_2_0`), así que las existentes no cambian.
     */
    private fun matrizDe(nombre: String, contenido: @Composable () -> Unit) {
        listOf(false, true).forEach { dark ->
            listOf(
                FontSizeLevel.NORMAL,
                FontSizeLevel.GRANDE,
                FontSizeLevel.MUY_GRANDE
            ).forEach { nivel ->
                capture(
                    name = "${nombre}_${if (dark) "dark" else "light"}_${sufijoDe(nivel)}",
                    dark = dark,
                    nivel = nivel,
                    content = contenido
                )
            }
        }
    }

    @Test
    fun `bandeja con folios en toda la matriz`() = matrizDe("garantias_bandeja_folios") {
        BandejaDeGarantiasContenido(
            state = BandejaUiState(
                cargando = false,
                folios = listOf(
                    FolioEnBandeja(
                        folio = folioPendiente,
                        comandosPendientes = 2,
                        fotosPendientes = 1,
                        ultimoError = null
                    ),
                    FolioEnBandeja(
                        folio = folioAbierto,
                        comandosPendientes = 0,
                        fotosPendientes = 0,
                        ultimoError = null
                    ),
                    FolioEnBandeja(
                        folio = folioRechazado,
                        comandosPendientes = 1,
                        fotosPendientes = 0,
                        ultimoError = "Sin permiso"
                    )
                )
            ),
            onAtras = {},
            onAbrirFolio = {},
            onFolio = {},
            onRecargar = {}
        )
    }

    @Test
    fun `bandeja vacia light normal`() = capture(
        name = "garantias_bandeja_vacia_light_1_0",
        dark = false,
        nivel = FontSizeLevel.NORMAL
    ) {
        BandejaDeGarantiasContenido(
            state = BandejaUiState(cargando = false, folios = emptyList()),
            onAtras = {},
            onAbrirFolio = {},
            onFolio = {},
            onRecargar = {}
        )
    }

    @Test
    fun `bandeja carga light normal`() = capture(
        name = "garantias_bandeja_cargando_light_1_0",
        dark = false,
        nivel = FontSizeLevel.NORMAL
    ) {
        BandejaDeGarantiasContenido(
            state = BandejaUiState(cargando = true, folios = emptyList()),
            onAtras = {},
            onAbrirFolio = {},
            onFolio = {},
            onRecargar = {}
        )
    }

    @Test
    fun `bandeja fallo light normal`() = capture(
        name = "garantias_bandeja_fallo_light_1_0",
        dark = false,
        nivel = FontSizeLevel.NORMAL
    ) {
        BandejaDeGarantiasContenido(
            state = BandejaUiState(cargando = false, folios = emptyList(), fallo = true),
            onAtras = {},
            onAbrirFolio = {},
            onFolio = {},
            onRecargar = {}
        )
    }

    @Test
    fun `abrir folio origen piso en toda la matriz`() = matrizDe("garantias_abrir_piso") {
        AbrirFolioContenido(
            state = AbrirFolioUiState(origen = OrigenDeFolio.PISO),
            onOrigen = {},
            onBusqueda = {},
            onCliente = {},
            onDomicilio = { _, _ -> },
            onDescripcion = {},
            onClave = {},
            onDescripcionDelArticulo = {},
            onVigencia = {},
            onGuardar = {},
            onAtras = {}
        )
    }

    @Test
    fun `abrir folio origen cliente light normal`() = capture(
        name = "garantias_abrir_cliente_light_1_0",
        dark = false,
        nivel = FontSizeLevel.NORMAL
    ) {
        AbrirFolioContenido(
            state = AbrirFolioUiState(
                origen = OrigenDeFolio.CLIENTE,
                clienteElegido = cliente,
                busquedaDeCliente = "Juan",
                domicilio = DomicilioDeGarantia(calle = "Av. Reforma", ciudad = "CDMX")
            ),
            onOrigen = {},
            onBusqueda = {},
            onCliente = {},
            onDomicilio = { _, _ -> },
            onDescripcion = {},
            onClave = {},
            onDescripcionDelArticulo = {},
            onVigencia = {},
            onGuardar = {},
            onAtras = {}
        )
    }

    @Test
    fun `abrir folio con fallo light normal`() = capture(
        name = "garantias_abrir_fallo_light_1_0",
        dark = false,
        nivel = FontSizeLevel.NORMAL
    ) {
        AbrirFolioContenido(
            state = AbrirFolioUiState(
                origen = OrigenDeFolio.PISO,
                fallo = FalloDeCaptura.SIN_DESCRIPCION
            ),
            onOrigen = {},
            onBusqueda = {},
            onCliente = {},
            onDomicilio = { _, _ -> },
            onDescripcion = {},
            onClave = {},
            onDescripcionDelArticulo = {},
            onVigencia = {},
            onGuardar = {},
            onAtras = {}
        )
    }

    @Test
    fun `detalle disponible en toda la matriz`() = matrizDe("garantias_detalle_disponible") {
        DetalleDeFolioContenido(
            state = DetalleDeFolioUiState(
                cargando = false,
                detalle = DetalleDelFolio(
                    folio = folioPendiente.copy(
                        folioId = "G-0001",
                        estadoLocal = EstadoLocalDeFolio.ABIERTO
                    ),
                    estado = EstadoDeLaLectura.DISPONIBLE,
                    detalle = detalleDisponible,
                    articulos = listOf(articuloDelFolio),
                    eventos = listOf(evento),
                    comandosPendientes = 0,
                    fotosPendientes = 0
                )
            ),
            onRecolectar = {},
            onEntregar = {},
            onRecargar = {},
            onAtras = {}
        )
    }

    @Test
    fun `detalle sin señal light normal`() = capture(
        name = "garantias_detalle_sin_senal_light_1_0",
        dark = false,
        nivel = FontSizeLevel.NORMAL
    ) {
        DetalleDeFolioContenido(
            state = DetalleDeFolioUiState(
                cargando = false,
                detalle = DetalleDelFolio(
                    folio = folioPendiente,
                    estado = EstadoDeLaLectura.SIN_SENAL,
                    comandosPendientes = 2,
                    fotosPendientes = 1,
                    motivo = "No se pudo leer"
                )
            ),
            onRecolectar = {},
            onEntregar = {},
            onRecargar = {},
            onAtras = {}
        )
    }

    @Test
    fun `detalle rechazado light normal`() = capture(
        name = "garantias_detalle_rechazado_light_1_0",
        dark = false,
        nivel = FontSizeLevel.NORMAL
    ) {
        DetalleDeFolioContenido(
            state = DetalleDeFolioUiState(
                cargando = false,
                detalle = DetalleDelFolio(
                    folio = folioRechazado,
                    estado = EstadoDeLaLectura.RECHAZADO,
                    comandosPendientes = 1,
                    fotosPendientes = 0,
                    motivo = "Sin permiso"
                )
            ),
            onRecolectar = {},
            onEntregar = {},
            onRecargar = {},
            onAtras = {}
        )
    }

    @Test
    fun `recoleccion con catalogo vacio light normal`() = capture(
        name = "garantias_recoleccion_sin_catalogo_light_1_0",
        dark = false,
        nivel = FontSizeLevel.NORMAL
    ) {
        RecoleccionContenido(
            state = RecoleccionUiState(
                cargando = false,
                etapa = "registrado",
                acciones = listOf(
                    AccionDeGarantia(etapaDestino = "recolectado", etiqueta = "Marcar recolectado")
                ),
                accionElegida = "recolectado",
                catalogoVacio = true,
                fotos = listOf(foto),
                ubicacion = ubicacion,
                pidiendoUbicacion = false
            ),
            onAccion = {},
            onTomarFoto = {},
            onQuitarFoto = {},
            onPedirUbicacion = {},
            onGuardar = {},
            onAtras = {}
        )
    }

    @Test
    fun `recoleccion con catalogo en toda la matriz`() = matrizDe(
        "garantias_recoleccion_con_catalogo"
    ) {
        RecoleccionContenido(
            state = RecoleccionUiState(
                cargando = false,
                etapa = "registrado",
                acciones = listOf(
                    AccionDeGarantia(
                        etapaDestino = "enviar_proveedor",
                        etiqueta = "Enviar a proveedor"
                    )
                ),
                accionElegida = "enviar_proveedor",
                catalogoVacio = false,
                fotos = emptyList(),
                ubicacion = null,
                pidiendoUbicacion = true
            ),
            onAccion = {},
            onTomarFoto = {},
            onQuitarFoto = {},
            onPedirUbicacion = {},
            onGuardar = {},
            onAtras = {}
        )
    }

    @Test
    fun `entrega sin ubicacion ni firma light normal`() = capture(
        name = "garantias_entrega_bloqueada_light_1_0",
        dark = false,
        nivel = FontSizeLevel.NORMAL
    ) {
        EntregaContenido(
            state = EntregaUiState(
                ubicacion = null,
                pidiendoUbicacion = true,
                guardando = false
            ),
            estadoDelPad = padVacio,
            onLimpiarFirma = {},
            onPedirUbicacion = {},
            onGuardar = {},
            onAtras = {}
        )
    }

    @Test
    fun `entrega con ubicacion y firma en toda la matriz`() = matrizDe(
        "garantias_entrega_listo"
    ) {
        EntregaContenido(
            state = EntregaUiState(
                ubicacion = ubicacion,
                pidiendoUbicacion = false,
                guardando = false
            ),
            estadoDelPad = padVacio,
            onLimpiarFirma = {},
            onPedirUbicacion = {},
            onGuardar = {},
            onAtras = {}
        )
    }

    @Test
    fun `entrega fallo tecnico light normal`() = capture(
        name = "garantias_entrega_fallo_tecnico_light_1_0",
        dark = false,
        nivel = FontSizeLevel.NORMAL
    ) {
        EntregaContenido(
            state = EntregaUiState(
                ubicacion = ubicacion,
                pidiendoUbicacion = false,
                guardando = false,
                falloTecnico = true
            ),
            estadoDelPad = padVacio,
            onLimpiarFirma = {},
            onPedirUbicacion = {},
            onGuardar = {},
            onAtras = {}
        )
    }
}
