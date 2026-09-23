package com.example.msp_app.data.local.datasource.visit

import android.content.Context
import androidx.room.Transaction
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.VisitsWorkEnqueuer
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.sale.EstadoCobranza
import com.example.msp_app.core.database.dao.sale.SaleDao
import com.example.msp_app.core.database.dao.visit.VisitDao
import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.sync.pendingwork.data.enqueuers.VisitsWorkManagerEnqueuer
import com.example.msp_app.core.utils.VisitScope
import com.example.msp_app.core.utils.VisitScopeMapper
import java.time.Duration
import javax.inject.Inject

class VisitsLocalDataSource @Inject constructor(
    private val visitDao: VisitDao,
    private val saleDao: SaleDao,
    private val enqueuer: VisitsWorkEnqueuer,
    private val clock: AppClock = AppClock.System
) {
    /**
     * Puente legacy: los callers `viewModel()` y los workers aún no-Hilt
     * siguen construyendo con `context` sin cambios. Delega en la MISMA
     * instancia que `@Inject` recibe vía [com.example.msp_app.core.database.di.DatabaseModule]
     * — ambos resuelven a [AppDatabase.getInstance], una sola conexión a
     * `msp_db`. No abre un builder nuevo.
     */
    constructor(context: Context) : this(
        AppDatabase.getInstance(context).visitDao(),
        AppDatabase.getInstance(context).saleDao(),
        VisitsWorkManagerEnqueuer(context)
    )

    suspend fun getVisitById(id: String): VisitEntity {
        return visitDao.getVisitById(id)
    }

    suspend fun saveVisit(visit: VisitEntity) {
        visitDao.insertVisit(visit)
    }

    suspend fun getPendingVisits(): List<VisitEntity> {
        return visitDao.getPendingVisits()
    }

    suspend fun getVisitsByDate(start: String, end: String): List<VisitEntity> {
        return visitDao.getVisitsByDate(start, end)
    }

    suspend fun updateVisitState(id: String, newState: Int) {
        visitDao.updateState(id, newState)
    }

    suspend fun updateVisitLocation(id: String, lat: Double, lng: Double) {
        visitDao.updateLocation(id, lat, lng)
    }

    suspend fun changeVisitStatus(id: String, status: Boolean) {
        visitDao.updateState(
            id,
            if (status) 1 else 0
        )
    }

    /**
     * Task 13 (plan `pagos-y-visitas`) — antes de este cambio, TODA visita
     * colapsaba a la venta `saleId` sin importar su tipo. Eso es correcto
     * para "vuelvo" / "se negó" / "prometió" (esa venta específica), pero
     * incorrecto para "no estaba" / "cita a una hora": una puerta cerrada
     * no es un hecho sobre una venta, es un hecho sobre el cliente, y las
     * demás ventas de ese cliente se quedaban sin tocar aunque el modelo
     * de dominio (`internal/visitas/domain/visita.go:46-61`, indexado por
     * `ClienteID`) ya lo permitía.
     *
     * El alcance se deriva de `visit.TIPO_VISITA` vía [VisitScopeMapper] —
     * no hay una segunda lista de literales aquí. [VisitScope.CLIENTE]
     * propaga a TODAS las ventas activas del cliente
     * ([SaleDao.updateEstadoCobranzaActivasByClienteId], acotada por
     * `CLIENTE_ID = visit.CLIENTE_ID`: no puede tocar la venta de otro
     * cliente). [VisitScope.VENTA] preserva el comportamiento anterior —
     * toca solo `saleId`.
     */
    @Transaction
    suspend fun insertVisitAndUpdateState(
        saleId: Int,
        visit: VisitEntity,
        newState: EstadoCobranza
    ) {
        visitDao.insertVisit(visit)
        when (VisitScopeMapper.map(visit.TIPO_VISITA, visit.CITA_FECHA != null)) {
            VisitScope.CLIENTE ->
                saleDao.updateEstadoCobranzaActivasByClienteId(visit.CLIENTE_ID, newState)

            VisitScope.VENTA ->
                saleDao.updateTotal(saleId, 0.0, newState)
        }
    }

    /**
     * Task 5 (plan `pagos-y-visitas`) — bug: the upload used to be enqueued
     * only from [com.example.msp_app.services.UpdateLocationHandler], which
     * ran inside [com.example.msp_app.services.UpdateLocationService]. If
     * that `Service` never ran (permission denied, Play Services down, a
     * refused foreground-service start), the visit was written to Room and
     * NEVER enqueued for upload — the collector believed it was registered;
     * it was not.
     *
     * **Audit of pagos' shape (see task-5-report.md):**
     * [com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource.saveAndEnqueue]
     * has the same name and the same intent — insert + enqueue as one call
     * — but its body is only [insertPaymentAndUpdateSale]; it never actually
     * calls an enqueuer. Task 5 recorded that pagos carried the identical
     * latent bug and left it out of scope (title and brief were visit-only).
     * **Arreglo C closed it**, not here but one layer up: `RegistroDeAbonoAdapter`
     * now enqueues through `PaymentsWorkEnqueuer` in the same coroutine as the
     * write, so the money upload no longer depends on `UpdateLocationService`
     * running either. This method stays the corrected shape applied to visits.
     *
     * The local write and the enqueue happen in the same call, so the
     * upload no longer depends on the location service ever running. The
     * location arriving later only calls [updateVisitLocation] (LAT/LNG
     * columns only, via `VisitDao.updateLocation`) — it never touches
     * `GUARDADO_EN_MICROSIP` and [UpdateLocationHandler.handle] no longer
     * enqueues on that path, so a late location update can neither clobber
     * an already-uploaded visit nor create a second upload.
     *
     * Placed on the data source — not in `VisitsViewModel.saveVisit` —
     * because Task 13 rewrites [insertVisitAndUpdateState] to propagate a
     * client-scope visit to every active sale, instead of collapsing it
     * onto a single one. That rewrite still has to insert the visit through
     * this data source; keeping the enqueue glued to the insert here means
     * Task 13's rewrite carries it along by construction (unchanged below),
     * instead of it living at a call site a restructuring could drop.
     */
    suspend fun saveVisitAndEnqueue(saleId: Int, visit: VisitEntity, newState: EstadoCobranza) {
        insertVisitAndUpdateState(saleId, visit, newState)
        enqueuer.enqueue(visit.ID)
    }

    /**
     * Encola el envío de [visitId] — la SEGUNDA mitad de [saveVisitAndEnqueue],
     * expuesta para el único caso en que las dos mitades no pueden ir juntas.
     *
     * `RegistroDeVisitaAdapter` (Task 19) envuelve el insert en una
     * `db.withTransaction` junto con dos escrituras más (la reagenda legada y el
     * enlace con la recomendación). Con el encolado DENTRO de esa transacción,
     * un fallo posterior revertía la fila y dejaba agendado un trabajo que
     * despertaría a buscar una visita inexistente. Con esto, el adaptador
     * inserta dentro y encola inmediatamente después.
     *
     * **La propiedad de la Task 5 no se toca:** el encolado sigue ocurriendo en
     * la misma llamada que la escritura, incondicionalmente y sin depender de
     * que `UpdateLocationService` corra. Quien no necesite transacción sigue
     * usando [saveVisitAndEnqueue], que es y sigue siendo el camino normal.
     */
    suspend fun enqueueUpload(visitId: String) {
        enqueuer.enqueue(visitId)
    }

    suspend fun updateTemporaryCollectionDate(saleId: Int, newDate: String) {
        saleDao.updateTemporaryCollectionDate(saleId, newDate)
    }

    suspend fun deleteAllVisits() {
        visitDao.deleteAllVisits()
    }

    /**
     * Prunes uploaded visitas — **except the ones still holding a promesa or a
     * cita this phone is the only place in the world that knows about**
     * (Ruling V, plan `pagos-y-visitas`).
     *
     * `POST /v2/visitas` has no field for `PROMESA_FECHA`,
     * `PROMESA_MONTO_CENTAVOS`, `CITA_FECHA` or `CITA_HORA`, and the plan
     * decided not to expand the server. So an uploaded visit is only "safely
     * copied elsewhere" for the columns the contract carries; its promesa is
     * NOT. Deleting the row deleted the promise for good — and the promise is
     * the input the recommender and the compliance measurement are built on.
     *
     * The window is [RETENCION_DE_COMPROMISOS_DIAS] days **after** the
     * commitment's own date, not after the visit: a promise for next Friday
     * survives until long after next Friday, which is exactly the span in
     * which "did they pay what they promised?" can still be answered. Once it
     * closes, the row prunes like any other and the table stays bounded.
     *
     * A visita with neither promesa nor cita prunes exactly as before — the
     * `COALESCE(..., '')` in the query makes its commitment date the empty
     * string, which is below every real date.
     */
    suspend fun deleteUploadedVisits() {
        val corte = AppTime.todayInBusinessZone(clock).minusDays(RETENCION_DE_COMPROMISOS_DIAS)
        val corteDeComprobantes = clock.now().minus(RETENCION_DE_COMPROBANTES)
        visitDao.deleteUploadedVisits(
            conservarDesde = AppTime.toWireDate(corte),
            comprobantesDesde = AppTime.toWireFormat(corteDeComprobantes)
        )
    }

    private companion object {
        /**
         * Cuánto sobrevive una visita subida DESPUÉS de la fecha de su
         * compromiso. Noventa días cubren de sobra la ventana en que la promesa
         * todavía se puede cruzar contra los pagos posteriores (la cobranza
         * corre por semana), y ponen un techo al crecimiento de la tabla: sin
         * él la poda dejaría de podar para siempre las filas con promesa.
         */
        const val RETENCION_DE_COMPROMISOS_DIAS: Long = 90

        /**
         * Cuánto bloquea la poda un comprobante que todavía no subió (Task 23).
         *
         * Es **la misma ventana** que usa el barrido de huérfanos de
         * `ComprobantesDeVisitaAdapter`, y tienen que coincidir: mientras la fila
         * bloquea, el barrido no la toca; en cuanto deja de bloquear, el barrido
         * ya la puede recoger. Con dos números distintos habría un hueco en el
         * que la visita se poda y su archivo se queda, o uno en el que la visita
         * no se poda y nadie limpia nunca.
         */
        val RETENCION_DE_COMPROBANTES: Duration = Duration.ofDays(7)
    }
}
