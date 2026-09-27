package com.example.msp_app.feature.pagos.application

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.feature.pagos.domain.BitacoraDelCliente
import com.example.msp_app.feature.pagos.domain.model.BitacoraCompleta
import com.example.msp_app.feature.pagos.domain.port.ProductosPort
import com.example.msp_app.feature.pagos.domain.port.VentasPort
import javax.inject.Inject

/**
 * La bitácora COMPLETA de UNA VENTA: todo lo que pasó en esa cuenta, sin
 * recortar a cinco.
 *
 * **Por venta y no por cliente** — el detalle de cliente perdió su sección
 * "últimos contactos" (decisión del dueño), así que la única puerta a "ver los
 * N contactos" es hoy el detalle de VENTA. Antes esta pantalla mezclaba TODAS
 * las cuentas del domicilio; ahora sólo la que se abrió, con el mismo criterio
 * de alcance que [BitacoraDelCliente.VISIBLES_EN_LA_VENTA] ya usaba para la
 * muestra: [ContactoDeCobranza.ventaId] == esta venta. Una visita registrada
 * sin cuenta (`ventaId == null`) NO entra — el dueño lo pidió explícito:
 * "sólo contactos de esa venta, no de todas las ventas del cliente".
 *
 * Resuelve el cliente igual que [CargarDetalleVenta]: sólo pide el `ventaId`
 * —desde un pago o un recibo se entra directo a la venta— y de ahí reúne la
 * cobranza COMPLETA del cliente por [ReunirCobranzaDelCliente], que es la misma
 * lectura que ya usan los dos detalles. Se mezcla con [BitacoraDelCliente], la
 * misma función que arma los cinco de [CargarDetalleVenta.invoke]: dos
 * pantallas, una sola mezcla, así que el hecho no puede contarse distinto según
 * por dónde se mire.
 *
 * Devuelve `null` cuando el teléfono no tiene esa venta, igual que
 * [CargarDetalleVenta]: sin venta no hay pantalla que mostrar.
 */
class CargarBitacoraDeLaVenta @Inject constructor(
    private val ventasPort: VentasPort,
    private val productosPort: ProductosPort,
    private val reunirCobranzaDelCliente: ReunirCobranzaDelCliente,
    private val clock: AppClock
) {

    suspend operator fun invoke(ventaId: Int): BitacoraCompleta? {
        val cabecera = ventasPort.venta(ventaId) ?: return null
        val cobranza = reunirCobranzaDelCliente(cabecera.clienteId)
        val venta = cobranza.ventas.firstOrNull { it.ventaId == ventaId } ?: return null
        val cuentas = cuentasDeLasVentas(cobranza.ventas, productosPort)
        return BitacoraCompleta(
            clienteId = venta.clienteId,
            ventaId = ventaId,
            // Nombre y dirección salen de la MISMA fila representante de la
            // venta que ya usa el detalle. La dirección viaja porque el mapa
            // que se abre desde un renglón la pinta al pie — ver el KDoc de
            // [BitacoraCompleta].
            nombre = venta.clienteNombre,
            titulo = venta.descripcion.ifBlank { venta.folio },
            direccion = venta.direccionCompleta,
            // SÓLO lo de esta cuenta: una visita sin cuenta (`ventaId == null`)
            // no entra, igual que un cobro de otra venta del mismo cliente. Ver
            // el KDoc de la clase para por qué es una decisión del dueño y no
            // un descuido.
            contactos = BitacoraDelCliente.de(
                visitas = cobranza.visitas,
                pagos = cobranza.pagos,
                cuentas = cuentas
            ).filter { it.ventaId == ventaId },
            // El "hoy" con el que el toque de un renglón sabe si ese cobro es de
            // hoy, tomado del reloj inyectado. Ver el KDoc de
            // `BitacoraCompleta.hoy`.
            hoy = AppTime.todayInBusinessZone(clock)
        )
    }
}
