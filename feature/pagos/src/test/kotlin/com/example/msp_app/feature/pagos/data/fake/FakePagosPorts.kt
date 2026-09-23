package com.example.msp_app.feature.pagos.data.fake

import com.example.msp_app.core.common.cobranza.domain.VentanaCobro
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.feature.pagos.domain.model.ComprobanteDelAbono
import com.example.msp_app.feature.pagos.domain.model.DatosDeVenta
import com.example.msp_app.feature.pagos.domain.model.DestinoDeFoto
import com.example.msp_app.feature.pagos.domain.model.FichaDelCliente
import com.example.msp_app.feature.pagos.domain.model.GarantiaDeLaVenta
import com.example.msp_app.feature.pagos.domain.model.Liquidacion
import com.example.msp_app.feature.pagos.domain.model.Miniatura
import com.example.msp_app.feature.pagos.domain.model.PagoDelHistorial
import com.example.msp_app.feature.pagos.domain.model.ProductoDeVenta
import com.example.msp_app.feature.pagos.domain.model.VisitaDelCliente
import com.example.msp_app.feature.pagos.domain.port.AbonoARegistrar
import com.example.msp_app.feature.pagos.domain.port.AccionesExternasPort
import com.example.msp_app.feature.pagos.domain.port.ComprobantesPort
import com.example.msp_app.feature.pagos.domain.port.DestinoEnElMapa
import com.example.msp_app.feature.pagos.domain.port.FichaDelClientePort
import com.example.msp_app.feature.pagos.domain.port.GarantiasPort
import com.example.msp_app.feature.pagos.domain.port.LiquidacionPort
import com.example.msp_app.feature.pagos.domain.port.PagosPort
import com.example.msp_app.feature.pagos.domain.port.PeriodoDeCobroPort
import com.example.msp_app.feature.pagos.domain.port.PrivacidadPort
import com.example.msp_app.feature.pagos.domain.port.ProductosPort
import com.example.msp_app.feature.pagos.domain.port.RegistroDeAbonoPort
import com.example.msp_app.feature.pagos.domain.port.ResultadoDeLaFicha
import com.example.msp_app.feature.pagos.domain.port.ResultadoDelAbono
import com.example.msp_app.feature.pagos.domain.port.TemaDeLaAppPort
import com.example.msp_app.feature.pagos.domain.port.VentasPort
import com.example.msp_app.feature.pagos.domain.port.VisitasPort
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fakes escritos a mano de los cinco puertos: estado público + lista pública
 * que graba las llamadas. **Sin MockK ni Mockito**, por contrato del repo.
 */
class FakeVentasPort : VentasPort {

    var ventas: List<DatosDeVenta> = emptyList()

    /** Si no es `null`, la siguiente lectura lanza esto (camino de error del ViewModel). */
    var falla: Throwable? = null

    val clientesConsultados: MutableList<Int> = mutableListOf()

    override suspend fun ventasDelCliente(clienteId: Int): List<DatosDeVenta> {
        falla?.let { throw it }
        clientesConsultados += clienteId
        return ventas.filter { it.clienteId == clienteId }
    }

    override suspend fun venta(ventaId: Int): DatosDeVenta? {
        falla?.let { throw it }
        return ventas.firstOrNull { it.ventaId == ventaId }
    }

    /** Cuántas veces se pidió la ruta completa — lo afirma el test de "una vez por carga". */
    var lecturasDeTodas: Int = 0
        private set

    override suspend fun todasLasVentas(): List<DatosDeVenta> {
        falla?.let { throw it }
        lecturasDeTodas += 1
        return ventas
    }
}

class FakePagosPort : PagosPort {

    var pagos: List<PagoDelHistorial> = emptyList()

    val ventasConsultadas: MutableList<Int> = mutableListOf()

    val ventanasConsultadas: MutableList<VentanaCobro> = mutableListOf()

    override suspend fun pagosDe(ventaId: Int): List<PagoDelHistorial> {
        ventasConsultadas += ventaId
        return pagos.filter { it.ventaId == ventaId }
    }

    /** Filtra por la ventana igual que el adaptador Room, para que el fake no mienta. */
    override suspend fun pagosDelPeriodo(ventana: VentanaCobro): List<PagoDelHistorial> {
        ventanasConsultadas += ventana
        return pagos.filter { ventana.contiene(it.fecha) }
    }

    /**
     * Los importes que el teléfono tendría de TODA la ruta — la muestra de la
     * línea base. Vacía por default: sin muestra no hay línea base y el escalón
     * 3 no avisa, que es el lado conservador y el que casi todos los tests
     * quieren.
     */
    var importesDeLaRuta: List<Money> = emptyList()

    /** Cuántas veces se pidió la muestra de la ruta. Para afirmar que NO se pidió. */
    var vecesQueSePidioLaRuta: Int = 0

    override suspend fun importesCobrados(): List<Money> {
        vecesQueSePidioLaRuta += 1
        return importesDeLaRuta
    }

    /** Cada `pago(id)` recibido, en orden — para poder afirmar que NO se llamó. */
    val pagosConsultados: MutableList<String> = mutableListOf()

    /** Busca por id sobre el MISMO conjunto que devuelve el historial. */
    override suspend fun pago(pagoId: String): PagoDelHistorial? {
        pagosConsultados += pagoId
        return pagos.firstOrNull { it.pagoId == pagoId }
    }
}

class FakeVisitasPort : VisitasPort {

    var visitas: List<VisitaDelCliente> = emptyList()

    val ventanasConsultadas: MutableList<VentanaCobro> = mutableListOf()

    /**
     * Los clientes cuyas visitas se pidieron. Es una lista que GRABA, no la
     * semilla de entrada: un test que afirmara sobre [visitas] estaría afirmando
     * lo que él mismo puso.
     */
    val clientesConsultados: MutableList<Int> = mutableListOf()

    override suspend fun visitasDelCliente(clienteId: Int): List<VisitaDelCliente> {
        clientesConsultados += clienteId
        return visitas.filter { it.clienteId == clienteId }
    }

    override suspend fun visitasDelPeriodo(ventana: VentanaCobro): List<VisitaDelCliente> {
        ventanasConsultadas += ventana
        return visitas.filter { ventana.contiene(it.fecha) }
    }
}

/**
 * Fake de [AccionesExternasPort]. Graba qué se pidió abrir y, con [falla]
 * puesto, contesta `Result` fallido — que es el caso que la norma de errores
 * exige probar: un teléfono sin WhatsApp o sin app de mapas existe en la flota.
 */
class FakeAccionesExternasPort : AccionesExternasPort {

    /** Los teléfonos que se pidió marcar, en orden. */
    val marcados: MutableList<String> = mutableListOf()

    /** Los teléfonos a los que se pidió escribir, en orden. */
    val escritos: MutableList<String> = mutableListOf()

    /** Los destinos de mapa pedidos, en orden. */
    val destinos: MutableList<DestinoEnElMapa> = mutableListOf()

    /** Si no es `null`, las tres acciones contestan este fallo. */
    var falla: Throwable? = null

    override suspend fun marcar(telefono: String): Result<Unit> {
        marcados += telefono
        return resultado()
    }

    override suspend fun escribirPorWhatsApp(telefono: String): Result<Unit> {
        escritos += telefono
        return resultado()
    }

    override suspend fun comoLlegar(destino: DestinoEnElMapa): Result<Unit> {
        destinos += destino
        return resultado()
    }

    private fun resultado(): Result<Unit> =
        falla?.let { Result.failure(it) } ?: Result.success(Unit)
}

class FakeLiquidacionPort : LiquidacionPort {

    var liquidaciones: Map<Int, Liquidacion> = emptyMap()

    override suspend fun liquidacionDe(ventaId: Int): Liquidacion? = liquidaciones[ventaId]
}

/**
 * Fake de [ProductosPort]. Por defecto contesta VACÍO, que es el caso real de un
 * folio cuyos renglones todavía no sincronizaron — así el respaldo por comas de
 * `CargarDetalleVenta` queda cubierto por los tests que no siembran nada.
 */
class FakeProductosPort : ProductosPort {

    var porFolio: Map<String, List<ProductoDeVenta>> = emptyMap()

    /** Cada folio consultado por [productosDe], en orden — para afirmar que SÍ se preguntó. */
    val foliosConsultados: MutableList<String> = mutableListOf()

    /**
     * Cada LOTE pedido a [productosDeVarios], en orden — es sobre esta lista
     * que se afirma "una consulta por lote, no una por venta": su TAMAÑO
     * cuenta cuántas veces se llamó al puerto, y su CONTENIDO qué folios traía
     * cada llamada.
     */
    val lotesConsultados: MutableList<List<String>> = mutableListOf()

    override suspend fun productosDe(folio: String): List<ProductoDeVenta> {
        foliosConsultados += folio
        return porFolio[folio].orEmpty()
    }

    /** Un folio sin llave en [porFolio] no entra al mapa — igual que el adaptador real. */
    override suspend fun productosDeVarios(
        folios: List<String>
    ): Map<String, List<ProductoDeVenta>> {
        lotesConsultados += folios
        return folios.mapNotNull { folio -> porFolio[folio]?.let { folio to it } }.toMap()
    }
}

class FakeGarantiasPort : GarantiasPort {

    var garantias: Map<Int, GarantiaDeLaVenta> = emptyMap()

    val creditosConsultados: MutableList<Int> = mutableListOf()

    override suspend fun garantiaDe(creditoId: Int): GarantiaDeLaVenta? {
        creditosConsultados += creditoId
        return garantias[creditoId]
    }
}

class FakePeriodoDeCobroPort : PeriodoDeCobroPort {

    var inicio: Instant? = Instant.parse("2026-08-31T06:00:00Z")

    val lecturas: MutableList<Instant?> = mutableListOf()

    override suspend fun inicioDelPeriodo(): Instant? {
        lecturas += inicio
        return inicio
    }
}

/**
 * El fake de la escritura de dinero. [registrados] es la lista que graba CADA
 * llamada — es sobre ella que se afirma "ninguna ruta guarda dos veces": la
 * prueba no es que el resultado sea correcto, es que el tamaño de esta lista
 * sea exactamente uno.
 */
class FakeRegistroDeAbonoPort : RegistroDeAbonoPort {

    /** Todo lo que se intentó escribir, en orden. */
    val registrados: MutableList<AbonoARegistrar> = mutableListOf()

    /** Qué contesta el puerto. Se cambia para probar los caminos de fallo. */
    var resultado: ResultadoDelAbono = ResultadoDelAbono.REGISTRADO

    /**
     * Efecto lateral del intento de escritura, ANTES de contestar. Sirve para
     * los dos casos en que el resultado y la realidad no coinciden: la
     * escritura aterrizó pero se reportó un fallo, o la base dejó de responder.
     */
    var alRegistrar: (AbonoARegistrar) -> Unit = {}

    override suspend fun registrar(abono: AbonoARegistrar): ResultadoDelAbono {
        registrados += abono
        alRegistrar(abono)
        return resultado
    }
}

/**
 * El fake de la cámara del comprobante. Estado público + listas que graban, sin
 * MockK y sin tocar un archivo real.
 *
 * Los tres [fallaAlPreparar]/[fallaAlAceptar]/[fallaAlDescartar] existen porque
 * el contrato del puerto dice que **puede lanzar**, y la regla que manda sobre
 * la Task 22 es que ningún fallo suyo llegue al dinero. Un fake que nunca
 * lanzara dejaría esa regla sin probar.
 */
class FakeComprobantesPort : ComprobantesPort {

    /** Cada destino entregado, en orden. */
    val destinos: MutableList<DestinoDeFoto> = mutableListOf()

    /** Cada comprobante aceptado, en orden. */
    val aceptados: MutableList<ComprobanteDelAbono> = mutableListOf()

    /** Las rutas que se pidió borrar, en orden. Es lo que prueba que no se filtra disco. */
    val descartados: MutableList<String> = mutableListOf()

    /** Los `content://` que se mandó importar, en orden. */
    val importados: MutableList<String> = mutableListOf()

    /** Las rutas de las que se pidió miniatura, en orden. */
    val miniaturasPedidas: MutableList<String> = mutableListOf()

    /** El MIME que devolverá el próximo [aceptar]. Se cambia para el tipo no permitido. */
    var mime: String = "image/jpeg"

    /** El MIME que devolverá el próximo [importar]. */
    var mimeImportado: String = "image/jpeg"

    /** Lo que devuelve [miniatura]. `null` = el cuadro se pinta sin vista previa. */
    var miniaturaDeCadaArchivo: Miniatura? = null

    var fallaAlImportar: Throwable? = null

    var fallaAlPedirMiniatura: Throwable? = null

    var fallaAlPreparar: Throwable? = null

    var fallaAlAceptar: Throwable? = null

    var fallaAlDescartar: Throwable? = null

    private var siguiente = 0

    override suspend fun nuevoDestino(): DestinoDeFoto {
        fallaAlPreparar?.let { throw it }
        siguiente += 1
        val destino = DestinoDeFoto(
            id = "IMG-%03d".format(siguiente),
            uriParaLaCamara = "content://fake/camara/$siguiente",
            archivoCrudo = "/tmp/fake/crudo-$siguiente.jpg"
        )
        destinos += destino
        return destino
    }

    override suspend fun aceptar(destino: DestinoDeFoto): ComprobanteDelAbono {
        fallaAlAceptar?.let { throw it }
        // El id del destino se CONSERVA: es el contrato del puerto, y el fake
        // no puede mentir sobre eso o los tests de idempotencia probarían nada.
        val comprobante = ComprobanteDelAbono(
            id = destino.id,
            archivo = "/tmp/fake/comprobante-${destino.id}.jpg",
            mime = mime
        )
        aceptados += comprobante
        return comprobante
    }

    override suspend fun importar(uri: String): ComprobanteDelAbono {
        fallaAlImportar?.let { throw it }
        importados += uri
        siguiente += 1
        // Acuña su propio id, igual que el adaptador real: la importación no
        // pasa por un destino, así que nadie le acuñó uno antes.
        return ComprobanteDelAbono(
            id = "ARCH-%03d".format(siguiente),
            archivo = "/tmp/fake/importado-$siguiente.jpg",
            mime = mimeImportado
        )
    }

    override suspend fun miniatura(archivo: String): Miniatura? {
        miniaturasPedidas += archivo
        fallaAlPedirMiniatura?.let { throw it }
        return miniaturaDeCadaArchivo
    }

    override suspend fun descartar(archivo: String) {
        fallaAlDescartar?.let { throw it }
        descartados += archivo
    }
}

/**
 * Fake de [FichaDelClientePort]: guarda en memoria y graba lo que se le pidió
 * escribir. Estado público + lista pública, sin MockK.
 *
 * [seLee] existe para el caso que el puerto real distingue y que la pantalla
 * NO puede aplanar: `false` significa *"no se pudo leer"*, y entonces
 * [fichaDe] devuelve `null` — no una ficha vacía.
 */
class FakeFichaPort : FichaDelClientePort {

    val fichas: MutableMap<Int, FichaDelCliente> = mutableMapOf()

    /** `false` = la lectura falla y contesta `null`. */
    var seLee: Boolean = true

    /** `false` = la escritura falla; nada se guarda. */
    var seGuarda: Boolean = true

    /** Cada guardado que llegó, en orden: el cliente y la ficha pedida. */
    val guardados: MutableList<Pair<Int, FichaDelCliente>> = mutableListOf()

    /** Con qué instante se sella la ficha guardada — el reloj del fake. */
    var actualizadaEn: Instant = Instant.parse("2026-09-01T18:00:00Z")

    override suspend fun fichaDe(clienteId: Int): FichaDelCliente? =
        if (seLee) fichas[clienteId] ?: FichaDelCliente() else null

    override suspend fun guardar(clienteId: Int, ficha: FichaDelCliente): ResultadoDeLaFicha {
        guardados += clienteId to ficha
        if (!seGuarda) return ResultadoDeLaFicha.FalloElGuardado
        val guardada = ficha.copy(actualizada = actualizadaEn)
        fichas[clienteId] = guardada
        return ResultadoDeLaFicha.Guardada(guardada)
    }
}

/**
 * El tema GLOBAL de la app, fingido. **Flipea de verdad** y emite el valor
 * nuevo por [oscuro], igual que `ThemeController.toggle()`: un fake que solo
 * grabara la llamada sin cambiar el valor dejaría pasar un cableado donde el
 * botón llama al puerto y la pantalla nunca se enterara.
 */
class FakeTemaDeLaAppPort(oscuroInicial: Boolean = false) : TemaDeLaAppPort {

    private val estado = MutableStateFlow(oscuroInicial)

    /** Cuántas veces se pidió alternar, en orden de llegada. */
    var alternaciones: Int = 0
        private set

    override val oscuro: Flow<Boolean> = estado.asStateFlow()

    override fun oscuroAhora(): Boolean = estado.value

    override fun alternar() {
        alternaciones++
        estado.value = !estado.value
    }
}

/** "Esconder cantidades" — estado público y cuenta de alternaciones. */
class FakePrivacidadPort(ocultosIniciales: Boolean = false) : PrivacidadPort {

    private val estado = MutableStateFlow(ocultosIniciales)

    /** Cuántas veces se pidió alternar, en orden de llegada. */
    var alternaciones: Int = 0
        private set

    override val ocultos: Flow<Boolean> = estado.asStateFlow()

    override fun ocultosAhora(): Boolean = estado.value

    override suspend fun alternar() {
        alternaciones++
        estado.value = !estado.value
    }
}
