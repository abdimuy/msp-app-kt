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

/**
 * Una cuenta del cliente en el ticket: folio, lo que debe y lo que le toca
 * abonar por periodo.
 *
 * [parcialidad] **se lee, no se deriva**: es la misma columna cruda que ya
 * viaja en [VentaParaVisitar] y que pinta `HojaDeAbono` en `:feature:pagos`.
 * Llega hasta aquí porque la carta de "visité, vuelvo" la nombra ("SU
 * COMPROMISO FUE DAR ABONOS SEMANALES DE ..."), y el ticket viejo la imprimía
 * como el literal `$200.00`, igual para todos los clientes.
 */
data class CuentaImpresa(val folio: String, val saldo: Money, val parcialidad: Money)

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
 * ## Las tres cartas del ticket viejo, de vuelta
 *
 * Lo que se retiró con la pantalla legada no fue el mecanismo —elegir la carta
 * a mano era el defecto— sino **las palabras**, que llevan años imprimiéndose y
 * que el cliente reconoce. Vuelven literales, en mayúsculas y sin acentos, y es
 * el desenlace el que las escoge:
 *
 * | Ticket viejo (`DropdownMenu`)   | Desenlace           |
 * |---------------------------------|---------------------|
 * | 1 · "Ticket de Visita"          | [NO_ESTABA]         |
 * | 2 · "Ticket de Cliente Moroso"  | [SE_NEGO]           |
 * | 3 · "Ticket de no Pago"         | [VISITE_VUELVO]     |
 *
 * [PROMETIO] y [CITA] **no tienen carta vieja**: el ticket legado no conocía la
 * promesa ni la cita —no existían como dato— así que conservan su texto propio.
 * Inventarles una de las tres sería dejar la carta equivocada en la puerta, que
 * es el defecto que la Task 19 vino a cerrar.
 *
 * [mensaje] va en párrafos: el formatter los envuelve al ancho del rollo. Los
 * textos viejos venían pre-cortados a mano a 32 columnas; aquí van como párrafo
 * y el corte lo hace `TicketLayout.wrap`, que es el único mecanismo de ancho del
 * papel. Las palabras son las mismas; los saltos de línea los pone el rollo.
 *
 * Todo es ASCII a propósito — el fold del codepage descarta lo que no puede
 * imprimir, y un carácter descartado corre una línea ya centrada.
 */
enum class DesenlaceImpreso(val titulo: String, val mensaje: List<String>) {

    /**
     * Nadie atendió. Es el texto que más se imprime en campo.
     *
     * Carta 1 del ticket viejo ("Ticket de Visita"), literal.
     */
    NO_ESTABA(
        titulo = "NO LO ENCONTRAMOS",
        mensaje = listOf(
            "SU AGENTE DE COBRANZA DE MUEBLES SAN PABLO PASO A VISITAR EN SU " +
                "DOMICILIO PARA SU PAGO CORRESPONDIENTE DE ESTA SEMANA, PERO NO FUE " +
                "POSIBLE ENCONTRARLO, LE INFORMO QUE PASARE NUEVAMENTE A VISITARLO " +
                "MAS TARDE. EN CASO DE NO ENCONTRARSE LE PEDIMOS DE FAVOR NOS PUEDA " +
                "APOYAR DEJANDO SU PAGO CORRESPONDIENTE CON LA PERSONA QUE SE " +
                "ENCUENTRE EN SU DOMICILIO O LLAMAME PARA COORDINARNOS EN EL HORARIO " +
                "QUE LO PUEDA VISITAR."
        )
    ),

    /**
     * Se visitó y no se resolvió. Vuelve en los próximos días.
     *
     * Carta 3 del ticket viejo ("Ticket de no Pago"), literal. El recordatorio
     * del abono por periodo y el exhorto a regularizarse van en el formatter y
     * no aquí: el primero lleva una cifra, y una cifra no cabe en un `enum`.
     */
    VISITE_VUELVO(
        titulo = "PASAMOS A VISITARLO",
        mensaje = listOf(
            "RECUERDE QUE LA PUNTUALIDAD EN SUS PAGOS ES IMPORTANTE PARA SU " +
                "HISTORIAL DE CREDITO."
        )
    ),

    /**
     * Prometió. El papel repite la fecha y el monto que quedaron escritos.
     *
     * **Sin carta vieja**: el ticket legado no conocía la promesa.
     */
    PROMETIO(
        titulo = "GRACIAS POR SU COMPROMISO",
        mensaje = listOf(
            "Le agradecemos el compromiso de pago que acordamos hoy.",
            "Abajo aparecen la fecha y el monto que quedaron registrados."
        )
    ),

    /**
     * Quedaron de verse. El papel repite el día y la hora.
     *
     * **Sin carta vieja**: el ticket legado no conocía la cita.
     */
    CITA(
        titulo = "QUEDAMOS DE VERNOS",
        mensaje = listOf(
            "Acordamos vernos en la fecha y hora que aparecen abajo.",
            "Si necesita cambiarla, llamenos con anticipacion."
        )
    ),

    /**
     * Se negó o hubo conflicto. El texto de cobranza dura, y solo aquí.
     *
     * Carta 2 del ticket viejo ("Ticket de Cliente Moroso"), literal, con el
     * mismo corte en dos párrafos que tenía en el papel.
     */
    SE_NEGO(
        titulo = "AVISO DE COBRANZA",
        mensaje = listOf(
            "EN REITERADAS OCASIONES HEMOS TRATADO DE ACERCARNOS A USTED PARA " +
                "SOLUCIONAR SU ADEUDO PENDIENTE, SIN EMBARGO, NO HEMOS TENIDO UNA " +
                "RESPUESTA FAVORABLE.",
            "CON LA INTENCION DE EVITARLE CONTINUAR CON EL PROCESO DE COBRO POR " +
                "OTRA VIA, ASI COMO GASTOS INNECESARIOS, LO INVITAMOS A QUE JUNTOS " +
                "ENCONTREMOS LA ALTERNATIVA QUE MAS SE ACOMODE PARA SOLUCIONAR EN " +
                "DEFINITIVA ESTA SITUACION."
        )
    )
}
