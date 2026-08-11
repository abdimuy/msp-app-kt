package com.example.msp_app.feature.collectionreport.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Puerto Kotlin FIEL del cálculo de cobranza semanal del backend Go (`msp-api`,
 * `internal/rutas/domain/{aporte,calendario,desglose}.go` +
 * `internal/rutas/app/{cobranza_semanal,listar_rutas}.go`). La app es offline-first: estas
 * dos métricas se calculan localmente sobre Room, SIN llamar al API — mismo resultado que
 * vería el cobrador en el reporte de oficina.
 *
 * **Fechas en días civiles, sin zona horaria** ([LocalDate], no [java.time.Instant]): el Go
 * original trabaja sobre `dateUTC(t)` (medianoche UTC truncada) para TODA la aritmética de
 * calendario — nunca la zona de negocio (`America/Mexico_City`). El caller (adapter/
 * StateBuilder) debe convertir sus `Instant` a [LocalDate] vía UTC
 * (`instant.atZone(ZoneOffset.UTC).toLocalDate()`), NUNCA vía `AppTime.toBusinessDate` — usar
 * la zona de negocio aquí introduciría un desvío de hasta 6 horas contra el Go real (que
 * corre en un servidor UTC). Ver KDoc de cada función pública para el mapeo 1:1 con su
 * contraparte Go.
 *
 * **Desviación consciente:** el Go excluye CONTADO río arriba (SQL `<> 'CONTADO'`) y vuelve a
 * excluirlo en `enrichVentas` como defensa en profundidad ("una fila contado colada nunca
 * infla una métrica financiera"). Aquí se replica exactamente esa doble defensa: [Frecuencia]
 * SÍ modela `CONTADO` y las funciones de agregado ([resumenPonderado]/[resumenCobertura]) lo
 * excluyen explícitamente, aunque la tabla `sales` de Room ya solo contenga ventas de crédito
 * (mismo filtro aplicado en el sync del backend) — no confiar SOLO en que el llamador filtre.
 */
object CobranzaPorcentaje {

    /**
     * Meta de "Porcentaje cobro" (ponderado) mostrada en `MetaCard` — el subtítulo del ring
     * marca "meta 60% ✓" cuando `porcentajeCobro >= META_COBRO_PCT`. Cifra de negocio (oficina),
     * no derivada del Go — vive aquí para que la UI (`MetaCard`/`ReportSheetContent`) no la
     * duplique como magic number en dos archivos.
     */
    const val META_COBRO_PCT = 60

    /** Escala de trabajo interna (antes de exponer el resultado como `Float`/`BigDecimal`). */
    private const val CALC_SCALE = 12
    private val HUNDRED = BigDecimal(100)

    // region — Frecuencia -----------------------------------------------------

    /**
     * Cadencia de pago (`sales.FREC_PAGO`). Puerto de `rutasdomain.Frecuencia` (Go).
     * `CONTADO` no debería llegar nunca desde `sales` (el sync ya solo trae crédito), pero se
     * modela para la defensa en profundidad — ver KDoc de la clase.
     */
    enum class Frecuencia {
        SEMANAL,
        QUINCENAL,
        MENSUAL,
        CONTADO;

        companion object {
            /**
             * Resuelve [Frecuencia] desde el string crudo de `sales.FREC_PAGO` (nullable en el
             * schema Room). Cualquier valor desconocido o `null` cae a SEMANAL — mismo default
             * que `rutasdomain.CadenciaDias`/`VencimientosVencidos` en Go ("Unknown frecuencia
             * → treated as SEMANAL").
             */
            fun fromWire(value: String?): Frecuencia = when (value?.trim()?.uppercase()) {
                "QUINCENAL" -> QUINCENAL
                "MENSUAL" -> MENSUAL
                "CONTADO" -> CONTADO
                else -> SEMANAL
            }
        }
    }

    // endregion

    // region — CalcAporte (aporte.go) ------------------------------------------

    /** Inputs de [calcAporte] — puerto de `rutasdomain.AporteInput`. */
    data class AporteInput(
        val parcialidad: BigDecimal,
        val plazos: BigDecimal,
        val totalImporte: BigDecimal,
        val abonoSemana: BigDecimal,
        val saldoHoy: BigDecimal
    )

    /**
     * Puerto FIEL de `rutasdomain.CalcAporte` (Go, `aporte.go`):
     *
     * ```
     * saldoAlInicio = saldoHoy + abonoSemana
     * pagadoAntes   = totalImporte − saldoAlInicio
     * debia         = MIN(parcialidad × plazos, totalImporte)
     * vencidas      = MAX(0, (debia − pagadoAntes) / parcialidad)
     * aporte        = MIN(abonoSemana / parcialidad, vencidas + 1)
     * ```
     *
     * `parcialidad <= 0` -> `BigDecimal.ZERO` (guardia contra división por cero y ventas sin
     * crédito real, igual que el Go).
     */
    fun calcAporte(input: AporteInput): BigDecimal {
        if (input.parcialidad.signum() <= 0) return BigDecimal.ZERO

        val saldoAlInicio = input.saldoHoy.add(input.abonoSemana)
        val pagadoAntes = input.totalImporte.subtract(saldoAlInicio)

        val expectedDebt = input.parcialidad.multiply(input.plazos)
        val debia = expectedDebt.min(input.totalImporte)

        val diff = debia.subtract(pagadoAntes)
        val vencidasRaw = diff.divide(input.parcialidad, CALC_SCALE, RoundingMode.HALF_UP)
        val vencidas = vencidasRaw.max(BigDecimal.ZERO)

        val abonoEnCuotas = input.abonoSemana.divide(
            input.parcialidad,
            CALC_SCALE,
            RoundingMode.HALF_UP
        )
        return abonoEnCuotas.min(vencidas.add(BigDecimal.ONE))
    }

    // endregion

    // region — VencimientosVencidos / AplicaEnVentana (calendario.go) ----------

    /**
     * Puerto FIEL de `rutasdomain.VencimientosVencidos` (Go, `calendario.go`): número de fechas
     * programadas de pago ESTRICTAMENTE anteriores a [fechaInicio] cuyo periodo de gracia ya
     * transcurrió.
     *
     * - SEMANAL: `floor(daysBetween(fechaCargo, fechaInicio) / 7)`, mínimo 0. Sin gracia.
     * - QUINCENAL: cuenta día-15 y último-día-de-mes `v` con `fechaCargo < v` y
     *   `v + graceDias < fechaInicio`.
     * - MENSUAL: cuenta día-1 `v` con `fechaCargo < v` y `v + graceDias < fechaInicio`.
     * - CONTADO: 0 (sin fechas de vencimiento).
     */
    fun vencimientosVencidos(
        frecuencia: Frecuencia,
        fechaCargo: LocalDate,
        fechaInicio: LocalDate,
        graceDias: Int
    ): Int = when (frecuencia) {
        Frecuencia.CONTADO -> 0
        Frecuencia.QUINCENAL -> CobranzaCalendario.contarVencidosQuincenal(
            fechaCargo,
            fechaInicio,
            graceDias
        )
        Frecuencia.MENSUAL -> CobranzaCalendario.contarVencidosMensual(
            fechaCargo,
            fechaInicio,
            graceDias
        )
        else -> CobranzaCalendario.weeklyVencidos(fechaCargo, fechaInicio)
    }

    /**
     * Puerto FIEL de `rutasdomain.AplicaEnVentana` (Go, `calendario.go`): existe una fecha
     * programada dentro de `[desde, hasta]` (inclusivo) ESTRICTAMENTE posterior a [fechaCargo].
     * Sin gracia (a diferencia de [vencimientosVencidos]). La aritmética de calendario vive en
     * [CobranzaCalendario] (split por `TooManyFunctions` de detekt, ver su KDoc).
     */
    fun aplicaEnVentana(
        frecuencia: Frecuencia,
        fechaCargo: LocalDate,
        desde: LocalDate,
        hasta: LocalDate
    ): Boolean = when (frecuencia) {
        Frecuencia.CONTADO -> false
        Frecuencia.QUINCENAL -> CobranzaCalendario.aplicaQuincenalEnVentana(
            fechaCargo,
            desde,
            hasta
        )
        Frecuencia.MENSUAL -> CobranzaCalendario.aplicaMensualEnVentana(fechaCargo, desde, hasta)
        else -> CobranzaCalendario.aplicaSemanalEnVentana(fechaCargo, desde, hasta)
    }

    // endregion

    // region — Resumen semanal (desglose.go + cobranza_semanal.go/listar_rutas.go) ---------

    /**
     * Una venta de crédito no-contado, activa, dentro de la ventana del cobrador — puerto de
     * los campos de `rutasdomain.VentaCobranza` que necesita el cálculo (los demás, como
     * folio/cliente, son solo de presentación y no viven aquí).
     *
     * @property parcialidad `sales.PARCIALIDAD` — pago periódico esperado.
     * @property frecuencia `sales.FREC_PAGO` resuelta.
     * @property fechaCargo `sales.FECHA` — fecha de alta del crédito, en día civil UTC.
     * @property totalImporte `sales.PRECIO_TOTAL` — total original del crédito (NO
     *   `TOTAL_IMPORTE`, que es la suma de pagos — mismo cuidado que el comentario de
     *   `cobranza_repo.go` en el Go).
     * @property abonoSemana suma de pagos REALES de cobranza en la ventana (concepto
     *   "Cobranza en ruta" en Go; en la app, pagos con forma 157/158/52569, condonación
     *   137026 excluida — ver `PaymentsPort`).
     * @property saldoHoy `sales.SALDO_REST` — saldo vivo actual.
     */
    data class VentaCobranzaInput(
        val parcialidad: BigDecimal,
        val frecuencia: Frecuencia,
        val fechaCargo: LocalDate,
        val totalImporte: BigDecimal,
        val abonoSemana: BigDecimal,
        val saldoHoy: BigDecimal
    )

    /** Agregado ponderado — puerto de `rutasdomain.ResumenPonderado`. */
    data class ResumenPonderado(
        val numerador: BigDecimal,
        val denominador: Int,
        val pct: BigDecimal?
    )

    /** Agregado de cobertura — puerto de `calcReporteZona`'s cobertura loop (listar_rutas.go). */
    data class ResumenCobertura(val numerador: Int, val denominador: Int, val pct: BigDecimal?)

    /** Resultado combinado listo para la tarjeta "Meta de la semana" (Step C/D). */
    data class CobranzaSemanal(
        val porcentajeCobro: BigDecimal?,
        val porcentajeCuentas: BigDecimal?,
        val clientesPagaron: Int,
        val clientesTotal: Int
    )

    /**
     * Porcentaje cobro (ponderado): `Σ aporte(ventas que aplican) / count(ventas que aplican) × 100`.
     * Puerto FIEL de `enrichVentas` + `CalcularResumenPonderado` (Go): para cada venta no-contado
     * que [aplicaEnVentana] en `[fechaInicio, hoy]`, calcula `plazos` vía [vencimientosVencidos]
     * (gracia de 2 días para QUINCENAL/MENSUAL, 0 para SEMANAL — mismo criterio que
     * `enrichVentas`) y su [calcAporte]; el pct puede EXCEDER 100% (una venta puede aportar más
     * de una cuota). `denominador == 0` -> `pct = null` (sin ventas aplicables en la ventana).
     */
    fun resumenPonderado(
        ventas: List<VentaCobranzaInput>,
        fechaInicio: LocalDate,
        hoy: LocalDate
    ): ResumenPonderado {
        val aplicables = ventas.filter { venta ->
            venta.frecuencia != Frecuencia.CONTADO &&
                aplicaEnVentana(venta.frecuencia, venta.fechaCargo, fechaInicio, hoy)
        }
        val denominador = aplicables.size
        val numerador = aplicables.fold(BigDecimal.ZERO) { acc, venta ->
            acc.add(aporteDe(venta, fechaInicio))
        }
        val pct = if (denominador > 0) {
            numerador.divide(
                BigDecimal(denominador),
                CALC_SCALE,
                RoundingMode.HALF_UP
            ).multiply(HUNDRED)
        } else {
            null
        }
        return ResumenPonderado(numerador, denominador, pct)
    }

    /** Aporte de una venta que YA se sabe aplicable — plazos vía [vencimientosVencidos] + [calcAporte]. */
    private fun aporteDe(venta: VentaCobranzaInput, fechaInicio: LocalDate): BigDecimal {
        val grace = if (venta.frecuencia == Frecuencia.QUINCENAL || venta.frecuencia == Frecuencia.MENSUAL) {
            GRACE_DIAS
        } else {
            0
        }
        val plazos =
            BigDecimal(vencimientosVencidos(venta.frecuencia, venta.fechaCargo, fechaInicio, grace))
        return calcAporte(
            AporteInput(
                parcialidad = venta.parcialidad,
                plazos = plazos,
                totalImporte = venta.totalImporte,
                abonoSemana = venta.abonoSemana,
                saldoHoy = venta.saldoHoy
            )
        )
    }

    /**
     * Porcentaje cuentas (cobertura): `count(ventas no-contado con abonoSemana > 0) /
     * count(ventas no-contado activas) × 100`. Puerto FIEL del loop de cobertura en
     * `calcReporteZona` (Go, `listar_rutas.go`) — a diferencia de [resumenPonderado], NO exige
     * `aplicaEnVentana`: cuenta TODA venta de crédito activa, aplique o no en la ventana.
     * `denominador == 0` -> `pct = null`.
     */
    fun resumenCobertura(ventas: List<VentaCobranzaInput>): ResumenCobertura {
        var numerador = 0
        var denominador = 0
        for (venta in ventas) {
            if (venta.frecuencia == Frecuencia.CONTADO) continue
            denominador++
            if (venta.abonoSemana.signum() > 0) numerador++
        }
        val pct = if (denominador > 0) {
            BigDecimal(numerador).divide(BigDecimal(denominador), CALC_SCALE, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
        } else {
            null
        }
        return ResumenCobertura(numerador, denominador, pct)
    }

    /** Calcula ambos porcentajes de una sola pasada — punto de entrada de `StateBuilder`. */
    fun calcular(
        ventas: List<VentaCobranzaInput>,
        fechaInicio: LocalDate,
        hoy: LocalDate
    ): CobranzaSemanal {
        val ponderado = resumenPonderado(ventas, fechaInicio, hoy)
        val cobertura = resumenCobertura(ventas)
        return CobranzaSemanal(
            porcentajeCobro = ponderado.pct,
            porcentajeCuentas = cobertura.pct,
            clientesPagaron = cobertura.numerador,
            clientesTotal = cobertura.denominador
        )
    }

    private const val GRACE_DIAS = 2

    // endregion
}
