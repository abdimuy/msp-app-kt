package com.example.msp_app.feature.visitas.domain.model

import com.example.msp_app.core.common.money.Money

/**
 * La puerta a la que el cobrador está parado: el cliente y sus cuentas.
 *
 * La pantalla es **ligera a propósito** — el contexto ya se leyó en el detalle
 * (Task 16), así que aquí solo entra lo que la captura necesita: a quién se
 * visita, cuántas cuentas toca el desenlace, y de cuál venta puede ser la
 * promesa.
 */
data class ContextoDeVisita(
    val clienteId: Int,
    val nombre: String,
    val direccion: String,
    val saldoTotal: Money,
    val ventas: List<VentaParaVisitar>
) {
    /** Las iniciales del avatar del mock. Nunca más de dos letras. */
    val iniciales: String
        get() = nombre.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")

    /** "2 cuentas · C. Hidalgo 214" — la línea bajo el nombre. */
    val resumen: String
        get() {
            val cuentas = if (ventas.size == 1) "1 cuenta" else "${ventas.size} cuentas"
            return listOf(cuentas, direccion).filter { it.isNotBlank() }.joinToString(" · ")
        }
}

/** Una cuenta del cliente, con lo justo para elegirla como destino de la promesa. */
data class VentaParaVisitar(
    val ventaId: Int,
    val folio: String,
    val descripcion: String,
    val saldo: Money
)
