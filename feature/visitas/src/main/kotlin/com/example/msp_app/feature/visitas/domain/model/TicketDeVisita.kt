package com.example.msp_app.feature.visitas.domain.model

import com.example.msp_app.core.common.money.Money
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * La visita **como quedó escrita**, leída de vuelta por su id — lo que devuelve
 * [com.example.msp_app.feature.visitas.domain.port.VisitaImpresaPort].
 *
 * Es la visita ya registrada, no la captura: [registradaEn] es el instante que
 * quedó en `Visit.FECHA` y es **la entrada de la regla del día** (el ticket de
 * visita solo se imprime el día de esa visita). [cobrador] sale de la misma
 * fila (`Visit.COBRADOR`), así que una copia dice quién visitó, no quién trae el
 * teléfono hoy.
 *
 * [tipoVisita] viaja crudo, igual que en `VisitaDelCliente`: clasificarlo es de
 * `TipoVisitaCatalogo` y se hace donde se necesita, nunca en el adaptador.
 */
data class VisitaRegistrada(
    val visitaId: String,
    val clienteId: Int,
    val ventaId: Int?,
    val registradaEn: Instant,
    val tipoVisita: String,
    val cobrador: String,
    val nota: String?,
    val fechaPromesa: LocalDate?,
    val montoPrometido: Money?,
    val fechaCita: LocalDate?,
    val horaCita: LocalTime?
)

/**
 * El **contenido** del ticket de visita, ya armado y en tipos de dominio.
 *
 * El importe prometido viaja en [Money] hasta el formatter, que es el único que
 * lo redondea al peso para el papel (REGLA DE DINERO + "pesos enteros" del
 * brief: el redondeo es de presentación, el modelo conserva la escala 2).
 */
data class TicketDeVisita(
    val visitaId: String,
    val registradaEn: Instant,
    val cliente: String,
    val domicilio: String,
    val cobrador: String,
    val desenlace: DesenlaceImpreso,
    val cuentas: List<CuentaImpresa>,
    val saldoTotal: Money,
    val promesa: PromesaImpresa?,
    val cita: CitaImpresa?
)

/** Una cuenta del cliente en el ticket: folio y lo que debe. */
data class CuentaImpresa(val folio: String, val saldo: Money)

/** El compromiso que el cliente hizo: cuándo y —si lo dijo— cuánto. */
data class PromesaImpresa(val fecha: LocalDate, val monto: Money?)

/** La cita: el día y, si la hubo, la hora. */
data class CitaImpresa(val fecha: LocalDate, val hora: LocalTime?)

/**
 * **Qué dice el papel que se deja en la puerta.**
 *
 * El ticket viejo (`app/.../VisitTicketScreen.kt`) obligaba al cobrador a
 * elegir a mano entre tres textos en un `DropdownMenu` —"visita", "cliente
 * moroso", "no pago"—, sin ninguna relación con lo que acababa de registrar.
 * Nada impedía dejar una carta de cobranza dura en una puerta donde el cliente
 * acababa de prometer pagar.
 *
 * Aquí el texto **lo decide el desenlace ya registrado** (Task 19), así que el
 * papel y la base de datos no pueden contar historias distintas.
 *
 * [mensaje] va en párrafos: el formatter los envuelve al ancho del rollo.
 * Todo es ASCII a propósito — el fold del codepage descarta lo que no puede
 * imprimir, y un carácter descartado corre una línea ya centrada.
 */
enum class DesenlaceImpreso(val titulo: String, val mensaje: List<String>) {

    /** Nadie atendió. Es el texto que más se imprime en campo. */
    NO_ESTABA(
        titulo = "NO LO ENCONTRAMOS",
        mensaje = listOf(
            "Pasamos a su domicilio para el pago de esta semana y no fue posible " +
                "encontrarlo.",
            "Volveremos mas tarde. Si no se encuentra, puede dejar su pago con la " +
                "persona que este en el domicilio, o llamarnos para acordar un horario."
        )
    ),

    /** Se visitó y no se resolvió. Vuelve en los próximos días. */
    VISITE_VUELVO(
        titulo = "PASAMOS A VISITARLO",
        mensaje = listOf(
            "Pasamos a su domicilio y no fue posible resolver su pago.",
            "Volveremos en los proximos dias. Puede llamarnos para acordar un horario."
        )
    ),

    /** Prometió. El papel repite la fecha y el monto que quedaron escritos. */
    PROMETIO(
        titulo = "GRACIAS POR SU COMPROMISO",
        mensaje = listOf(
            "Le agradecemos el compromiso de pago que acordamos hoy.",
            "Abajo aparecen la fecha y el monto que quedaron registrados."
        )
    ),

    /** Quedaron de verse. El papel repite el día y la hora. */
    CITA(
        titulo = "QUEDAMOS DE VERNOS",
        mensaje = listOf(
            "Acordamos vernos en la fecha y hora que aparecen abajo.",
            "Si necesita cambiarla, llamenos con anticipacion."
        )
    ),

    /** Se negó o hubo conflicto. El texto de cobranza dura, y solo aquí. */
    SE_NEGO(
        titulo = "AVISO DE COBRANZA",
        mensaje = listOf(
            "Hemos intentado acercarnos a usted para resolver su adeudo pendiente " +
                "sin obtener una respuesta favorable.",
            "Para evitar continuar el cobro por otra via y gastos innecesarios, lo " +
                "invitamos a que juntos encontremos una alternativa."
        )
    )
}
