package com.example.msp_app.feature.pagos.ui

import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.feature.pagos.domain.AvisosDelAbono
import com.example.msp_app.feature.pagos.domain.Comprobantes
import com.example.msp_app.feature.pagos.domain.CuotaDeLaVenta
import com.example.msp_app.feature.pagos.domain.MontosSugeridos
import com.example.msp_app.feature.pagos.domain.OrigenDeLaCuota
import com.example.msp_app.feature.pagos.domain.PlanDeAbonos
import com.example.msp_app.feature.pagos.domain.SeguridadDelAbono
import com.example.msp_app.feature.pagos.domain.model.ComprobanteDelAbono
import com.example.msp_app.feature.pagos.domain.model.DatosDeVenta
import com.example.msp_app.feature.pagos.domain.model.DetalleVenta
import com.example.msp_app.feature.pagos.domain.model.EstadoDelPeriodo
import com.example.msp_app.feature.pagos.domain.model.HistorialDePagos
import com.example.msp_app.feature.pagos.domain.model.MesDePagos
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import com.example.msp_app.feature.pagos.domain.model.Miniatura
import com.example.msp_app.feature.pagos.domain.model.PagoDelHistorial
import com.example.msp_app.feature.pagos.domain.model.ProductoDeVenta
import com.example.msp_app.feature.pagos.domain.model.ResumenDeRitmo
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * La venta del mock `registrar-abono.html`, en tipos de dominio: Victoria
 * Flores Olmedo, refrigerador V-5188, **saldo $1,450**.
 *
 * Las cifras están elegidas para que los TRES sugeridos existan a la vez, como
 * en el mock — y salen de la aritmética real, no de constantes puestas a mano:
 *
 * | dato | valor | de dónde |
 * |---|---|---|
 * | parcialidad | $220 | la cuota semanal |
 * | saldo | $1,450 | lo que falta |
 * | total / enganche | $6,310 / $900 | fijan lo abonado en $4,860 |
 * | fecha de venta | 14-abr-2026 | 140 días antes de "hoy" = **20 semanas** |
 * | liquidación | $1,290 | "hoy liquida con" |
 *
 * De ahí: **esperado hoy $220**, **al corriente** `20 x 220 - (4860 - 900)` =
 * **$440**, **liquidar $1,290**. Los tres exactamente como el mock.
 *
 * "Hoy" es [PagosFixtures.HOY] (1-sep-2026), el mismo de las otras pantallas.
 */
object AbonoFixtures {

    const val VENTA_ID: Int = PagosFixtures.VENTA_EN_PROMESA

    /** El saldo. El techo del bloqueo duro; el borde exacto se prueba contra él. */
    val SALDO: Money = dinero("1450")

    val PARCIALIDAD: Money = dinero("220")

    val TOTAL: Money = dinero("6310")

    val ENGANCHE: Money = dinero("900")

    val LIQUIDACION: Money = dinero("1290")

    /** 140 días antes de [PagosFixtures.HOY]: veinte semanas justas. */
    val FECHA_DE_VENTA: LocalDate = LocalDate.of(2026, 4, 14)

    /** Lo esperado hoy con el periodo sin tocar. */
    val ESPERADO_HOY: Money = PARCIALIDAD

    /** El atraso en dinero: dos cuotas. */
    val AL_CORRIENTE: Money = dinero("440")

    /** El periodo sin tocar: nadie ha cobrado esta semana. */
    fun estadoSinTocar(): EstadoDelPeriodo = EstadoDelPeriodo.sinTocar(PARCIALIDAD)

    /**
     * El periodo con un abono parcial de $100 — lo que enciende la rareza "ya
     * abonó esta semana". Sale del DINERO del periodo, nunca de una visita.
     */
    fun estadoConAbonoParcial(): EstadoDelPeriodo = EstadoDelPeriodo(
        estado = EstadoCuenta.ABONO_PARCIAL,
        abonoDelPeriodo = dinero("100"),
        parcialidad = PARCIALIDAD
    )

    /** La venta del mock. [estado] varía para los estados de la pantalla. */
    fun detalle(estado: EstadoDelPeriodo = estadoSinTocar()): DetalleVenta {
        val abonado = TOTAL - SALDO
        val plan = PlanDeAbonos.de(totalVenta = TOTAL, abonado = abonado, parcialidad = PARCIALIDAD)
        return DetalleVenta(
            ventaId = VENTA_ID,
            folio = "V-5188",
            hoy = PagosFixtures.HOY,
            creditoId = 12232,
            clienteId = PagosFixtures.CLIENTE_ID,
            clienteNombre = "Victoria Flores Olmedo",
            titulo = "Refrigerador Mabe 14'",
            fechaVenta = FECHA_DE_VENTA,
            saldo = SALDO,
            parcialidad = PARCIALIDAD,
            // La venta del mock no trae pagos en el historial, así que la cuota
            // se queda en la parcialidad capturada: es el escalón 2/3 sin nada
            // que la contradiga, y es el comportamiento de siempre.
            cuota = CuotaDeLaVenta(PARCIALIDAD, OrigenDeLaCuota.PARCIALIDAD),
            frecuencia = "semanal",
            abonosPagados = plan.pagados,
            abonosTotales = plan.totales,
            avance = plan.avance,
            totalVenta = TOTAL,
            precioContado = dinero("5200"),
            enganche = ENGANCHE,
            abonado = abonado,
            vendedor = "J. Carlos Méndez",
            estado = estado,
            productos = listOf(ProductoDeVenta("Refrigerador Mabe 14'", TOTAL)),
            historial = HistorialDePagos(
                semanas = emptyList(),
                resumen = ResumenDeRitmo(0, 0, Money.ZERO, 0),
                meses = emptyList(),
                totalPagos = 0
            ),
            liquidacion = com.example.msp_app.feature.pagos.domain.model.Liquidacion(
                monto = LIQUIDACION,
                vigenteHasta = LocalDate.of(2026, 9, 6),
                categoria = "precio a 4 meses"
            ),
            garantia = null
        )
    }

    /** La misma venta como dato crudo de puerto, para armar el ViewModel real. */
    fun datosDeVenta(): DatosDeVenta = PagosFixtures.datosDeVenta(
        ventaId = VENTA_ID,
        folio = "V-5188",
        descripcion = "Refrigerador Mabe 14'",
        cifras = PagosFixtures.Cifras(
            total = TOTAL,
            restante = SALDO,
            cuota = PARCIALIDAD,
            cubierto = TOTAL - SALDO
        )
    ).copy(fechaVenta = FECHA_DE_VENTA, enganche = ENGANCHE, frecuencia = "semanal")

    /** Un abono de $100 dentro del periodo abierto: el que enciende el duplicado. */
    fun abonoDeEstaSemana(): PagoDelHistorial = PagoDelHistorial(
        pagoId = "COB-A-010430",
        ventaId = VENTA_ID,
        fecha = Instant.parse("2026-09-01T16:00:00Z"),
        importe = dinero("100"),
        formaCobroId = MetodoDeCobro.EFECTIVO.formaCobroId,
        metodo = MetodoDeCobro.EFECTIVO,
        nota = null
    )

    /** El detalle con un abono ya guardado bajo [pagoId] — el guard resuelto por el hecho. */
    fun detalleConAbono(pagoId: String): DetalleVenta {
        val base = detalle()
        return base.copy(
            historial = base.historial.copy(
                meses = listOf(
                    MesDePagos(
                        mes = java.time.YearMonth.of(2026, 9),
                        nombre = "septiembre",
                        subtotal = ESPERADO_HOY,
                        pagos = listOf(abonoDeEstaSemana().copy(pagoId = pagoId))
                    )
                ),
                totalPagos = 1
            )
        )
    }

    /**
     * **La parcialidad de la venta se ve mal**: el caso `Y00002184`, con la
     * cuota marcada [OrigenDeLaCuota.DUDOSA].
     *
     * La venta dice $3,000 y no tiene un solo pago que lo respalde. La pantalla
     * no ofrece ese esperado —ni chip, ni prellenado, ni abono corto— y en su
     * lugar dice que hay que revisar el dato. El teclado arranca en blanco
     * porque no hay cifra que proponer.
     */
    fun conCuotaDudosa(): RegistrarAbonoUiState {
        val venta = detalle().copy(
            parcialidad = dinero("3000"),
            cuota = CuotaDeLaVenta(dinero("3000"), OrigenDeLaCuota.DUDOSA)
        )
        return RegistrarAbonoUiState(
            cargando = false,
            venta = venta,
            monto = MontoCapturado(),
            sugeridos = MontosSugeridos.de(venta, PagosFixtures.HOY),
            veredicto = SeguridadDelAbono.evaluar(
                monto = Money.ZERO,
                saldo = venta.saldo,
                esperadoHoy = MontosSugeridos.esperadoHoy(venta),
                yaAbonoEstePeriodo = false
            )
        )
    }

    /** El estado de pantalla en captura, con lo esperado hoy ya puesto. */
    fun enCaptura(estado: EstadoDelPeriodo = estadoSinTocar()): RegistrarAbonoUiState =
        conMonto(MontoCapturado.deSugerido(ESPERADO_HOY), estado)

    /** El estado de pantalla con un sobrepago tecleado: el bloqueo duro. */
    fun enBloqueo(): RegistrarAbonoUiState =
        conMonto(MontoCapturado(crudo = "300000"), estadoSinTocar())

    /** El paso dos, con un monto sano. */
    fun enConfirmacion(): RegistrarAbonoUiState = enCaptura().let { base ->
        base.copy(
            confirmacion = ConfirmacionPendiente(
                importe = base.monto.importe,
                metodo = base.metodo,
                veredicto = base.veredicto,
                aviso = base.aviso
            )
        )
    }

    /**
     * El paso dos escalado: $1,200 sobre una venta que ya recibió $100 esta
     * semana y espera $120 — diez veces lo esperado **y** posible duplicado.
     * Sigue por debajo del saldo: un monto que excediera el saldo no llegaría
     * nunca a esta hoja, lo bloquearía la pantalla anterior.
     */
    fun enMontoRaro(): RegistrarAbonoUiState =
        conMonto(MontoCapturado(crudo = "1200"), estadoConAbonoParcial()).let { base ->
            base.copy(
                confirmacion = ConfirmacionPendiente(
                    importe = base.monto.importe,
                    metodo = base.metodo,
                    veredicto = base.veredicto,
                    aviso = base.aviso
                )
            )
        }

    /**
     * El paso dos de un **abono corto**: $150 sobre una venta que espera $220
     * (Ruling AL, ronda 2 de arreglo). Repone el caso que `NewPaymentDialog`
     * avisaba en rojo y que se perdió al retirarlo.
     *
     * $150 termina en 50 y no llega a 5x lo esperado, así que **la única**
     * rareza encendida es [com.example.msp_app.feature.pagos.domain.RarezaDelAbono.ABAJO_DE_LO_ESPERADO]
     * — lo que hace que la banda que se pinta sea inequívocamente la suya.
     *
     * **No es golden.** Los cuatro estados que sí lo son (`enBloqueo`,
     * `enConfirmacion`, `enMontoRaro`, `enCaptura`) tienen montos iguales o
     * mayores a lo esperado, así que la rareza nueva no mueve ninguna imagen.
     */
    fun enAbonoCorto(): RegistrarAbonoUiState =
        conMonto(MontoCapturado(crudo = "150"), estadoSinTocar()).let { base ->
            base.copy(
                confirmacion = ConfirmacionPendiente(
                    importe = base.monto.importe,
                    metodo = base.metodo,
                    veredicto = base.veredicto,
                    aviso = base.aviso
                )
            )
        }

    /**
     * **Corto Y duplicado a la vez**: $50 sobre una venta que espera $120 y que
     * ya recibió $100 esta semana. Gana la más grave — la hoja escala.
     *
     * Con `abonoDelPeriodo = $100` sobre una parcialidad de $220, lo esperado
     * hoy baja a $120, así que el monto tiene que quedar por debajo de esa
     * cifra. **Y no puede ser uno de los chips**: desde que la app no interroga
     * lo que ella misma propuso, $100 —que es el redondo más común y sí está en
     * la fila— dejó de encender el abono corto. $50 cumple las tres: es corto,
     * es múltiplo de 50 y **no** lo ofrece la pantalla.
     */
    fun enAbonoCortoYDuplicado(): RegistrarAbonoUiState =
        conMonto(MontoCapturado(crudo = "50"), estadoConAbonoParcial()).let { base ->
            base.copy(
                confirmacion = ConfirmacionPendiente(
                    importe = base.monto.importe,
                    metodo = base.metodo,
                    veredicto = base.veredicto,
                    aviso = base.aviso
                )
            )
        }

    /**
     * **Nivel 2**: $900 sobre una cuota de $220 son 4.09 cuotas (el 0.70 % de
     * los abonos de la ruta). Es múltiplo de 50 y está por encima de lo
     * esperado, así que la ÚNICA señal encendida es la de las cuotas — lo que
     * hace que el mensaje que se pinta sea inequívocamente el suyo.
     */
    fun enAvisoDeCuotas(): RegistrarAbonoUiState =
        conMonto(MontoCapturado(crudo = "900"), estadoSinTocar())

    /**
     * **Nivel 3**: $1,400 son 6.36 cuotas de $220, o sea más de seis. Sigue por
     * debajo del saldo ($1,450): lo que cambia no es si se puede registrar, es
     * cuánto cuesta decir que sí.
     */
    fun enAvisoDeTeclear(): RegistrarAbonoUiState =
        conMonto(MontoCapturado(crudo = "1400"), estadoSinTocar())

    /** El paso dos de nivel 3, con el campo del eco todavía vacío. */
    fun tecleandoElMonto(eco: String = ""): RegistrarAbonoUiState = enAvisoDeTeclear()
        .let { base ->
            base.copy(
                confirmacion = ConfirmacionPendiente(
                    importe = base.monto.importe,
                    metodo = base.metodo,
                    veredicto = base.veredicto,
                    aviso = base.aviso,
                    eco = eco
                )
            )
        }

    /** El paso dos de nivel 2: un toque extra, sin teclear nada. */
    fun confirmandoConUnToque(): RegistrarAbonoUiState = enAvisoDeCuotas().let { base ->
        base.copy(
            confirmacion = ConfirmacionPendiente(
                importe = base.monto.importe,
                metodo = base.metodo,
                veredicto = base.veredicto,
                aviso = base.aviso
            )
        )
    }

    /**
     * **Un chip que está por debajo de lo esperado**: $100 sobre una venta que
     * espera $120 y que ya cobró esta semana.
     *
     * Es el caso que la regla "la app no interroga lo que propuso" vino a
     * arreglar: $100 es el redondo más común de la ruta y la fila lo está
     * ofreciendo, así que tocarlo **no** puede abrir una hoja que le reclame al
     * cobrador haberlo tocado. El duplicado sí sigue saliendo: ése no es un
     * juicio sobre el monto, es un hecho sobre la cuenta.
     */
    fun enChipCortoYDuplicado(): RegistrarAbonoUiState =
        conMonto(MontoCapturado(crudo = "100"), estadoConAbonoParcial()).let { base ->
            base.copy(
                confirmacion = ConfirmacionPendiente(
                    importe = base.monto.importe,
                    metodo = base.metodo,
                    veredicto = base.veredicto,
                    aviso = base.aviso
                )
            )
        }

    /**
     * **La captura con dos comprobantes ya adjuntos** (Task 22).
     *
     * Dos y no uno: con uno solo, un bug que pintara siempre "comprobante 1"
     * pasaría desapercibido, y el número de la fila es lo único que distingue
     * una foto de otra en esta pantalla.
     */
    fun enCapturaConComprobantes(): RegistrarAbonoUiState = enCaptura().copy(
        comprobantes = listOf(
            comprobante("IMG-1"),
            comprobante("IMG-2"),
            // El tercero es un PDF: no tiene miniatura que enseñar y su cuadro
            // pinta el glifo. Va en el fixture porque es el caso que un golden de
            // puras fotos nunca vería, y cobranza acepta PDF a propósito.
            ComprobanteDelAbono("IMG-3", "/files/comprobante_pago_IMG-3.pdf", "application/pdf")
        ),
        miniaturas = mapOf("IMG-1" to miniatura(0), "IMG-2" to miniatura(1)),
        intentos = listOf(IntentoFallido("ARCH-9", FalloDeLaFoto.TIPO_NO_PERMITIDO))
    )

    /** La rejilla llena: sin «+», porque ya no caben más. */
    fun comprobantesLlenos(): RegistrarAbonoUiState = enCaptura().copy(
        comprobantes = (1..Comprobantes.MAXIMO).map { comprobante("IMG-$it") },
        miniaturas = (1..Comprobantes.MAXIMO).associate { "IMG-$it" to miniatura(it) }
    )

    /** La hoja del «+» arriba, sobre la captura con una foto ya puesta. */
    fun eligiendoOrigen(): RegistrarAbonoUiState = enCaptura().copy(
        comprobantes = listOf(comprobante("IMG-1")),
        miniaturas = mapOf("IMG-1" to miniatura(0)),
        eligiendoOrigen = true
    )

    /** El cuadro ámbar de la foto que no se pudo adjuntar, sobre la captura sana. */
    fun enFalloDeFoto(): RegistrarAbonoUiState = enCaptura().copy(
        intentos = listOf(IntentoFallido("IMG-9", FalloDeLaFoto.NO_SE_PUDO_TOMAR))
    )

    /**
     * Una miniatura **sintética y determinista**, para los goldens.
     *
     * Píxeles calculados, no leídos de un archivo: un golden que dependiera de
     * decodificar un JPEG de disco dependería del decodificador de la máquina que
     * lo grabó. Esta fórmula da el mismo buffer en cualquier parte, y el diagonal
     * con dos tonos hace que un `ContentScale.Crop` mal puesto se note — un
     * relleno liso se vería igual recortado que estirado.
     */
    fun miniatura(semilla: Int): Miniatura {
        val lado = LADO_DE_LA_MINIATURA
        val pixeles = IntArray(lado * lado) { indice ->
            val x = indice % lado
            val y = indice / lado
            if (x + y < lado) TONOS[semilla % TONOS.size] else TONOS[(semilla + 1) % TONOS.size]
        }
        return Miniatura(ancho = lado, alto = lado, pixeles = pixeles)
    }

    /** Chico a propósito: el cuadro la escala, y 32x32 basta para ver el diagonal. */
    private const val LADO_DE_LA_MINIATURA = 32

    /** Tres ARGB opacos, escogidos para que se distingan en claro y en oscuro. */
    private val TONOS = intArrayOf(
        0xFF2563EB.toInt(),
        0xFFB0C2B6.toInt(),
        0xFFEDE8DC.toInt()
    )

    /** La hoja de confirmación con un comprobante adjunto. */
    fun enConfirmacionConComprobante(): RegistrarAbonoUiState =
        enConfirmacion().copy(comprobantes = listOf(comprobante("IMG-1")))

    /** Un comprobante de prueba. La ruta es la de un archivo local ya comprimido. */
    fun comprobante(id: String): ComprobanteDelAbono = ComprobanteDelAbono(
        id = id,
        archivo = "/data/user/0/com.example.msp_app/files/comprobante_pago_$id.jpg",
        mime = "image/jpeg"
    )

    /**
     * El final incómodo: la escritura no se pudo comprobar, el guard sigue
     * puesto y el CTA tiene que estar APAGADO, con la banda ofreciendo el
     * reintento real.
     */
    fun enDudaDeVerificacion(): RegistrarAbonoUiState = enCaptura().copy(
        fallo = FalloDelAbono.NO_SE_PUDO_VERIFICAR,
        verificacionPendiente = true
    )

    private fun conMonto(monto: MontoCapturado, estado: EstadoDelPeriodo): RegistrarAbonoUiState {
        val venta = detalle(estado)
        val sugeridos = MontosSugeridos.de(venta, PagosFixtures.HOY)
        return RegistrarAbonoUiState(
            cargando = false,
            venta = venta,
            monto = monto,
            sugeridos = sugeridos,
            veredicto = SeguridadDelAbono.evaluar(
                monto = monto.importe,
                saldo = venta.saldo,
                esperadoHoy = MontosSugeridos.esperadoHoy(venta),
                yaAbonoEstePeriodo = venta.estado.abonoDelPeriodo > Money.ZERO
            ),
            // El aviso se CALCULA con la misma llamada que hace el ViewModel, no
            // se pone a mano: un fixture que fijara el nivel podría pintar una
            // pantalla que el clasificador nunca produce.
            aviso = AvisosDelAbono.evaluar(
                monto = monto.importe,
                saldo = venta.saldo,
                parcialidad = venta.parcialidad,
                esperadoHoy = MontosSugeridos.esperadoHoy(venta),
                historial = emptyList(),
                sugeridos = sugeridos.map { it.importe }
            )
        )
    }

    private fun dinero(pesos: String): Money = Money.of(BigDecimal(pesos))
}
