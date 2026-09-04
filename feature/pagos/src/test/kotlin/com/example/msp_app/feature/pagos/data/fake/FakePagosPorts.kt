package com.example.msp_app.feature.pagos.data.fake

import com.example.msp_app.core.common.cobranza.domain.VentanaCobro
import com.example.msp_app.feature.pagos.domain.model.ComprobanteDelAbono
import com.example.msp_app.feature.pagos.domain.model.DatosDeVenta
import com.example.msp_app.feature.pagos.domain.model.DestinoDeFoto
import com.example.msp_app.feature.pagos.domain.model.GarantiaDeLaVenta
import com.example.msp_app.feature.pagos.domain.model.Liquidacion
import com.example.msp_app.feature.pagos.domain.model.PagoDelHistorial
import com.example.msp_app.feature.pagos.domain.model.VisitaDelCliente
import com.example.msp_app.feature.pagos.domain.port.AbonoARegistrar
import com.example.msp_app.feature.pagos.domain.port.ComprobantesPort
import com.example.msp_app.feature.pagos.domain.port.GarantiasPort
import com.example.msp_app.feature.pagos.domain.port.LiquidacionPort
import com.example.msp_app.feature.pagos.domain.port.PagosPort
import com.example.msp_app.feature.pagos.domain.port.PeriodoDeCobroPort
import com.example.msp_app.feature.pagos.domain.port.RegistroDeAbonoPort
import com.example.msp_app.feature.pagos.domain.port.ResultadoDelAbono
import com.example.msp_app.feature.pagos.domain.port.VentasPort
import com.example.msp_app.feature.pagos.domain.port.VisitasPort
import java.time.Instant

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

class FakeLiquidacionPort : LiquidacionPort {

    var liquidaciones: Map<Int, Liquidacion> = emptyMap()

    override suspend fun liquidacionDe(ventaId: Int): Liquidacion? = liquidaciones[ventaId]
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

    /** El MIME que devolverá el próximo [aceptar]. Se cambia para el tipo no permitido. */
    var mime: String = "image/jpeg"

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

    override suspend fun descartar(archivo: String) {
        fallaAlDescartar?.let { throw it }
        descartados += archivo
    }
}
