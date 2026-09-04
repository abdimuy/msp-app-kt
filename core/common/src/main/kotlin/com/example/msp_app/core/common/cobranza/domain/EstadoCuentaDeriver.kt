package com.example.msp_app.core.common.cobranza.domain

import java.math.BigDecimal

/**
 * Deriva el estado de cada cuenta en el periodo. Dominio PURO: cero `android.*`,
 * cero Room, cero Retrofit, cero `Instant.now()`.
 *
 * ## El orden de precedencia, y por qué
 *
 * 1. **El dinero manda.** Si entró dinero en el periodo, la cuenta es
 *    [EstadoCuenta.PAGO] o [EstadoCuenta.ABONO_PARCIAL] sin importar qué
 *    visita se haya capturado. Un abono es un hecho; una visita es un
 *    desenlace. Si el cobrador anotó "no estaba" en la mañana y el cliente pagó
 *    en la tarde, la cuenta está pagada.
 * 2. **La visita más reciente del periodo**, con la propagación de la Task 13:
 *    una visita de alcance [VisitScope.VENTA] toca solo su venta; una de
 *    alcance [VisitScope.CLIENTE] ("no estaba", "cita") toca TODAS las ventas
 *    de ese cliente.
 * 3. **[EstadoCuenta.SIN_TOCAR]** si no hubo ni pago ni visita en el periodo.
 *
 * ## Lo que deliberadamente NO entra en la derivación
 *
 * `Sale.ESTADO_COBRANZA` **no** participa. Es la columna que hoy escribe
 * `PaymentsViewModel.savePayment` con `EstadoCobranza.PAGADO` en **cada** pago
 * guardado, sin comparar el monto contra `PARCIALIDAD` — el bug que esta tarea
 * arregla. Si el fallback de "no hubo nada este periodo" leyera esa columna, un
 * `PAGADO` viejo de la semana pasada seguiría pintado al abrir la semana nueva
 * y "sin tocar" nunca aparecería. Por eso el paso 3 es incondicional, y por eso
 * `EstadoCobranza.aEstadoCuenta()` (en `:core:database`) existe aparte: sirve
 * para leer el histórico y conservar el orden de la lista, no para decidir el
 * periodo en curso.
 */
object EstadoCuentaDeriver {

    /**
     * El borde exacto entre *Pagó* y *Abonó (parcial)*.
     *
     * ## La fórmula, cruzada contra el servidor
     *
     * `internal/rutas/domain/aporte.go:71` calcula
     * `aporte = MIN(AbonoSemana / Parcialidad, vencidas + 1)`, con
     * `vencidas = MAX(0, …) >= 0`. Una venta aporta una cuota completa
     * (`aporte >= 1`) **si y solo si** `AbonoSemana >= Parcialidad`. Y
     * `calcReporteZona` (`internal/rutas/app/listar_rutas.go:80`, con el
     * `AbonoSemana.IsPositive()` en `:96`) cuenta la venta como "cobrada algo".
     * Ojo al citar: el `IsPositive()` de `cobranza_semanal.go:139` es OTRO —
     * está sobre `Parcialidad`, no sobre `AbonoSemana`. Esos dos predicados son
     * exactamente los dos cortes de abajo:
     *
     * - `abonoVentana <= 0` → no entró dinero: devuelve `null` (ver abajo).
     * - `0 < abonoVentana < parcialidad` → [EstadoCuenta.ABONO_PARCIAL].
     * - `abonoVentana >= parcialidad` → [EstadoCuenta.PAGO].
     *
     * El borde es **`>=`**, no `>`: pagar la parcialidad exacta es haber pagado.
     *
     * La comparación es `compareTo` de [BigDecimal] (vía los operadores de
     * Kotlin), no `equals`, a propósito: `BigDecimal("100").equals(BigDecimal(
     * "100.00"))` es `false` porque `equals` mira la escala. Con `compareTo`,
     * cien pesos son cien pesos vengan con dos decimales o con ninguno.
     *
     * `parcialidad <= 0` no permite decidir, así que un abono positivo cae en
     * [EstadoCuenta.ABONO_PARCIAL] —el estado conservador, el que manda a
     * confirmar— y se reporta con
     * [IncidenciaCobranza.CODE_PARCIALIDAD_NO_POSITIVA]. El servidor toma la
     * misma precaución devolviendo `decimal.Zero` (`aporte.go:76-78`).
     *
     * ## Por qué devuelve `EstadoCuenta?` y no [EstadoCuenta.SIN_TOCAR]
     *
     * "No entró dinero" NO es un estado: es la ausencia de una respuesta, y
     * quien pregunta tiene que seguir preguntando (¿hubo visita?). Una versión
     * anterior devolvía [EstadoCuenta.SIN_TOCAR] en ese caso y solo era correcta
     * porque [derivar] la llamaba detrás de una guarda. Quien la llamara directo
     * desde una pantalla convertía en silencio un [EstadoCuenta.SE_NEGO] en
     * "nadie tocó esta cuenta" — un cobrador al que le cerraron la puerta
     * pintado como cuenta sin trabajar. El `null` obliga al llamador a decidir,
     * y el compilador se lo recuerda.
     */
    fun estadoPorDinero(abonoVentana: BigDecimal, parcialidad: BigDecimal): EstadoCuenta? = when {
        abonoVentana.signum() <= 0 -> null
        parcialidad.signum() <= 0 -> EstadoCuenta.ABONO_PARCIAL
        abonoVentana >= parcialidad -> EstadoCuenta.PAGO
        else -> EstadoCuenta.ABONO_PARCIAL
    }

    /**
     * Deriva el periodo completo.
     *
     * [cuentas] son las ventas a crédito activas del cobrador. El llamador NO
     * debe incluir ventas de contado: el servidor las excluye de toda métrica
     * de cobranza (`Frecuencia.EsContado`, `cobranza_repo.go:116`) porque no
     * tienen parcialidad real.
     *
     * [pagos] y [visitas] pueden traer filas de cualquier fecha; esta función
     * filtra por [ventana]. Devuelve una entrada por cada cuenta de [cuentas],
     * siempre — ninguna se queda sin estado.
     */
    fun derivar(
        cuentas: List<CuentaDelPeriodo>,
        pagos: List<PagoEnVentana>,
        visitas: List<VisitaEnVentana>,
        ventana: VentanaCobro
    ): DerivacionPeriodo {
        val abonos = abonosPorVenta(pagos, ventana)
        val visitasDelPeriodo = visitas.filter { ventana.contiene(it.fechaHora) }
        val indice = IndiceVisitas.de(visitasDelPeriodo)

        val porVenta = cuentas.associate { cuenta ->
            cuenta.ventaId to resolver(
                cuenta = cuenta,
                abonoVentana = abonos[cuenta.ventaId] ?: BigDecimal.ZERO,
                visita = indice.masRecienteFor(cuenta)
            )
        }

        return DerivacionPeriodo(
            porVenta = porVenta,
            incidencias = incidencias(cuentas, abonos, visitasDelPeriodo, indice)
        )
    }

    /**
     * `AbonoSemana` por venta: suma de los pagos del periodo cuya forma de
     * cobro cuenta como cobranza. Espeja
     * `SELECT DOCTO_CC_ACR_ID, SUM(IMPORTE) … WHERE CANCELADO = 'N' AND
     * CONCEPTO_CC_ID = 87327 AND FECHA >= ? AND FECHA <= ? GROUP BY
     * DOCTO_CC_ACR_ID` (`cobranza_repo.go:104-112`; el filtro de fecha en
     * `:110-111`), con
     * [VentanaCobro.FORMAS_COBRO_COBRANZA] en el papel de `CONCEPTO_CC_ID`.
     *
     * Un pago cancelado no debe llegar hasta acá: el adaptador no lo trae, igual
     * que la consulta del servidor lo descarta con `CANCELADO = 'N'`.
     */
    private fun abonosPorVenta(
        pagos: List<PagoEnVentana>,
        ventana: VentanaCobro
    ): Map<Int, BigDecimal> = pagos
        .filter { it.formaCobroId in VentanaCobro.FORMAS_COBRO_COBRANZA }
        .filter { ventana.contiene(it.fechaHora) }
        .groupingBy { it.ventaId }
        .fold(BigDecimal.ZERO) { acumulado, pago -> acumulado + pago.importe }

    private fun resolver(
        cuenta: CuentaDelPeriodo,
        abonoVentana: BigDecimal,
        visita: VisitaEnVentana?
    ): ResultadoEstadoCuenta {
        val porDinero = estadoPorDinero(abonoVentana, cuenta.parcialidad)
        if (porDinero != null) {
            return ResultadoEstadoCuenta(
                estado = porDinero,
                abonoVentana = abonoVentana,
                parcialidad = cuenta.parcialidad
            )
        }
        if (visita != null) {
            return ResultadoEstadoCuenta(
                estado = TipoVisitaCatalogo.estadoDe(visita.tipoVisita),
                abonoVentana = abonoVentana,
                parcialidad = cuenta.parcialidad,
                fechaPromesa = visita.fechaPromesa,
                montoPrometido = visita.montoPrometido,
                fechaCita = visita.fechaCita,
                horaCita = visita.horaCita
            )
        }
        return ResultadoEstadoCuenta(
            estado = EstadoCuenta.SIN_TOCAR,
            abonoVentana = abonoVentana,
            parcialidad = cuenta.parcialidad
        )
    }

    private fun incidencias(
        cuentas: List<CuentaDelPeriodo>,
        abonos: Map<Int, BigDecimal>,
        visitasDelPeriodo: List<VisitaEnVentana>,
        indice: IndiceVisitas
    ): List<IncidenciaCobranza> {
        val fueraDeCatalogo = visitasDelPeriodo.count {
            !TipoVisitaCatalogo.esConocido(it.tipoVisita)
        }
        // Solo se reporta la parcialidad no positiva que de verdad impidió
        // decidir: sin abono, el estado no depende de ella y el aviso sería ruido.
        val parcialidadNoPositiva = cuentas.count { cuenta ->
            cuenta.parcialidad.signum() <= 0 &&
                (abonos[cuenta.ventaId] ?: BigDecimal.ZERO).signum() > 0
        }
        return listOfNotNull(
            incidencia(IncidenciaCobranza.CODE_TIPO_VISITA_FUERA_DE_CATALOGO, fueraDeCatalogo),
            incidencia(IncidenciaCobranza.CODE_PARCIALIDAD_NO_POSITIVA, parcialidadNoPositiva),
            incidencia(IncidenciaCobranza.CODE_VISITA_SIN_VENTA, indice.huerfanas)
        )
    }

    private fun incidencia(code: String, ocurrencias: Int): IncidenciaCobranza? =
        if (ocurrencias > 0) IncidenciaCobranza(code, ocurrencias) else null

    /**
     * Las visitas del periodo, indexadas por cómo se propagan (Task 13).
     *
     * [porVenta] son las de alcance [VisitScope.VENTA] con venta ligada;
     * [porCliente] son las de alcance [VisitScope.CLIENTE]; [huerfanas] cuenta
     * las de alcance venta SIN venta ligada, que no se pueden aplicar a nada.
     */
    private class IndiceVisitas(
        val porVenta: Map<Int, List<VisitaEnVentana>>,
        val porCliente: Map<Int, List<VisitaEnVentana>>,
        val huerfanas: Int
    ) {
        /**
         * La visita más reciente aplicable a [cuenta]: las suyas propias más las
         * de alcance cliente de su cliente.
         *
         * Empate exacto de instante: `maxByOrNull` devuelve el PRIMER máximo y
         * las propias van primero en la concatenación, así que gana la de
         * alcance venta. Es lo correcto — ante el mismo instante, el hecho más
         * específico (sobre esta deuda) pesa más que el del domicilio.
         */
        fun masRecienteFor(cuenta: CuentaDelPeriodo): VisitaEnVentana? {
            val propias = porVenta[cuenta.ventaId].orEmpty()
            val delCliente = porCliente[cuenta.clienteId].orEmpty()
            return (propias + delCliente).maxByOrNull { it.fechaHora }
        }

        companion object {
            fun de(visitasDelPeriodo: List<VisitaEnVentana>): IndiceVisitas {
                val (deCliente, deVenta) = visitasDelPeriodo.partition {
                    TipoVisitaCatalogo.alcanceDe(it.tipoVisita) == VisitScope.CLIENTE
                }
                val (ligadas, sueltas) = deVenta.partition { it.ventaId != null }
                return IndiceVisitas(
                    porVenta = ligadas.groupBy { requireNotNull(it.ventaId) },
                    porCliente = deCliente.groupBy { it.clienteId },
                    huerfanas = sueltas.size
                )
            }
        }
    }
}
