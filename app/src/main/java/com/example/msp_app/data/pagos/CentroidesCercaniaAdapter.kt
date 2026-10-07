package com.example.msp_app.data.pagos

import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.core.utils.Coord
import com.example.msp_app.core.utils.centroidesPorVenta
import com.example.msp_app.core.utils.sortGroupsByClosestCentroid
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import com.example.msp_app.feature.pagos.application.PagosTelemetria
import com.example.msp_app.feature.pagos.domain.port.CercaniaPort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementación real de [CercaniaPort]: la MISMA cuenta que Inicio hace para
 * "VENTAS CERCANAS" — [posicionActual] contra el centroide de pagos más
 * cercano de cada venta ([centroidesPorVenta], extraída de
 * `PaymentsViewModel.getCentroidsBySale()`) — pero expuesta por venta
 * (`DOCTO_CC_ACR_ID → metros`) en vez de colapsada a tarjetas de cliente, que
 * es lo que [com.example.msp_app.feature.pagos.domain.OrdenDeLaLista.MAS_CERCA]
 * necesita para ordenar.
 *
 * Vive en `:app` por lo mismo que [SettlementLiquidacionAdapter] y
 * [FichaDelClienteAdapter]: la posición del cobrador sale de
 * `com.example.msp_app.core.utils.CurrentLocationReader`, que usa
 * `play-services-location`, declarada en `:app` y fuera del alcance de
 * `:feature:pagos`.
 *
 * [posicionActual] es inyectable **solo para test** (fakes-only, sin MockK) —
 * mismo patrón que `FichaDelClienteAdapter.traerUsuario`. En producción es
 * `CurrentLocationReader(context)::current`, construido en `PagosPortsModule`.
 * `CurrentLocationReaderTest` ya documenta por qué: en Robolectric, Play
 * Services no da fix, así que fingir la respuesta de [posicionActual] prueba
 * ESTE adaptador sin fingir el lector real.
 */
class CentroidesCercaniaAdapter(
    private val paymentStore: PaymentsLocalDataSource,
    private val telemetry: Telemetry,
    private val posicionActual: suspend () -> Coord?
) : CercaniaPort {

    /**
     * Mapa vacío — nunca una excepción— sin permiso, sin posición, sin
     * centroides o si cualquier lectura de Room falla. El contrato del puerto
     * lo exige: [com.example.msp_app.feature.pagos.domain.OrdenDeLaLista.MAS_CERCA]
     * ya sabe degradar a orden de cobranza con un mapa vacío.
     *
     * **DBSCAN en [Dispatchers.Default], no en [Dispatchers.IO]** (hallazgo de
     * la revisión final de `feat/lista-venta-nueva-orden`): [centroidesPorVenta]
     * corre el clustering real (`org.apache.commons.math3...DBSCANClusterer`,
     * ver el KDoc de [CentroidesCercaniaAdapterTest]) y es trabajo de CPU, no
     * de E/S — correrlo en el dispatcher de IO compite por los hilos que ese
     * pool reserva para operaciones que SÍ bloquean en disco o red. La lectura
     * de Room ([PaymentsLocalDataSource.getLocationsGroupedBySaleId]) se queda
     * en IO, que es lo que le corresponde.
     */
    @Suppress(
        "TooGenericExceptionCaught"
    ) // Room y el proveedor de ubicación pueden fallar con cualquier excepción; la lista degrada.
    override suspend fun distanciasPorVenta(): Map<Int, Double> = withContext(Dispatchers.IO) {
        try {
            val posicion = posicionActual() ?: return@withContext emptyMap()
            val ubicaciones = paymentStore.getLocationsGroupedBySaleId()
            withContext(Dispatchers.Default) {
                val centroides = centroidesPorVenta(ubicaciones)
                if (centroides.isEmpty()) return@withContext emptyMap()

                sortGroupsByClosestCentroid(centroides, posicion)
                    .mapNotNull { proximidad ->
                        proximidad.distance.metersOrNull?.let { metros -> proximidad.saleId to metros }
                    }
                    .toMap()
            }
        } catch (cancelada: CancellationException) {
            throw cancelada
        } catch (fallo: Throwable) {
            telemetry.error(
                code = PagosTelemetria.CODE_CERCANIA_FALLO,
                message = "no se pudo calcular la distancia del cobrador a las ventas",
                props = mapOf(PagosTelemetria.PROP_EXCEPCION to fallo.javaClass.simpleName)
            )
            emptyMap()
        }
    }
}
