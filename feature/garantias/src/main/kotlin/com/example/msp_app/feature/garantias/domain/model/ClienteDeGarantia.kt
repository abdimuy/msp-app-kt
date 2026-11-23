package com.example.msp_app.feature.garantias.domain.model

/**
 * El cliente del padrón con lo que un folio de origen [OrigenDeFolio.CLIENTE]
 * necesita para su `POST /v2/garantias`.
 *
 * [ventaId] es el `DOCTO_CC_ID` de la venta más reciente del cliente (la llave
 * con la que el sistema legado liga una garantía a su venta). Puede faltar: un
 * cliente del padrón sin ventas no se puede usar para abrir un folio, y la
 * pantalla lo dice.
 *
 * [calle] y [ciudad] son lo único del domicilio que el teléfono conoce
 * (`SaleEntity`); el resto —número, colonia, localidad, código postal— se
 * captura a mano. **No hay GPS del domicilio**: el del cobrador nunca se usa
 * como si fuera la casa del cliente (D5).
 */
data class ClienteDeGarantia(
    val clienteId: Int,
    val nombre: String,
    val ventaId: Int?,
    val estadoDeCuenta: EstadoDeCuenta,
    val calle: String,
    val ciudad: String
) {
    /** Sin venta no hay `venta_id` que mandar y el servidor responde 422. */
    val sePuedeUsar: Boolean
        get() = ventaId != null
}
