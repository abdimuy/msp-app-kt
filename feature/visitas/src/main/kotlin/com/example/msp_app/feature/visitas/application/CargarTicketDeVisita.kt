package com.example.msp_app.feature.visitas.application

import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.common.cobranza.domain.TipoVisitaCatalogo
import com.example.msp_app.feature.visitas.domain.VencimientoDelCredito
import com.example.msp_app.feature.visitas.domain.model.CitaImpresa
import com.example.msp_app.feature.visitas.domain.model.ContextoDeVisita
import com.example.msp_app.feature.visitas.domain.model.CuentaImpresa
import com.example.msp_app.feature.visitas.domain.model.DesenlaceImpreso
import com.example.msp_app.feature.visitas.domain.model.PromesaImpresa
import com.example.msp_app.feature.visitas.domain.model.TicketDeVisita
import com.example.msp_app.feature.visitas.domain.model.VisitaRegistrada
import com.example.msp_app.feature.visitas.domain.port.ContextoDeVisitaPort
import com.example.msp_app.feature.visitas.domain.port.VisitaImpresaPort
import javax.inject.Inject

/**
 * Arma el contenido del ticket de visita a partir del `visitaId` de la ruta:
 * la visita ya registrada ([VisitaImpresaPort]) más el cliente y sus cuentas
 * ([ContextoDeVisitaPort], el MISMO puerto que pinta la pantalla de captura, no
 * una segunda proyección del mismo cliente).
 *
 * ## El desenlace se DERIVA, no se elige
 *
 * El ticket viejo obligaba al cobrador a escoger a mano entre tres cartas en un
 * menú, sin relación con lo que acababa de registrar: nada impedía dejar la
 * carta de cobranza dura en una puerta donde el cliente acababa de prometer
 * pagar. Aquí sale de la fila:
 *
 * 1. **Hay `CITA_FECHA`** -> cita. Es el único dato que sostiene ese estado
 *    (`EstadoCuenta.CITA_A_UNA_HORA` no sale de ningún literal de `TIPO_VISITA`,
 *    ver `TipoVisitaCatalogo`), así que se pregunta por el dato y no por el
 *    literal.
 * 2. **Hay `PROMESA_FECHA`** -> prometió. Misma razón: sin fecha no hay promesa.
 * 3. **Si no**, manda [TipoVisitaCatalogo.estadoDe] sobre `TIPO_VISITA`, que es
 *    la ÚNICA clasificación del repo. Un literal desconocido cae en su
 *    `ESTADO_DESCONOCIDO` (visité, vuelvo) y el papel dice el texto suave — que
 *    es la degradación correcta: ante la duda no se deja una carta dura.
 */
class CargarTicketDeVisita @Inject constructor(
    private val visitas: VisitaImpresaPort,
    private val contextos: ContextoDeVisitaPort
) {

    /** El ticket, o `null` si el teléfono ya no tiene esa visita o ese cliente. */
    suspend operator fun invoke(visitaId: String): TicketDeVisita? {
        val visita = visitas.visita(visitaId) ?: return null
        val contexto = contextos.contexto(visita.clienteId) ?: return null
        return TicketDeVisita(
            visitaId = visita.visitaId,
            registradaEn = visita.registradaEn,
            cliente = contexto.nombre,
            domicilio = contexto.direccion,
            cobrador = visita.cobrador,
            desenlace = desenlaceDe(visita),
            cuentas = cuentasDe(visita, contexto),
            saldoTotal = contexto.saldoTotal,
            promesa = visita.fechaPromesa?.let {
                PromesaImpresa(fecha = it, monto = visita.montoPrometido)
            },
            cita = visita.fechaCita?.let { CitaImpresa(fecha = it, hora = visita.horaCita) }
        )
    }

    /**
     * Las cuentas que el papel lista. Si la visita entró por UNA venta se
     * imprime solo esa —es la cuenta de la que se habló en la puerta—; si entró
     * por el cliente, todas: el desenlace de domicilio aplica a todas sus
     * cuentas.
     *
     * La parcialidad viaja con cada cuenta porque la carta de "visité, vuelvo"
     * la nombra. **Se copia, no se calcula**: es la columna cruda que
     * `VentaParaVisitar` ya trae leída.
     *
     * El vencimiento sí se **resuelve** aquí, con [VencimientoDelCredito] sobre
     * sus dos entradas crudas (la fecha de la venta y el plazo en meses). Es el
     * único punto del módulo donde esa regla se aplica.
     */
    private fun cuentasDe(
        visita: VisitaRegistrada,
        contexto: ContextoDeVisita
    ): List<CuentaImpresa> {
        val ventas = contexto.ventas
        val soloLaSuya = visita.ventaId?.let { id -> ventas.filter { it.ventaId == id } }.orEmpty()
        return soloLaSuya.ifEmpty { ventas }.map {
            CuentaImpresa(
                folio = it.folio,
                saldo = it.saldo,
                parcialidad = it.parcialidad,
                vencimiento = VencimientoDelCredito.de(it.fechaVenta, it.plazoMeses),
                totalDeCompra = it.totalDeCompra,
                pagosVencidos = it.pagosVencidos
            )
        }
    }

    private fun desenlaceDe(visita: VisitaRegistrada): DesenlaceImpreso = when {
        visita.fechaCita != null -> DesenlaceImpreso.CITA
        visita.fechaPromesa != null -> DesenlaceImpreso.PROMETIO
        else -> when (TipoVisitaCatalogo.estadoDe(visita.tipoVisita)) {
            EstadoCuenta.NO_ESTABA -> DesenlaceImpreso.NO_ESTABA
            EstadoCuenta.SE_NEGO -> DesenlaceImpreso.SE_NEGO
            EstadoCuenta.PROMETIO_PROXIMA -> DesenlaceImpreso.PROMETIO
            EstadoCuenta.CITA_A_UNA_HORA -> DesenlaceImpreso.CITA
            else -> DesenlaceImpreso.VISITE_VUELVO
        }
    }
}
