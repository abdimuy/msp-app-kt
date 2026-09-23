package com.example.msp_app.data.pagos

import androidx.room.withTransaction
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.PaymentsWorkEnqueuer
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.payment.PaymentImageDao
import com.example.msp_app.core.database.dao.sale.EstadoCobranza
import com.example.msp_app.core.database.dao.sale.SaleDao
import com.example.msp_app.core.database.entities.PaymentImageEntity
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.core.utils.Constants
import com.example.msp_app.data.auth.usuarioAutenticado
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.data.models.payment.toEntity
import com.example.msp_app.data.models.sale.toDomain
import com.example.msp_app.feature.pagos.application.PagosTelemetria
import com.example.msp_app.feature.pagos.domain.SeguridadDelAbono
import com.example.msp_app.feature.pagos.domain.port.AbonoARegistrar
import com.example.msp_app.feature.pagos.domain.port.RegistroDeAbonoPort
import com.example.msp_app.feature.pagos.domain.port.ResultadoDelAbono
import com.example.msp_app.features.payments.newpayment.PaymentFactory
import com.example.msp_app.features.payments.newpayment.currentPaymentTimestamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Implementación real de [RegistroDeAbonoPort], provista desde el composition
 * root de `:app` (precedente `LiquidacionPort` → [SettlementLiquidacionAdapter]).
 *
 * **No reimplementa la escritura de dinero.** El camino que ya corre en
 * producción es `PaymentFactory.fromSale` + `PaymentsLocalDataSource.
 * saveAndEnqueue`, que inserta el pago y baja el `SALDO_REST` de la venta. Esta
 * tarea lo CONSUME tal cual: reescribirlo sería tocar dinero que no vino a
 * tocar, y tenerlo dos veces garantizaría que las dos versiones se despeguen.
 *
 * ## Por qué el par va dentro de `db.withTransaction`
 *
 * `PaymentsLocalDataSource` es una `class` común, y el `@Transaction` que lleva
 * `insertPaymentAndUpdateSale` **no hace nada fuera de un `@Dao`**: Room solo
 * genera el envoltorio para métodos de un DAO. O sea que hoy el insert del pago
 * y el descuento del saldo son dos escrituras sueltas.
 *
 * Sin transacción, un fallo entre las dos deja **un pago cobrado con el saldo
 * intacto**: dinero que el historial cuenta y que la venta sigue debiendo. Y es
 * peor de lo que suena, porque el guard anti-duplicado de la pantalla se
 * resuelve mirando el historial — vería ese pago huérfano y daría el abono por
 * registrado, con el saldo nunca descontado. Se envuelve con
 * `db.withTransaction`, el mismo patrón que ya usan `CobranzaReconciler` y
 * `CobranzaSyncManager`.
 *
 * ## El tercer cinturón vive aquí, contra el saldo recién leído
 *
 * Este puerto es un ESCRITOR DE DINERO inyectable en todo el grafo. Los dos
 * cinturones de arriba (pantalla y caso de uso) se topan contra el saldo que el
 * llamador traía cargado; si mañana otro consumidor inyecta este puerto, o si el
 * saldo cambió entre la carga y el toque, ninguno de los dos ayuda. Por eso el
 * adaptador vuelve a evaluar **con el `SALDO_REST` que acaba de leer de la base**
 * y con la MISMA función que los otros dos ([SeguridadDelAbono.bloqueosDe]).
 * Ningún camino escribe un sobrepago.
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
 * ## El encolado es del abono, no de la ubicación (Arreglo C)
 *
 * Hasta la revisión final de esta rama, el ÚNICO encolado inmediato del abono
 * vivía dentro de `UpdateLocationHandler`: `pedirUbicacion` arrancaba
 * `UpdateLocationService` y el handler llamaba a `enqueuePendingPaymentsWorker`
 * **después** de escribir `LAT`/`LNG`. Eso hacía que la subida del dinero
 * colgara de un adorno — servicio que Android 12+ puede rechazar en segundo
 * plano, GPS sin fix, permiso denegado, escritura de coordenadas que truena.
 *
 * Es exactamente el hallazgo de campo #2 de la Task 5 (*"pagos tiene el mismo
 * bug latente"*), que se cerró **solo para visitas**. Aquí se cierra para el
 * dinero, con la MISMA forma que `VisitsLocalDataSource.saveVisitAndEnqueue`:
 * [encolador] se llama en la misma corrutina de la escritura,
 * **incondicionalmente**, y la ubicación pasa a ser lo que siempre debió ser —
 * un acompañante que no decide nada.
 *
 * ## Por qué el segundo encolado no puede cobrar dos veces
 *
 * `UpdateLocationHandler` sigue encolando —es el único encolado del camino de
 * la condonación—, así que el mismo pago puede encolarse dos veces. **Lo que
 * impide el doble cobro NO es `ExistingWorkPolicy.KEEP`**: el KDoc de
 * `WorkManagerUtils:22-36` dice literal que `KEEP` solo salta el encolado
 * mientras el trabajo previo sigue ENQUEUED/RUNNING/BLOCKED, y que en cuanto
 * llega a un estado terminal encola *"exactamente como haría `REPLACE`"*. Lo
 * que `KEEP` sí compra es no cancelar una subida viva, que es otra cosa.
 *
 * Lo que impide el doble cobro es la **idempotencia del servidor**: la subida
 * viaja con `Idempotency-Key = Payment.ID`, fijado desde antes de este arreglo
 * por `PendingPaymentsWorkerV2Test.v2_happy_path_marks_guardado`
 * (*"Idempotency-Key must equal the pago ID"*). Un segundo request con la
 * misma clave es un replay, no un cobro nuevo.
 *
 * ## Por qué el par va dentro de `NonCancellable`
 *
 * Misma razón que `RegistroDeVisitaAdapter` (Ruling AD): `registrar` corre bajo
 * el `viewModelScope` de la pantalla, que se cancela en cuanto el cobrador
 * navega hacia atrás, y `withTransaction` termina en un `withContext` que
 * **lanza al reanudar si el job se canceló mientras el bloque corría, aunque el
 * bloque haya terminado bien** — o sea con el dinero ya commiteado. Sin la
 * guarda quedaría un abono escrito, sin fotos y jamás encolado.
 *
 * ## La ubicación: se pide DESPUÉS de escribir, y nunca decide nada
 *
 * `PaymentFactory` deja `LAT`/`LNG` en `0.0`, y el mapa del día descarta
 * justamente ese punto (`RouteMapScreen`: `lat != 0.0 || lng != 0.0`). Sin este
 * paso, **todo abono tomado por la pantalla nueva sería invisible en el mapa**.
 *
 * Se pide *después* de que la transacción commiteó, *fuera* de ella y *después*
 * del encolado, y su fallo se atrapa entero: se emite
 * [PagosTelemetria.CODE_ABONO_SIN_UBICACION] y el resultado sigue siendo
 * `REGISTRADO`. Misma regla que la foto — **el dinero se guarda, y se encola,
 * aunque no haya GPS, señal ni permiso**.
 *
 * ## Los comprobantes: el segundo caso de la misma familia (Task 22)
 *
 * Las fotos se escriben con la MISMA forma que la ubicación —después del
 * commit, fuera de la transacción, dentro de un `catch (Throwable)` con
 * telemetría— porque tienen el mismo contrato: **la foto nunca bloquea el
 * guardado**. Ni la cámara que falla, ni el disco lleno, ni Room negándose a
 * insertar pueden convertir un abono ya escrito en `FALLO_EL_GUARDADO`.
 *
 * Lo único que las distingue es el ORDEN: los comprobantes van **antes** del
 * encolado, porque encolar es lo que despierta al worker que los va a subir.
 * Ver [guardarComprobantes].
 *
 * [traerUsuario] y [pedirUbicacion] son inyectables **solo para test**
 * (fakes-only, sin MockK): ningún test unitario arranca un servicio real.
 */
class RegistroDeAbonoAdapter(
    private val db: AppDatabase,
    private val saleDao: SaleDao,
    private val pagos: PaymentsLocalDataSource,
    private val imagenes: PaymentImageDao,
    private val telemetry: Telemetry,
    private val encolador: PaymentsWorkEnqueuer,
    private val clock: AppClock = AppClock.System,
    private val traerUsuario: suspend () -> User? = ::usuarioAutenticado,
    private val pedirUbicacion: (pagoId: String) -> Unit
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
            // Tercer cinturón: contra el saldo que acaba de leerse, no contra el
            // que el llamador traía. Ver el KDoc de la clase.
            sobrepasaElSaldo(abono, venta.SALDO_REST) -> ResultadoDelAbono.BLOQUEADO_POR_SEGURIDAD
            else -> {
                guardar(abono, venta.toDomain(), usuario)
                ResultadoDelAbono.REGISTRADO
            }
        }
    } catch (cancelada: CancellationException) {
        // Se relanza ANTES del catch general, igual que hacen `verificar` y
        // `leer` en el ViewModel: una cancelación no es un fallo de escritura, y
        // tragársela ahora que hay una transacción adentro sería peor todavía.
        throw cancelada
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
        withContext(NonCancellable) {
            // El insert del pago y el descuento del saldo, o los dos o ninguno.
            db.withTransaction {
                pagos.saveAndEnqueue(
                    payment = pago.toEntity(),
                    saleId = pago.DOCTO_CC_ACR_ID,
                    newAmount = pago.IMPORTE,
                    newEstadoCobranza = EstadoCobranza.PAGADO
                )
            }
            // Fuera de la transacción y DESPUÉS del commit: el dinero ya está
            // escrito y nada de lo que pase aquí puede deshacerlo.
            //
            // Los comprobantes van ANTES del encolado, y ese orden es funcional,
            // no estético: encolar es lo que despierta al worker, y el worker
            // manda lo que encuentre en `pago_imagenes`. Escribir las fotos
            // después sería mandar el pago sin ellas cada vez que el teléfono
            // tenga señal en ese instante.
            guardarComprobantes(abono)
            encolarSubida(abono.abonoId)
            pedirUbicacionDelAbono(abono.abonoId)
        }
    }

    /**
     * Encola la subida del abono recién escrito. **Total: no propaga nada.**
     *
     * `WorkManager.enqueueUniqueWork` puede lanzar (el proceso muriendo, el
     * componente deshabilitado), y un abono ya escrito no puede volverse
     * `FALLO_EL_GUARDADO` por eso: la pantalla ofrecería reintentar un cobro que
     * ya ocurrió. Se reporta con código propio y se sigue —
     * `PaymentsPendingSynchronizer` recoge en el siguiente login todo lo que
     * quedó con `GUARDADO_EN_MICROSIP = 0`.
     */
    @Suppress(
        "TooGenericExceptionCaught"
    ) // encolar puede fallar de varias formas; ninguna toca el dinero ya commiteado.
    private fun encolarSubida(pagoId: String) {
        try {
            encolador.enqueue(pagoId)
        } catch (cancelada: CancellationException) {
            throw cancelada
        } catch (fallo: Throwable) {
            // Anti-PII: el nombre de la clase de la excepción, nunca su texto.
            telemetry.error(
                code = PagosTelemetria.CODE_ABONO_SIN_ENCOLAR,
                message = "no se pudo encolar la subida del abono; el abono si quedo escrito",
                props = mapOf(PagosTelemetria.PROP_EXCEPCION to fallo.javaClass.simpleName)
            )
        }
    }

    /**
     * Guarda los comprobantes del abono recién escrito. **Total: no propaga
     * nada** — es el segundo caso de la misma familia que la ubicación.
     *
     * ## Qué va en cada columna, y por qué importa
     *
     * - `ID` es el UUID de **la imagen**, el que el teléfono acuñó antes de
     *   abrir la cámara y el que viaja como `id_<n>`. Es lo que hace idempotente
     *   al reintento: sin él, el servidor inventa uno nuevo en cada intento
     *   (`parsePositionalImagenID`) y la misma foto sube dos veces.
     * - `PAGO_ID` es el `Payment.ID` — el mismo `abonoId` que ya viajó a
     *   `pedirUbicacion`. **No son la misma columna ni el mismo valor**, y
     *   cruzarlos es el defecto que este plan ya cazó siete veces.
     * - `ORDEN` es la posición en la lista, que es el orden de captura y el `n`
     *   del multipart.
     * - `SUBIDA_EN` nace `NULL`: nadie ha subido nada todavía. Lo estampa el
     *   worker, y solo por las que el servidor efectivamente recibió.
     *
     * Un fallo aquí **no puede** cambiar el resultado: el abono ya está en la
     * base, y devolver `FALLO_EL_GUARDADO` haría que la pantalla ofreciera
     * reintentar un cobro que ya ocurrió. Se reporta y se sigue.
     */
    @Suppress(
        "TooGenericExceptionCaught"
    ) // Room puede fallar de varias formas; ninguna toca el dinero ya commiteado.
    private suspend fun guardarComprobantes(abono: AbonoARegistrar) {
        if (abono.comprobantes.isEmpty()) return
        try {
            val creadaEn = AppTime.toWireFormat(clock.now())
            imagenes.insertAll(
                abono.comprobantes.mapIndexed { orden, comprobante ->
                    PaymentImageEntity(
                        ID = comprobante.id,
                        PAGO_ID = abono.abonoId,
                        URI = comprobante.archivo,
                        MIME = comprobante.mime,
                        ORDEN = orden,
                        CREADA_EN = creadaEn
                    )
                }
            )
        } catch (cancelada: CancellationException) {
            throw cancelada
        } catch (fallo: Throwable) {
            // Anti-PII: el nombre de la clase de la excepción, nunca su texto
            // (que podría arrastrar la ruta del archivo).
            telemetry.error(
                code = PagosTelemetria.CODE_ABONO_SIN_COMPROBANTES,
                message = "no se pudieron guardar los comprobantes; el abono si quedo escrito",
                props = mapOf(PagosTelemetria.PROP_EXCEPCION to fallo.javaClass.simpleName)
            )
        }
    }

    /**
     * Pide la ubicación del abono recién escrito. **Total: no propaga nada.**
     *
     * El id que viaja es el `Payment.ID` (`AbonoARegistrar.abonoId`), que es lo
     * que `PaymentDao.updateLocation` filtra — no el `DOCTO_CC_ACR_ID` de la
     * venta ni el `CLIENTE_ID`.
     *
     * Un fallo aquí no puede cambiar el resultado del registro: el abono ya
     * está en la base y devolver `FALLO_EL_GUARDADO` haría que la pantalla
     * ofreciera reintentar un cobro que ya ocurrió. Se reporta y se sigue.
     */
    @Suppress(
        "TooGenericExceptionCaught"
    ) // arrancar un servicio puede fallar de varias formas; ninguna toca el dinero.
    private fun pedirUbicacionDelAbono(pagoId: String) {
        try {
            pedirUbicacion(pagoId)
        } catch (cancelada: CancellationException) {
            throw cancelada
        } catch (fallo: Throwable) {
            // Anti-PII: el nombre de la clase de la excepción, nunca su texto.
            telemetry.error(
                code = PagosTelemetria.CODE_ABONO_SIN_UBICACION,
                message = "no se pudo pedir la ubicacion del abono; el abono si quedo escrito",
                props = mapOf(PagosTelemetria.PROP_EXCEPCION to fallo.javaClass.simpleName)
            )
        }
    }

    /**
     * ¿El monto excede el saldo REAL de la venta en este instante? El
     * `SALDO_REST` cruza a [Money] aquí, en el borde, con `Money.of(Double)`
     * (que usa `BigDecimal.valueOf`, nunca el constructor de `double`).
     *
     * Anti-PII: viajan los NOMBRES de los bloqueos, nunca el monto ni el saldo.
     */
    private fun sobrepasaElSaldo(abono: AbonoARegistrar, saldoRest: Double): Boolean {
        val bloqueos = SeguridadDelAbono.bloqueosDe(
            monto = abono.importe,
            saldo = Money.of(saldoRest)
        )
        if (bloqueos.isEmpty()) return false
        telemetry.error(
            code = PagosTelemetria.CODE_ABONO_BLOQUEADO_EN_ESCRITURA,
            message = "el saldo real de la venta no admite este abono; nada se escribio",
            props = mapOf(PagosTelemetria.PROP_BLOQUEOS to bloqueos.joinToString(",") { it.name })
        )
        return true
    }

    /**
     * La forma de cobro que va al schema. Se toma de `Constants`, que es de
     * donde la tomaba el retirado `NewPaymentDialog`, y se comprueba contra el
     * id del catálogo de
     * dominio: son el mismo número (157 / 52569) y esta línea existe para que
     * sigan siéndolo.
     */
    private fun formaCobroDe(abono: AbonoARegistrar): Int = when (abono.metodo) {
        com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro.TRANSFERENCIA ->
            Constants.PAGO_CON_TRANSFERENCIA_ID

        else -> Constants.PAGO_EN_EFECTIVO_ID
    }
}
