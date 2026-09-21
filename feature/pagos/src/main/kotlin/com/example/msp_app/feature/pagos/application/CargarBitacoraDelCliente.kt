package com.example.msp_app.feature.pagos.application

import com.example.msp_app.feature.pagos.domain.BitacoraDelCliente
import com.example.msp_app.feature.pagos.domain.model.BitacoraCompleta
import com.example.msp_app.feature.pagos.domain.port.ProductosPort
import javax.inject.Inject

/**
 * La bitácora COMPLETA de un cliente: todo lo que pasó en ese domicilio, sin
 * recortar a tres.
 *
 * Reúne por [ReunirCobranzaDelCliente] —la misma lectura que ya usan los dos
 * detalles— y mezcla con [BitacoraDelCliente], que es la misma función que arma
 * los tres del detalle. Dos pantallas, una sola mezcla: el hecho no puede
 * contarse distinto según por dónde se mire.
 *
 * Devuelve `null` cuando el teléfono no tiene ninguna venta de ese cliente, igual
 * que [CargarDetalleCliente]: sin ventas no hay cliente que mostrar, y un
 * cascarón vacío se leería como "nunca pasó nada aquí", que es otra cosa.
 */
class CargarBitacoraDelCliente @Inject constructor(
    private val reunirCobranzaDelCliente: ReunirCobranzaDelCliente,
    private val productosPort: ProductosPort
) {

    suspend operator fun invoke(clienteId: Int): BitacoraCompleta? {
        val cobranza = reunirCobranzaDelCliente(clienteId)
        val primera = cobranza.ventas.firstOrNull() ?: return null
        return BitacoraCompleta(
            clienteId = clienteId,
            // Nombre y dirección salen de la MISMA fila representante, que es la
            // que el detalle ya usa para lo mismo. La dirección viaja porque el
            // mapa que se abre desde un renglón la pinta al pie — ver el KDoc de
            // [BitacoraCompleta].
            nombre = primera.clienteNombre,
            direccion = primera.direccion,
            // TODAS las ventas del cliente, no solo la que encabeza: la
            // bitácora completa mezcla los contactos de todas sus cuentas, así
            // que cada una necesita poder nombrarse. Ver `cuentasDeLasVentas`.
            contactos = BitacoraDelCliente.de(
                visitas = cobranza.visitas,
                pagos = cobranza.pagos,
                cuentas = cuentasDeLasVentas(cobranza.ventas, productosPort)
            )
        )
    }
}
