package com.example.msp_app.feature.garantias.data.clientes

import com.example.msp_app.core.database.dao.ClienteDao
import com.example.msp_app.core.database.dao.sale.SaleDao
import com.example.msp_app.core.database.entities.SaleWithProductsEntity
import com.example.msp_app.feature.garantias.domain.model.ClienteDeGarantia
import com.example.msp_app.feature.garantias.domain.model.EstadoDeCuenta
import com.example.msp_app.feature.garantias.domain.port.ClientesDeGarantiaPort
import javax.inject.Inject

/**
 * El padrón de clientes del teléfono, con lo que un folio de origen cliente
 * necesita.
 *
 * ## Qué se toma de la venta
 *
 * [ClienteDeGarantia.ventaId] es el `DOCTO_CC_ID` de la **venta más reciente**.
 * Es la misma columna con la que el sistema legado liga una garantía a su venta
 * (`garantias.DOCTO_CC_ID`), así que un folio abierto desde acá queda ligado al
 * mismo cargo que el servidor espera. El teléfono no tiene la fecha de la venta
 * como número ordenable (llega como texto) y por eso el criterio de "más
 * reciente" es el `DOCTO_CC_ID` más alto: es un supuesto declarado en el
 * reporte, no un dato verificado contra Microsip.
 *
 * ## Qué se toma del cliente
 *
 * [ClienteDeGarantia.estadoDeCuenta] sale de la suma de `SALDO_REST` de **todas**
 * sus ventas: si queda algo positivo, el folio nace "saldo pendiente"; si no,
 * "liquidada". Es el dato que el servidor usa para decidir si la garantía se
 * cobra o no, y el cobrador lo ve antes de abrir el folio.
 *
 * La calle y la ciudad son lo único del domicilio que el teléfono conoce: el
 * padrón no trae número, colonia, localidad ni código postal. El resto se
 * captura a mano en la pantalla.
 *
 * ## Sin venta
 *
 * Un cliente del padrón sin ventas se devuelve igual, con `ventaId = null` y
 * [ClienteDeGarantia.sePuedeUsar] en falso. La pantalla lo muestra para que el
 * cobrador sepa que el cliente existe y que no se le puede abrir un folio; un
 * filtro silencioso lo haría buscar de nuevo creyendo que se equivocó de nombre.
 */
class ClientesDeGarantiaAdapter @Inject constructor(
    private val clientes: ClienteDao,
    private val ventas: SaleDao
) : ClientesDeGarantiaPort {

    override suspend fun buscar(nombre: String, limite: Int): List<ClienteDeGarantia> {
        if (nombre.isBlank()) return emptyList()
        return clientes.searchByNombre(nombre.trim())
            .take(limite)
            .map { cliente ->
                val ventasDelCliente = ventas.getByClientId(cliente.CLIENTE_ID)
                clienteDeGarantia(
                    clienteId = cliente.CLIENTE_ID,
                    nombre = cliente.NOMBRE,
                    ventasDelCliente = ventasDelCliente
                )
            }
    }

    private fun clienteDeGarantia(
        clienteId: Int,
        nombre: String,
        ventasDelCliente: List<SaleWithProductsEntity>
    ): ClienteDeGarantia {
        val reciente = ventasDelCliente.maxByOrNull { it.DOCTO_CC_ID }
        val saldo = ventasDelCliente.sumOf { it.SALDO_REST }
        return ClienteDeGarantia(
            clienteId = clienteId,
            nombre = nombre,
            ventaId = reciente?.DOCTO_CC_ID,
            estadoDeCuenta = if (saldo > TOLERANCIA_DE_CENTAVOS) {
                EstadoDeCuenta.SALDO_PENDIENTE
            } else {
                EstadoDeCuenta.LIQUIDADA
            },
            calle = reciente?.CALLE.orEmpty(),
            ciudad = reciente?.CIUDAD.orEmpty()
        )
    }

    private companion object {
        /**
         * `SALDO_REST` es `REAL` en el schema: una venta liquidada puede quedar
         * en `0.000000001` por el ruido binario de los abonos. El medio centavo
         * es la misma tolerancia que usa la condonación para no inventar deuda.
         */
        const val TOLERANCIA_DE_CENTAVOS = 0.005
    }
}
