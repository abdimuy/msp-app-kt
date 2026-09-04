package com.example.msp_app.feature.pagos.application

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.feature.pagos.domain.SeguridadDelAbono
import com.example.msp_app.feature.pagos.domain.model.DetalleVenta
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import com.example.msp_app.feature.pagos.domain.port.AbonoARegistrar
import com.example.msp_app.feature.pagos.domain.port.RegistroDeAbonoPort
import com.example.msp_app.feature.pagos.domain.port.ResultadoDelAbono
import javax.inject.Inject

/**
 * Registra un abono — **el segundo cinturón del bloqueo duro**.
 *
 * La pantalla ya apaga el CTA con el veredicto de
 * [SeguridadDelAbono], y aun así este caso de uso vuelve a evaluar los bloqueos
 * antes de llamar al puerto. No es desconfianza del ViewModel: es que el
 * bloqueo por sobrepago es la respuesta a dinero real ya perdido una vez, y una
 * invariante de dinero que vive en un solo lugar es una invariante que un
 * refactor puede borrar sin que nada se ponga rojo. Los dos cinturones llaman a
 * la MISMA función ([SeguridadDelAbono.bloqueosDe]), así que no pueden
 * discrepar sobre qué es sobrepago.
 *
 * Si el cinturón alcanza a atrapar algo, **no se traga**: emite
 * [PagosTelemetria.CODE_ABONO_BLOQUEADO_EN_APLICACION]. Un monto prohibido que
 * llegó hasta aquí significa que la pantalla dejó pasar algo, y eso tiene que
 * ser diagnosticable.
 */
class RegistrarAbono @Inject constructor(
    private val port: RegistroDeAbonoPort,
    private val telemetry: Telemetry
) {

    /**
     * @param abonoId la clave de idempotencia del destino, que es también el id del pago.
     * @param venta la venta cargada — de ella sale el saldo contra el que se topa el monto.
     */
    suspend operator fun invoke(
        abonoId: String,
        venta: DetalleVenta,
        importe: Money,
        metodo: MetodoDeCobro
    ): ResultadoDelAbono {
        val bloqueos = SeguridadDelAbono.bloqueosDe(monto = importe, saldo = venta.saldo)
        if (bloqueos.isNotEmpty()) {
            // Anti-PII: viajan los NOMBRES de los bloqueos, nunca el monto ni el saldo.
            telemetry.error(
                code = PagosTelemetria.CODE_ABONO_BLOQUEADO_EN_APLICACION,
                message = "un abono bloqueado llego al caso de uso; la pantalla dejo pasar algo",
                props = mapOf(
                    PagosTelemetria.PROP_BLOQUEOS to bloqueos.joinToString(",") { it.name }
                )
            )
            return ResultadoDelAbono.BLOQUEADO_POR_SEGURIDAD
        }
        return port.registrar(
            AbonoARegistrar(
                abonoId = abonoId,
                ventaId = venta.ventaId,
                importe = importe,
                metodo = metodo
            )
        )
    }
}
