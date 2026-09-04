package com.example.msp_app.feature.pagos.application

import com.example.msp_app.core.common.cobranza.domain.CuentaDelPeriodo
import com.example.msp_app.core.common.cobranza.domain.EstadoCuentaDeriver
import com.example.msp_app.core.common.cobranza.domain.IncidenciaCobranza
import com.example.msp_app.core.common.cobranza.domain.PagoEnVentana
import com.example.msp_app.core.common.cobranza.domain.VentanaCobro
import com.example.msp_app.core.common.cobranza.domain.VisitaEnVentana
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.feature.pagos.domain.model.DatosDeVenta
import com.example.msp_app.feature.pagos.domain.model.EstadoDelPeriodo
import com.example.msp_app.feature.pagos.domain.model.PagoDelHistorial
import com.example.msp_app.feature.pagos.domain.model.VisitaDelCliente
import javax.inject.Inject

/**
 * Deriva el estado del periodo de cada venta del cliente **y reenvía las
 * incidencias a telemetría**.
 *
 * ## Por qué este caso de uso existe y por qué está en `application/`
 *
 * `EstadoCuentaDeriver.derivar` devuelve
 * [com.example.msp_app.core.common.cobranza.domain.DerivacionPeriodo.incidencias]
 * **como dato**: el dominio es puro y no puede emitir telemetría —
 * `:core:telemetry` depende de `:core:common`, así que la dependencia inversa
 * sería un ciclo (Task 14). Hasta esta tarea nada las reenviaba, y eso
 * significaba que un `TIPO_VISITA` fuera de catálogo producía un
 * `VISITE_VUELVO` silencioso en producción, sin una sola señal.
 *
 * Aquí se cierra ese hueco, y se cierra **en `application/`, no en un
 * `@Composable`**: este caso de uso se invoca UNA vez por carga/sincronización
 * y la emisión ocurre exactamente ahí. Un Composable se recompone N veces por
 * el mismo estado —cada scroll, cada cambio de tema, cada cambio de
 * `fontScale`— y emitir desde ahí inundaría la cola durable con el mismo
 * evento. La prueba de esa distinción vive en
 * `IncidenciasUnaVezPorSyncTest`.
 *
 * ## Anti-PII
 *
 * Se emite el `code` de la incidencia (constante del propio catálogo) y su
 * conteo. NO se emite el literal ofensor de `TIPO_VISITA`, ni el cliente, ni la
 * venta, ni ningún monto — el KDoc de [IncidenciaCobranza] ya toma esa decisión
 * en la frontera correcta y aquí solo se respeta.
 */
class DerivarEstadoDelPeriodo @Inject constructor(
    private val telemetry: Telemetry
) {

    /**
     * Estado por venta (`DOCTO_CC_ACR_ID` → [EstadoDelPeriodo]).
     *
     * Cuando [ventana] es `null` —`FECHA_CARGA_INICIAL` todavía no se conoce—
     * NO se inventa un periodo: cada venta queda en
     * [com.example.msp_app.core.common.cobranza.domain.EstadoCuenta.SIN_TOCAR],
     * que es la verdad ("nadie trabajó esta cuenta en un periodo que no
     * sabemos cuál es"), y se emite [PagosTelemetria.CODE_PERIODO_DESCONOCIDO].
     */
    operator fun invoke(
        ventas: List<DatosDeVenta>,
        pagos: List<PagoDelHistorial>,
        visitas: List<VisitaDelCliente>,
        ventana: VentanaCobro?
    ): Map<Int, EstadoDelPeriodo> {
        if (ventana == null) {
            telemetry.error(
                code = PagosTelemetria.CODE_PERIODO_DESCONOCIDO,
                message = "FECHA_CARGA_INICIAL desconocida: no hay ventana de cobro que derivar"
            )
            return ventas.associate { it.ventaId to EstadoDelPeriodo.sinTocar(it.parcialidad) }
        }
        val derivacion = EstadoCuentaDeriver.derivar(
            cuentas = ventas.map { it.aCuentaDelPeriodo() },
            pagos = pagos.map { it.aPagoEnVentana() },
            visitas = visitas.map { it.aVisitaEnVentana() },
            ventana = ventana
        )
        reportar(derivacion.incidencias)
        return derivacion.porVenta.mapValues { (_, resultado) -> EstadoDelPeriodo.de(resultado) }
    }

    private fun reportar(incidencias: List<IncidenciaCobranza>) {
        incidencias.forEach { incidencia ->
            telemetry.error(
                code = incidencia.code,
                message = "incidencia de derivacion de cobranza detectada al abrir el detalle",
                props = mapOf(PagosTelemetria.PROP_OCURRENCIAS to incidencia.ocurrencias.toString())
            )
        }
    }
}

/**
 * `Money` → `BigDecimal` pelado para alimentar el dominio de cobranza. Es el
 * viaje de VUELTA de la REGLA DE DINERO y es exacto: `Money.amount` ya es un
 * `BigDecimal` de escala 2, no hay puente por `Double` en ningún punto.
 */
private fun DatosDeVenta.aCuentaDelPeriodo(): CuentaDelPeriodo = CuentaDelPeriodo(
    ventaId = ventaId,
    clienteId = clienteId,
    parcialidad = parcialidad.amount
)

private fun PagoDelHistorial.aPagoEnVentana(): PagoEnVentana = PagoEnVentana(
    ventaId = ventaId,
    importe = importe.amount,
    formaCobroId = formaCobroId,
    fechaHora = fecha
)

private fun VisitaDelCliente.aVisitaEnVentana(): VisitaEnVentana = VisitaEnVentana(
    clienteId = clienteId,
    ventaId = ventaId,
    tipoVisita = tipoVisita,
    fechaHora = fecha,
    fechaPromesa = fechaPromesa,
    montoPrometido = montoPrometido?.amount,
    horaCita = horaCita
)
