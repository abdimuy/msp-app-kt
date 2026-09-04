package com.example.msp_app.data.pagos

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.database.dao.sale.EstadoCobranza
import com.example.msp_app.core.database.dao.sale.SaleDao
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.core.utils.Constants
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.data.models.payment.toEntity
import com.example.msp_app.data.models.sale.toDomain
import com.example.msp_app.feature.pagos.application.PagosTelemetria
import com.example.msp_app.feature.pagos.domain.port.AbonoARegistrar
import com.example.msp_app.feature.pagos.domain.port.RegistroDeAbonoPort
import com.example.msp_app.feature.pagos.domain.port.ResultadoDelAbono
import com.example.msp_app.features.payments.newpayment.PaymentFactory
import com.example.msp_app.features.payments.newpayment.currentPaymentTimestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Implementación real de [RegistroDeAbonoPort], provista desde el composition
 * root de `:app` (precedente `LiquidacionPort` → [SettlementLiquidacionAdapter]).
 *
 * **No reimplementa la escritura de dinero.** El camino que ya corre en
 * producción es `PaymentFactory.fromSale` + `PaymentsLocalDataSource.
 * saveAndEnqueue`, que inserta el pago y baja el `SALDO_REST` de la venta en
 * **una sola transacción** (`insertPaymentAndUpdateSale`, `@Transaction`). Esta
 * tarea lo CONSUME tal cual: reescribirlo sería tocar dinero que no vino a
 * tocar, y tenerlo dos veces garantizaría que las dos versiones se despeguen.
 *
 * ## El borde `Money` -> `Double`
 *
 * `PaymentEntity.IMPORTE` es `Double` en el schema de producción, que es
 * inmutable. La conversión ocurre **aquí y solo aquí**, con
 * `BigDecimal.toPlainString().toDouble()` sobre el `BigDecimal` de escala 2 de
 * `Money`: se pasa por el texto decimal exacto, nunca por una operación que
 * pueda arrastrar el error binario del flotante. Del otro lado de esta línea no
 * hay un solo `Double` de dinero.
 *
 * ## Por qué no arranca `UpdateLocationService`
 *
 * `NewPaymentDialog` sí lo arranca para parchar `LAT`/`LNG` después. Ese
 * servicio está listado como defecto conocido del plan (§8.1: sin `try/catch`,
 * revienta con `SecurityException`) y además es el que sostiene el encolado de
 * las visitas (§8.2). Colgar de él el camino nuevo del abono metería
 * exactamente el acoplamiento que esta pantalla existe para no tener: **el pago
 * sigue soberano**. La ubicación se cablea cuando la Task 21 conecte los puntos
 * de entrada y ese defecto esté arreglado; se reporta, no se arrastra.
 *
 * [traerUsuario] es inyectable **solo para test** (fakes-only, sin MockK).
 */
class RegistroDeAbonoAdapter(
    private val saleDao: SaleDao,
    private val pagos: PaymentsLocalDataSource,
    private val telemetry: Telemetry,
    private val clock: AppClock = AppClock.System,
    private val traerUsuario: suspend () -> User? = ::usuarioAutenticado
) : RegistroDeAbonoPort {

    @Suppress(
        "TooGenericExceptionCaught"
    ) // Room/Firestore pueden fallar con cualquier excepción; nada quedó escrito.
    override suspend fun registrar(abono: AbonoARegistrar): ResultadoDelAbono = try {
        val venta = saleDao.getById(abono.ventaId)
        val usuario = traerUsuario()
        when {
            venta == null -> ResultadoDelAbono.VENTA_NO_ESTA_EN_EL_TELEFONO
            usuario == null || usuario.COBRADOR_ID == 0 -> ResultadoDelAbono.SIN_COBRADOR
            else -> {
                guardar(abono, venta.toDomain(), usuario)
                ResultadoDelAbono.REGISTRADO
            }
        }
    } catch (fallo: Throwable) {
        // Anti-PII: viaja el nombre de la clase de la excepción, nunca su texto
        // (que puede arrastrar datos del cliente) ni el monto.
        telemetry.error(
            code = PagosTelemetria.CODE_ABONO_NO_SE_GUARDO,
            message = "la escritura del abono fallo; la transaccion no dejo nada escrito",
            props = mapOf(PagosTelemetria.PROP_EXCEPCION to fallo.javaClass.simpleName)
        )
        ResultadoDelAbono.FALLO_EL_GUARDADO
    }

    private suspend fun guardar(
        abono: AbonoARegistrar,
        venta: com.example.msp_app.data.models.sale.Sale,
        usuario: User
    ) {
        val importe = abono.importe.amount.toPlainString().toDouble()
        val pago = PaymentFactory.fromSale(
            sale = venta,
            currentUser = usuario,
            importe = importe,
            formaCobroId = formaCobroDe(abono),
            id = abono.abonoId,
            fecha = currentPaymentTimestamp(clock)
        )
        pagos.saveAndEnqueue(
            payment = pago.toEntity(),
            saleId = pago.DOCTO_CC_ACR_ID,
            newAmount = pago.IMPORTE,
            newEstadoCobranza = EstadoCobranza.PAGADO
        )
    }

    /**
     * La forma de cobro que va al schema. Se toma de `Constants`, que es lo que
     * usa hoy `NewPaymentDialog`, y se comprueba contra el id del catálogo de
     * dominio: son el mismo número (157 / 52569) y esta línea existe para que
     * sigan siéndolo.
     */
    private fun formaCobroDe(abono: AbonoARegistrar): Int = when (abono.metodo) {
        com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro.TRANSFERENCIA ->
            Constants.PAGO_CON_TRANSFERENCIA_ID

        else -> Constants.PAGO_EN_EFECTIVO_ID
    }
}

/**
 * El usuario autenticado desde Firestore (`users` where `EMAIL == email`) — la
 * MISMA resolución que usan [com.example.msp_app.features.auth.viewModels.AuthViewModel]
 * y `FirebaseUserCycleAdapter`, como lectura suspend one-shot. Su `COBRADOR_ID`
 * es a quién se le atribuye el abono: al que está parado frente al cliente, no
 * al cobrador de la venta (contrato de atribución de `PaymentFactory`).
 */
private suspend fun usuarioAutenticado(): User? {
    val email = FirebaseAuth.getInstance().currentUser?.email ?: return null
    val snapshot = FirebaseFirestore.getInstance()
        .collection(Constants.USERS_COLLECTION)
        .whereEqualTo("EMAIL", email)
        .get()
        .await()
    val doc = snapshot.documents.firstOrNull() ?: return null
    return doc.toObject(User::class.java)?.copy(ID = doc.id)
}
