package com.example.msp_app.feature.pagos.data.fake

import com.example.msp_app.feature.pagos.domain.model.DatosDeVenta
import com.example.msp_app.feature.pagos.domain.model.Liquidacion
import com.example.msp_app.feature.pagos.domain.model.PagoDelHistorial
import com.example.msp_app.feature.pagos.domain.model.VisitaDelCliente
import com.example.msp_app.feature.pagos.domain.port.LiquidacionPort
import com.example.msp_app.feature.pagos.domain.port.PagosPort
import com.example.msp_app.feature.pagos.domain.port.PeriodoDeCobroPort
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
}

class FakePagosPort : PagosPort {

    var pagos: List<PagoDelHistorial> = emptyList()

    val ventasConsultadas: MutableList<Int> = mutableListOf()

    override suspend fun pagosDe(ventaId: Int): List<PagoDelHistorial> {
        ventasConsultadas += ventaId
        return pagos.filter { it.ventaId == ventaId }
    }
}

class FakeVisitasPort : VisitasPort {

    var visitas: List<VisitaDelCliente> = emptyList()

    override suspend fun visitasDelCliente(clienteId: Int): List<VisitaDelCliente> =
        visitas.filter { it.clienteId == clienteId }
}

class FakeLiquidacionPort : LiquidacionPort {

    var liquidaciones: Map<Int, Liquidacion> = emptyMap()

    override suspend fun liquidacionDe(ventaId: Int): Liquidacion? = liquidaciones[ventaId]
}

class FakePeriodoDeCobroPort : PeriodoDeCobroPort {

    var inicio: Instant? = Instant.parse("2026-08-31T06:00:00Z")

    val lecturas: MutableList<Instant?> = mutableListOf()

    override suspend fun inicioDelPeriodo(): Instant? {
        lecturas += inicio
        return inicio
    }
}
