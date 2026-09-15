package com.example.msp_app.feature.pagos.domain.model

/**
 * Todo lo que ha pasado en un domicilio, para la pantalla de bitácora.
 *
 * Trae [nombre] además de [contactos] porque la pantalla se abre sola —desde el
 * "ver los N contactos" del detalle— y sin el nombre el encabezado no podría
 * decir de quién es esta historia sin volver a leer las ventas.
 */
data class BitacoraCompleta(
    val clienteId: Int,
    val nombre: String,
    val contactos: List<ContactoDeCobranza>
)
